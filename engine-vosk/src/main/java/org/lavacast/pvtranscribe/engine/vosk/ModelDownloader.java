package org.lavacast.pvtranscribe.engine.vosk;

import org.lavacast.pvtranscribe.core.util.PlatformLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Finds the configured Vosk model on disk, downloading and unpacking it on first start if allowed.
 */
final class ModelDownloader {

    private final VoskEngineFactory.VoskSettings settings;
    private final PlatformLogger logger;

    ModelDownloader(VoskEngineFactory.VoskSettings settings, PlatformLogger logger) {
        this.settings = settings;
        this.logger = logger;
    }

    Path ensureModel() throws Exception {
        // an explicit path to a model folder
        Path explicit = Path.of(settings.model());
        if (explicit.isAbsolute() && Files.isDirectory(explicit)) {
            return requireModel(explicit);
        }

        Path folder = settings.modelsFolder();
        Path modelDir = folder.resolve(settings.model());
        if (Files.isDirectory(modelDir)) {
            return requireModel(modelDir);
        }

        if (!settings.autoDownload()) {
            throw new IllegalStateException("Vosk model not found: " + modelDir.toAbsolutePath()
                    + ". Download '" + settings.model() + "' from https://alphacephei.com/vosk/models, unzip it there, "
                    + "or set engine.vosk.auto-download to true.");
        }

        download(folder, modelDir);
        return requireModel(modelDir);
    }

    private Path requireModel(Path dir) {
        if (!VoskEngine.isModelDirectory(dir)) {
            // some archives contain one extra nested folder
            try (Stream<Path> children = Files.list(dir)) {
                Path nested = children.filter(Files::isDirectory).filter(VoskEngine::isModelDirectory).findFirst().orElse(null);
                if (nested != null) return nested;
            } catch (IOException ignored) {
            }
            throw new IllegalStateException("Folder " + dir.toAbsolutePath() + " does not look like a Vosk model "
                    + "(expected 'am', 'conf' or 'graph' inside).");
        }
        return dir;
    }

    private void download(Path folder, Path modelDir) throws Exception {
        Files.createDirectories(folder);
        String url = settings.downloadUrl().replace("{model}", settings.model());
        Path zip = folder.resolve(settings.model() + ".zip.part");
        Path tmpDir = folder.resolve(settings.model() + ".unpacking");

        logger.info("Vosk model '" + settings.model() + "' not found, downloading it from " + url + " ...");
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(30)).GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("Model download failed: HTTP " + response.statusCode() + " for " + url);
        }

        long total = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(zip)) {
            byte[] buffer = new byte[64 * 1024];
            long done = 0;
            int lastPercent = -1;
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
                done += read;
                if (total > 0) {
                    int percent = (int) (done * 100 / total);
                    if (percent / 20 != lastPercent / 20) {
                        lastPercent = percent;
                        logger.info("Downloading Vosk model: " + percent + "% (" + done / (1024 * 1024) + " MB)");
                    }
                }
            }
        }

        try {
            deleteRecursively(tmpDir);
            unzip(zip, tmpDir);
            // archives contain a top-level folder named like the model
            Path inner = tmpDir.resolve(settings.model());
            Path source = Files.isDirectory(inner) ? inner : tmpDir;
            Files.move(source, modelDir, StandardCopyOption.ATOMIC_MOVE);
            logger.info("Vosk model installed to " + modelDir.toAbsolutePath());
        } finally {
            Files.deleteIfExists(zip);
            deleteRecursively(tmpDir);
        }
    }

    private static void unzip(Path zip, Path target) throws IOException {
        Files.createDirectories(target);
        Path root = target.toAbsolutePath().normalize();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                Path out = root.resolve(entry.getName()).normalize();
                if (!out.startsWith(root)) {
                    throw new IOException("Refusing to unpack entry outside of the model folder: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}
