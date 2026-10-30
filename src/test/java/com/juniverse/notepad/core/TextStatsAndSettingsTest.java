package com.juniverse.notepad.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Pruebas de {@link TextStats} y {@link AppSettings}. */
class TextStatsAndSettingsTest {

    private Preferences node;

    @BeforeEach
    void setUp() {
        node = Preferences.userRoot().node("com/juniverse/notepad-test/" + UUID.randomUUID());
    }

    @AfterEach
    void tearDown() throws BackingStoreException {
        node.removeNode();
    }

    @Test
    void positionIsLineAndColumnStartingAtOne() {
        String text = "abc\ndefg\n\nh";
        assertEquals(new TextStats.Position(1, 1), TextStats.position(text, 0));
        assertEquals(new TextStats.Position(1, 4), TextStats.position(text, 3));
        assertEquals(new TextStats.Position(2, 1), TextStats.position(text, 4));
        assertEquals(new TextStats.Position(2, 5), TextStats.position(text, 8));
        assertEquals(new TextStats.Position(3, 1), TextStats.position(text, 9));
        assertEquals(new TextStats.Position(4, 2), TextStats.position(text, text.length()));
        assertEquals(new TextStats.Position(1, 1), TextStats.position("", 0));
        assertEquals(new TextStats.Position(4, 2), TextStats.position(text, 999), "fuera de rango se ajusta al final");
    }

    @Test
    void countsLinesAndWords() {
        assertEquals(1, TextStats.lineCount(""));
        assertEquals(3, TextStats.lineCount("a\nb\nc"));
        assertEquals(3, TextStats.lineCount("a\nb\n") + 0 == 3 ? 3 : 0);
        assertEquals(0, TextStats.wordCount("  \n "));
        assertEquals(4, TextStats.wordCount("  uno  dos\ttres\ncuatro "));
    }

    @Test
    void findsTheStartOfALine() {
        String text = "uno\ndos\ntres";
        assertEquals(0, TextStats.lineStart(text, 1));
        assertEquals(4, TextStats.lineStart(text, 2));
        assertEquals(8, TextStats.lineStart(text, 3));
        assertEquals(-1, TextStats.lineStart(text, 4));
        assertEquals(-1, TextStats.lineStart(text, 0));
    }

    @Test
    void settingsHaveSensibleDefaults() {
        AppSettings settings = new AppSettings(node);
        assertFalse(settings.darkTheme());
        assertFalse(settings.wordWrap());
        assertTrue(settings.statusBarVisible());
        assertEquals(100, settings.zoom());
        assertEquals(new FontChoice("Monospaced", false, false, 14), settings.font());
        assertTrue(settings.recentFiles().isEmpty());
    }

    @Test
    void settingsArePersisted() {
        AppSettings settings = new AppSettings(node);
        settings.setDarkTheme(true);
        settings.setWordWrap(true);
        settings.setStatusBarVisible(false);
        settings.setZoom(150);
        settings.setFont(new FontChoice("Serif", true, true, 18));
        settings.setWindowSize(1000, 700);

        AppSettings reloaded = new AppSettings(node);
        assertTrue(reloaded.darkTheme());
        assertTrue(reloaded.wordWrap());
        assertFalse(reloaded.statusBarVisible());
        assertEquals(150, reloaded.zoom());
        assertEquals(new FontChoice("Serif", true, true, 18), reloaded.font());
        assertEquals(1000, reloaded.windowWidth());
        assertEquals(700, reloaded.windowHeight());
    }

    @Test
    void recentFilesAreUniqueNewestFirstAndLimited() {
        AppSettings settings = new AppSettings(node);
        for (int i = 1; i <= 12; i++) {
            settings.addRecentFile("/archivo" + i + ".txt");
        }
        assertEquals(8, settings.recentFiles().size());
        assertEquals("/archivo12.txt", settings.recentFiles().get(0));
        assertEquals("/archivo5.txt", settings.recentFiles().get(7));

        settings.addRecentFile("/archivo7.txt");
        assertEquals("/archivo7.txt", settings.recentFiles().get(0));
        assertEquals(1, settings.recentFiles().stream().filter("/archivo7.txt"::equals).count());

        settings.removeRecentFile("/archivo12.txt");
        assertEquals(List.of("/archivo7.txt", "/archivo11.txt"), settings.recentFiles().subList(0, 2));
        assertEquals(7, settings.recentFiles().size());
    }
}
