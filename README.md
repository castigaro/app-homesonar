# HomeSonar (Android)

Haushalts-Inventar mit KI-Bilderkennung: Dinge fotografieren, von einem
Vision-Modell erkennen lassen, mit ihrem Platz in der Wohnung speichern —
und später per Foto wiederfinden („Wo gehört das hin?“).

## Funktionen

- **Erfassen**: Foto aufnehmen → die KI erkennt die Gegenstände darauf →
  auswählen, Namen anpassen, Ort eintragen (z. B. „Wohnzimmer, Schrank
  unten rechts, Schublade“) → speichern.
- **Wiederfinden**: Einen einzelnen Gegenstand fotografieren → die App
  gleicht ihn mit den gespeicherten Gegenständen ab und zeigt den
  hinterlegten Ort an.
- **KI-Anbieter wählbar**: Anthropic (Claude, Standard `claude-opus-4-8`)
  oder OpenAI (Standard `gpt-4o-mini`). Der API-Key wird in den
  Einstellungen hinterlegt und nur lokal gespeichert; das Modell ist frei
  wählbar (z. B. `claude-haiku-4-5` für günstigere Analysen).
- **Lokale Daten**: Gegenstände und Fotos bleiben auf dem Gerät
  (JSON-Datei + app-privater Fotospeicher). Nur zur Analyse wird das
  jeweilige Foto an den gewählten KI-Anbieter übertragen.

## Technik

- Kotlin, klassische Views mit ViewBinding, Material Components
- Kamera über die System-Kamera-App (`ACTION_IMAGE_CAPTURE` via
  FileProvider) — keine Kamera-Berechtigung nötig
- Fotos werden vor dem API-Aufruf auf max. 1280 px verkleinert (JPEG 80)
- Anthropic Messages API bzw. OpenAI Chat Completions API per OkHttp
- minSdk 26 (Android 8.0), compileSdk 34, AGP 8.5

## Build

`./gradlew assembleDebug` in diesem Verzeichnis, oder in Android Studio
öffnen. Der CI-Workflow `build.yml` baut signierte APKs in das
GitHub-Release `latest`.

## Lizenz

Copyright © 2026 Torsten Klein

Dieses Projekt steht unter der **GNU Affero General Public License v3.0 oder
später** (AGPL-3.0-or-later), siehe [LICENSE](LICENSE): Wer den Code — auch als
Netzwerkdienst — weiterverwendet oder verändert, muss den Quellcode unter
derselben Lizenz offenlegen. Eine kommerzielle Lizenz ist auf Anfrage möglich.
