![Live subtitles](https://raw.githubusercontent.com/ArabKustam/Plasma-Voice-Transcribe/main/docs/images/live-subtitles-en.png)

**PV-Transcribe turns [Plasmo Voice](https://modrinth.com/plugin/plasmo-voice) speech into text in real time** and shows it as speech bubbles above the speaker's head. The text grows word by word while they talk. Players don't need any extra mod or resource pack to see the bubbles.

*Русское описание ниже.*

## ✨ Features

- 🗨️ **Live subtitles.** The text appears while the player is still speaking.
- 👂 **Follows the voice chat.** Only players who can hear the speaker see the bubble: proximity distance, [pv-addon-groups](https://modrinth.com/plugin/pv-addon-groups) and [pv-addon-broadcast](https://modrinth.com/plugin/pv-addon-broadcast).
- 📚 **Stacked bubbles.** Up to 3 per player; new phrases at the head, older ones float up and fade out.
- 🎨 **Your style.** Light, dark, glass or minimal presets, or your own colors, tail, alignment, padding and size.
- 💬 **Chat copy.** Finished phrases also go to the chat of the players who heard them.
- 🧠 **Choose your engine.** Switch in game with `/vtt engine`:
  - **Vosk**: free, local, 20+ languages
  - **T-one**: free, local, Russian
  - **Deepgram**: cloud, most accurate, player names spelled right
  - **OpenAI**: cloud, any language
- 🔢 **Smart text.** Nicknames of online players are recognised; spoken numbers become digits (2 + 2 = 4).
- 🛠️ **Admin tools.** Change any setting in game with `/vtt set` (with Tab completion) or in config.yml. Also permissions, per-player and per-world switches, live status, English and Russian messages.
- ⚡ **Lag-free.** Recognition never runs on the main thread, and there is overload protection.
- 🔒 **Privacy.** Voice is never saved to disk.

![How it works](https://raw.githubusercontent.com/ArabKustam/Plasma-Voice-Transcribe/main/docs/images/how-it-works-en.png)

![Bubble styles](https://raw.githubusercontent.com/ArabKustam/Plasma-Voice-Transcribe/main/docs/images/bubble-styles-en.png)

![Stacked bubbles](https://raw.githubusercontent.com/ArabKustam/Plasma-Voice-Transcribe/main/docs/images/stacked-bubbles-en.png)

![Several speakers and chat copy](https://raw.githubusercontent.com/ArabKustam/Plasma-Voice-Transcribe/main/docs/images/speakers-chat-en.png)

## 🧩 For developers

The voice-chat-independent API gives you speech start and end, live and final text, phrase triggers ("say *fireball* to cast it"), moderation filters and Bukkit events. The API is the same as in [SVC-Transcribe](https://modrinth.com/plugin/svc-transcribe), so one plugin works with both voice chats. Docs: [GitHub](https://github.com/ArabKustam/Plasma-Voice-Transcribe#for-developers).

## 📋 Requirements

- Paper / Spigot / Purpur **1.20.2+**, Java 17+
- Plasmo Voice **2.1+**
- Internet on first start: libraries and the speech model download automatically

---

# 🇷🇺 Описание на русском

**PV-Transcribe превращает речь из Plasmo Voice в текст в реальном времени** и показывает её облачками над головой говорящего. Текст растёт по ходу речи, игрокам ничего не нужно устанавливать.

![Живые субтитры](https://raw.githubusercontent.com/ArabKustam/Plasma-Voice-Transcribe/main/docs/images/live-subtitles-ru.png)

- 🗨️ **Живые субтитры**, пока игрок говорит
- 👂 Облачка видят **только те, кто слышит** говорящего: дальность голоса, [pv-addon-groups](https://modrinth.com/plugin/pv-addon-groups) и [pv-addon-broadcast](https://modrinth.com/plugin/pv-addon-broadcast)
- 📚 До 3 облачков, копия фраз в чат
- 🎨 Пресеты и свои цвета, хвостик, выравнивание, размер
- 🧠 Движки: Vosk и T-one (бесплатно, на сервере), Deepgram и OpenAI (облако, точнее всего)
- 🔢 Ники игроков и цифры (2 + 2 = 4)
- 🛠️ Любая настройка меняется прямо в игре (`/vtt set`, с подсказками Tab) или в config.yml. Права, отключение для игроков и миров, сообщения на русском (`locale: ru`)
- 🧩 API для голосовых команд, NPC и квестов

![Стили облачков](https://raw.githubusercontent.com/ArabKustam/Plasma-Voice-Transcribe/main/docs/images/bubble-styles-ru.png)

![Несколько игроков и копия в чат](https://raw.githubusercontent.com/ArabKustam/Plasma-Voice-Transcribe/main/docs/images/speakers-chat-ru.png)

![Как это работает](https://raw.githubusercontent.com/ArabKustam/Plasma-Voice-Transcribe/main/docs/images/how-it-works-ru.png)

Требования: Paper / Spigot / Purpur 1.20.2+, Java 17+, Plasmo Voice **2.1+**.
