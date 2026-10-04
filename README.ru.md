<div align="center">

<img src="icon.png" width="128" alt="Иконка PV-Transcribe">

# PV-Transcribe

**Распознавание речи из [Plasmo Voice](https://modrinth.com/plugin/plasmo-voice) в реальном времени.**
Игрок говорит, и все, кто его слышит, видят его слова в облачке над головой.

[![Build](https://github.com/ArabKustam/Plasma-Voice-Transcribe/actions/workflows/build.yml/badge.svg)](https://github.com/ArabKustam/Plasma-Voice-Transcribe/actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![Paper 1.20.2+](https://img.shields.io/badge/Paper%20%2F%20Spigot-1.20.2%2B-brightgreen)
![Java 17+](https://img.shields.io/badge/Java-17%2B-orange)

[English version](README.md) · [Скачать](https://github.com/ArabKustam/Plasma-Voice-Transcribe/releases) · [Сообщить об ошибке](https://github.com/ArabKustam/Plasma-Voice-Transcribe/issues)

</div>

![Живые субтитры](docs/images/live-subtitles-ru.png)

## Возможности

- **Живые субтитры.** Текст появляется и растёт, пока игрок ещё говорит, а не через несколько секунд.
- **Работает по правилам голосового чата.** Облачко видят только те, кто реально слышит говорящего: дальность голоса, [pv-addon-groups](https://modrinth.com/plugin/pv-addon-groups) и [pv-addon-broadcast](https://modrinth.com/plugin/pv-addon-broadcast).
- **Без модов для субтитров.** Облачка сделаны на обычных text display, игрокам нужен только сам Plasmo Voice.
- **Несколько облачков.** До 3 над игроком: новая фраза появляется у головы, старые поднимаются вверх и плавно исчезают. Длинная речь делится на несколько облачков.
- **Свой стиль.** Пресеты (light, dark, glass, minimal) или свои цвета фона и текста, хвостик, выравнивание, отступы и размер.
- **Копия в чат.** Каждая законченная фраза приходит в чат тем, кто её слышал.
- **Выбор движка распознавания**, переключение прямо в игре:

| Движок | Где работает | Языки | Лучше всего для |
|---|---|---|---|
| `vosk` | на сервере, бесплатно | 20+ | Слабых серверов и многих языков |
| `t-one` | на сервере, бесплатно | русский | Бесплатного и точного русского |
| `deepgram` | облако, платно (стартовый кредит при регистрации) | много + автоопределение | Максимальной точности, пунктуации, ников |
| `openai` | облако, платно | любые + автоопределение | Любых языков |

- **Умный текст.** Ники онлайн-игроков узнаются. Числа и арифметика пишутся цифрами и знаками (2 + 2 = 4).
- **API для разработчиков.** Начало и конец речи, живой и итоговый текст, триггеры фраз, фильтры субтитров, события Bukkit.
- **Для больших серверов.** Распознавание никогда не идёт в основном потоке, каждый игрок обрабатывается отдельно, есть защита от перегрузки.
- **Приватность.** Голос не сохраняется на диск. Облачные движки получают звук только пока игрок говорит.

![Несколько игроков и копия в чат](docs/images/speakers-chat-ru.png)

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

Настройки меняются двумя способами: в [`config.yml`](platform-paper/src/main/resources/config.yml) с последующим `/vtt reload` или прямо в игре командой `/vtt set <параметр> <значение>`, например `/vtt set subtitles.style.preset dark`. Все параметры описаны в конфиге: язык, движок и ключи API, облачка и их вид, кто их видит, тайминги, копия в чат, цифры, ники, миры, производительность и отладка. Тексты сообщений лежат в `plugins/PV-Transcribe/lang/` (английский и русский).

![Стили облачков](docs/images/bubble-styles-ru.png)

![Несколько облачков](docs/images/stacked-bubbles-ru.png)

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

У PV-Transcribe и [SVC-Transcribe](https://github.com/ArabKustam/Simple-Voice-Chat-Transcribe) (Simple Voice Chat) общий API, поэтому ваш плагин работает с любым голосовым чатом без изменений. Подключите jar плагина как `compileOnly` и добавьте `softdepend: [PV-Transcribe, SVC-Transcribe]` в `plugin.yml`. Пример кода есть в [английском README](README.md#for-developers).

## Частые вопросы

**Для каких версий?**
Paper, Spigot и Purpur 1.20.2 и новее. Проверено на 1.21.8.

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

## Лицензия

[MIT](LICENSE). Сторонние компоненты: [THIRD_PARTY.md](THIRD_PARTY.md).
