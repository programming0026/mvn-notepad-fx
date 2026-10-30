package com.juniverse.notepad.ui;

import com.juniverse.notepad.core.SearchOptions;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** Ventana no modal de "Buscar y reemplazar": permite seguir editando mientras está abierta. */
final class FindReplaceDialog {

    private final NotepadController controller;
    private final Stage stage = new Stage();
    private final TextField findField = new TextField();
    private final TextField replaceField = new TextField();
    private final CheckBox matchCase = new CheckBox("Coincidir mayúsculas y minúsculas");
    private final CheckBox wholeWord = new CheckBox("Solo palabras completas");
    private final CheckBox wrap = new CheckBox("Continuar al llegar al final");
    private final Label status = new Label();
    private final Label replaceLabel = new Label("Reemplazar con:");
    private final Button replaceButton = new Button("Reemplazar");
    private final Button replaceAllButton = new Button("Reemplazar todo");

    FindReplaceDialog(NotepadController controller) {
        this.controller = controller;
        stage.setTitle("Buscar y reemplazar");
        stage.setAlwaysOnTop(true);
        wrap.setSelected(true);

        Button next = new Button("Siguiente");
        next.setDefaultButton(true);
        next.setOnAction(e -> report(controller.findNext(findField.getText(), options()), "No se encontró \"" + findField.getText() + "\""));
        Button previous = new Button("Anterior");
        previous.setOnAction(e -> report(controller.findPrevious(findField.getText(), options()), "No se encontró \"" + findField.getText() + "\""));
        replaceButton.setOnAction(e -> {
            controller.replaceCurrent(findField.getText(), replaceField.getText(), options());
            status.setText("");
        });
        replaceAllButton.setOnAction(e -> {
            int count = controller.replaceAll(findField.getText(), replaceField.getText(), options());
            status.setText(count == 0 ? "No se encontró \"" + findField.getText() + "\"" : count + (count == 1 ? " reemplazo" : " reemplazos"));
        });

        GridPane fields = new GridPane();
        fields.setHgap(10);
        fields.setVgap(8);
        fields.addRow(0, new Label("Buscar:"), findField);
        fields.addRow(1, replaceLabel, replaceField);
        findField.setPrefWidth(280);
        HBox buttons = new HBox(8, next, previous, replaceButton, replaceAllButton);
        VBox box = new VBox(10, fields, matchCase, wholeWord, wrap, buttons, status);
        box.setPadding(new Insets(14));
        stage.setScene(new javafx.scene.Scene(box));
    }

    private SearchOptions options() {
        return new SearchOptions(matchCase.isSelected(), wholeWord.isSelected(), wrap.isSelected());
    }

    private void report(boolean found, String notFound) {
        status.setText(found ? "" : notFound);
    }

    void show(boolean replaceMode, String initialQuery) {
        if (!initialQuery.isEmpty() && !initialQuery.contains("\n")) {
            findField.setText(initialQuery);
        }
        replaceLabel.setVisible(replaceMode);
        replaceField.setVisible(replaceMode);
        replaceButton.setVisible(replaceMode);
        replaceAllButton.setVisible(replaceMode);
        status.setText("");
        stage.show();
        stage.toFront();
        findField.requestFocus();
        findField.selectAll();
    }
}
