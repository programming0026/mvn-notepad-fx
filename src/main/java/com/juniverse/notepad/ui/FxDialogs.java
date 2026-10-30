package com.juniverse.notepad.ui;

import com.juniverse.notepad.core.FontChoice;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/** Implementación de {@link Dialogs} con las ventanas estándar de JavaFX. */
public final class FxDialogs implements Dialogs {

    private final Stage owner;
    private Path lastDirectory;

    /**
     * Crea los diálogos.
     *
     * @param owner ventana principal (los diálogos se muestran sobre ella)
     */
    public FxDialogs(Stage owner) {
        this.owner = owner;
    }

    @Override
    public UnsavedChoice confirmUnsaved(String documentName) {
        ButtonType save = new ButtonType("Guardar", ButtonBar.ButtonData.YES);
        ButtonType discard = new ButtonType("No guardar", ButtonBar.ButtonData.NO);
        ButtonType cancel = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "¿Quieres guardar los cambios en " + documentName + "?", save, discard, cancel);
        alert.initOwner(owner);
        alert.setTitle("Bloc de notas");
        alert.setHeaderText(null);
        ButtonType result = alert.showAndWait().orElse(cancel);
        return result == save ? UnsavedChoice.SAVE : result == discard ? UnsavedChoice.DISCARD : UnsavedChoice.CANCEL;
    }

    @Override
    public Optional<Path> chooseFileToOpen() {
        FileChooser chooser = chooser("Abrir");
        java.io.File file = chooser.showOpenDialog(owner);
        return remember(file);
    }

    @Override
    public Optional<Path> chooseFileToSave(String suggestedName) {
        FileChooser chooser = chooser("Guardar como");
        chooser.setInitialFileName(suggestedName);
        java.io.File file = chooser.showSaveDialog(owner);
        return remember(file);
    }

    private FileChooser chooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Documentos de texto (*.txt)", "*.txt"),
                new FileChooser.ExtensionFilter("Código y datos (*.java, *.md, *.json, *.xml, *.csv, *.log)", "*.java", "*.md", "*.json", "*.xml", "*.csv", "*.log", "*.html", "*.css", "*.js", "*.properties"),
                new FileChooser.ExtensionFilter("Todos los archivos", "*.*"));
        if (lastDirectory != null && java.nio.file.Files.isDirectory(lastDirectory)) {
            chooser.setInitialDirectory(lastDirectory.toFile());
        }
        return chooser;
    }

    private Optional<Path> remember(java.io.File file) {
        if (file == null) {
            return Optional.empty();
        }
        Path path = file.toPath();
        lastDirectory = path.toAbsolutePath().getParent();
        return Optional.of(path);
    }

    @Override
    public void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    @Override
    public void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    @Override
    public OptionalInt askLineNumber(int current, int max) {
        TextInputDialog dialog = new TextInputDialog(String.valueOf(current));
        dialog.initOwner(owner);
        dialog.setTitle("Ir a la línea");
        dialog.setHeaderText(null);
        dialog.setContentText("Número de línea (1 a " + max + "):");
        Optional<String> answer = dialog.showAndWait();
        if (answer.isEmpty()) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(answer.get().strip()));
        } catch (NumberFormatException e) {
            showError("Ir a la línea", "Escribe un número entero.");
            return OptionalInt.empty();
        }
    }

    @Override
    public Optional<FontChoice> chooseFont(FontChoice current) {
        Dialog<FontChoice> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Fuente");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ComboBox<String> family = new ComboBox<>(FXCollections.observableArrayList(Font.getFamilies()));
        family.setValue(current.family());
        family.setPrefWidth(260);
        ComboBox<String> style = new ComboBox<>(FXCollections.observableArrayList("Normal", "Cursiva", "Negrita", "Negrita cursiva"));
        style.setValue(styleName(current));
        Spinner<Integer> size = new Spinner<>(6, 96, (int) current.size());
        size.setEditable(true);
        Label preview = new Label("AaBbYyZz 0123 áéíóú ñ");
        Runnable refresh = () -> preview.setFont(Font.font(family.getValue(),
                style.getValue().startsWith("Negrita") ? FontWeight.BOLD : FontWeight.NORMAL,
                style.getValue().endsWith("cursiva") || style.getValue().equals("Cursiva") ? FontPosture.ITALIC : FontPosture.REGULAR, size.getValue()));
        family.valueProperty().addListener((o, a, b) -> refresh.run());
        style.valueProperty().addListener((o, a, b) -> refresh.run());
        size.valueProperty().addListener((o, a, b) -> refresh.run());
        refresh.run();

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(15));
        grid.addRow(0, new Label("Fuente:"), family);
        grid.addRow(1, new Label("Estilo:"), style);
        grid.addRow(2, new Label("Tamaño:"), size);
        grid.addRow(3, new Label("Vista previa:"), preview);
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(button -> button == ButtonType.OK
                ? new FontChoice(family.getValue(), style.getValue().startsWith("Negrita"),
                        style.getValue().endsWith("cursiva") || style.getValue().equals("Cursiva"), size.getValue())
                : null);
        return dialog.showAndWait();
    }

    private static String styleName(FontChoice font) {
        return font.bold() && font.italic() ? "Negrita cursiva" : font.bold() ? "Negrita" : font.italic() ? "Cursiva" : "Normal";
    }

    @Override
    public boolean confirmSaveAsUtf8(String encodingName) {
        ButtonType yes = new ButtonType("Guardar como UTF-8", ButtonBar.ButtonData.YES);
        ButtonType no = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.WARNING, "El texto tiene caracteres que " + encodingName + " no puede guardar. "
                + "¿Quieres guardarlo como UTF-8 para no perderlos?", yes, no);
        alert.initOwner(owner);
        alert.setTitle("Bloc de notas");
        alert.setHeaderText(null);
        return alert.showAndWait().orElse(no) == yes;
    }

    /**
     * Familias tipográficas disponibles (útil para depuración).
     *
     * @return lista de familias
     */
    static List<String> families() {
        return Font.getFamilies();
    }
}
