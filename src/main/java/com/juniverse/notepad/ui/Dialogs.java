package com.juniverse.notepad.ui;

import com.juniverse.notepad.core.FontChoice;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Todo lo que el editor le pide al usuario mediante ventanas. Es una interfaz para separar la lógica de la presentación:
 * la aplicación usa {@link FxDialogs} (ventanas reales) y las pruebas una implementación con respuestas guionizadas.
 */
public interface Dialogs {

    /**
     * Pregunta qué hacer con los cambios sin guardar.
     *
     * @param documentName nombre del documento
     * @return la decisión del usuario
     */
    UnsavedChoice confirmUnsaved(String documentName);

    /**
     * Pide un archivo para abrir.
     *
     * @return la ruta elegida, o vacío si se canceló
     */
    Optional<Path> chooseFileToOpen();

    /**
     * Pide dónde guardar.
     *
     * @param suggestedName nombre propuesto
     * @return la ruta elegida, o vacío si se canceló
     */
    Optional<Path> chooseFileToSave(String suggestedName);

    /**
     * Muestra un error.
     *
     * @param title título de la ventana
     * @param message descripción
     */
    void showError(String title, String message);

    /**
     * Pide un número de línea.
     *
     * @param current línea actual
     * @param max última línea del documento
     * @return la línea elegida, o vacío si se canceló
     */
    OptionalInt askLineNumber(int current, int max);

    /**
     * Pide una fuente.
     *
     * @param current fuente actual
     * @return la fuente elegida, o vacío si se canceló
     */
    Optional<FontChoice> chooseFont(FontChoice current);

    /**
     * Avisa de que el texto tiene caracteres que la codificación del archivo no admite y pregunta si guardar como UTF-8.
     *
     * @param encodingName codificación actual
     * @return {@code true} si el usuario acepta convertir a UTF-8
     */
    boolean confirmSaveAsUtf8(String encodingName);

    /**
     * Muestra información.
     *
     * @param title título
     * @param message contenido
     */
    void showInfo(String title, String message);
}
