package com.juniverse.notepad.core;

/**
 * Tipo de fin de línea de un archivo. El editor trabaja siempre con {@code \n} internamente; al guardar se convierte al
 * tipo original del archivo para no alterar los saltos de línea de documentos creados en otro sistema.
 */
public enum LineEnding {
    /** Windows: retorno de carro + salto de línea. */
    CRLF("\r\n", "Windows (CRLF)"),
    /** Unix, Linux y macOS: salto de línea. */
    LF("\n", "Unix (LF)"),
    /** Mac clásico: retorno de carro. */
    CR("\r", "Mac clásico (CR)");

    private final String sequence;
    private final String label;

    LineEnding(String sequence, String label) {
        this.sequence = sequence;
        this.label = label;
    }

    /**
     * Secuencia de caracteres.
     *
     * @return el texto del salto de línea
     */
    public String sequence() {
        return sequence;
    }

    /**
     * Nombre para mostrar en la barra de estado.
     *
     * @return descripción legible
     */
    public String label() {
        return label;
    }

    /**
     * Detecta el fin de línea predominante de un texto (el primero que aparece).
     *
     * @param text texto leído de un archivo
     * @return el tipo detectado; si no hay saltos, el del sistema operativo
     */
    public static LineEnding detect(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                return LF;
            }
            if (c == '\r') {
                return i + 1 < text.length() && text.charAt(i + 1) == '\n' ? CRLF : CR;
            }
        }
        return system();
    }

    /**
     * Fin de línea del sistema operativo actual.
     *
     * @return {@link #CRLF} en Windows, {@link #LF} en el resto
     */
    public static LineEnding system() {
        return System.lineSeparator().equals("\r\n") ? CRLF : LF;
    }

    /**
     * Convierte cualquier combinación de saltos de línea a {@code \n}.
     *
     * @param text texto con saltos mezclados
     * @return texto con solo {@code \n}
     */
    public static String normalize(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    /**
     * Convierte un texto con {@code \n} a este tipo de fin de línea.
     *
     * @param text texto normalizado
     * @return texto listo para guardar
     */
    public String apply(String text) {
        return this == LF ? text : text.replace("\n", sequence);
    }
}
