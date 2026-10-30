package com.logvex.crystal.ai.report;

import com.logvex.crystal.ai.AISettings;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * A minimal SMTP client, implemented directly over a socket so no extra dependency is added.
 *
 * <p>It supports plain, implicit-TLS (SMTPS) and STARTTLS connections, with optional
 * {@code AUTH LOGIN}. This is deliberately small: the client only ever sends a single plain-text
 * message to one configured recipient. Credentials come from the user's settings and are only
 * ever sent to the configured host.
 *
 * <p>Everything here runs on a background thread via the report service; a failure throws
 * {@link IOException} and the queue keeps the report for a retry.
 */
public final class SmtpEmailTransport implements EmailTransport {

    private static final int SOCKET_TIMEOUT_MS = 15_000;

    private final AISettings settings;
    private final String envelopeFrom;

    public SmtpEmailTransport(AISettings settings) {
        this.settings = settings;
        this.envelopeFrom = (settings.smtpFrom == null || settings.smtpFrom.isBlank())
                ? settings.smtpUser : settings.smtpFrom;
    }

    @Override
    public void send(String recipient, String subject, String body) throws IOException {
        if (settings.smtpHost == null || settings.smtpHost.isBlank()) {
            throw new IOException("No SMTP host configured");
        }
        if (envelopeFrom == null || envelopeFrom.isBlank()) {
            throw new IOException("No sender address configured");
        }
        boolean implicitTls = settings.smtpPort == 465;
        Socket socket = openSocket(implicitTls);
        try {
            Conversation conversation = new Conversation(socket);
            conversation.expect(220, "greeting");
            String hostName = localHostName();
            conversation.send("EHLO " + hostName);
            String capabilities = conversation.expectMultiline(250, "EHLO");

            if (!implicitTls && settings.smtpStartTls && capabilities.toUpperCase(Locale.ROOT).contains("STARTTLS")) {
                conversation.send("STARTTLS");
                conversation.expect(220, "STARTTLS");
                socket = upgradeToTls(socket);
                conversation = new Conversation(socket);
                conversation.send("EHLO " + hostName);
                conversation.expectMultiline(250, "EHLO after STARTTLS");
            }

            if (settings.smtpUser != null && !settings.smtpUser.isBlank()) {
                conversation.send("AUTH LOGIN");
                conversation.expect(334, "AUTH LOGIN");
                conversation.send(Base64.getEncoder().encodeToString(
                        settings.smtpUser.getBytes(StandardCharsets.UTF_8)));
                conversation.expect(334, "AUTH user");
                String password = settings.smtpPassword == null ? "" : settings.smtpPassword;
                conversation.send(Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8)));
                conversation.expect(235, "AUTH password");
            }

            conversation.send("MAIL FROM:<" + envelopeFrom + ">");
            conversation.expect(250, "MAIL FROM");
            conversation.send("RCPT TO:<" + recipient + ">");
            conversation.expect(250, "RCPT TO");
            conversation.send("DATA");
            conversation.expect(354, "DATA");

            String message = buildMessage(envelopeFrom, recipient, subject, body);
            conversation.writeData(message);
            conversation.expect(250, "message accepted");

            conversation.send("QUIT");
            // A QUIT reply is not required; some servers close immediately.
        } finally {
            closeQuietly(socket);
        }
    }

    private Socket openSocket(boolean implicitTls) throws IOException {
        Socket plain = new Socket();
        plain.connect(new InetSocketAddress(settings.smtpHost, settings.smtpPort), SOCKET_TIMEOUT_MS);
        plain.setSoTimeout(SOCKET_TIMEOUT_MS);
        if (implicitTls) {
            return upgradeToTls(plain);
        }
        return plain;
    }

    private Socket upgradeToTls(Socket socket) throws IOException {
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket tls = (SSLSocket) factory.createSocket(socket, settings.smtpHost, settings.smtpPort, true);
        tls.setSoTimeout(SOCKET_TIMEOUT_MS);
        tls.startHandshake();
        return tls;
    }

    /** Builds the RFC 5322 message. Lines are CRLF-terminated; dots are escaped in the body. */
    static String buildMessage(String from, String recipient, String subject, String body) {
        StringBuilder message = new StringBuilder(body.length() + 512);
        message.append("From: ").append(from).append("\r\n");
        message.append("To: ").append(recipient).append("\r\n");
        message.append("Subject: ").append(subject.replaceAll("[\\r\\n]", " ")).append("\r\n");
        message.append("MIME-Version: 1.0\r\n");
        message.append("Content-Type: text/plain; charset=UTF-8\r\n");
        message.append("Content-Transfer-Encoding: 8bit\r\n");
        message.append("\r\n");
        for (String line : body.replace("\r\n", "\n").split("\n", -1)) {
            message.append(line.startsWith(".") ? "." + line : line).append("\r\n");
        }
        return message.toString();
    }

    private static String localHostName() {
        String name = System.getenv("COMPUTERNAME");
        if (name == null || name.isBlank()) {
            name = System.getenv("HOSTNAME");
        }
        return name == null || name.isBlank() ? "localhost" : name.replaceAll("[^A-Za-z0-9.-]", "");
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Closing is best-effort.
        }
    }

    /** A tiny line-oriented SMTP conversation helper. */
    private static final class Conversation {
        private final BufferedReader reader;
        private final Writer writer;

        Conversation(Socket socket) throws IOException {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            this.reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII));
            this.writer = new OutputStreamWriter(out, StandardCharsets.US_ASCII);
        }

        void send(String line) throws IOException {
            writer.write(line);
            writer.write("\r\n");
            writer.flush();
        }

        /** Writes message data and the terminating dot, dot-stuffing already applied. */
        void writeData(String data) throws IOException {
            writer.write(data);
            if (!data.endsWith("\r\n")) {
                writer.write("\r\n");
            }
            writer.write(".\r\n");
            writer.flush();
        }

        void expect(int code, String stage) throws IOException {
            String line = reader.readLine();
            if (line == null) {
                throw new IOException("SMTP server closed during " + stage);
            }
            checkCode(line, code, stage);
        }

        /** Reads a possibly multi-line reply, returning the concatenated text. */
        String expectMultiline(int code, String stage) throws IOException {
            StringBuilder all = new StringBuilder();
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    throw new IOException("SMTP server closed during " + stage);
                }
                checkCode(line, code, stage);
                all.append(line).append('\n');
                // "250-" continues, "250 " ends.
                if (line.length() < 4 || line.charAt(3) != '-') {
                    break;
                }
            }
            return all.toString();
        }

        private static void checkCode(String line, int expected, String stage) throws IOException {
            if (line.length() < 3) {
                throw new IOException("Malformed SMTP reply during " + stage + ": " + line);
            }
            int code;
            try {
                code = Integer.parseInt(line.substring(0, 3));
            } catch (NumberFormatException e) {
                throw new IOException("Malformed SMTP reply during " + stage + ": " + line);
            }
            if (code != expected) {
                throw new IOException("SMTP " + stage + " failed: " + line);
            }
        }
    }
}
