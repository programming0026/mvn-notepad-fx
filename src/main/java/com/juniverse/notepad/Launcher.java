package com.juniverse.notepad;

import javafx.application.Application;

/**
 * Punto de entrada del jar ejecutable. Una clase que hereda de {@link Application} no puede ser la clase principal de un
 * jar "gordo" (JavaFX comprueba que se cargue desde el módulo y falla con "faltan componentes de JavaFX"), así que se usa
 * esta clase intermedia que simplemente lanza la aplicación.
 */
public final class Launcher {

    private Launcher() {
    }

    /**
     * Lanza la aplicación.
     *
     * @param args opcionalmente, la ruta de un archivo para abrirlo al iniciar
     */
    public static void main(String[] args) {
        Application.launch(NotepadApp.class, args);
    }
}
