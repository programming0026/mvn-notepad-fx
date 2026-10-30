package com.juniverse.notepad.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Lectura y escritura de archivos de texto conservando su formato. Al leer detecta la codificación (BOM de UTF-8 o UTF-16,
 * UTF-8 sin BOM o, si los bytes no son UTF-8 válido, Windows-1252) y el tipo de fin de línea; al guardar los respeta.
 */
public final class DocumentIO {

    private static final byte[] BOM_UTF8 = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private DocumentIO() {
    }

    /**
     * Lee un archivo detectando su formato.
     *
     * @param path ruta del archivo
     * @return el documento con su texto normalizado y su formato original
     * @throws IOException si no se puede leer
     */
    public static TextDocument read(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        Charset charset;
        int offset = 0;
        boolean bom = false;
        if (startsWith(bytes, BOM_UTF8)) {
            charset = StandardCharsets.UTF_8;
            offset = 3;
            bom = true;
        } else if (startsWith(bytes, new byte[] {(byte) 0xFF, (byte) 0xFE})) {
            charset = StandardCharsets.UTF_16LE;
            offset = 2;
            bom = true;
        } else if (startsWith(bytes, new byte[] {(byte) 0xFE, (byte) 0xFF})) {
            charset = StandardCharsets.UTF_16BE;
            offset = 2;
            bom = true;
        } else if (isValidUtf8(bytes)) {
            charset = StandardCharsets.UTF_8;
        } else {
            charset = TextDocument.WINDOWS_1252; // los archivos antiguos de Windows suelen estar así
        }
        String raw = new String(bytes, offset, bytes.length - offset, charset);
        return new TextDocument(LineEnding.normalize(raw), charset, bom, LineEnding.detect(raw));
    }

    /**
     * Guarda un documento en su formato. Escribe primero en un archivo temporal y lo mueve al destino, de modo que si algo
     * falla a medias (disco lleno, corte de luz) el archivo original no queda truncado.
     *
     * @param path destino
     * @param document documento a guardar
     * @throws UnmappableTextException si la codificación no puede representar algún carácter (no se escribe nada)
     * @throws IOException si no se puede escribir
     */
    public static void write(Path path, TextDocument document) throws IOException {
        byte[] body = encode(document.lineEnding().apply(document.text()), document.charset());
        byte[] bom = document.bom() ? bomFor(document.charset()) : new byte[0];
        byte[] all = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, all, 0, bom.length);
        System.arraycopy(body, 0, all, bom.length, body.length);

        Path directory = path.toAbsolutePath().getParent();
        Path temp = Files.createTempFile(directory, path.getFileName().toString(), ".tmp");
        try {
            Files.write(temp, all);
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static byte[] encode(String text, Charset charset) throws IOException {
        CharsetEncoder encoder = charset.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            ByteBuffer buffer = encoder.encode(java.nio.CharBuffer.wrap(text));
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        } catch (CharacterCodingException e) {
            throw new UnmappableTextException("El texto contiene caracteres que " + charset.displayName() + " no puede representar");
        }
    }

    private static byte[] bomFor(Charset charset) {
        if (charset.equals(StandardCharsets.UTF_8)) {
            return BOM_UTF8;
        }
        if (charset.equals(StandardCharsets.UTF_16LE)) {
            return new byte[] {(byte) 0xFF, (byte) 0xFE};
        }
        if (charset.equals(StandardCharsets.UTF_16BE)) {
            return new byte[] {(byte) 0xFE, (byte) 0xFF};
        }
        return new byte[0];
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidUtf8(byte[] bytes) {
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}
