package com.juniverse.notepad.ui;

import com.juniverse.notepad.core.AppSettings;
import com.juniverse.notepad.core.DocumentIO;
import com.juniverse.notepad.core.FontChoice;
import com.juniverse.notepad.core.SearchEngine;
import com.juniverse.notepad.core.SearchOptions;
import com.juniverse.notepad.core.TextDocument;
import com.juniverse.notepad.core.TextStats;
import com.juniverse.notepad.core.UnmappableTextException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Supplier;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.DragEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

/**
 * Cerebro del editor: sostiene el área de texto y el estado del documento (archivo, formato, cambios sin guardar) y expone
 * las acciones que invocan los menús. La lógica pesada (leer/escribir, buscar, estadísticas) vive en el paquete
 * {@code core}, que no depende de JavaFX; los diálogos llegan por la interfaz {@link Dialogs}. Por eso toda la clase se
 * puede probar sin abrir ventanas.
 */
public final class NotepadController {

    /** Nombre de la aplicación en la barra de título. */
    public static final String APP_NAME = "Bloc de notas";
    /** Zoom mínimo, en porcentaje. */
    public static final int MIN_ZOOM = 30;
    /** Zoom máximo, en porcentaje. */
    public static final int MAX_ZOOM = 500;
    private static final int ZOOM_STEP = 10;
    private static final String DARK_CSS = "/com/juniverse/notepad/dark.css";

    private final Stage stage;
    private final Dialogs dialogs;
    private final AppSettings settings;
    private final Supplier<LocalDateTime> clock;

    private final TextArea editor = new TextArea();
    private final BorderPane root = new BorderPane();
    private final Label positionLabel = new Label();
    private final Label countLabel = new Label();
    private final Label zoomLabel = new Label();
    private final Label encodingLabel = new Label();
    private final Label lineEndingLabel = new Label();
    private final HBox statusBar = new HBox(24);
    private final BooleanProperty dirty = new SimpleBooleanProperty(false);
    private final NotepadMenus menus;

    private Path currentPath;
    private TextDocument document = TextDocument.empty();
    private String savedText = "";
    private FontChoice font;
    private int zoom;
    private boolean darkTheme;
    private String lastQuery = "";
    private SearchOptions lastOptions = SearchOptions.DEFAULT;

    /**
     * Crea el editor.
     *
     * @param stage ventana principal
     * @param dialogs ventanas de diálogo
     * @param settings preferencias del usuario
     */
    public NotepadController(Stage stage, Dialogs dialogs, AppSettings settings) {
        this(stage, dialogs, settings, LocalDateTime::now);
    }

