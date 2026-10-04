# PV-Transcribe

**See what people say in voice chat.** PV-Transcribe turns [Plasmo Voice](https://modrinth.com/plugin/plasmo-voice) speech into text in real time and shows it as speech bubbles above the speaker's head. The text grows word by word while they talk.

Players don't need any extra mod or resource pack to see the bubbles.

## Features

- **Live subtitles.** The text appears while the player is still speaking, not seconds later.
- **Follows the voice chat.** Only players who can actually hear the speaker see the bubble. Proximity distance, [pv-addon-groups](https://modrinth.com/plugin/pv-addon-groups) and [pv-addon-broadcast](https://modrinth.com/plugin/pv-addon-broadcast) are all taken into account.
- **Stacked bubbles.** Up to 3 per player: new phrases appear at the head and older ones float up and fade out. Long speech is split into several bubbles.
- **Your style.** Light, dark, glass or minimal presets, or set your own colors, tail, alignment, padding and size.
- **Chat copy.** Every finished phrase also goes to the chat of the players who heard it.
- **Choose your engine.** Switch in game with `/pvt engine`:
  - **Vosk**: free, runs on your server, 20+ languages
  - **T-one**: free, runs on your server, Russian
  - **Deepgram**: cloud, most accurate, punctuation, player names spelled right (free credit for new accounts)
  - **OpenAI**: cloud, any language, automatic language detection
- **Smart text.** Online players' nicknames are recognised. In Russian, spoken numbers and math become digits and symbols ("two plus two" → "2 + 2").
- **Admin tools.** Permissions, per-player and per-world switches, `/pvt status`, English and Russian messages.
- **Lag-free.** Recognition never runs on the main thread, every speaker is processed independently, and there is built-in overload protection.
- **Privacy.** Voice is never saved to disk. Cloud engines send audio to the provider only while someone speaks.

## For developers

A voice-chat-independent API: speech start and end, live and final text, phrase triggers ("say *fireball* to cast it"), subtitle filters for moderation, plus Bukkit events. See [GitHub](https://github.com/ArabKustam/PV-Transcribe).

## Requirements

- Paper / Spigot / Purpur **1.20.2+**, Java 17+
- **Plasmo Voice 2.1+**
- Internet on first start: libraries and the speech model are downloaded automatically

Not supported yet: Simple Voice Chat, Folia.
