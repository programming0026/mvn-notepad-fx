package com.juniverse.notepad.core;

/**
 * Búsqueda y reemplazo de texto <b>literal</b> (sin expresiones regulares: un {@code .} o un {@code *} buscan esos
 * caracteres). Es independiente de la interfaz, por eso se prueba sin JavaFX.
 */
public final class SearchEngine {

    /**
     * Resultado de reemplazar todas las coincidencias.
     *
     * @param text texto resultante
     * @param count cuántas coincidencias se reemplazaron
     */
    public record ReplaceResult(String text, int count) {
    }

    private SearchEngine() {
    }

    /**
     * Busca una coincidencia.
     *
     * @param text texto donde buscar
     * @param query texto buscado
     * @param from posición desde la que se busca: hacia adelante, la primera coincidencia que empiece en {@code from} o después;
     *             hacia atrás, la última que termine en {@code from} o antes
     * @param forward {@code true} hacia adelante, {@code false} hacia atrás
     * @param options opciones de búsqueda
     * @return posición de inicio de la coincidencia, o -1 si no hay
     */
    public static int find(String text, String query, int from, boolean forward, SearchOptions options) {
        if (query == null || query.isEmpty() || text.length() < query.length()) {
            return -1;
        }
        int start = Math.max(0, Math.min(from, text.length()));
        int found = forward ? indexForward(text, query, start, options) : indexBackward(text, query, start, options);
        if (found < 0 && options.wrapAround()) {
            found = forward ? indexForward(text, query, 0, options) : indexBackward(text, query, text.length(), options);
        }
        return found;
    }

    /**
     * Reemplaza todas las coincidencias, sin solaparlas y de izquierda a derecha.
     *
     * @param text texto original
     * @param query texto buscado
     * @param replacement texto de reemplazo (literal)
     * @param options opciones de búsqueda (el reemplazo ignora {@code wrapAround})
     * @return el texto nuevo y la cantidad de reemplazos
     */
    public static ReplaceResult replaceAll(String text, String query, String replacement, SearchOptions options) {
        if (query == null || query.isEmpty()) {
            return new ReplaceResult(text, 0);
        }
        SearchOptions noWrap = new SearchOptions(options.matchCase(), options.wholeWord(), false);
        StringBuilder result = new StringBuilder();
        int cursor = 0;
        int count = 0;
        while (true) {
            int index = indexForward(text, query, cursor, noWrap);
            if (index < 0) {
                break;
            }
            result.append(text, cursor, index).append(replacement);
            cursor = index + query.length();
            count++;
        }
        result.append(text, cursor, text.length());
        return new ReplaceResult(count == 0 ? text : result.toString(), count);
    }

    /**
     * Comprueba si un fragmento coincide exactamente con la búsqueda (para saber si el texto seleccionado se puede reemplazar).
     *
     * @param candidate fragmento, por ejemplo el texto seleccionado
     * @param query texto buscado
     * @param options opciones de búsqueda
     * @return {@code true} si coincide
     */
    public static boolean matches(String candidate, String query, SearchOptions options) {
        return query != null && !query.isEmpty() && candidate.length() == query.length()
                && candidate.regionMatches(!options.matchCase(), 0, query, 0, query.length());
    }

    private static int indexForward(String text, String query, int from, SearchOptions options) {
        int last = text.length() - query.length();
        for (int i = from; i <= last; i++) {
            if (matchesAt(text, query, i, options)) {
                return i;
            }
        }
        return -1;
    }

    private static int indexBackward(String text, String query, int before, SearchOptions options) {
        for (int i = Math.min(before - query.length(), text.length() - query.length()); i >= 0; i--) {
            if (matchesAt(text, query, i, options)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean matchesAt(String text, String query, int index, SearchOptions options) {
        if (!text.regionMatches(!options.matchCase(), index, query, 0, query.length())) {
            return false;
        }
        if (!options.wholeWord()) {
            return true;
        }
        boolean startOk = index == 0 || !isWordChar(text.charAt(index - 1));
        int end = index + query.length();
        boolean endOk = end == text.length() || !isWordChar(text.charAt(end));
        return startOk && endOk;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
