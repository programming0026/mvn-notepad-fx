# Bloc de notas con JavaFX

Editor de texto de escritorio similar al **Bloc de notas** de Windows, hecho con **JavaFX 21**, **Java 21** y **Maven**, sin más dependencias que JavaFX. Además de ser usable, está pensado para aprender a **estructurar una aplicación de escritorio de forma que se pueda probar**.

## Funciones

| Menú | Opciones |
|------|----------|
| **Archivo** | Nuevo (`Ctrl+N`), Abrir (`Ctrl+O`), Guardar (`Ctrl+S`), Guardar como (`Ctrl+Mayús+S`), **Archivos recientes**, Salir |
| **Edición** | Deshacer (`Ctrl+Z`), Rehacer (`Ctrl+Y`), Cortar, Copiar, Pegar, Eliminar, **Buscar** (`Ctrl+F`), Buscar siguiente/anterior (`F3` / `Mayús+F3`), **Reemplazar** (`Ctrl+H`), **Ir a la línea** (`Ctrl+G`), Seleccionar todo, **Hora y fecha** (`F5`) |
| **Formato** | Ajuste de línea, **Fuente** (familia, estilo y tamaño con vista previa) |
| **Ver** | Acercar / Alejar / Restaurar zoom (`Ctrl +`, `Ctrl -`, `Ctrl 0` o `Ctrl` + rueda del ratón), Barra de estado, **Tema oscuro** |

Y además:

- **Marca de cambios** en el título (`*notas.txt - Bloc de notas`) y aviso "¿Guardar los cambios?" al cerrar, abrir otro archivo o crear uno nuevo. Si vuelves al texto guardado, la marca desaparece.
- **Barra de estado** con línea y columna, cantidad de caracteres, zoom, tipo de fin de línea y codificación.
- **Arrastrar y soltar** un archivo sobre la ventana para abrirlo.
- Abrir un archivo desde la línea de comandos: `java -jar ... notas.txt`.
- **Preferencias persistentes**: tema, fuente, zoom, ajuste de línea, barra de estado, tamaño de ventana y archivos recientes se recuerdan entre sesiones.
- Buscar/reemplazar en una **ventana no modal** (puedes seguir escribiendo) con opciones de mayúsculas, palabra completa y "continuar al llegar al final". "Reemplazar todo" se deshace con un solo `Ctrl+Z`.

### Cuidado con los archivos

| Situación | Qué hace |
|-----------|----------|
| Codificación | Detecta UTF-8 (con o sin BOM) y UTF-16 (con BOM); si los bytes no son UTF-8 válido, los lee como **Windows-1252** (archivos antiguos). Al guardar respeta la codificación original |
| Fin de línea | Detecta CRLF, LF o CR y **los conserva** al guardar (un archivo de Linux no se llena de `\r`). Internamente el editor usa siempre `\n` |
| Caracteres que no caben | Si el texto tiene, por ejemplo, un emoji y el archivo es Windows-1252, **avisa** y ofrece guardarlo como UTF-8 en lugar de perder el carácter |
| Guardado seguro | Escribe en un archivo temporal y lo mueve al destino: si falla a medias, el original no queda truncado |
| Nombre sin extensión | Añade `.txt`, como el Bloc de notas |
| Archivos enormes | Rechaza los de más de 50 MB (el `TextArea` de JavaFX no está pensado para ellos) |

## Arquitectura: separar para poder probar

```text
src/main/java/com/juniverse/notepad/
├── Launcher.java, NotepadApp.java   # arranque
├── core/                            # lógica SIN JavaFX (se prueba con JUnit puro)
│   ├── DocumentIO, TextDocument     # leer/escribir con codificación y fin de línea
│   ├── LineEnding                   # CRLF / LF / CR
│   ├── SearchEngine, SearchOptions  # buscar y reemplazar (texto literal)
│   ├── TextStats                    # línea y columna, nº de líneas, palabras
│   └── AppSettings, FontChoice      # preferencias (java.util.prefs)
└── ui/                              # interfaz JavaFX
    ├── NotepadController            # estado y acciones: abrir, guardar, buscar, zoom...
    ├── NotepadMenus                 # menús, atajos y opciones que se habilitan según el estado
    ├── FindReplaceDialog            # ventana de buscar y reemplazar
    ├── Dialogs (interfaz) + FxDialogs  # lo que se le pide al usuario
    └── UnsavedChoice
src/main/resources/.../dark.css      # tema oscuro
```

Tres ideas que hacen la diferencia:

1. **La lógica no sabe de JavaFX.** Leer archivos, buscar y calcular la posición del cursor viven en `core/` y se prueban sin abrir nada.
2. **Los diálogos son una interfaz** (`Dialogs`). La aplicación usa ventanas reales (`FxDialogs`); las pruebas usan respuestas guionizadas (`FakeDialogs`) para simular "el usuario eligió Cancelar" o "eligió este archivo".
3. **El controlador expone acciones** (`save()`, `findNext(...)`, `zoomIn()`...) que los menús solo invocan, así que se pueden llamar desde una prueba igual que desde un clic.

## Requisitos

- JDK 21 y Maven 3.9 o superior.

## Ejecutar

```bash
mvn javafx:run                                                     # directamente desde Maven
mvn clean package
java -jar target/mvn-notepad-fx-1.0.0-SNAPSHOT-launcher.jar        # jar ejecutable
java -jar target/mvn-notepad-fx-1.0.0-SNAPSHOT-launcher.jar notas.txt   # abre un archivo
```

El jar incluye las bibliotecas nativas de JavaFX de **la plataforma donde lo compilas** (Windows, Linux o macOS): para otro sistema operativo hay que compilarlo allí. La clase `Launcher` existe porque la clase principal de un jar "gordo" no puede heredar de `Application`.

## Pruebas

```bash
mvn test
```

75 pruebas **sin necesidad de pantalla**:

- **`core` (39):** lectura de UTF-8, UTF-8 con BOM, UTF-16 y Windows-1252; conservación de los bytes exactos al abrir y guardar; no perder el archivo original si falla el guardado; archivos grandes; búsqueda hacia adelante y atrás con vuelta al documento, mayúsculas, palabra completa y texto literal (sin expresiones regulares); reemplazo sin solapar; línea y columna; preferencias y archivos recientes (sin duplicados, máximo 8).
- **`ui` (36):** el editor completo con JavaFX en modo *headless* mediante **Monocle** (la configuración está en el `argLine` de Surefire): marca de cambios y título, barra de estado, abrir y guardar conservando el formato, las tres respuestas a "cambios sin guardar" (guardar, descartar, cancelar), buscar/reemplazar, ir a la línea, hora y fecha con un reloj controlado, zoom con límites, tema, fuente, atajos de teclado y habilitación de los menús, archivos recientes y que las preferencias se guardan al salir y se restauran al volver a abrir.

Con Monocle las pruebas de interfaz corren igual en un servidor de integración continua que en tu equipo.

## Ideas para ampliar

- Imprimir (`PrinterJob`) y vista previa.
- Pestañas para varios documentos.
- Resaltado de sintaxis (RichTextFX) o numeración de líneas.
- Reemplazar con expresiones regulares y buscar en todos los archivos de una carpeta.
- Guardado automático y recuperación tras un cierre inesperado.

## Licencia

Código de ejemplo de uso libre con fines educativos.