    /**
     * Crea el editor con un reloj propio (para que las pruebas controlen la fecha y hora).
     *
     * @param stage ventana principal
     * @param dialogs ventanas de diálogo
     * @param settings preferencias del usuario
     * @param clock proveedor de la fecha y hora actuales
     */
    public NotepadController(Stage stage, Dialogs dialogs, AppSettings settings, Supplier<LocalDateTime> clock) {
        this.stage = stage;
        this.dialogs = dialogs;
        this.settings = settings;
        this.clock = clock;

        this.font = settings.font();
        this.zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, settings.zoom()));
        editor.setWrapText(settings.wordWrap());
        editor.setStyle("-fx-padding: 0;");
        applyFont();

        statusBar.setPadding(new Insets(4, 10, 4, 10));
        statusBar.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        statusBar.getChildren().addAll(positionLabel, countLabel, spacer, zoomLabel, lineEndingLabel, encodingLabel);
        statusBar.getStyleClass().add("status-bar");

        this.menus = new NotepadMenus(this, editor);
        root.setTop(menus.menuBar());
        root.setCenter(editor);
        root.setBottom(settings.statusBarVisible() ? statusBar : null);

        editor.textProperty().addListener((o, a, b) -> {
            updateDirty();
            updateStatus();
        });
        editor.caretPositionProperty().addListener((o, a, b) -> updateStatus());
        this.darkTheme = settings.darkTheme();
        updateDirty();
        updateStatus();
        stage.setOnCloseRequest(event -> {
            if (!confirmDiscard()) {
                event.consume();
            } else {
                saveSettings();
            }
        });
    }

    /**
     * Completa lo que necesita una escena ya creada: tema, atajos de zoom con el teclado y la rueda, y arrastrar archivos.
     * Debe llamarse después de asignar la escena a la ventana.
     */
    public void attachToScene() {
        Scene scene = stage.getScene();
        applyTheme();
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.isShortcutDown() && (e.getCode() == KeyCode.ADD || e.getCode() == KeyCode.PLUS)) {
                zoomIn();
                e.consume();
            } else if (e.isShortcutDown() && e.getCode() == KeyCode.SUBTRACT) {
                zoomOut();
                e.consume();
            } else if (e.isShortcutDown() && e.getCode() == KeyCode.NUMPAD0) {
                zoomReset();
                e.consume();
            }
        });
        scene.addEventFilter(ScrollEvent.SCROLL, e -> {
            if (e.isShortcutDown() && e.getDeltaY() != 0) {
                if (e.getDeltaY() > 0) {
                    zoomIn();
                } else {
                    zoomOut();
                }
                e.consume();
            }
        });
        scene.setOnDragOver((DragEvent e) -> {
            if (e.getDragboard().hasFiles()) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        scene.setOnDragDropped((DragEvent e) -> {
            boolean ok = e.getDragboard().hasFiles() && !e.getDragboard().getFiles().isEmpty();
            if (ok) {
                openPath(e.getDragboard().getFiles().get(0).toPath());
            }
            e.setDropCompleted(ok);
            e.consume();
        });
    }

    // ------------------------------------------------------------------ acceso (para los menús y las pruebas)

    /**
     * Raíz de la interfaz para colocarla en una escena.
     *
     * @return el panel principal
     */
    public BorderPane root() {
        return root;
    }

    /**
     * Área de texto.
     *
     * @return el editor
     */
    public TextArea editor() {
        return editor;
    }

    /**
     * Texto actual.
     *
     * @return contenido del editor
     */
    public String text() {
        return editor.getText();
    }

    public boolean isDirty() {
        return dirty.get();
    }

    /**
     * Propiedad observable de "hay cambios sin guardar".
     *
     * @return la propiedad
     */
    public BooleanProperty dirtyProperty() {
        return dirty;
    }

    /**
     * Archivo abierto.
     *
     * @return la ruta, o {@code null} si el documento aún no se ha guardado
     */
    public Path currentPath() {
        return currentPath;
    }

    /**
     * Formato (codificación y fin de línea) del documento actual.
     *
     * @return formato del documento
     */
    public TextDocument document() {
        return document;
    }

    /**
     * Zoom actual.
     *
     * @return porcentaje (30 a 500)
     */
    public int zoom() {
        return zoom;
    }

    /**
     * Fuente actual (sin el zoom).
     *
     * @return fuente elegida
     */
    public FontChoice font() {
        return font;
    }

    public boolean isDarkTheme() {
        return darkTheme;
    }

    public boolean isStatusBarVisible() {
        return root.getBottom() != null;
    }

    /**
     * Archivos recientes.
     *
     * @return rutas, del más nuevo al más antiguo
     */
    public List<String> recentFiles() {
        return settings.recentFiles();
    }

    /**
     * Texto de la posición del cursor.
     *
     * @return por ejemplo "Ln 3, Col 5"
     */
    public String positionText() {
        return positionLabel.getText();
    }

    /**
     * Texto de la cantidad de caracteres.
     *
     * @return por ejemplo "120 caracteres"
     */
    public String countText() {
        return countLabel.getText();
    }

    /**
     * Texto del zoom.
     *
     * @return por ejemplo "100%"
     */
    public String zoomText() {
        return zoomLabel.getText();
    }

    /**
     * Texto de la codificación.
     *
     * @return por ejemplo "UTF-8"
     */
    public String encodingText() {
        return encodingLabel.getText();
    }

    /**
     * Texto del tipo de fin de línea.
     *
     * @return por ejemplo "Windows (CRLF)"
     */
    public String lineEndingText() {
        return lineEndingLabel.getText();
    }

    /**
     * Título de la ventana.
     *
     * @return por ejemplo "*notas.txt - Bloc de notas"
     */
    public String title() {
        return (isDirty() ? "*" : "") + documentName() + " - " + APP_NAME;
    }

    private String documentName() {
        return currentPath == null ? "Sin título" : currentPath.getFileName().toString();
    }

    // ------------------------------------------------------------------ archivo

    /**
     * Nuevo documento vacío (pregunta antes si hay cambios sin guardar).
     *
     * @return {@code false} si el usuario canceló
     */
    public boolean newDocument() {
        if (!confirmDiscard()) {
            return false;
        }
        load(TextDocument.empty(), null);
        return true;
    }

    /**
     * Pide un archivo y lo abre.
     *
     * @return {@code false} si se canceló o falló
     */
    public boolean open() {
        if (!confirmDiscard()) {
            return false;
        }
        return dialogs.chooseFileToOpen().map(this::loadFile).orElse(false);
    }

    /**
     * Abre un archivo concreto (pregunta antes si hay cambios sin guardar).
     *
     * @param path ruta del archivo
     * @return {@code false} si se canceló o falló
     */
    public boolean openPath(Path path) {
        return confirmDiscard() && loadFile(path);
    }

    private boolean loadFile(Path path) {
        try {
            if (Files.size(path) > 50L * 1024 * 1024) {
                dialogs.showError("Abrir", "El archivo es demasiado grande para este editor (más de 50 MB).");
                return false;
            }
            load(DocumentIO.read(path), path);
            settings.addRecentFile(path.toAbsolutePath().toString());
            menus.refreshRecent();
            return true;
        } catch (IOException e) {
            settings.removeRecentFile(path.toAbsolutePath().toString());
            menus.refreshRecent();
            dialogs.showError("Abrir", "No se pudo abrir el archivo:\n" + path + "\n\n" + e.getMessage());
            return false;
        }
    }

    private void load(TextDocument loaded, Path path) {
        this.document = loaded;
        this.currentPath = path;
        this.savedText = loaded.text();
        editor.setText(loaded.text());
        editor.positionCaret(0);
        updateDirty();
        updateStatus();
    }

    /**
     * Guarda en el archivo actual; si el documento es nuevo pide un nombre.
     *
     * @return {@code true} si quedó guardado
     */
    public boolean save() {
        return currentPath == null ? saveAs() : writeTo(currentPath);
    }

    /**
     * Pide un nombre y guarda.
     *
     * @return {@code true} si quedó guardado
     */
    public boolean saveAs() {
        String suggested = currentPath == null ? "Sin título.txt" : currentPath.getFileName().toString();
        return dialogs.chooseFileToSave(suggested).map(path -> {
            Path target = path.getFileName().toString().contains(".") ? path : path.resolveSibling(path.getFileName() + ".txt");
            return writeTo(target);
        }).orElse(false);
    }

    private boolean writeTo(Path path) {
        TextDocument toWrite = document.withText(editor.getText());
        try {
            try {
                DocumentIO.write(path, toWrite);
            } catch (UnmappableTextException e) {
                if (!dialogs.confirmSaveAsUtf8(toWrite.charset().displayName())) {
                    return false;
                }
                toWrite = toWrite.withEncoding(StandardCharsets.UTF_8, false);
                DocumentIO.write(path, toWrite);
            }
        } catch (IOException e) {
            dialogs.showError("Guardar", "No se pudo guardar el archivo:\n" + path + "\n\n" + e.getMessage());
            return false;
        }
        this.document = toWrite;
        this.currentPath = path;
        this.savedText = toWrite.text();
        settings.addRecentFile(path.toAbsolutePath().toString());
        menus.refreshRecent();
        updateDirty();
        updateStatus();
        return true;
    }

    /**
     * Si hay cambios sin guardar pregunta qué hacer, y guarda si se pide.
     *
     * @return {@code true} si se puede continuar (no había cambios, se guardaron o se descartaron)
     */
    public boolean confirmDiscard() {
        if (!isDirty()) {
            return true;
        }
        return switch (dialogs.confirmUnsaved(documentName())) {
            case SAVE -> save();
            case DISCARD -> true;
            case CANCEL -> false;
        };
    }

    /** Cierra la aplicación si el usuario confirma lo de los cambios sin guardar. */
    public void exit() {
        if (confirmDiscard()) {
            saveSettings();
            stage.close();
        }
    }

    private void saveSettings() {
        settings.setWordWrap(editor.isWrapText());
        settings.setDarkTheme(darkTheme);
        settings.setStatusBarVisible(isStatusBarVisible());
        settings.setFont(font);
        settings.setZoom(zoom);
        if (stage.getScene() != null && !stage.isMaximized() && !stage.isIconified()) {
            settings.setWindowSize(stage.getScene().getWidth(), stage.getScene().getHeight());
        }
    }

    // ------------------------------------------------------------------ edición

    /** Deshace el último cambio. */
    public void undo() {
        editor.undo();
    }

    /** Rehace el cambio deshecho. */
    public void redo() {
        editor.redo();
    }

    /** Selecciona todo el texto. */
    public void selectAll() {
        editor.selectAll();
    }

    /** Inserta la fecha y hora actuales en la posición del cursor (formato "HH:mm dd/MM/yyyy"). */
    public void insertDateTime() {
        editor.replaceSelection(clock.get().format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")));
    }

    /**
     * Mueve el cursor al inicio de una línea y la resalta.
     *
     * @param line línea (desde 1)
     * @return {@code false} si la línea no existe
     */
    public boolean goToLine(int line) {
        int start = TextStats.lineStart(editor.getText(), line);
        if (start < 0) {
            return false;
        }
        int end = editor.getText().indexOf('\n', start);
        // anclaje al final y cursor al inicio: la línea queda resaltada y el cursor en su primera columna
        // (positionCaret() después de seleccionar borraría la selección)
        editor.selectRange(end < 0 ? editor.getLength() : end, start);
        editor.requestFocus();
        return true;
    }

    /** Pide un número de línea y va a ella. */
    public void goToLineDialog() {
        int current = TextStats.position(editor.getText(), editor.getCaretPosition()).line();
        int max = TextStats.lineCount(editor.getText());
        OptionalInt line = dialogs.askLineNumber(current, max);
        if (line.isPresent() && !goToLine(line.getAsInt())) {
            dialogs.showError("Ir a la línea", "El número de línea debe estar entre 1 y " + max + ".");
        }
    }

    // ------------------------------------------------------------------ buscar y reemplazar

    /**
     * Busca la siguiente coincidencia y la selecciona.
     *
     * @param query texto buscado
     * @param options opciones de búsqueda
     * @return {@code true} si se encontró
     */
    public boolean findNext(String query, SearchOptions options) {
        return find(query, options, true);
    }

    /**
     * Busca la coincidencia anterior y la selecciona.
     *
     * @param query texto buscado
     * @param options opciones de búsqueda
     * @return {@code true} si se encontró
     */
    public boolean findPrevious(String query, SearchOptions options) {
        return find(query, options, false);
    }

    /**
     * Repite la última búsqueda hacia adelante (F3).
     *
     * @return {@code true} si se encontró
     */
    public boolean findNextAgain() {
        return !lastQuery.isEmpty() && find(lastQuery, lastOptions, true);
    }

    /**
     * Repite la última búsqueda hacia atrás (Mayús+F3).
     *
     * @return {@code true} si se encontró
     */
    public boolean findPreviousAgain() {
        return !lastQuery.isEmpty() && find(lastQuery, lastOptions, false);
    }

    private boolean find(String query, SearchOptions options, boolean forward) {
        lastQuery = query == null ? "" : query;
        lastOptions = options;
        int from = forward ? editor.getSelection().getEnd() : editor.getSelection().getStart();
        int index = SearchEngine.find(editor.getText(), lastQuery, from, forward, options);
        if (index < 0) {
            return false;
        }
        editor.selectRange(index, index + lastQuery.length());
        return true;
    }

    /**
     * Reemplaza la coincidencia seleccionada (si lo seleccionado coincide con la búsqueda) y pasa a la siguiente; si lo
     * seleccionado no coincide, solo busca la siguiente.
     *
     * @param query texto buscado
     * @param replacement texto de reemplazo
     * @param options opciones de búsqueda
     * @return {@code true} si se reemplazó algo
     */
    public boolean replaceCurrent(String query, String replacement, SearchOptions options) {
        boolean replaced = false;
        if (SearchEngine.matches(editor.getSelectedText(), query, options)) {
            editor.replaceSelection(replacement);
            replaced = true;
        }
        findNext(query, options);
        return replaced;
    }

    /**
     * Reemplaza todas las coincidencias en una sola operación (un único "deshacer" las revierte todas).
     *
     * @param query texto buscado
     * @param replacement texto de reemplazo
     * @param options opciones de búsqueda
     * @return cantidad de reemplazos
     */
    public int replaceAll(String query, String replacement, SearchOptions options) {
        SearchEngine.ReplaceResult result = SearchEngine.replaceAll(editor.getText(), query, replacement, options);
        if (result.count() > 0) {
            editor.replaceText(0, editor.getLength(), result.text());
        }
        return result.count();
    }

    /**
     * Texto seleccionado, útil para precargar la ventana de búsqueda.
     *
     * @return la selección actual
     */
    public String selectedText() {
        return editor.getSelectedText();
    }

    /**
     * Última búsqueda hecha.
     *
     * @return el texto buscado
     */
    public String lastQuery() {
        return lastQuery;
    }

    // ------------------------------------------------------------------ formato y vista

    /**
     * Activa o desactiva el ajuste de línea.
     *
     * @param wrap {@code true} para ajustar el texto al ancho de la ventana
     */
    public void setWordWrap(boolean wrap) {
        editor.setWrapText(wrap);
    }

    /**
     * Cambia la fuente.
     *
     * @param choice nueva fuente
     */
    public void setFont(FontChoice choice) {
        this.font = choice;
        applyFont();
    }

    /** Pide una fuente y la aplica. */
    public void chooseFontDialog() {
        dialogs.chooseFont(font).ifPresent(this::setFont);
    }

    /** Aumenta el zoom 10 puntos porcentuales (hasta 500 %). */
    public void zoomIn() {
        setZoom(zoom + ZOOM_STEP);
    }

    /** Reduce el zoom 10 puntos porcentuales (hasta 30 %). */
    public void zoomOut() {
        setZoom(zoom - ZOOM_STEP);
    }

    /** Vuelve al 100 %. */
    public void zoomReset() {
        setZoom(100);
    }

    private void setZoom(int value) {
        this.zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, value));
        applyFont();
        updateStatus();
    }

    /**
     * Muestra u oculta la barra de estado.
     *
     * @param visible {@code true} para mostrarla
     */
    public void setStatusBarVisible(boolean visible) {
        root.setBottom(visible ? statusBar : null);
    }

    /**
     * Cambia entre el tema claro y el oscuro.
     *
     * @param dark {@code true} para el tema oscuro
     */
    public void setDarkTheme(boolean dark) {
        this.darkTheme = dark;
        applyTheme();
    }

    private void applyTheme() {
        Scene scene = stage.getScene();
        if (scene == null) {
            return;
        }
        String css = java.util.Objects.requireNonNull(getClass().getResource(DARK_CSS), "No se encontró dark.css").toExternalForm();
        scene.getStylesheets().remove(css);
        if (darkTheme) {
            scene.getStylesheets().add(css);
        }
    }

    private void applyFont() {
        editor.setFont(Font.font(font.family(), font.bold() ? FontWeight.BOLD : FontWeight.NORMAL,
                font.italic() ? FontPosture.ITALIC : FontPosture.REGULAR, font.size() * zoom / 100.0));
    }

    /** Muestra la ventana "Acerca de". */
    public void showAbout() {
        dialogs.showInfo("Acerca de " + APP_NAME, APP_NAME + " con JavaFX\nEjemplo de Java Universe: menús, atajos, diálogos, temas y preferencias.");
    }

    // ------------------------------------------------------------------ estado

    private void updateDirty() {
        String current = editor.getText();
        boolean changed = current.length() != savedText.length() || !current.equals(savedText);
        dirty.set(changed);
        stage.setTitle(title());
    }

    private void updateStatus() {
        String text = editor.getText();
        TextStats.Position position = TextStats.position(text, editor.getCaretPosition());
        positionLabel.setText("Ln " + position.line() + ", Col " + position.column());
        countLabel.setText(text.length() + (text.length() == 1 ? " carácter" : " caracteres"));
        zoomLabel.setText(zoom + "%");
        encodingLabel.setText(document.encodingLabel());
        lineEndingLabel.setText(document.lineEnding().label());
    }
}
