package com.juniverse.notepad.core;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Contenido de un archivo de texto junto con el formato con el que se leyó, para poder guardarlo igual.
 *
 * @param text texto con saltos de línea normalizados ({@code \n})
 * @param charset codificación original
 * @param bom si el archivo empezaba con la marca de orden de bytes (BOM)
 * @param lineEnding tipo de fin de línea original
 */
public record TextDocument(String text, Charset charset, boolean bom, LineEnding lineEnding) {

    /** Windows-1252, la codificación a la que se recurre cuando el archivo no es UTF-8 válido. */
    public static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    /**
     * Documento vacío con el formato por defecto (UTF-8 sin BOM y el fin de línea del sistema).
     *
     * @return documento nuevo
     */
    public static TextDocument empty() {
        return new TextDocument("", StandardCharsets.UTF_8, false, LineEnding.system());
    }

    /**
     * Copia con otro texto y el mismo formato.
     *
     * @param newText texto nuevo
     * @return documento con el texto reemplazado
     */
    public TextDocument withText(String newText) {
        return new TextDocument(newText, charset, bom, lineEnding);
    }

    /**
     * Copia con otra codificación.
     *
     * @param newCharset codificación nueva
     * @param newBom si debe llevar BOM
     * @return documento con la codificación cambiada
     */
    public TextDocument withEncoding(Charset newCharset, boolean newBom) {
        return new TextDocument(text, newCharset, newBom, lineEnding);
    }

    /**
     * Nombre de la codificación para la barra de estado.
     *
     * @return por ejemplo "UTF-8", "UTF-8 con BOM" o "Windows-1252"
     */
    public String encodingLabel() {
        String name = charset.equals(WINDOWS_1252) ? "Windows-1252" : charset.displayName().replace("UTF-16LE", "UTF-16 LE").replace("UTF-16BE", "UTF-16 BE");
        return bom ? name + " con BOM" : name;
    }
}
