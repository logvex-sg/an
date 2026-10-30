package com.logvex.crystal.ai.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Owns the on-disk lifecycle of a local model: locate, download (with progress and checksum
 * verification), validate the format, detect an incomplete download, load, and remove.
 *
 * <p>Downloading is never automatic — {@link #download} only runs when the user asks. A partial
 * download is written to a {@code .part} file and only moved into place after the size and
 * (when pinned) the SHA-256 match, so a truncated file can never be mistaken for a usable model.
 *
 * <p>The actual inference runtime is resolved reflectively in {@link #load}: the client ships no
 * native LLM binding, so a machine without one cleanly reports "runtime unavailable" and the
 * rules-based engine keeps working. This class is Minecraft-independent so it can be unit tested
 * with plain files.
 */
public final class ModelLoader {

    /** GGUF files begin with this magic. */
    private static final byte[] GGUF_MAGIC = {'G', 'G', 'U', 'F'};

    /** Progress callback; runs on the downloading thread. */
    public interface ProgressListener {
        void onProgress(long downloaded, long total);
    }

    /** Raised when a download or verification fails. */
    public static class DownloadException extends IOException {
        public DownloadException(String message) {
            super(message);
        }

        public DownloadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final Path directory;
    private final HttpClient http;

    public ModelLoader(Path directory) {
        this(directory, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    public ModelLoader(Path directory, HttpClient http) {
        this.directory = directory;
        this.http = http;
    }

    public Path directory() {
        return directory;
    }

    /** The final path a model would occupy once installed. */
    public Path fileFor(ModelSpec spec) {
        return directory.resolve(spec.fileName());
    }

    /** The in-progress path for a model download. */
    public Path partialFor(ModelSpec spec) {
        return directory.resolve(spec.fileName() + ".part");
    }

    /**
     * True when the model looks installed and usable: the file exists, is at least the expected
     * size (allowing for the odd byte), and begins with the GGUF magic.
     */
    public boolean isInstalled(ModelSpec spec) {
        Path file = fileFor(spec);
        try {
            if (!Files.isRegularFile(file)) {
                return false;
            }
            if (Files.size(file) < spec.sizeBytes() * 9 / 10) {
                return false;
            }
            return hasGgufMagic(file);
        } catch (IOException e) {
            return false;
        }
    }

    /** True when a partial download is present (an interrupted fetch). */
    public boolean hasPartialDownload(ModelSpec spec) {
        return Files.isRegularFile(partialFor(spec));
    }

    /** Deletes the installed file and any partial download. Best effort; returns true if gone. */
    public boolean remove(ModelSpec spec) {
        boolean ok = true;
        try {
            Files.deleteIfExists(fileFor(spec));
        } catch (IOException e) {
            ok = false;
        }
        try {
            Files.deleteIfExists(partialFor(spec));
        } catch (IOException e) {
            ok = false;
        }
        return ok;
    }

    /**
     * Downloads {@code spec} to {@link #partialFor}, verifies it, and atomically installs it.
     * On any failure the partial file is deleted so a retry starts clean.
     */
    public void download(ModelSpec spec, ProgressListener listener) throws DownloadException {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new DownloadException("Cannot create model directory: " + e.getMessage(), e);
        }
        Path partial = partialFor(spec);
        long total = spec.sizeBytes();
        long written = 0;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(spec.url()))
                    .timeout(Duration.ofMinutes(30))
                    .header("User-Agent", "LOGVEX-CRYSTAL-model-fetch")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() / 100 != 2) {
                throw new DownloadException("Model server returned HTTP " + response.statusCode());
            }
            long declared = response.headers().firstValueAsLong("content-length").orElse(-1L);
            if (declared > 0) {
                total = declared;
            }
            try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(partial)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new DownloadException("Download cancelled");
                    }
                    out.write(buffer, 0, read);
                    written += read;
                    if (listener != null) {
                        listener.onProgress(written, total);
                    }
                }
            }
            if (written == 0) {
                throw new DownloadException("Downloaded file is empty");
            }
            if (!hasGgufMagic(partial)) {
                throw new DownloadException("Downloaded file is not a GGUF model");
            }
            if (spec.hasPinnedHash()) {
                String actual = sha256(partial);
                if (!actual.equalsIgnoreCase(spec.sha256())) {
                    throw new DownloadException("Checksum mismatch (expected " + spec.sha256() + ", got " + actual + ")");
                }
            }
            Files.move(partial, fileFor(spec), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            deleteQuietly(partial);
            if (e instanceof DownloadException de) {
                throw de;
            }
            throw new DownloadException("Download failed: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            deleteQuietly(partial);
            throw new DownloadException("Download failed: " + e.getMessage(), e);
        }
    }

    /**
     * Resolves an inference runtime for the installed model.
     *
     * <p>No native LLM binding is bundled, so this returns a model that reports itself as
     * unavailable unless a compatible runtime is present on the classpath (resolved by name,
     * never by loading arbitrary user code). The manager treats "unavailable" as "fall back to
     * the rules engine".
     */
    public MiniAIModel load(ModelSpec spec, int threads) throws InferenceException {
        if (!isInstalled(spec)) {
            throw new InferenceException("Model file is not installed or is incomplete");
        }
        Path file = fileFor(spec);
        MiniAIModel reflective = LlamaRuntimeBinding.tryCreate(file, threads);
        if (reflective != null) {
            return reflective;
        }
        throw new InferenceException("No local inference runtime available for " + spec.fileName());
    }

    /** Streaming SHA-256 of a file, lower-case hex. */
    public static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 unavailable", e);
        }
    }

    /** True when the file starts with the GGUF magic bytes. */
    public static boolean hasGgufMagic(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] head = new byte[4];
            int read = in.read(head);
            if (read < 4) {
                return false;
            }
            for (int i = 0; i < 4; i++) {
                if (head[i] != GGUF_MAGIC[i]) {
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Writes {@code text} to {@code path} (test/utility helper). */
    public static void writeText(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, text, StandardCharsets.UTF_8);
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // The caller already failed; leaving a .part behind is harmless.
        }
    }
}
