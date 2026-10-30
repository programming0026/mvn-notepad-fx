package com.juniverse.notepad.core;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Preferencias del usuario que se recuerdan entre sesiones (tema, fuente, ajuste de línea, tamaño de ventana, archivos
 * recientes). Usa la API estándar {@link Preferences}: en Windows las guarda en el registro, en Linux y macOS en archivos
 * del usuario. El nodo se recibe como parámetro para poder probarlo con uno temporal.
 */
public final class AppSettings {

    private static final int MAX_RECENT = 8;

    private final Preferences node;

    /**
     * Crea los ajustes sobre un nodo de preferencias.
     *
     * @param node nodo donde se guardan
     */
    public AppSettings(Preferences node) {
        this.node = node;
    }

    /**
     * Ajustes reales del usuario para esta aplicación.
     *
     * @return ajustes persistentes
     */
    public static AppSettings forUser() {
        return new AppSettings(Preferences.userRoot().node("com/juniverse/notepad"));
    }

    public boolean darkTheme() { return node.getBoolean("theme.dark", false); }

    public void setDarkTheme(boolean value) { node.putBoolean("theme.dark", value); }

    public boolean wordWrap() { return node.getBoolean("wrap", false); }

    public void setWordWrap(boolean value) { node.putBoolean("wrap", value); }

    public boolean statusBarVisible() { return node.getBoolean("statusbar", true); }

    public void setStatusBarVisible(boolean value) { node.putBoolean("statusbar", value); }

    public int zoom() { return node.getInt("zoom", 100); }

    public void setZoom(int value) { node.putInt("zoom", value); }

    public double windowWidth() { return node.getDouble("window.width", 900); }

    public double windowHeight() { return node.getDouble("window.height", 600); }

    /**
     * Guarda el tamaño de la ventana.
     *
     * @param width ancho
     * @param height alto
     */
    public void setWindowSize(double width, double height) {
        node.putDouble("window.width", width);
        node.putDouble("window.height", height);
    }

    /**
     * Fuente guardada.
     *
     * @return la fuente (por defecto, monoespaciada de tamaño 14)
     */
    public FontChoice font() {
        return new FontChoice(node.get("font.family", "Monospaced"), node.getBoolean("font.bold", false), node.getBoolean("font.italic", false),
                node.getDouble("font.size", 14));
    }

    /**
     * Guarda la fuente.
     *
     * @param font fuente elegida
     */
    public void setFont(FontChoice font) {
        node.put("font.family", font.family());
        node.putBoolean("font.bold", font.bold());
        node.putBoolean("font.italic", font.italic());
        node.putDouble("font.size", font.size());
    }

    /**
     * Archivos recientes, del más nuevo al más antiguo.
     *
     * @return rutas guardadas
     */
    public List<String> recentFiles() {
        List<String> files = new ArrayList<>();
        for (int i = 0; i < MAX_RECENT; i++) {
            String path = node.get("recent." + i, null);
            if (path != null) {
                files.add(path);
            }
        }
        return files;
    }

    /**
     * Registra un archivo como el más reciente (sin duplicados y con un máximo de {@value #MAX_RECENT}).
     *
     * @param path ruta del archivo
     */
    public void addRecentFile(String path) {
        List<String> files = recentFiles();
        files.remove(path);
        files.add(0, path);
        storeRecent(files.size() > MAX_RECENT ? files.subList(0, MAX_RECENT) : files);
    }

    /**
     * Quita un archivo de la lista (por ejemplo, si ya no existe).
     *
     * @param path ruta del archivo
     */
    public void removeRecentFile(String path) {
        List<String> files = recentFiles();
        files.remove(path);
        storeRecent(files);
    }

    private void storeRecent(List<String> files) {
        for (int i = 0; i < MAX_RECENT; i++) {
            if (i < files.size()) {
                node.put("recent." + i, files.get(i));
            } else {
                node.remove("recent." + i);
            }
        }
    }
}
