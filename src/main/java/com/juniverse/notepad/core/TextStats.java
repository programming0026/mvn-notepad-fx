package com.juniverse.notepad.core;

/** Cálculos sobre el texto para la barra de estado y la navegación: posición del cursor, líneas y palabras. */
public final class TextStats {

    /**
     * Posición legible del cursor.
     *
     * @param line línea (desde 1)
     * @param column columna (desde 1)
     */
    public record Position(int line, int column) {
    }

    private TextStats() {
    }

    /**
     * Línea y columna de un punto del texto (con saltos de línea {@code \n}).
     *
     * @param text texto
     * @param caret posición del cursor (0 es antes del primer carácter)
     * @return línea y columna, ambas desde 1
     */
    public static Position position(String text, int caret) {
        int limit = Math.max(0, Math.min(caret, text.length()));
        int line = 1;
        int lineStart = 0;
        for (int i = 0; i < limit; i++) {
            if (text.charAt(i) == '\n') {
                line++;
                lineStart = i + 1;
            }
        }
        return new Position(line, limit - lineStart + 1);
    }

    /**
     * Cantidad de líneas (un texto vacío tiene una).
     *
     * @param text texto
     * @return número de líneas
     */
    public static int lineCount(String text) {
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    /**
     * Posición del inicio de una línea.
     *
     * @param text texto
     * @param line número de línea (desde 1)
     * @return índice del primer carácter de la línea, o -1 si la línea no existe
     */
    public static int lineStart(String text, int line) {
        if (line < 1) {
            return -1;
        }
        int current = 1;
        int index = 0;
        while (current < line) {
            int newline = text.indexOf('\n', index);
            if (newline < 0) {
                return -1;
            }
            index = newline + 1;
            current++;
        }
        return index;
    }

    /**
     * Cantidad de palabras (grupos de caracteres separados por espacios).
     *
     * @param text texto
     * @return número de palabras
     */
    public static int wordCount(String text) {
        String trimmed = text.strip();
        return trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
    }
}
