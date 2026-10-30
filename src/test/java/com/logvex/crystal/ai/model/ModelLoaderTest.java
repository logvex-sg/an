package com.logvex.crystal.ai.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Model file lifecycle: format validation, download with checksum verification, incomplete
 * download detection and removal. The download test uses a real local HTTP server, so the
 * actual {@code java.net.http} path is exercised rather than a stub.
 */
class ModelLoaderTest {

    @TempDir
    Path dir;

    private HttpServer server;
    private final AtomicLong served = new AtomicLong();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/good.gguf", exchange -> {
            byte[] body = ggufBytes(4096);
            served.incrementAndGet();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.createContext("/notgguf.bin", exchange -> {
            byte[] body = "this is not a model".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.createContext("/missing.gguf", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private static byte[] ggufBytes(int size) {
        byte[] bytes = new byte[size];
        bytes[0] = 'G';
        bytes[1] = 'G';
        bytes[2] = 'U';
        bytes[3] = 'F';
        for (int i = 4; i < size; i++) {
            bytes[i] = (byte) (i % 251);
        }
        return bytes;
    }

    private ModelSpec spec(String path, long size, String hash) {
        return new ModelSpec("test", "Test", "", url(path), "test.gguf", size, hash,
                "Apache-2.0", "Q4_K_M", "0.36B", 2048);
    }

    @Test
    void detectsGgufMagic() throws IOException {
        Path good = dir.resolve("good.gguf");
        Files.write(good, ggufBytes(128));
        assertTrue(ModelLoader.hasGgufMagic(good));

        Path bad = dir.resolve("bad.gguf");
        Files.writeString(bad, "nope");
        assertFalse(ModelLoader.hasGgufMagic(bad));
    }

    @Test
    void isInstalledRequiresCorrectSizeAndMagic() throws IOException {
        ModelLoader loader = new ModelLoader(dir);
        ModelSpec spec = spec("/good.gguf", 4000, "");
        assertFalse(loader.isInstalled(spec), "not downloaded yet");

        Files.write(dir.resolve(spec.fileName()), ggufBytes(4096));
        assertTrue(loader.isInstalled(spec));

        Files.write(dir.resolve(spec.fileName()), "G".getBytes(StandardCharsets.UTF_8));
        assertFalse(loader.isInstalled(spec), "too small / wrong magic");
    }

    @Test
    void downloadInstallsVerifiedFile() throws IOException {
        ModelLoader loader = new ModelLoader(dir);
        ModelSpec spec = spec("/good.gguf", 4096, "");
        loader.download(spec, null);
        assertTrue(loader.isInstalled(spec));
        assertFalse(loader.hasPartialDownload(spec));
        assertEquals(1, served.get());
    }

    @Test
    void downloadVerifiesPinnedChecksum() throws IOException {
        ModelLoader loader = new ModelLoader(dir);
        // Compute the real hash of the served bytes, then pin it.
        Path tmp = dir.resolve("expected.bin");
        Files.write(tmp, ggufBytes(4096));
        String realHash = ModelLoader.sha256(tmp);
        Files.delete(tmp);

        ModelSpec good = spec("/good.gguf", 4096, realHash);
        loader.download(good, null);
        assertTrue(loader.isInstalled(good));

        ModelSpec wrong = new ModelSpec("t2", "T", "", url("/good.gguf"), "wrong.gguf", 4096,
                "0".repeat(64), "Apache-2.0", "Q4_K_M", "0.36B", 2048);
        ModelLoader.DownloadException ex = assertThrows(ModelLoader.DownloadException.class,
                () -> loader.download(wrong, null));
        assertTrue(ex.getMessage().toLowerCase().contains("checksum"));
        assertFalse(loader.hasPartialDownload(wrong), "failed download leaves no partial");
    }

    @Test
    void downloadRejectsNonGgufAndCleansUp() {
        ModelLoader loader = new ModelLoader(dir);
        ModelSpec spec = spec("/notgguf.bin", 19, "");
        assertThrows(ModelLoader.DownloadException.class, () -> loader.download(spec, null));
        assertFalse(loader.hasPartialDownload(spec));
        assertFalse(loader.isInstalled(spec));
    }

    @Test
    void downloadReportsHttpError() {
        ModelLoader loader = new ModelLoader(dir);
        ModelSpec spec = spec("/missing.gguf", 10, "");
        ModelLoader.DownloadException ex = assertThrows(ModelLoader.DownloadException.class,
                () -> loader.download(spec, null));
        assertTrue(ex.getMessage().contains("404"));
    }

    @Test
    void removeDeletesInstalledAndPartial() throws IOException {
        ModelLoader loader = new ModelLoader(dir);
        ModelSpec spec = spec("/good.gguf", 4096, "");
        Files.write(dir.resolve(spec.fileName()), ggufBytes(4096));
        Files.writeString(loader.partialFor(spec), "partial");
        assertTrue(loader.remove(spec));
        assertFalse(Files.exists(dir.resolve(spec.fileName())));
        assertFalse(loader.hasPartialDownload(spec));
    }

    @Test
    void loadFailsWhenFileNotInstalled() {
        ModelLoader loader = new ModelLoader(dir);
        ModelSpec spec = spec("/good.gguf", 4096, "");
        assertThrows(InferenceException.class, () -> loader.load(spec, 1));
    }

    @Test
    void catalogueEntriesAreSmallAndPermissive() {
        for (ModelSpec s : ModelCatalog.all()) {
            assertTrue(s.sizeMegabytes() < 600, s.id() + " must stay small");
            assertTrue(s.license().toLowerCase().contains("apache"), s.id() + " needs a permissive licence");
            assertEquals("Q4_K_M", s.quantization());
        }
        assertNotNull(ModelCatalog.byId(ModelCatalog.defaultId()).orElse(null));
    }
}
