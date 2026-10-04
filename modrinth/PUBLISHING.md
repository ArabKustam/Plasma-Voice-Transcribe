# Publishing on Modrinth

1. Build with `./gradlew build` and take `build/libs/PV-Transcribe-<version>.jar`, or download it from the GitHub release.
2. Create the project at https://modrinth.com/dashboard/projects:

   | Field | Value |
   |---|---|
   | Name | **PV-Transcribe** |
   | URL | `pv-transcribe` |
   | Summary | *Real-time speech-to-text for Plasmo Voice: live speech bubbles above players and an API for voice commands.* |
   | Type | Plugin, server-side only |
   | Icon | [`icon.png`](../icon.png) |
   | Description | paste [`description.md`](description.md); it already contains the English and Russian text |
   | Categories | Utility, Social, Management |
   | Links | Source `https://github.com/ArabKustam/Plasmo-Voice-Transcribe`, issues `https://github.com/ArabKustam/Plasmo-Voice-Transcribe/issues` |
   | License | MIT |

3. Upload the version:

   | Field | Value |
   |---|---|
   | Version number | `1.0.0` |
   | Loaders | Paper, Spigot, Purpur |
   | Game versions | 1.20.2 – 1.21.11 (tested on 1.21.8) |
   | Dependency | **Plasmo Voice**, required |
   | Changelog | the 1.0.0 section of [`CHANGELOG.md`](../CHANGELOG.md) |

4. Gallery: upload the in-game screenshots from [`docs/images`](../docs/images) (live subtitles, styles, stacked bubbles, chat).
