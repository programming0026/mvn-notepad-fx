package com.juniverse.notepad.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Pruebas de {@link SearchEngine}. */
class SearchEngineTest {

    private static final SearchOptions CASE_INSENSITIVE_NO_WRAP = new SearchOptions(false, false, false);
    private static final SearchOptions CASE_SENSITIVE = new SearchOptions(true, false, false);
    private static final SearchOptions WHOLE_WORD = new SearchOptions(false, true, false);
    private static final String TEXT = "Uno dos Dos tres DOS cuatro";

    @Test
    void findsForwardFromAnyPosition() {
        assertEquals(4, SearchEngine.find(TEXT, "dos", 0, true, CASE_INSENSITIVE_NO_WRAP));
        assertEquals(8, SearchEngine.find(TEXT, "dos", 5, true, CASE_INSENSITIVE_NO_WRAP));
        assertEquals(17, SearchEngine.find(TEXT, "dos", 9, true, CASE_INSENSITIVE_NO_WRAP));
        assertEquals(-1, SearchEngine.find(TEXT, "dos", 18, true, CASE_INSENSITIVE_NO_WRAP));
    }

    @Test
    void findsBackward() {
        assertEquals(17, SearchEngine.find(TEXT, "dos", TEXT.length(), false, CASE_INSENSITIVE_NO_WRAP));
        assertEquals(8, SearchEngine.find(TEXT, "dos", 17, false, CASE_INSENSITIVE_NO_WRAP));
        assertEquals(4, SearchEngine.find(TEXT, "dos", 8, false, CASE_INSENSITIVE_NO_WRAP));
        assertEquals(-1, SearchEngine.find(TEXT, "dos", 4, false, CASE_INSENSITIVE_NO_WRAP));
    }

    @Test
    void wrapsAroundWhenAllowed() {
        SearchOptions wrap = new SearchOptions(false, false, true);
        assertEquals(4, SearchEngine.find(TEXT, "dos", 18, true, wrap));
        assertEquals(17, SearchEngine.find(TEXT, "dos", 4, false, wrap));
    }

    @Test
    void honorsCaseSensitivity() {
        assertEquals(4, SearchEngine.find(TEXT, "dos", 0, true, CASE_SENSITIVE));
        assertEquals(8, SearchEngine.find(TEXT, "Dos", 0, true, CASE_SENSITIVE));
        assertEquals(-1, SearchEngine.find(TEXT, "dOS", 0, true, CASE_SENSITIVE));
    }

    @Test
    void wholeWordSkipsPartialMatches() {
        String text = "casa casas encasa casa_x casa";
        assertEquals(0, SearchEngine.find(text, "casa", 0, true, WHOLE_WORD));
        assertEquals(25, SearchEngine.find(text, "casa", 1, true, WHOLE_WORD));
    }

    @Test
    void searchesLiterallyNotAsRegex() {
        assertEquals(2, SearchEngine.find("a .*+ b", ".*+", 0, true, CASE_SENSITIVE));
        assertEquals(-1, SearchEngine.find("abc", ".", 0, true, CASE_SENSITIVE));
        assertEquals(4, SearchEngine.find("x = (a)", "(a)", 0, true, CASE_SENSITIVE));
    }

    @Test
    void handlesEmptyAndTooLongQueries() {
        assertEquals(-1, SearchEngine.find("abc", "", 0, true, SearchOptions.DEFAULT));
        assertEquals(-1, SearchEngine.find("abc", null, 0, true, SearchOptions.DEFAULT));
        assertEquals(-1, SearchEngine.find("ab", "abc", 0, true, SearchOptions.DEFAULT));
        assertEquals(-1, SearchEngine.find("", "a", 0, true, SearchOptions.DEFAULT));
    }

    @Test
    void ignoresCaseForAccentedLetters() {
        assertEquals(0, SearchEngine.find("ÁRBOL", "árbol", 0, true, CASE_INSENSITIVE_NO_WRAP));
    }

    @Test
    void replacesAllWithoutOverlapping() {
        SearchEngine.ReplaceResult result = SearchEngine.replaceAll("aaaa", "aa", "b", CASE_SENSITIVE);
        assertEquals("bb", result.text());
        assertEquals(2, result.count());

        result = SearchEngine.replaceAll(TEXT, "dos", "2", CASE_INSENSITIVE_NO_WRAP);
        assertEquals("Uno 2 2 tres 2 cuatro", result.text());
        assertEquals(3, result.count());
    }

    @Test
    void replacementIsLiteralAndCanBeEmpty() {
        assertEquals("a$1b", SearchEngine.replaceAll("a-b", "-", "$1", CASE_SENSITIVE).text());
        assertEquals("ab", SearchEngine.replaceAll("a--b", "-", "", CASE_SENSITIVE).text());
    }

    @Test
    void replaceAllWithNoMatchesReturnsTheSameText() {
        SearchEngine.ReplaceResult result = SearchEngine.replaceAll("hola", "x", "y", CASE_SENSITIVE);
        assertEquals("hola", result.text());
        assertEquals(0, result.count());
        assertEquals(0, SearchEngine.replaceAll("hola", "", "y", CASE_SENSITIVE).count());
    }

    @Test
    void replacingWholeWordsOnly() {
        assertEquals("sol solar sol", SearchEngine.replaceAll("luz solar luz", "luz", "sol", WHOLE_WORD).text().replace("sol solar sol", "sol solar sol"));
        assertEquals("el 1 y el 12", SearchEngine.replaceAll("el uno y el 12", "uno", "1", WHOLE_WORD).text());
    }

    @Test
    void matchesChecksTheWholeFragment() {
        assertTrue(SearchEngine.matches("Dos", "dos", CASE_INSENSITIVE_NO_WRAP));
        assertFalse(SearchEngine.matches("Dos", "dos", CASE_SENSITIVE));
        assertFalse(SearchEngine.matches("dos!", "dos", CASE_INSENSITIVE_NO_WRAP));
        assertFalse(SearchEngine.matches("", "", CASE_INSENSITIVE_NO_WRAP));
    }
}
