# Ciapongi Updater 🚂

![Minecraft Version](https://img.shields.io/badge/Minecraft-1.20.1-blue?style=flat-square&logo=minecraft)
![Fabric](https://img.shields.io/badge/Loader-Fabric-orange?style=flat-square)
![License](https://img.shields.io/badge/License-MIT-green?style=flat-square)
![Version](https://img.shields.io/badge/Version-1.0.0-blue?style=flat-square)

**Ciapongi Updater** to wyspecjalizowany mod do Minecrafta (Fabric), zaprojektowany w celu automatyzacji aktualizacji paczek zasobów (Resource Packs) oraz plików modów dla graczy serwera Ciapongi.

## ✨ Główne Funkcje

*   **Custom Splash Screen:** Unikalny ekran ładowania z animacją pociągu i paskiem postępu.
*   **Auto-Update:** Automatyczne pobieranie najnowszych wersji plików bezpośrednio z GitHub Releases.
*   **Windows-Safe Updates:** Autorski system obejścia blokady plików na systemach Windows (obsługa `.updated` i skryptów czyszczących po restarcie).
*   **I18n:** Pełne wsparcie dla języka polskiego i angielskiego.
*   **Klimatyczna Muzyka:** Dedykowany podkład dźwiękowy podczas procesu aktualizacji.

## 🛠️ Instalacja

1.  Upewnij się, że masz zainstalowany **Fabric Loader** dla wersji **1.20.1**.
2.  Pobierz najnowszy plik `.jar` z sekcji [Releases](https://github.com/KrolestwoSZABLIXa/ciapongi-updater/releases).
3.  Umieść plik w folderze `mods`.
4.  Przy pierwszym uruchomieniu mod automatycznie sprawdzi dostępność najnowszej paczki zasobów.

## ⚙️ Rozwój (Development)

Mod korzysta z Gradle. Aby zbudować projekt lokalnie:

```bash
git clone https://github.com/KrolestwoSZABLIXa/ciapongi-updater.git
cd ciapongi-updater
./gradlew build
```

## 📄 Licencja

Projekt udostępniany na licencji **MIT**. Zobacz plik [LICENSE](LICENSE) po szczegóły.

---
*Stworzone z pasją dla społeczności Ciapongi.*
