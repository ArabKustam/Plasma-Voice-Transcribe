# PV-Transcribe

**Real-time speech-to-text for [Plasmo Voice](https://modrinth.com/plugin/plasmo-voice).** Players talk in voice chat, and everyone who can hear them sees what they say as speech bubbles above their head. The text grows word by word while they speak. Other plugins can react to what players say through a simple API.

[Русская версия](README.ru.md)

- Live subtitles: the text appears and updates while the player is still speaking, not seconds after
- Speech bubbles above the speaker, made with text displays: players don't need any mod or resource pack to see them
- Visibility follows Plasmo Voice: only players who can hear the speaker see the bubble (proximity distance, pv-addon-groups, pv-addon-broadcast)
- Up to 3 stacked bubbles per player, a word limit per bubble, fade-out, and a copy of each phrase in chat
- Fully configurable look: presets (light, dark, glass, minimal), colors, tail, alignment, padding, size
- Several speech engines, switchable in game with `/pvt engine`:

| Engine | Where it runs | Languages | Notes |
|---|---|---|---|
| `vosk` | on your server, free | 20+ | Lightweight; picks words from a dictionary, so names and rare words suffer |
| `t-one` | on your server, free | Russian | Writes what it hears letter by letter; ~140 MB model |
| `deepgram` | cloud, paid (free credit for new accounts) | many, auto-detect | Most accurate: punctuation, digits, player names as hints |
| `openai` | cloud, paid | any, auto-detect | Player names as hints |

- Developer API: speech start and end, live and final text, phrase triggers ("fireball!"), subtitle filters. It doesn't depend on the voice chat, so the same code will work with Simple Voice Chat later.
- Built for busy servers: recognition never touches the main thread, every player is processed independently, and late results can't overwrite newer ones. Repeated errors are logged once a minute, not on every packet.
- Privacy: voice stays in memory only while it is being recognised and is never written to disk. With a cloud engine, audio is sent to that provider while players speak.

## Requirements

- Paper, Spigot or Purpur **1.20.2 or newer**: text displays exist since 1.19.4, smooth movement since 1.20.2. Tested on 1.21.8, built against the 1.21.11 API.
- Java 17+
- Plasmo Voice **2.1 or newer** on the server. Players need only the Plasmo Voice mod, which they need to talk anyway.
- Internet on first start: the server downloads the Vosk / ONNX Runtime libraries from Maven Central, and the plugin downloads the speech model. Offline servers can put the model into `plugins/PV-Transcribe/models/` by hand.

Not supported: Folia, Fabric/Forge servers, and Simple Voice Chat. The core is voice-chat independent; a Simple Voice Chat adapter is planned.

## Installation

1. Install Plasmo Voice.
2. Put `PV-Transcribe-x.y.z.jar` into `plugins/` and start the server.
3. Pick a language and an engine in `plugins/PV-Transcribe/config.yml`, or in game:
   ```
   /pvt language en
   /pvt engine auto
   ```
4. For the best accuracy use Deepgram: create a key at https://console.deepgram.com, put it into `engine.deepgram.api-key`, then run `/pvt reload` and `/pvt engine deepgram`.

## Commands and permissions

| Command | Permission | Default |
|---|---|---|
| `/pvt toggle`: hide or show bubbles for yourself | `pvtranscribe.command.toggle` | everyone |
| `/pvt status`: engine state, load, dropped audio | `pvtranscribe.admin.status` | op |
| `/pvt reload` | `pvtranscribe.admin.reload` | op |
| `/pvt engine <auto\|vosk\|t-one\|deepgram\|openai>` | `pvtranscribe.admin.engine` | op |
| `/pvt language <code\|auto>` | `pvtranscribe.admin.engine` | op |
| `/pvt player <name> <on\|off>` | `pvtranscribe.admin.player` | op |
| `/pvt world <world> <on\|off>` | `pvtranscribe.admin.world` | op |

Other permissions: `pvtranscribe.see` (see bubbles, everyone), `pvtranscribe.transcribe` (speech is transcribed, everyone), `pvtranscribe.admin` (all admin commands).

Aliases: `/pvtranscribe`, `/transcribe`.

## Configuration

Every option is described in [`config.yml`](platform-paper/src/main/resources/config.yml). The main sections:

- `transcription`: language, live text, silence timeout, threads, lag protection, disabled worlds
- `engine`: engine type and settings for each engine (API keys, models)
- `subtitles`: bubble count, word limit, wrapping, display time, height, `style` (look), `visibility` (who sees bubbles)
- `display-text`: numbers as digits, math symbols, nickname matching
- `chat`: copy finished phrases to chat
- `locale`: `en` or `ru`; message texts are in `plugins/PV-Transcribe/lang/`

Bubble look example:

```yaml
subtitles:
  style:
    preset: dark            # light, dark, glass, minimal
    background: "#C81E3A8A" # #AARRGGBB
    final-format: "&f&l{text}"
    alignment: center
    padding: 2
    scale: 1.2
    tail:
      symbol: "▾"
```

Minecraft draws text backgrounds as rectangles, so rounded corners would need a resource pack. Everything else works for players with an unmodified client.

## API

Add the plugin jar as a `compileOnly` dependency and `depend: [PV-Transcribe]` to your `plugin.yml`.

```java
PVTranscribeApi api = PVTranscribe.get();

// All speech events
api.addListener(new TranscriptionListener() {
    @Override public void onSpeechStart(SpeechStartEvent e) { }
    @Override public void onPartialTranscript(Transcript t) { /* live text, t.isPartial() */ }
    @Override public void onFinalTranscript(Transcript t) { /* finished phrase, t.isFinal() */ }
    @Override public void onSpeechEnd(SpeechEndEvent e) { /* e.reason() */ }
}, api.syncExecutor()); // deliver on the main thread

// React to phrases
api.phrases().register(PhraseTrigger.builder("magic:fireball")
        .phrases("fireball", "огненный шар")
        .mode(MatchMode.CONTAINS)       // EXACT, CONTAINS, STARTS_WITH, REGEX
        .matchPartial(true)             // fire as soon as it is heard
        .executor(api.syncExecutor())
        .handler(match -> {
            Player p = Bukkit.getPlayer(match.speakerId());
            if (p != null) p.launchProjectile(Fireball.class);
        })
        .build());

// Change or hide subtitle text (moderation); API events are not affected
api.addSubtitleProcessor((transcript, text) -> text.replace("badword", "***"));
```

A `Transcript` contains the speaker, `getText()`, `getRawText()` (exactly as the engine returned it), `getNormalizedText()` (lower case, no punctuation; use it for matching), `isFinal()`, `getUtteranceId()`, language, voice source (`plasmovoice`) and voice channel (`proximity`, `groups`, `broadcast`).

Bukkit events, fired on the main thread: `PlayerSpeechStartEvent`, `PlayerTranscriptEvent`, `PlayerSpeechEndEvent`.

## Building

```
./gradlew build
```

The plugin jar is written to `build/libs/`. `./gradlew runServer` starts a local test server with Plasmo Voice.

Project modules:

| Module | Contents |
|---|---|
| `api` | Public API, no Minecraft dependencies |
| `core` | Sessions, threading, subtitle state, text formatting, config model |
| `engine-vosk`, `engine-tone`, `engine-cloud` | Speech engines |
| `voice-plasmo` | Plasmo Voice adapter (platform-independent) |
| `platform-paper` | Paper/Spigot plugin: bubbles, commands, permissions, config |

- New voice chat: implement `VoiceSourceAdapter`.
- New engine: implement `SpeechEngine` + `SpeechEngineFactory`.

## License

MIT, see [LICENSE](LICENSE). Third-party components and models: see [THIRD_PARTY.md](THIRD_PARTY.md).
