# Changelog

## 1.0.0

First release.

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
