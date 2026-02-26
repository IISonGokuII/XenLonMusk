# InstaDownloader

Eine Android-App zum Herunterladen von Instagram-Inhalten. Geschrieben in Kotlin mit Jetpack Compose.

## Funktionen

- **Login mit Benutzername & Passwort** - Session-basierte Authentifizierung
- **Anonymer Modus** - Profilbilder und gepostete Bilder ohne Login (nur öffentliche Profile)
- **Stories herunterladen** - Alle aktiven Stories eines Benutzers
- **Highlights herunterladen** - Einzelne oder alle Highlight-Reels
- **Profilbilder herunterladen** - In HD-Qualität
- **Gepostete Bilder/Videos** - Alle Posts inkl. Karussells
- **Archiv herunterladen** - Deine eigenen archivierten Posts (nur eigener Account)
- **Modernes Dark-Theme** - Instagram-inspiriertes Design mit Gradient-Akzenten
- **Fortschrittsanzeige** - Echtzeit-Download-Fortschritt

## APK herunterladen

Die neueste APK findest du unter [Releases](../../releases) oder in den [GitHub Actions](../../actions) Artefakten.

## Voraussetzungen zum Bauen

- JDK 17 oder höher
- Android SDK 34

## Build

```bash
# Debug-APK erstellen
./gradlew assembleDebug

# Release-APK erstellen
./gradlew assembleRelease
```

Die APK findest du anschließend unter `build/outputs/apk/`.

## Projektstruktur

```
src/main/kotlin/com/xenlon/instadownloader/
├── MainActivity.kt                  # Android Activity (Einstiegspunkt)
├── model/
│   └── InstagramModels.kt           # Datenmodelle
├── service/
│   ├── InstagramService.kt          # Instagram API-Kommunikation
│   └── DownloadManager.kt           # Download-Verwaltung
└── ui/
    ├── Theme.kt                     # Farben und Theme-Definition
    ├── LoginScreen.kt               # Login-Bildschirm
    ├── MainScreen.kt                # Hauptbildschirm
    └── AppViewModel.kt              # Anwendungslogik
```

## Hinweise

- Die App speichert Downloads im Ordner `Downloads/InstaDownloader/`
- Alle Daten werden nur lokal auf deinem Gerät gespeichert
- Das Passwort wird nur für die Session-Authentifizierung verwendet und nicht gespeichert
- Archivierte Posts können nur für deinen eigenen Account heruntergeladen werden
