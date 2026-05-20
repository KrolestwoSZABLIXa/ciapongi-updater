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
    private static final String GITHUB_API_URL = "https://api.github.com/repos/KrolestwoSZABLIXa/Ciapongi-RP/releases/latest";
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("ciapongiupdater/latestversion.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public volatile float progress = 0;
    public volatile String statusKey = "status.checking";
    public volatile String statusArg = "";
    public volatile String speed = "";
    public volatile String sizeInfo = "";
    public volatile boolean isWorking = true;
    public volatile boolean updateAvailable = false;
    public volatile boolean needsRestart = false;
    public volatile String latestVersionTag = "";
    public volatile String downloadUrl = "";

    private final Map<Path, Path> pendingOperations = new LinkedHashMap<>();
    private final boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

    public void checkForUpdates() {
        statusKey = "status.checking";
        statusArg = "";
        isWorking = true;
        try {
            // ... (rest of the check logic)

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API_URL))
                    .header("Accept", "application/vnd.github.v3+json")
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                Map<String, Object> release = GSON.fromJson(response.body(), Map.class);
                latestVersionTag = (String) release.get("tag_name");

                List<Map<String, Object>> assets = (List<Map<String, Object>>) release.get("assets");
                for (Map<String, Object> asset : assets) {
                    String name = (String) asset.get("name");
                    if (name.endsWith(".zip")) {
                        downloadUrl = (String) asset.get("browser_download_url");
                        break;
                    }
                }

                String currentVersion = getCurrentVersion();
                if (!latestVersionTag.equals(currentVersion)) {
                    updateAvailable = true;
                    statusKey = "status.found";
                    statusArg = latestVersionTag;
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

    private String getCurrentVersion() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                UpdateInfo info = GSON.fromJson(reader, UpdateInfo.class);
                return info.version;
            } catch (IOException e) {
                LOGGER.error("Error reading config", e);
            }
        }
        return "none";
    }

    public void performUpdate() {
        if (downloadUrl.isEmpty()) {
            isWorking = false;
            return;
        }

        try {
            MusicManager.playUpdateMusic();
            statusKey = "status.downloading";
            statusArg = latestVersionTag;
            progress = 0;
            // ... (rest of method)

            Path tempZip = Files.createTempFile("ciapongi-update", ".zip");
            downloadFile(downloadUrl, tempZip);

            speed = "";
            sizeInfo = "";
            statusKey = "status.removing";
            statusArg = "";
            removeOldFiles();

            statusKey = "status.extracting";
            statusArg = "";
            List<String> newFiles = extractZip(tempZip);

            saveUpdateInfo(latestVersionTag, newFiles);

            Files.deleteIfExists(tempZip);

            if (!pendingOperations.isEmpty() && isWindows) {
                scheduleWindowsUpdate();
            }

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

    private void scheduleWindowsUpdate() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Path gameDir = FabricLoader.getInstance().getGameDir();
                Path script = gameDir.resolve("ciapongi-updater-cleanup.bat");
                StringBuilder sb = new StringBuilder();
                sb.append("@echo off\n");
                sb.append("chcp 65001 > nul\n");
                sb.append("set RETRIES=0\n");
                sb.append(":retry_loop\n");
                sb.append("set /a RETRIES+=1\n");
                sb.append("if %RETRIES% gtr 30 goto end\n");
                sb.append("set SUCCESS=1\n");
                
                for (Map.Entry<Path, Path> entry : pendingOperations.entrySet()) {
                    String target = entry.getKey().toAbsolutePath().toString();
                    Path sourcePath = entry.getValue();
                    
                    sb.append("if exist \"").append(target).append("\" (\n");
                    sb.append("  del /f /q \"").append(target).append("\"\n");
                    sb.append("  if exist \"").append(target).append("\" set SUCCESS=0\n");
                    sb.append(")\n");
                    
                    if (sourcePath != null) {
                        String source = sourcePath.toAbsolutePath().toString();
                        sb.append("if exist \"").append(source).append("\" (\n");
                        sb.append("  if not exist \"").append(target).append("\" (\n");
                        sb.append("    move /y \"").append(source).append("\" \"").append(target).append("\"\n");
                        sb.append("    if not exist \"").append(target).append("\" set SUCCESS=0\n");
                        sb.append("  )\n");
                        sb.append(")\n");
                    }
                }
                
                sb.append("if %SUCCESS% == 0 (\n");
                sb.append("  timeout /t 1 /nobreak > nul\n");
                sb.append("  goto retry_loop\n");
                sb.append(")\n");
                sb.append(":end\n");
                sb.append("del \"%~f0\"\n");
                
                Files.writeString(script, sb.toString());

                Runtime.getRuntime().exec("cmd /c start /min \"\" \"" + script.toAbsolutePath() + "\"");
                LOGGER.info("Windows update script scheduled at: " + script.toAbsolutePath());
            } catch (Exception e) {
                LOGGER.error("Failed to schedule Windows update", e);
            }
        }));
    }

    private void downloadFile(String url, Path target) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        
        if (contentLength > 0) {
            sizeInfo = String.format(Locale.US, "%.2f MB", contentLength / 1024.0 / 1024.0);
        }

        try (InputStream is = response.body();
             OutputStream os = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192];
            long downloaded = 0;
            int read;
            long startTime = System.currentTimeMillis();
            long lastUpdate = startTime;
            long lastDownloaded = 0;

            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
                downloaded += read;
                
                long now = System.currentTimeMillis();
                if (now - lastUpdate > 500) { // Update every 500ms
                    if (contentLength > 0) {
                        progress = (float) downloaded / contentLength * 0.5f;
                    }
                    
                    double seconds = (now - lastUpdate) / 1000.0;
                    double currentSpeed = (downloaded - lastDownloaded) / seconds / 1024.0 / 1024.0; // MB/s
                    speed = String.format(Locale.US, "%.2f MB/s", currentSpeed);
                    
                    lastUpdate = now;
                    lastDownloaded = downloaded;
                }
            }
        }
    }

    private void removeOldFiles() {
        if (!Files.exists(CONFIG_PATH)) return;

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            UpdateInfo info = GSON.fromJson(reader, UpdateInfo.class);
            Path gameDir = FabricLoader.getInstance().getGameDir();

            for (String filePath : info.files) {
                Path path = gameDir.resolve(filePath);
                if (Files.exists(path)) {
                    try {
                        Files.delete(path);
                        LOGGER.info("Deleted old file: " + filePath);
                    } catch (IOException e) {
                        if (isWindows) {
                            pendingOperations.put(path, null);
                            LOGGER.info("Scheduled deletion for locked file: " + filePath);
                        } else {
                            LOGGER.error("Failed to delete " + filePath, e);
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Error removing old files", e);
        }
    }

    private List<String> extractZip(Path zipPath) throws IOException {
        List<String> extractedFiles = new ArrayList<>();
        Path gameDir = FabricLoader.getInstance().getGameDir();
        long totalSize = Files.size(zipPath); // Approximate
        long extractedSize = 0;

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                
                // Skip Fabric API to prevent duplicates (it's required by this mod anyway)
                String fileName = name.contains("/") ? name.substring(name.lastIndexOf("/") + 1) : name;
                if (fileName.toLowerCase().startsWith("fabric-api")) {
                    LOGGER.info("Skipping Fabric API: " + name);
                    zis.closeEntry();
                    continue;
                }

                if (!entry.isDirectory()) {
                    Path target = gameDir.resolve(name);
                    Files.createDirectories(target.getParent());
                    
                    byte[] data = zis.readAllBytes();
                    
                    try {
                        Files.write(target, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                        LOGGER.info("Extracted: " + name);
                    } catch (IOException e) {
                        if (isWindows) {
                            Path updatedPath = target.resolveSibling(target.getFileName().toString() + ".updated");
                            Files.write(updatedPath, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                            pendingOperations.put(target, updatedPath);
                            LOGGER.info("Scheduled move for locked file: " + name);
                        } else {
                            throw e;
                        }
                    }
                    extractedFiles.add(name);
                }
                extractedSize += entry.getCompressedSize();
                progress = 0.5f + ((float) extractedSize / totalSize * 0.5f); // Last 50% for extraction
                zis.closeEntry();
            }
        }
        return extractedFiles;
    }

    private void saveUpdateInfo(String version, List<String> files) throws IOException {
        Files.createDirectories(CONFIG_PATH.getParent());
        UpdateInfo info = new UpdateInfo();
        info.version = version;
        info.files = files;

        try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(info, writer);
        }
    }

    private static class UpdateInfo {
        String version;
        List<String> files;
    }
}
