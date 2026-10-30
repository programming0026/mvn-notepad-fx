package com.juniverse.notepad.core;

/**
 * Opciones de búsqueda.
 *
 * @param matchCase distinguir mayúsculas de minúsculas
 * @param wholeWord buscar solo palabras completas
 * @param wrapAround continuar desde el otro extremo del documento al llegar al final (o al principio)
 */
public record SearchOptions(boolean matchCase, boolean wholeWord, boolean wrapAround) {

    /** Opciones por defecto: ignora mayúsculas, no exige palabra completa y da la vuelta al documento. */
    public static final SearchOptions DEFAULT = new SearchOptions(false, false, true);
}
