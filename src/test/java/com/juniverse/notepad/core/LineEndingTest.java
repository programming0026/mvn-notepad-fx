package com.juniverse.notepad.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Pruebas de {@link LineEnding}. */
class LineEndingTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"a\\r\\nb|CRLF", "a\\nb|LF", "a\\rb|CR", "a\\nb\\r\\nc|LF", "a\\r\\nb\\nc|CRLF"})
    void detectsTheFirstLineEnding(String escaped, LineEnding expected) {
        assertEquals(expected, LineEnding.detect(escaped.replace("\\r", "\r").replace("\\n", "\n")));
    }

    @Test
    void defaultsToTheSystemEndingWhenThereAreNoBreaks() {
        assertEquals(LineEnding.system(), LineEnding.detect("una sola línea"));
        assertEquals(LineEnding.system(), LineEnding.detect(""));
    }

    @Test
    void normalizesMixedBreaks() {
        assertEquals("a\nb\nc\nd", LineEnding.normalize("a\r\nb\rc\nd"));
    }

    @Test
    void appliesTheEnding() {
        assertEquals("a\r\nb", LineEnding.CRLF.apply("a\nb"));
        assertEquals("a\rb", LineEnding.CR.apply("a\nb"));
        assertEquals("a\nb", LineEnding.LF.apply("a\nb"));
    }
}
