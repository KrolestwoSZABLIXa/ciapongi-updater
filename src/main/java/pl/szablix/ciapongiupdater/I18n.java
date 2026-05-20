package pl.szablix.ciapongiupdater;

import net.minecraft.client.MinecraftClient;

public class I18n {
    private static Boolean isPolishCache = null;

    public static String get(String key, String... args) {
        if (isPolishCache == null) {
            try {
                isPolishCache = MinecraftClient.getInstance().getLanguageManager().getLanguage().equals("pl_pl");
            } catch (Exception e) {
                isPolishCache = false;
            }
        }
        boolean isPolish = isPolishCache;
        
        String text = switch (key) {
            case "status.checking" -> isPolish ? "Sprawdzanie aktualizacji..." : "Checking updates...";
            case "status.found" -> isPolish ? "Znaleziono wersje: %s" : "Found version: %s";
            case "status.uptodate" -> isPolish ? "Wersja jest aktualna." : "Up to date.";
            case "status.downloading" -> isPolish ? "Pobieranie (%s)..." : "Downloading (%s)...";
            case "status.removing" -> isPolish ? "Usuwanie starych plikow..." : "Removing old files...";
            case "status.extracting" -> isPolish ? "Rozpakowywanie..." : "Extracting files...";
            case "status.complete" -> isPolish ? "Aktualizacja zakonczona!" : "Update complete!";
            case "status.restart" -> isPolish ? "Zrestartuj gre, aby zastosowac zmiany." : "Restart the game to apply changes.";
            case "status.failed" -> isPolish ? "Blad aktualizacji." : "Update failed.";
            case "ui.close" -> isPolish ? "Zamknij gre" : "Close game";
            default -> key;
        };

        if (args != null && args.length > 0 && args[0] != null && !args[0].isEmpty()) {
            try {
                return String.format(text, (Object[]) args);
            } catch (Exception e) {
                return text.replace("%s", args[0]);
            }
        }
        return text.replace("%s", "");
    }
}
