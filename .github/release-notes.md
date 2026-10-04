## PV-Transcribe 1.0.1

`/vtt help` now shows links to GitHub, the author's Discord (**arab_kustam**) and the voice chat Discord; `website` and author added to plugin.yml. Everything else is the same as 1.0.0.

### Downloads

`PV-Transcribe-1.0.1.jar` is attached below.

### Compatibility

| | Supported |
|---|---|
| Server | Paper, Spigot, Purpur **1.20.2 – 1.21.11** (tested on 1.21.8) |
| Java | 17 or newer |
| Voice chat | Plasmo Voice **2.1+** |
| Players | only the Plasmo Voice mod, nothing extra |
| Not supported | Folia, Fabric/Forge servers, Simple Voice Chat (use SVC-Transcribe) |

On first start the server downloads the Vosk / ONNX Runtime libraries from Maven Central, and the plugin downloads the speech model.

### Installation

1. Install Plasmo Voice on the server.
2. Put `PV-Transcribe-1.0.1.jar` into `plugins/` and start the server.
3. Optional: for the best accuracy set a Deepgram key (`engine.deepgram.api-key`) and run `/vtt engine deepgram`.

### Changes

- Real-time speech-to-text with live (partial) and final results
- Speech bubbles above speakers (vanilla text displays, no client mod), shown only to players who can hear the speaker: proximity, pv-addon-groups, pv-addon-broadcast
- Up to 3 stacked bubbles, word limit per bubble, fade-out, copy of finished phrases in chat
- Bubble styles: `light`, `dark`, `glass`, `minimal` presets, plus colors, tail, alignment, padding and size
- Speech engines: Vosk (offline), T-one (offline, Russian), Deepgram and OpenAI (cloud), switchable with `/vtt engine`
- `/vtt set <option> <value>` changes any config option in game (Tab completion); `/vtt get` shows them
- `/vtt demo` shows a demo bubble without a microphone
- Numbers and math as digits and symbols (Russian), nickname matching for online players
- Developer API shared with SVC-Transcribe (Simple Voice Chat): speech start/end, partial/final transcripts, phrase triggers, subtitle processors, Bukkit events
- English and Russian messages (`locale: en|ru`)

Full documentation: [README](https://github.com/ArabKustam/Plasmo-Voice-Transcribe#readme) · [Русский](https://github.com/ArabKustam/Plasmo-Voice-Transcribe/blob/main/README.ru.md)
