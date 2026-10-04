# Publishing on Modrinth

1. Build: `./gradlew build` and take `build/libs/PV-Transcribe-<version>.jar`, or download the jar from the GitHub release.
2. Create the project at https://modrinth.com/dashboard/projects:
   - Name: **PV-Transcribe**
   - URL: `pv-transcribe`
   - Summary: *Real-time speech-to-text for Plasmo Voice: speech bubbles above players and an API for voice commands.*
   - Type: Plugin
   - Icon: [`icon.png`](../icon.png)
   - Description: paste [`description.md`](description.md)
   - Categories: Utility, Social, Management
   - Environment: server-side only
   - Source code: `https://github.com/ArabKustam/PV-Transcribe`, issues: `.../issues`
   - License: MIT
3. Upload the version:
   - Version number: `1.0.0`
   - Loaders: Paper, Spigot, Purpur
   - Game versions: 1.20.2 – 1.21.11 (tested on 1.21.8)
   - Dependencies: **Plasmo Voice**, required
   - Changelog: the 1.0.0 section of [`CHANGELOG.md`](../CHANGELOG.md)
4. Gallery: add a couple of screenshots of speech bubbles in game.
