package com.juniverse.notepad.ui;

import com.juniverse.notepad.core.SearchOptions;
import java.nio.file.Path;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

/** Construye la barra de menús, con atajos de teclado y opciones que se habilitan o deshabilitan según el estado del editor. */
final class NotepadMenus {

    private final NotepadController controller;
    private final TextArea editor;
    private final MenuBar menuBar = new MenuBar();
    private final Menu recentMenu = new Menu("Archivos recientes");
    private FindReplaceDialog findDialog;

    NotepadMenus(NotepadController controller, TextArea editor) {
        this.controller = controller;
        this.editor = editor;
        menuBar.getMenus().addAll(fileMenu(), editMenu(), formatMenu(), viewMenu(), helpMenu());
        refreshRecent();
    }

    MenuBar menuBar() {
        return menuBar;
    }

    private static KeyCombination ctrl(KeyCode code) {
        return new KeyCodeCombination(code, KeyCombination.SHORTCUT_DOWN);
    }

    private static KeyCombination ctrlShift(KeyCode code) {
        return new KeyCodeCombination(code, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);
    }

    private static MenuItem item(String text, KeyCombination accelerator, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setAccelerator(accelerator);
        item.setOnAction(e -> action.run());
        return item;
    }

    private Menu fileMenu() {
        Menu menu = new Menu("Archivo");
        menu.getItems().addAll(
                item("Nuevo", ctrl(KeyCode.N), controller::newDocument),
                item("Abrir...", ctrl(KeyCode.O), controller::open),
                item("Guardar", ctrl(KeyCode.S), controller::save),
                item("Guardar como...", ctrlShift(KeyCode.S), controller::saveAs),
                new SeparatorMenuItem(),
                recentMenu,
                new SeparatorMenuItem(),
                item("Salir", ctrl(KeyCode.Q), controller::exit));
        return menu;
    }

    private Menu editMenu() {
        Menu menu = new Menu("Edición");
        MenuItem undo = item("Deshacer", ctrl(KeyCode.Z), controller::undo);
        undo.disableProperty().bind(editor.undoableProperty().not());
        MenuItem redo = item("Rehacer", ctrl(KeyCode.Y), controller::redo);
        redo.disableProperty().bind(editor.redoableProperty().not());
        MenuItem cut = item("Cortar", ctrl(KeyCode.X), editor::cut);
        MenuItem copy = item("Copiar", ctrl(KeyCode.C), editor::copy);
        MenuItem delete = item("Eliminar", new KeyCodeCombination(KeyCode.DELETE), () -> editor.replaceSelection(""));
        for (MenuItem selectionItem : new MenuItem[] {cut, copy, delete}) {
            selectionItem.disableProperty().bind(editor.selectedTextProperty().isEmpty());
        }
        MenuItem paste = item("Pegar", ctrl(KeyCode.V), editor::paste);
        menu.getItems().addAll(undo, redo, new SeparatorMenuItem(), cut, copy, paste, delete, new SeparatorMenuItem(),
                item("Buscar...", ctrl(KeyCode.F), () -> showFind(false)),
                item("Buscar siguiente", new KeyCodeCombination(KeyCode.F3), controller::findNextAgain),
                item("Buscar anterior", new KeyCodeCombination(KeyCode.F3, KeyCombination.SHIFT_DOWN), controller::findPreviousAgain),
                item("Reemplazar...", ctrl(KeyCode.H), () -> showFind(true)),
                item("Ir a la línea...", ctrl(KeyCode.G), controller::goToLineDialog),
                new SeparatorMenuItem(),
                item("Seleccionar todo", ctrl(KeyCode.A), controller::selectAll),
                item("Hora y fecha", new KeyCodeCombination(KeyCode.F5), controller::insertDateTime));
        return menu;
    }

    private Menu formatMenu() {
        Menu menu = new Menu("Formato");
        CheckMenuItem wrap = new CheckMenuItem("Ajuste de línea");
        wrap.setSelected(editor.isWrapText());
        wrap.selectedProperty().addListener((o, a, b) -> controller.setWordWrap(b));
        menu.getItems().addAll(wrap, item("Fuente...", null, controller::chooseFontDialog));
        return menu;
    }

    private Menu viewMenu() {
        Menu menu = new Menu("Ver");
        CheckMenuItem status = new CheckMenuItem("Barra de estado");
        status.setSelected(controller.isStatusBarVisible());
        status.selectedProperty().addListener((o, a, b) -> controller.setStatusBarVisible(b));
        CheckMenuItem dark = new CheckMenuItem("Tema oscuro");
        dark.setSelected(controller.isDarkTheme());
        dark.selectedProperty().addListener((o, a, b) -> controller.setDarkTheme(b));
        menu.getItems().addAll(
                item("Acercar", ctrl(KeyCode.EQUALS), controller::zoomIn),
                item("Alejar", ctrl(KeyCode.MINUS), controller::zoomOut),
                item("Restaurar zoom predeterminado", ctrl(KeyCode.DIGIT0), controller::zoomReset),
                new SeparatorMenuItem(), status, dark);
        return menu;
    }

    private Menu helpMenu() {
        Menu menu = new Menu("Ayuda");
        menu.getItems().add(item("Acerca de", null, controller::showAbout));
        return menu;
    }

    /** Reconstruye el submenú de archivos recientes a partir de las preferencias. */
    void refreshRecent() {
        recentMenu.getItems().clear();
        for (String path : controller.recentFiles()) {
            recentMenu.getItems().add(item(path, null, () -> controller.openPath(Path.of(path))));
        }
        recentMenu.setDisable(recentMenu.getItems().isEmpty());
    }

    private void showFind(boolean replaceMode) {
        if (findDialog == null) {
            findDialog = new FindReplaceDialog(controller);
        }
        findDialog.show(replaceMode, controller.selectedText().isEmpty() ? controller.lastQuery() : controller.selectedText());
    }

    /**
     * Opciones usadas por defecto en la ventana de búsqueda.
     *
     * @return opciones por defecto
     */
    static SearchOptions defaultOptions() {
        return SearchOptions.DEFAULT;
    }
}
