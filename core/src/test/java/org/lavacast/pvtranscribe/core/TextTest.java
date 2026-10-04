package org.lavacast.pvtranscribe.core;

import org.junit.jupiter.api.Test;
import org.lavacast.pvtranscribe.api.text.TextNormalizer;
import org.lavacast.pvtranscribe.core.util.TextUtil;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextTest {

    @Test
    void normalizesRussianText() {
        assertEquals("огненный шар", TextNormalizer.normalize("  Огнённый, ШАР!! "));
        assertEquals("кто-то сказал don't", TextNormalizer.normalize("Кто-то сказал: «don't»"));
    }

    @Test
    void containsWholeWordsOnly() {
        assertTrue(TextNormalizer.containsPhrase("давай огненный шар туда", "огненный шар"));
        assertFalse(TextNormalizer.containsPhrase("огненныйшар", "огненный шар"));
        assertFalse(TextNormalizer.containsPhrase("шарик", "шар"));
    }

    @Test
    void cleansEngineMarkers() {
        assertEquals("ребята идем", TextUtil.cleanEngineText(" [unk] ребята  [noise] идем "));
    }

    @Test
    void wrapsAndKeepsTail() {
        String text = "ребята давайте пойдем сегодня в шахту и накопаем много алмазов для всех";
        List<String> lines = TextUtil.wrapTail(text, 16, 2);
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).startsWith("…"));
        assertTrue(lines.get(1).endsWith("для всех"));
        for (String line : lines) assertTrue(line.length() <= 17, line);
    }

    @Test
    void matchesNicknames() {
        List<String> online = List.of("buletka", "gugabel", "Verity", "MARK");
        assertEquals("это buletka и gugabel", org.lavacast.pvtranscribe.core.text.NicknameMatcher.apply("это булетка и гугобель", online));
        assertEquals("привет gugabel", org.lavacast.pvtranscribe.core.text.NicknameMatcher.apply("привет гуга бель", online));
        assertEquals("привет я Verity", org.lavacast.pvtranscribe.core.text.NicknameMatcher.apply("привет я верити", online));
        assertEquals("ребята идем в шахту", org.lavacast.pvtranscribe.core.text.NicknameMatcher.apply("ребята идем в шахту", online));
    }

    @Test
    void colorizes() {
        assertEquals("§eMARK §x§f§f§0§0§0§0red", TextUtil.colorize("&eMARK &#FF0000red"));
    }
}
