package com.juniverse.notepad;

import com.juniverse.notepad.core.AppSettings;
import com.juniverse.notepad.ui.FxDialogs;
import com.juniverse.notepad.ui.NotepadController;
import java.nio.file.Path;
import java.util.List;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Aplicación JavaFX: un editor de texto similar al Bloc de notas. */
public class NotepadApp extends Application {

    @Override
    public void start(Stage stage) {
        AppSettings settings = AppSettings.forUser();
        NotepadController controller = new NotepadController(stage, new FxDialogs(stage), settings);
        stage.setScene(new Scene(controller.root(), settings.windowWidth(), settings.windowHeight()));
        controller.attachToScene();
        stage.show();

        List<String> args = getParameters().getUnnamed();
        if (!args.isEmpty()) {
            controller.openPath(Path.of(args.get(0)));
        }
    }
}
