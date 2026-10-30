package com.juniverse.notepad.ui;

import com.juniverse.notepad.core.FontChoice;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/** {@link Dialogs} de prueba: responde con valores guionizados y registra lo que se le pidió. */
final class FakeDialogs implements Dialogs {

    final Deque<UnsavedChoice> unsavedAnswers = new ArrayDeque<>();
    final Deque<Optional<Path>> openAnswers = new ArrayDeque<>();
    final Deque<Optional<Path>> saveAnswers = new ArrayDeque<>();
    final Deque<OptionalInt> lineAnswers = new ArrayDeque<>();
    final Deque<Optional<FontChoice>> fontAnswers = new ArrayDeque<>();
    boolean utf8Answer = true;

    final List<String> errors = new ArrayList<>();
    final List<String> infos = new ArrayList<>();
    final List<String> unsavedPrompts = new ArrayList<>();
    int utf8Prompts;
    String lastSuggestedName;

    @Override
    public UnsavedChoice confirmUnsaved(String documentName) {
        unsavedPrompts.add(documentName);
        return unsavedAnswers.isEmpty() ? UnsavedChoice.CANCEL : unsavedAnswers.poll();
    }

    @Override
    public Optional<Path> chooseFileToOpen() {
        return openAnswers.isEmpty() ? Optional.empty() : openAnswers.poll();
    }

    @Override
    public Optional<Path> chooseFileToSave(String suggestedName) {
        lastSuggestedName = suggestedName;
        return saveAnswers.isEmpty() ? Optional.empty() : saveAnswers.poll();
    }

    @Override
    public void showError(String title, String message) {
        errors.add(title + ": " + message);
    }

    @Override
    public void showInfo(String title, String message) {
        infos.add(title + ": " + message);
    }

    @Override
    public OptionalInt askLineNumber(int current, int max) {
        return lineAnswers.isEmpty() ? OptionalInt.empty() : lineAnswers.poll();
    }

    @Override
    public Optional<FontChoice> chooseFont(FontChoice current) {
        return fontAnswers.isEmpty() ? Optional.empty() : fontAnswers.poll();
    }

    @Override
    public boolean confirmSaveAsUtf8(String encodingName) {
        utf8Prompts++;
        return utf8Answer;
    }
}
