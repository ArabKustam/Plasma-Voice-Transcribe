<div align="center">

<img src="docs/images/banner-ru.png" alt="PV-Transcribe">

[Релизы](https://github.com/ArabKustam/Plasma-Voice-Transcribe/releases) | [Modrinth](https://modrinth.com/plugin/pv-transcribe) | [Настройка](#настройка) | [API](#для-разработчиков) | [Сообщить об ошибке](https://github.com/ArabKustam/Plasma-Voice-Transcribe/issues) | [English](README.md)

[![Release](https://img.shields.io/github/v/release/ArabKustam/Plasma-Voice-Transcribe?label=release)](https://github.com/ArabKustam/Plasma-Voice-Transcribe/releases)
[![Build](https://github.com/ArabKustam/Plasma-Voice-Transcribe/actions/workflows/build.yml/badge.svg)](https://github.com/ArabKustam/Plasma-Voice-Transcribe/actions)
![Paper 1.20.2+](https://img.shields.io/badge/Paper%20%2F%20Spigot-1.20.2%E2%80%931.21.11-brightgreen)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

</div>

## Видно, что говорят в голосовом чате

Игроки говорят в [Plasmo Voice](https://modrinth.com/plugin/plasmo-voice), и все, кто их слышит, видят их слова над головой. Текст растёт по словам прямо во время речи. Игрокам не нужны ни дополнительные моды, ни ресурспаки.

<p align="center"><img src="docs/images/live-demo-ru.gif" alt="Живые субтитры" width="760"></p>

## Возможности

![Живые субтитры](docs/images/panel-1-ru.png)

<p align="center"><img src="docs/images/conversation-ru.gif" alt="Разговор двух игроков" width="860"></p>

![Стили облачков](docs/images/panel-2-ru.png)

![Движки распознавания](docs/images/panel-3-ru.png)

![Для разработчиков](docs/images/panel-4-ru.png)

Облачка видят только те, кто слышит говорящего: дальность голоса, [pv-addon-groups](https://modrinth.com/plugin/pv-addon-groups) и [pv-addon-broadcast](https://modrinth.com/plugin/pv-addon-broadcast).

![Как это работает](docs/images/how-it-works-ru.png)

## Требования

| | |
|---|---|
| Сервер | Paper, Spigot или Purpur **1.20.2+**, проверено на 1.21.8 |
| Java | 17 и новее |
| Голосовой чат | Plasmo Voice **2.1+** |
| Игрокам | только мод Plasmo Voice |
| Первый запуск | нужен интернет: сервер скачает библиотеки, плагин скачает модель распознавания |

## Установка

1. Установите Plasmo Voice на сервер.
2. Скачайте `PV-Transcribe-x.y.z.jar` из [Releases](https://github.com/ArabKustam/Plasma-Voice-Transcribe/releases) (или с Modrinth) в `plugins/`.
3. Запустите сервер. Плагин сам выберет бесплатный локальный движок.
4. Для русского языка впишите в `plugins/PV-Transcribe/config.yml` строки `locale: ru` и `transcription.language: ru`.
5. По желанию, для максимальной точности, подключите Deepgram:
   1. Создайте ключ на [console.deepgram.com](https://console.deepgram.com).
   2. Впишите его в `engine.deepgram.api-key`.
   3. Выполните `/vtt reload` и `/vtt engine deepgram`.

## Команды

| Команда | Что делает | Право |
|---|---|---|
| `/vtt toggle` | Скрыть или показать облачка для себя | `pvtranscribe.command.toggle` (все) |
| `/vtt status` | Состояние движка, нагрузка, потерянный звук | `pvtranscribe.admin.status` |
| `/vtt reload` | Перезагрузить конфиг и сообщения | `pvtranscribe.admin.reload` |
| `/vtt demo [игрок] [текст]` | Демо-облачко без микрофона (проверить стиль, сделать скриншот) | `pvtranscribe.admin.demo` |
| `/vtt get [раздел]` | Показать настройки (ключи API скрыты) | `pvtranscribe.admin.config` |
| `/vtt set <параметр> <значение>` | Изменить **любой** параметр конфига прямо в игре; Tab подсказывает параметры и значения; сохраняется в config.yml | `pvtranscribe.admin.config` |
| `/vtt engine <auto\|vosk\|t-one\|deepgram\|openai>` | Сменить движок на лету | `pvtranscribe.admin.engine` |
| `/vtt language <код\|auto>` | Язык распознавания, `auto` — автоопределение | `pvtranscribe.admin.engine` |
| `/vtt player <ник> <on\|off>` | Включить или выключить транскрибацию игрока | `pvtranscribe.admin.player` |
| `/vtt world <мир> <on\|off>` | Включить или выключить транскрибацию в мире | `pvtranscribe.admin.world` |

Другие права:

| Право | По умолчанию | Значение |
|---|---|---|
| `pvtranscribe.see` | все | Видеть облачка |
| `pvtranscribe.transcribe` | все | Речь игрока распознаётся |
| `pvtranscribe.admin` | op | Все админские команды |

## Настройка

Настройки меняются двумя способами: в [`config.yml`](platform-paper/src/main/resources/config.yml) с последующим `/vtt reload` или прямо в игре командой `/vtt set <параметр> <значение>`, например `/vtt set subtitles.style.preset dark`. Все параметры описаны в конфиге: язык, движок и ключи API, облачка и их вид, кто их видит, тайминги, копия в чат, цифры, ники, миры, производительность и отладка. Полный конфиг с комментариями на русском: [docs/config.ru.md](docs/config.ru.md). Тексты сообщений лежат в `plugins/PV-Transcribe/lang/` (английский и русский).

```yaml
subtitles:
  max-bubbles: 3
  max-words-per-bubble: 10
  style:
    preset: dark              # light, dark, glass, minimal
    background: "#C81E3A8A"   # #AARRGGBB
    final-format: "&f&l{text}"
    alignment: center
    padding: 2
    scale: 1.2
    tail:
      symbol: "▾"
```

> Фон текста Minecraft рисует только прямоугольным, скруглённые углы возможны только с ресурспаком. Всё остальное работает на обычном клиенте.

## Для разработчиков

У PV-Transcribe и [SVC-Transcribe](https://github.com/ArabKustam/Simple-Voice-Chat-Transcribe) (Simple Voice Chat) общий API, поэтому ваш плагин работает с любым голосовым чатом без изменений. Подключите jar плагина как `compileOnly` и добавьте `softdepend: [PV-Transcribe, SVC-Transcribe]` в `plugin.yml`.

```java
PVTranscribeApi api = PVTranscribe.get();

// Живой и итоговый текст каждого игрока
api.addListener(new TranscriptionListener() {
    @Override public void onSpeechStart(SpeechStartEvent e) { }
    @Override public void onPartialTranscript(Transcript t) { /* ещё говорит */ }
    @Override public void onFinalTranscript(Transcript t) { /* фраза закончена */ }
    @Override public void onSpeechEnd(SpeechEndEvent e) { }
}, api.syncExecutor());

// Голосовые команды
api.phrases().register(PhraseTrigger.builder("magic:fireball")
        .phrases("fireball", "огненный шар")
        .mode(MatchMode.CONTAINS)
        .matchPartial(true)                // срабатывает, не дожидаясь конца фразы
        .executor(api.syncExecutor())      // главный поток
        .handler(match -> {
            Player player = Bukkit.getPlayer(match.speakerId());
            if (player != null) player.launchProjectile(Fireball.class);
        })
        .build());

// Модерация: изменить или скрыть текст облачка
api.addSubtitleProcessor((transcript, text) -> text.replace("плохоеслово", "***"));
```

В `Transcript` есть говорящий, `getText()`, `getRawText()`, `getNormalizedText()` (для сравнения), `isFinal()`, `getUtteranceId()`, язык, источник голоса (`plasmovoice`) и канал голоса (`proximity`, `groups`, `broadcast`). Также в главном потоке вызываются события Bukkit: `PlayerSpeechStartEvent`, `PlayerTranscriptEvent`, `PlayerSpeechEndEvent`.

| Метод / класс | Для чего |
|---|---|
| `PVTranscribe.get()` | Получить API (одинаково в обоих плагинах) |
| `addListener(listener, executor)` | Начало и конец речи, промежуточный и итоговый текст |
| `phrases().register(...)` | Реакция на слова и фразы (голосовые команды) |
| `addSubtitleProcessor(...)` | Изменить текст облачка перед показом или скрыть его (вернуть `null`) |
| `syncExecutor()` | Выполнять обработчики в главном потоке сервера |

## Частые вопросы

**Для каких версий?**
Paper, Spigot и Purpur 1.20.2 и новее (текстовые дисплеи появились в 1.19.4, плавное движение в 1.20.2). Проверено на 1.21.8.

**Работает ли с Simple Voice Chat?**
Для него есть отдельный плагин [SVC-Transcribe](https://github.com/ArabKustam/Simple-Voice-Chat-Transcribe). Ставьте только один из двух.

**Насколько точно?**
Зависит от движка. Deepgram самый точный: ники, пунктуация, цифры. T-one — хороший бесплатный вариант для русского. Vosk самый лёгкий, но иногда заменяет редкие слова на похожие частые.

**Записывается ли голос?**
Нет. Звук хранится только в памяти, пока распознаётся. С облачным движком звук отправляется провайдеру, пока игрок говорит, и об этом стоит предупредить игроков.

## Сборка

```
./gradlew build          # jar в build/libs/
./gradlew runServer      # тестовый сервер
```

| Модуль | Что внутри |
|---|---|
| `api` | Публичный API, без зависимостей от Minecraft |
| `core` | Сессии, потоки, облачка, обработка текста |
| `engine-*` | Движки распознавания |
| `voice-*` | Адаптер голосового чата |
| `platform-paper` | Плагин Bukkit |

## Поддержка

- Ошибки и идеи: [GitHub Issues](https://github.com/ArabKustam/Plasma-Voice-Transcribe/issues)
- Автор в Discord: **arab_kustam**
- Discord Plasmo Voice: https://discord.com/invite/uueEqzwCJJ

## Лицензия

[MIT](LICENSE). Сторонние компоненты: [THIRD_PARTY.md](THIRD_PARTY.md). История версий: [CHANGELOG.ru.md](CHANGELOG.ru.md).
