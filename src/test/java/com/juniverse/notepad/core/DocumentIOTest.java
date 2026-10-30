package com.juniverse.notepad.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pruebas de lectura y escritura de {@link DocumentIO}. */
class DocumentIOTest {

    @TempDir
    Path dir;

    private Path file(byte[] content) throws IOException {
        Path path = dir.resolve("f" + System.nanoTime() + ".txt");
        Files.write(path, content);
        return path;
    }

    @Test
    void readsUtf8WithoutBom() throws IOException {
        TextDocument doc = DocumentIO.read(file("Hola ñandú — áéíóú".getBytes(StandardCharsets.UTF_8)));
        assertEquals("Hola ñandú — áéíóú", doc.text());
        assertEquals(StandardCharsets.UTF_8, doc.charset());
        assertFalse(doc.bom());
        assertEquals("UTF-8", doc.encodingLabel());
    }

    @Test
    void readsUtf8WithBomAndRemovesIt() throws IOException {
        byte[] bytes = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'h', 'i'};
        TextDocument doc = DocumentIO.read(file(bytes));
        assertEquals("hi", doc.text());
        assertTrue(doc.bom());
        assertEquals("UTF-8 con BOM", doc.encodingLabel());
    }

    @Test
    void readsUtf16WithBom() throws IOException {
        byte[] le = "hola ñ".getBytes(StandardCharsets.UTF_16LE);
        byte[] withBom = new byte[le.length + 2];
        withBom[0] = (byte) 0xFF;
        withBom[1] = (byte) 0xFE;
        System.arraycopy(le, 0, withBom, 2, le.length);
        TextDocument doc = DocumentIO.read(file(withBom));
        assertEquals("hola ñ", doc.text());
        assertEquals(StandardCharsets.UTF_16LE, doc.charset());
        assertEquals("UTF-16 LE con BOM", doc.encodingLabel());
    }

    @Test
    void fallsBackToWindows1252WhenNotValidUtf8() throws IOException {
        TextDocument doc = DocumentIO.read(file("canción €".getBytes(TextDocument.WINDOWS_1252)));
        assertEquals("canción €", doc.text());
        assertEquals(TextDocument.WINDOWS_1252, doc.charset());
        assertEquals("Windows-1252", doc.encodingLabel());
    }

    @Test
    void emptyFileIsAnEmptyDocument() throws IOException {
        TextDocument doc = DocumentIO.read(file(new byte[0]));
        assertEquals("", doc.text());
        assertEquals(StandardCharsets.UTF_8, doc.charset());
    }

    @Test
    void normalizesLineEndingsButRemembersTheOriginal() throws IOException {
        TextDocument doc = DocumentIO.read(file("a\r\nb\r\nc".getBytes(StandardCharsets.UTF_8)));
        assertEquals("a\nb\nc", doc.text());
        assertEquals(LineEnding.CRLF, doc.lineEnding());
    }

    @Test
    void roundTripPreservesBytesExactly() throws IOException {
        for (byte[] original : new byte[][] {
                "línea 1\r\nlínea 2\r\n".getBytes(StandardCharsets.UTF_8),
                "unix\nstyle\n".getBytes(StandardCharsets.UTF_8),
                new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'b', 'o', 'm', '\r', '\n'},
                "ñandú\r\n".getBytes(TextDocument.WINDOWS_1252)}) {
            Path source = file(original);
            TextDocument doc = DocumentIO.read(source);
            Path copy = dir.resolve("copy" + System.nanoTime());
            DocumentIO.write(copy, doc);
            assertArrayEquals(original, Files.readAllBytes(copy));
        }
    }

    @Test
    void writesWithTheChosenEnding() throws IOException {
        Path path = dir.resolve("out.txt");
        DocumentIO.write(path, new TextDocument("a\nb", StandardCharsets.UTF_8, false, LineEnding.CRLF));
        assertEquals("a\r\nb", Files.readString(path));
    }

    @Test
    void refusesToLoseCharactersAndLeavesTheFileUntouched() throws IOException {
        Path path = file("original".getBytes(StandardCharsets.UTF_8));
        TextDocument doc = new TextDocument("emoji 😀 y árbol", TextDocument.WINDOWS_1252, false, LineEnding.LF);
        assertThrows(UnmappableTextException.class, () -> DocumentIO.write(path, doc));
        assertEquals("original", Files.readString(path), "el archivo original no se tocó");
    }

    @Test
    void overwritesExistingFilesAndLeavesNoTemporaryFiles() throws IOException {
        Path path = file("viejo contenido mucho más largo".getBytes(StandardCharsets.UTF_8));
        DocumentIO.write(path, new TextDocument("nuevo", StandardCharsets.UTF_8, false, LineEnding.LF));
        assertEquals("nuevo", Files.readString(path));
        try (Stream<Path> files = Files.list(dir)) {
            assertTrue(files.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void handlesLargeFiles() throws IOException {
        String big = "línea de prueba con acentos áéíóú\n".repeat(200_000);
        Path path = dir.resolve("big.txt");
        DocumentIO.write(path, new TextDocument(big, StandardCharsets.UTF_8, false, LineEnding.LF));
        assertEquals(big, DocumentIO.read(path).text());
    }

    @Test
    void missingFileThrows() {
        assertThrows(IOException.class, () -> DocumentIO.read(dir.resolve("no-existe.txt")));
    }
}
