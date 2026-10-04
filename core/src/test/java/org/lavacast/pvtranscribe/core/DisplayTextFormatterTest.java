package org.lavacast.pvtranscribe.core;

import org.junit.jupiter.api.Test;
import org.lavacast.pvtranscribe.core.text.DisplayTextFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DisplayTextFormatterTest {

    private static String f(String s) {
        return DisplayTextFormatter.format(s, true, true);
    }

    @Test
    void arithmetic() {
        assertEquals("сколько будет 2 + 2", f("сколько будет два плюс два"));
        assertEquals("2 + 2 = 4", f("два плюс два равно четыре"));
        assertEquals("10 / 5", f("десять разделить на пять"));
        assertEquals("3 × 7", f("три умножить на семь"));
        assertEquals("5²", f("пять в квадрате"));
        assertEquals("скидка 50%", f("скидка пятьдесят процентов"));
    }

    @Test
    void compoundNumbers() {
        assertEquals("25", f("двадцать пять"));
        assertEquals("125", f("сто двадцать пять"));
        assertEquals("25300 блоков", f("двадцать пять тысяч триста блоков"));
        assertEquals("1000", f("тысяча"));
        assertEquals("2 3", f("два три"));
        assertEquals("12 5", f("двенадцать пять"));
    }

    @Test
    void leavesWordsAlone() {
        assertEquals("я один тут", f("я один тут"));
        assertEquals("в этом есть плюс", f("в этом есть плюс"));
        assertEquals("ребята идем в шахту", f("ребята идем в шахту"));
    }
}
