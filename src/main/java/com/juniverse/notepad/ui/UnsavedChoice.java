package com.juniverse.notepad.ui;

/** Respuesta del usuario cuando hay cambios sin guardar. */
public enum UnsavedChoice {
    /** Guardar los cambios y continuar. */
    SAVE,
    /** Descartar los cambios y continuar. */
    DISCARD,
    /** Cancelar la operación y seguir editando. */
    CANCEL
}
