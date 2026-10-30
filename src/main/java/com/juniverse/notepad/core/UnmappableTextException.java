package com.juniverse.notepad.core;

/** El texto contiene caracteres que la codificación del archivo no puede representar (se perderían al guardar). */
public class UnmappableTextException extends java.io.IOException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción.
     *
     * @param message explicación
     */
    public UnmappableTextException(String message) {
        super(message);
    }
}
