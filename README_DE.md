# InstaDownloader

Ein moderner, anonymer Instagram Downloader in Kotlin mit Compose Desktop.

## Funktionen

- **Login mit Benutzername & Passwort** - Session-basierte Authentifizierung
- **Stories herunterladen** - Alle aktiven Stories eines Benutzers
- **Highlights herunterladen** - Einzelne oder alle Highlight-Reels
- **Profilbilder herunterladen** - In HD-Qualität
- **Modernes Dark-Theme** - Instagram-inspiriertes Design mit Gradient-Akzenten
- **Fortschrittsanzeige** - Echtzeit-Download-Fortschritt

## Voraussetzungen

- JDK 17 oder höher
- Gradle (wird automatisch über Wrapper heruntergeladen)

## Starten

```bash
./gradlew run
```

## Build

```bash
# JAR erstellen
./gradlew packageUberJarForCurrentOS

# Nativen Installer erstellen
./gradlew packageDeb      # Linux
./gradlew packageMsi      # Windows
./gradlew packageDmg      # macOS
```

## Projektstruktur

```
src/main/kotlin/com/xenlon/instadownloader/
├── Main.kt                          # Anwendungseinstiegspunkt
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

- Die Anwendung speichert Downloads im Ordner `~/InstaDownloader/`
- Alle Daten werden nur lokal gespeichert
- Das Passwort wird nur für die Session-Authentifizierung verwendet und nicht gespeichert
