package pl.szablix.ciapongiupdater;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class UpdateManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("CiapongiUpdater-UpdateManager");
    private static final String MANIFEST_URL = "https://raw.githubusercontent.com/KrolestwoSZABLIXa/Ciapongi-RP/main/manifest.json";
    private static final String RAW_BASE_URL = "https://raw.githubusercontent.com/KrolestwoSZABLIXa/Ciapongi-RP/main/";
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("ciapongiupdater/latestversion.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // 120-byte dummy JAR trick from AutoModpack to bypass Windows file locks
    private static final byte[] DUMMY_JAR = {
            80, 75, 3, 4, 20, 0, 8, 8, 8, 0, 89, 116, -44, 86, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 20, 0, 4, 0, 77, 69, 84,
            65, 45, 73, 78, 70, 47, 77, 65, 78, 73, 70, 69, 83, 84, 46, 77,
            70, -2, -54, 0, 0, -13, 77, -52, -53, 76, 75, 45, 46, -47, 13, 75,
            45, 42, -50, -52, -49, -77, 82, 48, -44, 51, -32, -27, -30, -27, 2, 0,
            80, 75, 7, 8, -78, 127, 2, -18, 27, 0, 0, 0, 25, 0, 0, 0,
            80, 75, 1, 2, 20, 0, 20, 0, 8, 8, 8, 0, 89, 116, -44, 86,
            -78, 127, 2, -18, 27, 0, 0, 0, 25, 0, 0, 0, 20, 0, 4, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 77, 69,
            84, 65, 45, 73, 78, 70, 47, 77, 65, 78, 73, 70, 69, 83, 84, 46,
            77, 70, -2, -54, 0, 0, 80, 75, 5, 6, 0, 0, 0, 0, 1, 0,
            1, 0, 70, 0, 0, 0, 97, 0, 0, 0, 0, 0,
    };

    public volatile float progress = 0;
    public volatile String statusKey = "status.checking";
    public volatile String statusArg = "";
    public volatile String speed = "";
    public volatile String sizeInfo = "";
    public volatile boolean isWorking = true;
    public volatile boolean updateAvailable = false;
    public volatile boolean needsRestart = false;
    
    private Manifest manifest;
    private final boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

    public void checkForUpdates() {
        statusKey = "status.checking";
        statusArg = "";
        isWorking = true;
        try {
            cleanupDummyFiles();

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(MANIFEST_URL)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                manifest = GSON.fromJson(response.body(), Manifest.class);
                List<ManifestFile> toDownload = getFilesToUpdate();
                
                if (!toDownload.isEmpty()) {
                    updateAvailable = true;
                    statusKey = "status.found";
                    statusArg = String.valueOf(toDownload.size());
                    Thread.sleep(1500);
                } else {
                    statusKey = "status.uptodate";
                    isWorking = false;
                }
            } else {
                statusKey = "status.failed";
                isWorking = false;
            }
        } catch (Exception e) {
            LOGGER.error("Error checking for updates", e);
            statusKey = "status.failed";
            isWorking = false;
        }
    }

    private void cleanupDummyFiles() {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        String[] dirs = {"mods", "resourcepacks"};
        for (String dir : dirs) {
            Path path = gameDir.resolve(dir);
            if (!Files.exists(path)) continue;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path file : stream) {
                    if (Files.isRegularFile(file) && Files.size(file) == DUMMY_JAR.length) {
                        if (java.util.Arrays.equals(Files.readAllBytes(file), DUMMY_JAR)) {
                            Files.deleteIfExists(file);
                            LOGGER.info("Cleaned up dummy file: " + file.getFileName());
                        }
                    }
                }
            } catch (IOException e) {
                LOGGER.error("Error during dummy cleanup", e);
            }
        }
    }

    private List<ManifestFile> getFilesToUpdate() {
        List<ManifestFile> toUpdate = new ArrayList<>();
        Path gameDir = FabricLoader.getInstance().getGameDir();

        for (ManifestFile mFile : manifest.files) {
            Path localPath = gameDir.resolve(mFile.path);
            if (!Files.exists(localPath)) {
                toUpdate.add(mFile);
                continue;
            }
            try {
                String localHash = calculateHash(localPath);
                if (!localHash.equalsIgnoreCase(mFile.hash)) {
                    toUpdate.add(mFile);
                }
            } catch (Exception e) {
                toUpdate.add(mFile);
            }
        }
        return toUpdate;
    }

    private String calculateHash(Path path) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        try (InputStream is = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : digest.digest()) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public void performUpdate() {
        if (manifest == null) return;

        try {
            MusicManager.playUpdateMusic();
            List<ManifestFile> toDownload = getFilesToUpdate();
            long totalDownloadSize = toDownload.stream().mapToLong(f -> f.size).sum();
            long downloadedTotal = 0;

            // Remove files not in manifest
            removeUnexpectedFiles();

            statusKey = "status.downloading";
            progress = 0;

            for (int i = 0; i < toDownload.size(); i++) {
                ManifestFile mFile = toDownload.get(i);
                statusArg = (i + 1) + "/" + toDownload.size();
                
                Path target = FabricLoader.getInstance().getGameDir().resolve(mFile.path);
                Files.createDirectories(target.getParent());
                
                downloadFileGranular(RAW_BASE_URL + mFile.path, target, downloadedTotal, totalDownloadSize);
                downloadedTotal += mFile.size;
            }

            List<String> currentFiles = new ArrayList<>();
            for (ManifestFile mFile : manifest.files) {
                currentFiles.add(mFile.path);
            }
            saveUpdateInfo(currentFiles);

            statusKey = "status.complete";
            statusArg = "";
            needsRestart = true;
            MusicManager.stop();
        } catch (Exception e) {
            LOGGER.error("Update failed", e);
            statusKey = "status.failed";
            MusicManager.stop();
        } finally {
            isWorking = false;
        }
    }

    private void removeUnexpectedFiles() {
        if (!Files.exists(CONFIG_PATH)) return;

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            UpdateInfo oldInfo = GSON.fromJson(reader, UpdateInfo.class);
            if (oldInfo == null || oldInfo.files == null) return;

            for (String oldFilePath : oldInfo.files) {
                boolean stillInManifest = false;
                for (ManifestFile mFile : manifest.files) {
                    if (mFile.path.equals(oldFilePath)) {
                        stillInManifest = true;
                        break;
                    }
                }

                if (!stillInManifest) {
                    Path toDelete = FabricLoader.getInstance().getGameDir().resolve(oldFilePath);
                    if (Files.exists(toDelete)) {
                        smartDelete(toDelete);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Error reading old update info for cleanup", e);
        }
    }

    private void saveUpdateInfo(List<String> files) throws IOException {
        Files.createDirectories(CONFIG_PATH.getParent());
        UpdateInfo info = new UpdateInfo();
        info.files = files;

        try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(info, writer);
        }
    }

    private static class UpdateInfo {
        List<String> files;
    }

    private void smartDelete(Path path) {
        try {
            Files.deleteIfExists(path);
            LOGGER.info("Deleted: " + path.getFileName());
        } catch (IOException e) {
            if (isWindows) {
                try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
                    fos.write(DUMMY_JAR);
                    fos.flush();
                    LOGGER.info("Windows Lock: Replaced with dummy: " + path.getFileName());
                } catch (IOException ex) {
                    LOGGER.error("Failed to dummy file: " + path, ex);
                }
            }
        }
    }

    private void downloadFileGranular(String url, Path target, long alreadyDownloaded, long totalSize) throws Exception {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        byte[] data;
        try (InputStream is = response.body()) {
            data = is.readAllBytes();
        }

        try {
            Files.write(target, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            if (isWindows) {
                smartDelete(target);
                Files.write(target, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } else {
                throw e;
            }
        }

        if (totalSize > 0) {
            progress = (float) (alreadyDownloaded + data.length) / totalSize;
        }
    }

    private static class Manifest {
        List<ManifestFile> files;
    }

    private static class ManifestFile {
        String path;
        String hash;
        long size;
    }
}
