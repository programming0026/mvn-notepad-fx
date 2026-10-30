package com.juniverse.notepad.core;

/**
 * Fuente elegida por el usuario.
 *
 * @param family familia tipográfica
 * @param bold negrita
 * @param italic cursiva
 * @param size tamaño en puntos (al 100 % de zoom)
 */
public record FontChoice(String family, boolean bold, boolean italic, double size) {
}
