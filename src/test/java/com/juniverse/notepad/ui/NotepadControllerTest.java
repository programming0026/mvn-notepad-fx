package com.juniverse.notepad.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.juniverse.notepad.core.AppSettings;
import com.juniverse.notepad.core.FontChoice;
import com.juniverse.notepad.core.LineEnding;
import com.juniverse.notepad.core.SearchOptions;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pruebas del editor completo (menús, área de texto, barra de estado) SIN pantalla: JavaFX corre con Monocle en modo
 * "headless" (ver la configuración de Surefire en el pom). Los diálogos se sustituyen por {@link FakeDialogs}.
 */
@DisplayName("Editor de texto con JavaFX (sin pantalla)")
class NotepadControllerTest {

    @TempDir
    Path dir;

    private Preferences node;
    private FakeDialogs dialogs;
    private NotepadController controller;
    private Stage stage;

    @BeforeAll
    static void startToolkit() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyRunning) {
            started.countDown();
        }
        assertTrue(started.await(30, TimeUnit.SECONDS), "JavaFX no arrancó");
        Platform.setImplicitExit(false);
    }

    /** Ejecuta código en el hilo de JavaFX y devuelve su resultado (o relanza su excepción). */
    static <T> T onFx(Callable<T> action) {
        if (Platform.isFxApplicationThread()) {
            try {
                return action.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        try {
            return task.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e.getCause() == null ? e : e.getCause());
        }
    }

    static void runFx(Runnable action) {
        onFx(() -> {
            action.run();
            return null;
        });
    }

    @BeforeEach
    void setUp() {
        node = Preferences.userRoot().node("com/juniverse/notepad-test/" + UUID.randomUUID());
        dialogs = new FakeDialogs();
        onFx(() -> {
            stage = new Stage();
            controller = new NotepadController(stage, dialogs, new AppSettings(node), () -> LocalDateTime.of(2026, 1, 15, 9, 5));
            stage.setScene(new Scene(controller.root(), 900, 600));
            controller.attachToScene();
            return null;
        });
    }

    @AfterEach
    void tearDown() throws BackingStoreException {
        node.removeNode();
    }

    private void type(String text) {
        runFx(() -> controller.editor().appendText(text));
    }

    private String text() {
        return onFx(controller::text);
    }

    private Path write(String name, String content) throws IOException {
        Path path = dir.resolve(name);
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    @Nested
    @DisplayName("Documento y título")
    class DocumentState {

        @Test
        void startsAsAnEmptyUntitledDocument() {
            assertEquals("", text());
            assertFalse(onFx(controller::isDirty));
            assertEquals("Sin título - Bloc de notas", onFx(() -> stage.getTitle()));
            assertNull(controller.currentPath());
        }

        @Test
        void typingMarksTheTitleAndReturningToSavedTextClearsIt() {
            type("hola");
            assertTrue(onFx(controller::isDirty));
            assertEquals("*Sin título - Bloc de notas", onFx(() -> stage.getTitle()));

            runFx(() -> controller.editor().clear());
            assertFalse(onFx(controller::isDirty), "volver al texto guardado limpia la marca");
            assertEquals("Sin título - Bloc de notas", onFx(() -> stage.getTitle()));
        }

        @Test
        void statusBarShowsPositionCountAndFormat() {
            type("uno\ndos\ntres");
            assertEquals("Ln 3, Col 5", onFx(controller::positionText));
            assertEquals("12 caracteres", onFx(controller::countText));
            assertEquals("100%", onFx(controller::zoomText));
            assertEquals("UTF-8", onFx(controller::encodingText));

            runFx(() -> controller.editor().positionCaret(5));
            assertEquals("Ln 2, Col 2", onFx(controller::positionText));
        }
    }

    @Nested
    @DisplayName("Abrir y guardar")
    class OpenAndSave {

        @Test
        void opensAFileShowingItsFormat() throws IOException {
            Path file = write("notas.txt", "línea 1\r\nlínea 2");
            assertTrue(onFx(() -> controller.openPath(file)));

            assertEquals("línea 1\nlínea 2", text());
            assertFalse(onFx(controller::isDirty));
            assertEquals("notas.txt - Bloc de notas", onFx(() -> stage.getTitle()));
            assertEquals("Windows (CRLF)", onFx(controller::lineEndingText));
            assertEquals("UTF-8", onFx(controller::encodingText));
            assertEquals("Ln 1, Col 1", onFx(controller::positionText));
            assertEquals(file.toAbsolutePath().toString(), controller.recentFiles().get(0));
        }

        @Test
        void saveWritesTheFileKeepingTheOriginalLineEndings() throws IOException {
            Path file = write("crlf.txt", "a\r\nb");
            onFx(() -> controller.openPath(file));
            type("\nc");
            assertTrue(onFx(controller::isDirty));

            assertTrue(onFx(controller::save));
            assertEquals("a\r\nb\r\nc", Files.readString(file));
            assertFalse(onFx(controller::isDirty));
            assertTrue(dialogs.errors.isEmpty());
        }

        @Test
        void saveAsOnAnUntitledDocumentAsksForAName() throws IOException {
            type("contenido nuevo");
            Path target = dir.resolve("nuevo.txt");
            dialogs.saveAnswers.add(Optional.of(target));

            assertTrue(onFx(controller::save));
            assertEquals("Sin título.txt", dialogs.lastSuggestedName);
            assertEquals("contenido nuevo", Files.readString(target).replace("\r\n", "\n"));
            assertEquals("nuevo.txt - Bloc de notas", onFx(() -> stage.getTitle()));
            assertFalse(onFx(controller::isDirty));
        }

        @Test
        void addsTxtWhenTheNameHasNoExtension() throws IOException {
            type("x");
            dialogs.saveAnswers.add(Optional.of(dir.resolve("sinextension")));
            assertTrue(onFx(controller::saveAs));
            assertTrue(Files.exists(dir.resolve("sinextension.txt")));
        }

        @Test
        void cancellingTheSaveDialogLeavesTheDocumentDirty() {
            type("x");
            dialogs.saveAnswers.add(Optional.empty());
            assertFalse(onFx(controller::save));
            assertTrue(onFx(controller::isDirty));
        }

        @Test
        void reportsErrorsOpeningMissingFiles() {
            assertFalse(onFx(() -> controller.openPath(dir.resolve("no-existe.txt"))));
            assertEquals(1, dialogs.errors.size());
            assertTrue(dialogs.errors.get(0).startsWith("Abrir: No se pudo abrir el archivo"));
        }

        @Test
        void asksBeforeSavingCharactersTheEncodingCannotStore() throws IOException {
            Path file = dir.resolve("viejo.txt");
            Files.write(file, "canción".getBytes(com.juniverse.notepad.core.TextDocument.WINDOWS_1252));
            onFx(() -> controller.openPath(file));
            assertEquals("Windows-1252", onFx(controller::encodingText));

            type(" 😀");
            assertTrue(onFx(controller::save));
            assertEquals(1, dialogs.utf8Prompts);
            assertEquals("UTF-8", onFx(controller::encodingText));
            assertEquals("canción 😀", Files.readString(file, StandardCharsets.UTF_8));

            // y si el usuario no acepta, no se guarda ni se cambia nada
            Path other = dir.resolve("otro.txt");
            Files.write(other, "canción".getBytes(com.juniverse.notepad.core.TextDocument.WINDOWS_1252));
            onFx(() -> controller.openPath(other));
            type(" 😀");
            dialogs.utf8Answer = false;
            assertFalse(onFx(controller::save));
            assertEquals("canción", Files.readString(other, com.juniverse.notepad.core.TextDocument.WINDOWS_1252));
        }

        @Test
        void openMenuActionUsesTheFileDialog() throws IOException {
            Path file = write("elegido.txt", "elegido");
            dialogs.openAnswers.add(Optional.of(file));
            assertTrue(onFx(controller::open));
            assertEquals("elegido", text());
        }
    }

    @Nested
    @DisplayName("Cambios sin guardar")
    class UnsavedChanges {

        @Test
        void newDocumentAsksAndCanBeCancelled() {
            type("importante");
            dialogs.unsavedAnswers.add(UnsavedChoice.CANCEL);
            assertFalse(onFx(controller::newDocument));
            assertEquals("importante", text());
            assertEquals(java.util.List.of("Sin título"), dialogs.unsavedPrompts);
        }

        @Test
        void discardingThrowsAwayTheChanges() {
            type("descartable");
            dialogs.unsavedAnswers.add(UnsavedChoice.DISCARD);
            assertTrue(onFx(controller::newDocument));
            assertEquals("", text());
            assertFalse(onFx(controller::isDirty));
        }

        @Test
        void savingFirstWritesTheFileAndThenContinues() throws IOException {
            Path file = write("a.txt", "a");
            onFx(() -> controller.openPath(file));
            type("b");
            dialogs.unsavedAnswers.add(UnsavedChoice.SAVE);
            assertTrue(onFx(controller::newDocument));
            assertEquals("ab", Files.readString(file));
            assertEquals("", text());
        }

        @Test
        void opensAnotherFileOnlyAfterResolvingChanges() throws IOException {
            Path first = write("1.txt", "uno");
            Path second = write("2.txt", "dos");
            onFx(() -> controller.openPath(first));
            type("!");
            assertFalse(onFx(() -> controller.openPath(second)), "sin respuesta se asume cancelar");
            assertEquals("uno!", text());
            dialogs.unsavedAnswers.add(UnsavedChoice.DISCARD);
            assertTrue(onFx(() -> controller.openPath(second)));
            assertEquals("dos", text());
        }

        @Test
        void cleanDocumentsNeverAsk() {
            assertTrue(onFx(controller::newDocument));
            assertTrue(dialogs.unsavedPrompts.isEmpty());
        }
    }

    @Nested
    @DisplayName("Edición, búsqueda y reemplazo")
    class Editing {

        @Test
        void undoAndRedo() {
            type("abc");
            runFx(controller::undo);
            assertEquals("", text());
            runFx(controller::redo);
            assertEquals("abc", text());
        }

        @Test
        void findNextAndPreviousSelectMatches() {
            runFx(() -> controller.editor().setText("uno dos Dos tres"));
            SearchOptions options = new SearchOptions(false, false, true);

            assertTrue(onFx(() -> controller.findNext("dos", options)));
            assertEquals("dos", onFx(controller::selectedText));
            assertEquals(4, onFx(() -> controller.editor().getSelection().getStart()));

            assertTrue(onFx(() -> controller.findNext("dos", options)));
            assertEquals(8, onFx(() -> controller.editor().getSelection().getStart()));

            assertTrue(onFx(() -> controller.findNext("dos", options)), "da la vuelta al documento");
            assertEquals(4, onFx(() -> controller.editor().getSelection().getStart()));

            assertTrue(onFx(() -> controller.findPrevious("dos", options)));
            assertEquals(8, onFx(() -> controller.editor().getSelection().getStart()));

            assertFalse(onFx(() -> controller.findNext("zzz", options)));
        }

        @Test
        void findAgainRepeatsTheLastSearch() {
            runFx(() -> controller.editor().setText("a b a b a"));
            assertFalse(onFx(controller::findNextAgain), "todavía no hay búsqueda previa");
            onFx(() -> controller.findNext("a", SearchOptions.DEFAULT));
            onFx(controller::findNextAgain);
            assertEquals(4, onFx(() -> controller.editor().getSelection().getStart()));
            onFx(controller::findPreviousAgain);
            assertEquals(0, onFx(() -> controller.editor().getSelection().getStart()));
        }

        @Test
        void replaceCurrentReplacesTheSelectionAndMovesOn() {
            runFx(() -> controller.editor().setText("gato y gato y gato"));
            onFx(() -> controller.findNext("gato", SearchOptions.DEFAULT));
            assertTrue(onFx(() -> controller.replaceCurrent("gato", "perro", SearchOptions.DEFAULT)));
            assertEquals("perro y gato y gato", text());
            assertEquals("gato", onFx(controller::selectedText), "queda seleccionada la siguiente coincidencia");
            assertFalse(onFx(() -> controller.replaceCurrent("loro", "x", SearchOptions.DEFAULT)));
        }

        @Test
        void replaceAllIsASingleUndoStep() {
            runFx(() -> controller.editor().setText("a-b-c-d"));
            assertEquals(3, onFx(() -> controller.replaceAll("-", "+", SearchOptions.DEFAULT)));
            assertEquals("a+b+c+d", text());
            assertEquals(0, onFx(() -> controller.replaceAll("zzz", "x", SearchOptions.DEFAULT)));
            runFx(controller::undo);
            assertEquals("a-b-c-d", text());
        }

        @Test
        void goToLineSelectsThatLine() {
            runFx(() -> controller.editor().setText("uno\ndos\ntres"));
            assertTrue(onFx(() -> controller.goToLine(2)));
            assertEquals("dos", onFx(controller::selectedText));
            assertEquals("Ln 2, Col 1", onFx(controller::positionText));
            assertFalse(onFx(() -> controller.goToLine(9)));
        }

        @Test
        void goToLineDialogReportsInvalidNumbers() {
            runFx(() -> controller.editor().setText("uno\ndos"));
            dialogs.lineAnswers.add(OptionalInt.of(7));
            runFx(controller::goToLineDialog);
            assertEquals(1, dialogs.errors.size());
            dialogs.lineAnswers.add(OptionalInt.of(2));
            runFx(controller::goToLineDialog);
            assertEquals("dos", onFx(controller::selectedText));
        }

        @Test
        void insertsDateAndTimeAtTheCaret() {
            type("Hoy: ");
            runFx(controller::insertDateTime);
            assertEquals("Hoy: 09:05 15/01/2026", text());
        }

        @Test
        void selectAllSelectsEverything() {
            type("todo esto");
            runFx(controller::selectAll);
            assertEquals("todo esto", onFx(controller::selectedText));
        }
    }

    @Nested
    @DisplayName("Formato y vista")
    class FormatAndView {

        @Test
        void zoomIsLimitedAndChangesTheFontSize() {
            double base = onFx(() -> controller.editor().getFont().getSize());
            runFx(controller::zoomIn);
            assertEquals(110, controller.zoom());
            assertEquals("110%", onFx(controller::zoomText));
            assertEquals(base * 1.1, onFx(() -> controller.editor().getFont().getSize()), 0.01);

            for (int i = 0; i < 100; i++) {
                runFx(controller::zoomIn);
            }
            assertEquals(NotepadController.MAX_ZOOM, controller.zoom());
            for (int i = 0; i < 100; i++) {
                runFx(controller::zoomOut);
            }
            assertEquals(NotepadController.MIN_ZOOM, controller.zoom());
            runFx(controller::zoomReset);
            assertEquals(100, controller.zoom());
            assertEquals(base, onFx(() -> controller.editor().getFont().getSize()), 0.01);
        }

        @Test
        void toggleWordWrapStatusBarAndTheme() {
            assertFalse(onFx(() -> controller.editor().isWrapText()));
            runFx(() -> controller.setWordWrap(true));
            assertTrue(onFx(() -> controller.editor().isWrapText()));

            assertTrue(onFx(controller::isStatusBarVisible));
            runFx(() -> controller.setStatusBarVisible(false));
            assertFalse(onFx(controller::isStatusBarVisible));
            assertNull(onFx(() -> controller.root().getBottom()));

            assertTrue(onFx(() -> stage.getScene().getStylesheets().isEmpty()));
            runFx(() -> controller.setDarkTheme(true));
            assertEquals(1, onFx(() -> stage.getScene().getStylesheets().size()));
            assertTrue(onFx(() -> stage.getScene().getStylesheets().get(0)).endsWith("dark.css"));
            runFx(() -> controller.setDarkTheme(false));
            assertTrue(onFx(() -> stage.getScene().getStylesheets().isEmpty()));
        }

        @Test
        void changesTheFontThroughTheDialog() {
            dialogs.fontAnswers.add(Optional.of(new FontChoice("Serif", true, false, 20)));
            runFx(controller::chooseFontDialog);
            assertEquals(new FontChoice("Serif", true, false, 20), controller.font());
            assertEquals(20, onFx(() -> controller.editor().getFont().getSize()), 0.01);
            dialogs.fontAnswers.add(Optional.empty());
            runFx(controller::chooseFontDialog);
            assertEquals("Serif", controller.font().family(), "cancelar no cambia la fuente");
        }

        @Test
        void aboutShowsAnInformationDialog() {
            runFx(controller::showAbout);
            assertEquals(1, dialogs.infos.size());
        }
    }

    @Nested
    @DisplayName("Menús y preferencias")
    class MenusAndSettings {

        private Menu menu(String name) {
            return onFx(() -> controller.root().getTop() instanceof javafx.scene.control.MenuBar bar
                    ? bar.getMenus().stream().filter(m -> m.getText().equals(name)).findFirst().orElseThrow() : null);
        }

        private MenuItem item(String menuName, String itemName) {
            return onFx(() -> menu(menuName).getItems().stream().filter(i -> itemName.equals(i.getText())).findFirst().orElseThrow());
        }

        @Test
        void hasTheClassicMenusAndShortcuts() {
            for (String name : new String[] {"Archivo", "Edición", "Formato", "Ver", "Ayuda"}) {
                assertNotNull(menu(name), name);
            }
            assertEquals(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN), item("Archivo", "Guardar").getAccelerator());
            assertEquals(new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN), item("Edición", "Buscar...").getAccelerator());
            assertEquals(new KeyCodeCombination(KeyCode.F5), item("Edición", "Hora y fecha").getAccelerator());
        }

        @Test
        void menuItemsRunTheActions() {
            type("texto");
            runFx(() -> item("Edición", "Seleccionar todo").getOnAction().handle(new javafx.event.ActionEvent()));
            assertEquals("texto", onFx(controller::selectedText));
            runFx(() -> item("Ver", "Acercar").getOnAction().handle(new javafx.event.ActionEvent()));
            assertEquals(110, controller.zoom());
        }

        @Test
        void editItemsAreEnabledOnlyWhenTheyApply() {
            assertTrue(onFx(() -> item("Edición", "Deshacer").isDisable()));
            assertTrue(onFx(() -> item("Edición", "Copiar").isDisable()));
            type("algo");
            assertFalse(onFx(() -> item("Edición", "Deshacer").isDisable()));
            runFx(controller::selectAll);
            assertFalse(onFx(() -> item("Edición", "Copiar").isDisable()));
        }

        @Test
        void recentFilesMenuListsOpenedFiles() throws IOException {
            assertTrue(onFx(() -> menu("Archivo").getItems().stream().filter(i -> "Archivos recientes".equals(i.getText())).findFirst().orElseThrow().isDisable()));
            Path file = write("reciente.txt", "x");
            onFx(() -> controller.openPath(file));
            Menu recent = (Menu) item("Archivo", "Archivos recientes");
            assertFalse(onFx(recent::isDisable));
            assertEquals(file.toAbsolutePath().toString(), onFx(() -> recent.getItems().get(0).getText()));
        }

        @Test
        void exitSavesThePreferences() {
            runFx(() -> {
                controller.setWordWrap(true);
                controller.setDarkTheme(true);
                controller.zoomIn();
                controller.setFont(new FontChoice("Serif", false, true, 16));
            });
            runFx(controller::exit);

            AppSettings saved = new AppSettings(node);
            assertTrue(saved.wordWrap());
            assertTrue(saved.darkTheme());
            assertEquals(110, saved.zoom());
            assertEquals(new FontChoice("Serif", false, true, 16), saved.font());
        }

        @Test
        void restoresPreferencesOnTheNextStart() {
            AppSettings settings = new AppSettings(node);
            settings.setWordWrap(true);
            settings.setZoom(140);
            settings.setStatusBarVisible(false);
            settings.setDarkTheme(true);
            NotepadController second = onFx(() -> {
                Stage other = new Stage();
                NotepadController c = new NotepadController(other, new FakeDialogs(), settings);
                other.setScene(new Scene(c.root(), 800, 500));
                c.attachToScene();
                return c;
            });
            assertTrue(onFx(() -> second.editor().isWrapText()));
            assertEquals(140, second.zoom());
            assertFalse(onFx(second::isStatusBarVisible));
            assertTrue(second.isDarkTheme());
        }

        @Test
        void exitIsBlockedWhenTheUserCancelsTheUnsavedPrompt() {
            type("sin guardar");
            dialogs.unsavedAnswers.add(UnsavedChoice.CANCEL);
            runFx(controller::exit);
            assertTrue(onFx(() -> stage.getScene() != null));
            assertEquals("sin guardar", text());
            assertEquals(LineEnding.system(), controller.document().lineEnding());
        }
    }
}
