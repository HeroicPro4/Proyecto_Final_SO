package com.umg.banquero;

import com.umg.banquero.Banquero.EstadoSolicitud;
import com.umg.banquero.Banquero.Linea;
import com.umg.banquero.Banquero.ResultadoSeguridad;
import com.umg.banquero.Banquero.ResultadoSolicitud;
import com.umg.banquero.Escenarios.Escenario;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;

/**
 * SIMULADOR DEL ALGORITMO DEL BANQUERO - INTERFAZ GRÁFICA (JavaFX).
 * Universidad Mariano Gálvez de Guatemala - Sistemas Operativos 1.
 *
 * Esta clase construye la ventana y atiende los eventos. Todos los cálculos del
 * algoritmo están en la clase Banquero; aquí solo se leen las tablas, se llama
 * a Banquero y se muestran los resultados.
 *
 * Distribución de la ventana:
 *   - Barra superior: tamaño del sistema (N y M), escenarios de prueba y ayuda.
 *   - Panel izquierdo: tablas Allocation, Max, Need y Available, y los botones
 *     para ejecutar el algoritmo de seguridad o simular una solicitud (Request).
 *   - Panel derecho: recuadro de color con el veredicto y el registro paso a paso.
 *
 * Los campos y métodos sin "private" son visibles dentro del paquete para poder
 * probarlos desde otras clases.
 */
public class App extends Application {

    // =========================================================================
    // CONSTANTES
    // =========================================================================

    static final int MAX_PROCESOS = 20;   // Límite de filas que acepta la interfaz
    static final int MAX_RECURSOS = 10;   // Límite de columnas que acepta la interfaz
    static final int MAX_VALOR = 999999;  // Valor máximo de cada celda (evita desbordes)

    private static final String VERDE = "#2e7d32";
    private static final String ROJO = "#c62828";
    private static final String NARANJA = "#ef6c00";
    private static final String GRIS = "#607d8b";
    private static final String ESTILO_ERROR = "-fx-control-inner-background: #ffcdd2;"; // celda inválida
    private static final String ESTILO_NEED = "-fx-control-inner-background: #eceff1;";  // celda de solo lectura

    private static final Font FUENTE_LOG = Font.font("Monospaced", 11);
    private static final Font FUENTE_LOG_NEGRITA = Font.font("Monospaced", FontWeight.BOLD, 11);

    // =========================================================================
    // TIPOS AUXILIARES
    // =========================================================================

    /** Contenido de las tablas como texto (lo que el usuario ve escrito en las celdas). */
    private record Textos(String[][] allocation, String[][] max, String[] available) {

        static Textos vacio() {
            return new Textos(new String[0][0], new String[0][0], new String[0]);
        }

        static Textos de(Escenario e) {
            return new Textos(textoDeMatriz(e.allocation()), textoDeMatriz(e.max()), textoDeVector(e.available()));
        }
    }

    /** Datos del sistema ya validados y convertidos a números. */
    private record Estado(int[] available, int[][] allocation, int[][] max) { }

    // =========================================================================
    // ESTADO DE LA VENTANA
    // =========================================================================

    private Stage ventana;

    // Controles principales
    final Spinner<Integer> spN = new Spinner<>(1, MAX_PROCESOS, 5);
    final Spinner<Integer> spM = new Spinner<>(1, MAX_RECURSOS, 3);
    final ComboBox<String> cmbProceso = new ComboBox<>();
    final Label lblBanner = new Label();
    final Label lblDetalle = new Label();
    final Button btnDeshacer = new Button("Deshacer última solicitud concedida");

    // Celdas de las tablas: una fila por proceso y una columna por recurso
    TextField[][] alCeldas;   // Allocation
    TextField[][] mxCeldas;   // Max
    TextField[][] ndCeldas;   // Need (solo lectura, la calcula el programa)
    TextField[] avCeldas;     // Available (una sola fila)
    TextField[] rqCeldas;     // Vector Request de la solicitud

    // Etiquetas "P0, P1..." de las tres tablas (se colorean según el resultado)
    private final List<Label[]> etiquetasProceso = new ArrayList<>();

    private final VBox contenedorTablas = new VBox(10);
    private final HBox filaRequest = new HBox(4);
    private final TextFlow registro = new TextFlow();
    private final ScrollPane scrollRegistro = new ScrollPane(registro);

    // Estado previo a la última solicitud concedida (para poder deshacerla)
    private int[] respaldoAvailable;
    private int[][] respaldoAllocation;

    private boolean cargando;            // true mientras el programa rellena celdas por código
    private boolean ajustandoControles;  // true mientras el programa cambia los Spinner por código
    boolean resultadoVisible;            // ¿hay un resultado mostrado en el recuadro de color?

    // =========================================================================
    // ARRANQUE
    // =========================================================================

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        ventana = stage;

        BorderPane raiz = new BorderPane();
        raiz.setTop(crearBarraSuperior());
        raiz.setCenter(crearCuerpo());

        stage.setTitle("Simulador del Algoritmo del Banquero");
        stage.setScene(new Scene(raiz, 1240, 740));
        stage.setMinWidth(1000);
        stage.setMinHeight(640);
        stage.show();

        cargarEscenario(Escenarios.SEGURO); // Arranca con el caso seguro como ejemplo
    }

    // =========================================================================
    // CONSTRUCCIÓN DE LA INTERFAZ
    // =========================================================================

    /** Barra superior: N, M, escenarios de prueba, limpiar y ayuda. */
    private Node crearBarraSuperior() {
        spN.setEditable(true);
        spM.setEditable(true);
        spN.setPrefWidth(80);
        spM.setPrefWidth(80);

        // Al cambiar N o M (con las flechas, o escribiendo el valor y pulsando Enter)
        // se redibujan las tablas conservando los datos que ya estaban escritos.
        spN.valueProperty().addListener((obs, antes, ahora) -> {
            if (!ajustandoControles) {
                reconstruir(textosActuales());
            }
        });
        spM.valueProperty().addListener((obs, antes, ahora) -> {
            if (!ajustandoControles) {
                reconstruir(textosActuales());
            }
        });

        Button btnSeguro = new Button("Escenario SEGURO");
        btnSeguro.setOnAction(e -> cargarEscenario(Escenarios.SEGURO));
        Button btnInseguro = new Button("Escenario INSEGURO");
        btnInseguro.setOnAction(e -> cargarEscenario(Escenarios.INSEGURO));
        Button btnLimpiar = new Button("Limpiar tablas");
        btnLimpiar.setOnAction(e -> limpiar());
        Button btnAyuda = new Button("Ayuda");
        btnAyuda.setOnAction(e -> mostrarAyuda());
        HBox.setMargin(btnSeguro, new Insets(0, 0, 0, 16));

        Region espacio = new Region(); // Empuja el botón Ayuda hacia la derecha
        HBox.setHgrow(espacio, Priority.ALWAYS);

        HBox barra = new HBox(8, new Label("Procesos (N):"), spN, new Label("Recursos (M):"), spM,
                btnSeguro, btnInseguro, btnLimpiar, espacio, btnAyuda);
        barra.setAlignment(Pos.CENTER_LEFT);
        barra.setPadding(new Insets(8, 10, 8, 10));
        return barra;
    }

    /** Cuerpo de la ventana: panel de datos a la izquierda y de resultados a la derecha. */
    private Node crearCuerpo() {
        SplitPane divisor = new SplitPane(crearPanelDatos(), crearPanelResultados());
        divisor.setDividerPositions(0.52);
        BorderPane.setMargin(divisor, new Insets(0, 10, 10, 10));
        return divisor;
    }

    /** Panel izquierdo: tablas, botón del algoritmo de seguridad y simulación de solicitudes. */
    private Node crearPanelDatos() {
        Label titulo = new Label("Datos del sistema (edite las celdas; Need se calcula sola)");
        titulo.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        // Las tablas van dentro de un ScrollPane por si N y M son grandes
        contenedorTablas.setPadding(new Insets(6));
        ScrollPane scrollTablas = new ScrollPane(contenedorTablas);
        scrollTablas.setPrefViewportHeight(300);
        VBox.setVgrow(scrollTablas, Priority.ALWAYS);

        // Algoritmo de seguridad
        Button btnEjecutar = new Button("▶  Ejecutar algoritmo de seguridad");
        btnEjecutar.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        btnEjecutar.setOnAction(e -> ejecutarSeguridad());
        TitledPane panelSeguridad = titulado("Algoritmo de seguridad", new HBox(btnEjecutar));

        // Solicitud de recursos
        cmbProceso.setPrefWidth(80);
        Button btnEvaluar = new Button("Evaluar solicitud");
        btnEvaluar.setOnAction(e -> evaluarSolicitud());
        btnDeshacer.setDisable(true);
        btnDeshacer.setOnAction(e -> deshacerSolicitud());
        HBox filaRequestConEtiqueta = new HBox(8, filaCentrada("Request:"), filaRequest);
        filaRequestConEtiqueta.setAlignment(Pos.BOTTOM_LEFT); // La etiqueta queda a la altura de las casillas
        VBox contenidoSolicitud = new VBox(8,
                new HBox(8, filaCentrada("Proceso que solicita:"), cmbProceso),
                filaRequestConEtiqueta,
                new HBox(8, btnEvaluar, btnDeshacer));
        TitledPane panelSolicitud = titulado("Simular solicitud de recursos (Request)", contenidoSolicitud);

        VBox panel = new VBox(8, titulo, scrollTablas, panelSeguridad, panelSolicitud);
        panel.setPadding(new Insets(8));
        return panel;
    }

    /** Panel derecho: recuadro de color con el veredicto, detalle y registro paso a paso. */
    private Node crearPanelResultados() {
        lblBanner.setMaxWidth(Double.MAX_VALUE);
        lblBanner.setAlignment(Pos.CENTER);
        lblBanner.setPadding(new Insets(14));
        lblDetalle.setWrapText(true);
        lblDetalle.setMinHeight(Region.USE_PREF_SIZE); // Crece en altura si el texto ocupa varias líneas
        lblDetalle.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        registro.setPadding(new Insets(6));
        scrollRegistro.setFitToWidth(true);
        TitledPane panelRegistro = titulado("Registro paso a paso", scrollRegistro);
        panelRegistro.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(panelRegistro, Priority.ALWAYS);

        VBox panel = new VBox(8, lblBanner, lblDetalle, panelRegistro);
        panel.setPadding(new Insets(8));
        return panel;
    }

    /** Crea un panel con título (como un recuadro) que no se puede plegar. */
    private static TitledPane titulado(String titulo, Node contenido) {
        TitledPane panel = new TitledPane(titulo, contenido);
        panel.setCollapsible(false);
        return panel;
    }

    /** Etiqueta alineada verticalmente con los campos que tiene al lado. */
    private static Label filaCentrada(String texto) {
        Label etiqueta = new Label(texto);
        etiqueta.setMinHeight(28);
        return etiqueta;
    }

    /**
     * Crea una celda de la tabla.
     * @param editable  false para celdas de solo lectura (matriz Need).
     * @param notificar true si al cambiar el texto se debe recalcular Need y
     *                  limpiar el resultado anterior (Allocation, Max y Available).
     */
    private TextField crearCelda(boolean editable, boolean notificar) {
        TextField celda = new TextField();
        celda.setPrefWidth(44);
        celda.setMaxWidth(44);
        celda.setAlignment(Pos.CENTER);
        if (editable) {
            // Al entrar a la celda se selecciona todo el texto para poder reemplazarlo
            celda.focusedProperty().addListener((obs, antes, ahora) -> {
                if (ahora) {
                    Platform.runLater(celda::selectAll);
                }
            });
            if (notificar) {
                celda.textProperty().addListener((obs, antes, ahora) -> alCambiarDato());
            }
        } else {
            celda.setEditable(false);
            celda.setFocusTraversable(false); // Tab salta las celdas de solo lectura
            celda.setStyle(ESTILO_NEED);
        }
        return celda;
    }

    /** Etiqueta de encabezado de columna (R0, R1, ...). */
    private static Label encabezado(int j) {
        Label etiqueta = new Label("R" + j);
        etiqueta.setMinWidth(44);
        etiqueta.setAlignment(Pos.CENTER);
        return etiqueta;
    }

    /**
     * Dibuja una tabla de N filas x M columnas. Rellena 'celdas' y 'etiquetas'.
     * 'valores' es el texto inicial de cada celda (puede ser null: todo en 0).
     */
    private GridPane crearTabla(TextField[][] celdas, Label[] etiquetas, boolean editable, String[][] valores) {
        GridPane tabla = new GridPane();
        tabla.setHgap(3);
        tabla.setVgap(3);
        tabla.setPadding(new Insets(4));

        for (int j = 0; j < celdas[0].length; j++) {
            tabla.add(encabezado(j), j + 1, 0);
        }
        for (int i = 0; i < celdas.length; i++) {
            etiquetas[i] = new Label("P" + i);
            etiquetas[i].setMinWidth(32);
            tabla.add(etiquetas[i], 0, i + 1);
            for (int j = 0; j < celdas[i].length; j++) {
                TextField celda = crearCelda(editable, true);
                celda.setText(valorInicial(valores, i, j));
                celdas[i][j] = celda;
                tabla.add(celda, j + 1, i + 1);
            }
        }
        return tabla;
    }

    /** Dibuja la tabla de una sola fila de Available. Rellena 'avCeldas'. */
    private GridPane crearTablaAvailable(String[] valores) {
        GridPane tabla = new GridPane();
        tabla.setHgap(3);
        tabla.setVgap(3);
        tabla.setPadding(new Insets(4));

        Label libre = new Label("Libre");
        libre.setMinWidth(32);
        tabla.add(libre, 0, 1);
        for (int j = 0; j < avCeldas.length; j++) {
            tabla.add(encabezado(j), j + 1, 0);
            TextField celda = crearCelda(true, true);
            celda.setText(valores != null && j < valores.length ? valores[j] : "0");
            avCeldas[j] = celda;
            tabla.add(celda, j + 1, 1);
        }
        return tabla;
    }

    /** Texto inicial de la celda (i, j): el valor dado si existe; si no, "0". */
    private static String valorInicial(String[][] valores, int i, int j) {
        if (valores != null && i < valores.length && j < valores[i].length && valores[i][j] != null) {
            return valores[i][j];
        }
        return "0";
    }

    /**
     * Dibuja de nuevo todas las tablas con el tamaño N x M de los Spinner.
     * 'textos' trae lo que debe aparecer en las celdas; lo que no alcance queda en 0.
     */
    private void reconstruir(Textos textos) {
        int n = limitar(spN.getValue(), MAX_PROCESOS);
        int m = limitar(spM.getValue(), MAX_RECURSOS);

        cargando = true; // Evita reaccionar a cada setText mientras se rellena

        alCeldas = new TextField[n][m];
        mxCeldas = new TextField[n][m];
        ndCeldas = new TextField[n][m];
        avCeldas = new TextField[m];
        Label[] etAl = new Label[n];
        Label[] etMx = new Label[n];
        Label[] etNd = new Label[n];

        // El orden de creación define el orden del Tab: Allocation, Max y Available
        GridPane tablaAl = crearTabla(alCeldas, etAl, true, textos.allocation());
        GridPane tablaMx = crearTabla(mxCeldas, etMx, true, textos.max());
        GridPane tablaNd = crearTabla(ndCeldas, etNd, false, null);
        GridPane tablaAv = crearTablaAvailable(textos.available());

        etiquetasProceso.clear();
        for (int i = 0; i < n; i++) {
            etiquetasProceso.add(new Label[] {etAl[i], etMx[i], etNd[i]});
        }

        // Los paneles de las tablas se ajustan a su contenido (no se estiran)
        TitledPane panelAl = titulado("Allocation (asignado)", tablaAl);
        TitledPane panelMx = titulado("Max (demanda máxima)", tablaMx);
        TitledPane panelNd = titulado("Need = Max − Allocation", tablaNd);
        TitledPane panelAv = titulado("Available (recursos libres)", tablaAv);
        for (TitledPane panel : List.of(panelAl, panelMx, panelNd, panelAv)) {
            panel.setMaxWidth(Region.USE_PREF_SIZE);
            panel.setMaxHeight(Region.USE_PREF_SIZE);
        }
        HBox matrices = new HBox(10, panelAl, panelMx, panelNd);
        matrices.setAlignment(Pos.TOP_LEFT);
        contenedorTablas.getChildren().setAll(matrices, panelAv);

        reconstruirSolicitud(n, m);

        cargando = false;
        actualizarNeed();
        reiniciarResultado();
        respaldoAvailable = null;
        respaldoAllocation = null;
        btnDeshacer.setDisable(true);
    }

    /** Actualiza la lista de procesos y las casillas del vector Request según N y M. */
    private void reconstruirSolicitud(int n, int m) {
        int anterior = Math.max(0, cmbProceso.getSelectionModel().getSelectedIndex());
        cmbProceso.getItems().clear();
        for (int i = 0; i < n; i++) {
            cmbProceso.getItems().add("P" + i);
        }
        cmbProceso.getSelectionModel().select(Math.min(anterior, n - 1));

        filaRequest.getChildren().clear();
        rqCeldas = new TextField[m];
        for (int j = 0; j < m; j++) {
            TextField celda = crearCelda(true, false); // Request no recalcula Need
            celda.setText("0");
            rqCeldas[j] = celda;
            filaRequest.getChildren().add(new VBox(2, encabezado(j), celda));
        }
    }

    // =========================================================================
    // VALIDACIÓN Y LECTURA DE DATOS
    // =========================================================================

    /**
     * Convierte un texto en un entero entre 0 y MAX_VALOR. Devuelve null si no
     * es válido. Solo se aceptan dígitos: se rechazan "-3", "2.5", "1e3" o "abc".
     */
    static Integer aEntero(String texto) {
        String t = texto.trim();
        if (t.isEmpty() || t.length() > 6) {
            return null;
        }
        for (int k = 0; k < t.length(); k++) {
            if (t.charAt(k) < '0' || t.charAt(k) > '9') {
                return null;
            }
        }
        return Integer.parseInt(t);
    }

    /** Se ejecuta cada vez que el usuario modifica una celda de datos. */
    private void alCambiarDato() {
        if (cargando) {
            return;
        }
        actualizarNeed();
        // El resultado anterior ya no corresponde a lo que se ve en pantalla
        if (resultadoVisible) {
            reiniciarResultado();
        }
        // Si el usuario edita a mano, "deshacer" ya no tiene sentido
        if (respaldoAvailable != null) {
            respaldoAvailable = null;
            respaldoAllocation = null;
            btnDeshacer.setDisable(true);
        }
    }

    /** Pinta la celda de rojo claro si tiene un problema; si no, la deja normal. */
    private static void pintar(TextField celda, boolean conError) {
        celda.setStyle(conError ? ESTILO_ERROR : "");
    }

    /**
     * Recalcula la matriz Need en pantalla y marca en rojo las celdas inválidas.
     * En Need se muestra "?" si falta un dato válido y "!" si Allocation > Max.
     */
    void actualizarNeed() {
        for (int i = 0; i < alCeldas.length; i++) {
            for (int j = 0; j < avCeldas.length; j++) {
                Integer a = aEntero(alCeldas[i][j].getText());
                Integer x = aEntero(mxCeldas[i][j].getText());
                pintar(alCeldas[i][j], a == null);
                pintar(mxCeldas[i][j], x == null);
                if (a == null || x == null) {
                    ndCeldas[i][j].setText("?");
                } else if (a > x) {
                    ndCeldas[i][j].setText("!");
                    pintar(alCeldas[i][j], true); // Allocation no puede superar a Max
                    pintar(mxCeldas[i][j], true);
                } else {
                    ndCeldas[i][j].setText(String.valueOf(x - a));
                }
            }
        }
        for (TextField celda : avCeldas) {
            pintar(celda, aEntero(celda.getText()) == null);
        }
    }

    /**
     * Lee las tablas y las convierte en números. Si hay errores (vacío, no
     * numérico, negativo, Allocation > Max) muestra un aviso con el detalle,
     * deja las celdas problemáticas en rojo y devuelve null.
     */
    private Estado leerDatos() {
        int n = alCeldas.length;
        int m = avCeldas.length;
        List<String> errores = new ArrayList<>();
        int[][] allocation = new int[n][m];
        int[][] max = new int[n][m];
        int[] available = new int[m];
        String rango = "escriba un entero entre 0 y " + MAX_VALOR + ".";

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                Integer a = aEntero(alCeldas[i][j].getText());
                Integer x = aEntero(mxCeldas[i][j].getText());
                if (a == null) {
                    errores.add("Allocation[P" + i + "][R" + j + "]: " + rango);
                }
                if (x == null) {
                    errores.add("Max[P" + i + "][R" + j + "]: " + rango);
                }
                if (a != null && x != null && a > x) {
                    errores.add("Allocation[P" + i + "][R" + j + "] = " + a
                            + " supera a Max[P" + i + "][R" + j + "] = " + x + ".");
                }
                allocation[i][j] = a == null ? 0 : a;
                max[i][j] = x == null ? 0 : x;
            }
        }
        for (int j = 0; j < m; j++) {
            Integer v = aEntero(avCeldas[j].getText());
            if (v == null) {
                errores.add("Available[R" + j + "]: " + rango);
            }
            available[j] = v == null ? 0 : v;
        }

        if (!errores.isEmpty()) {
            actualizarNeed(); // Deja marcadas en rojo las celdas con problema
            List<String> mostrar = new ArrayList<>(errores.subList(0, Math.min(8, errores.size())));
            if (errores.size() > 8) {
                mostrar.add("... y " + (errores.size() - 8) + " errores más.");
            }
            avisar(Alert.AlertType.ERROR, "Datos inválidos", String.join("\n", mostrar));
            return null;
        }
        return new Estado(available, allocation, max);
    }

    /** Lee el vector Request. Devuelve null (y avisa) si alguna casilla no es válida. */
    private int[] leerSolicitud() {
        int[] solicitud = new int[rqCeldas.length];
        for (int j = 0; j < rqCeldas.length; j++) {
            Integer v = aEntero(rqCeldas[j].getText());
            if (v == null) {
                avisar(Alert.AlertType.ERROR, "Solicitud inválida",
                        "Escriba un entero entre 0 y " + MAX_VALOR + " en cada casilla del vector Request.");
                return null;
            }
            solicitud[j] = v;
        }
        return solicitud;
    }

    // =========================================================================
    // PRESENTACIÓN DE RESULTADOS
    // =========================================================================

    /** Muestra el veredicto en el recuadro grande de color. */
    private void mostrarBanner(String texto, String color, String detalle) {
        lblBanner.setText(texto);
        lblBanner.setStyle("-fx-background-color: " + color
                + "; -fx-text-fill: white; -fx-font-size: 22px; -fx-font-weight: bold;");
        lblDetalle.setText(detalle);
        resultadoVisible = true;
    }

    /** Deja el recuadro en gris y quita los colores de los procesos. */
    private void reiniciarResultado() {
        mostrarBanner("Sin resultado todavía", GRIS,
                "Pulse «Ejecutar algoritmo de seguridad» o evalúe una solicitud.");
        marcarProcesos(null);
        resultadoVisible = false;
    }

    /**
     * Colorea las etiquetas P0, P1... de las tres tablas: verde si el proceso
     * pudo terminar (está en la secuencia segura) y rojo si quedó bloqueado.
     * Con secuencia = null vuelven al color normal.
     */
    private void marcarProcesos(List<Integer> secuencia) {
        for (int i = 0; i < etiquetasProceso.size(); i++) {
            String estilo = "";
            if (secuencia != null) {
                estilo = "-fx-font-weight: bold; -fx-text-fill: " + (secuencia.contains(i) ? VERDE : ROJO) + ";";
            }
            for (Label etiqueta : etiquetasProceso.get(i)) {
                etiqueta.setStyle(estilo);
            }
        }
    }

    /** Reemplaza el contenido del registro paso a paso. */
    private void escribirRegistro(List<Linea> lineas) {
        registro.getChildren().clear();
        String anterior = "";
        for (Linea linea : lineas) {
            // Una línea en blanco antes de cada ronda y antes del bloque final
            boolean inicioBloque = linea.tipo().equals("ronda") || (esCierre(linea.tipo()) && !esCierre(anterior));
            if (inicioBloque && !registro.getChildren().isEmpty()) {
                Text vacia = new Text("\n");
                vacia.setFont(FUENTE_LOG);
                registro.getChildren().add(vacia);
            }
            Text texto = new Text(linea.texto() + "\n");
            texto.setFill(colorDe(linea.tipo()));
            texto.setFont(esNegrita(linea.tipo()) ? FUENTE_LOG_NEGRITA : FUENTE_LOG);
            registro.getChildren().add(texto);
            anterior = linea.tipo();
        }
        scrollRegistro.setVvalue(0);
    }

    private static boolean esCierre(String tipo) {
        return tipo.equals("exito") || tipo.equals("error");
    }

    private static boolean esNegrita(String tipo) {
        return tipo.equals("titulo") || tipo.equals("ronda") || esCierre(tipo);
    }

    /** Color de cada tipo de línea del registro. */
    private static Color colorDe(String tipo) {
        return switch (tipo) {
            case "titulo" -> Color.web("#1a237e");
            case "ronda" -> Color.web("#0d47a1");
            case "ok", "exito" -> Color.web(VERDE);
            case "calc" -> Color.web("#455a64");
            case "espera" -> Color.web("#bf360c");
            case "error" -> Color.web(ROJO);
            default -> Color.web("#37474f");
        };
    }

    /** [1, 3, 0] -> "P1 → P3 → P0". */
    private static String formatoSecuencia(List<Integer> secuencia, String separador) {
        List<String> nombres = new ArrayList<>();
        for (int p : secuencia) {
            nombres.add("P" + p);
        }
        return String.join(separador, nombres);
    }

    /** Muestra un aviso al usuario. Se deja separado para poder reemplazarlo en las pruebas. */
    protected void avisar(Alert.AlertType tipo, String titulo, String mensaje) {
        Alert alerta = new Alert(tipo);
        alerta.initOwner(ventana);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        alerta.setContentText(mensaje);
        alerta.getDialogPane().setMinHeight(Region.USE_PREF_SIZE); // Que no se corte el texto
        alerta.showAndWait();
    }

    // =========================================================================
    // ACCIONES DE LOS BOTONES
    // =========================================================================

    /** Carga uno de los escenarios predefinidos. */
    void cargarEscenario(Escenario escenario) {
        ajustandoControles = true; // Cambiar los Spinner por código no debe redibujar dos veces
        spN.getValueFactory().setValue(escenario.allocation().length);
        spM.getValueFactory().setValue(escenario.available().length);
        ajustandoControles = false;

        reconstruir(Textos.de(escenario));
        escribirRegistro(List.of(
                new Linea("titulo", "Escenario cargado: " + escenario.nombre()),
                new Linea("info", "Revise las tablas y pulse «Ejecutar algoritmo de seguridad»."),
                new Linea("info", "Puede editar cualquier celda: la matriz Need se recalcula sola.")));
    }

    /** Pone todas las celdas en 0 (conserva el tamaño actual). */
    void limpiar() {
        reconstruir(Textos.vacio());
        escribirRegistro(List.of(
                new Linea("titulo", "Tablas limpias."),
                new Linea("info", "Escriba Allocation, Max y Available (enteros entre 0 y " + MAX_VALOR + ").")));
    }

    /** Valida los datos, ejecuta el algoritmo de seguridad y muestra el veredicto. */
    void ejecutarSeguridad() {
        Estado datos = leerDatos();
        if (datos == null) {
            return;
        }
        int[][] need = Banquero.calcularNeed(datos.max(), datos.allocation());
        ResultadoSeguridad resultado = Banquero.algoritmoSeguridad(datos.available(), datos.allocation(), need);
        List<Integer> secuencia = resultado.secuencia();

        List<Linea> lineas = new ArrayList<>();
        lineas.add(new Linea("titulo", "EJECUCIÓN DEL ALGORITMO DE SEGURIDAD"));
        lineas.addAll(resultado.registro());

        marcarProcesos(secuencia);
        if (resultado.seguro()) {
            mostrarBanner("ESTADO SEGURO", VERDE, "Secuencia segura:  " + formatoSecuencia(secuencia, " → "));
            lineas.add(new Linea("exito", "RESULTADO: ESTADO SEGURO"));
            lineas.add(new Linea("exito", "Secuencia segura: " + formatoSecuencia(secuencia, " -> ")));
        } else {
            // Los procesos bloqueados son los que no aparecen en la secuencia
            List<Integer> bloqueados = new ArrayList<>();
            for (int i = 0; i < datos.allocation().length; i++) {
                if (!secuencia.contains(i)) {
                    bloqueados.add(i);
                }
            }
            String lista = formatoSecuencia(bloqueados, ", ");
            mostrarBanner("ESTADO INSEGURO", ROJO, "Procesos que no pueden terminar:  " + lista);
            lineas.add(new Linea("error", "RESULTADO: ESTADO INSEGURO (posible interbloqueo)"));
            lineas.add(new Linea("error", "Procesos que NO pueden terminar: " + lista));
            if (!secuencia.isEmpty()) {
                lineas.add(new Linea("info", "Procesos que sí alcanzaron a terminar: "
                        + formatoSecuencia(secuencia, ", ")));
            }
        }
        escribirRegistro(lineas);
    }

    /** Evalúa el vector Request del proceso elegido; si es segura, la aplica a las tablas. */
    void evaluarSolicitud() {
        Estado datos = leerDatos();
        if (datos == null) {
            return;
        }
        int[] solicitud = leerSolicitud();
        if (solicitud == null) {
            return;
        }
        if (Arrays.stream(solicitud).sum() == 0) {
            avisar(Alert.AlertType.INFORMATION, "Solicitud vacía",
                    "Todos los valores del Request son 0: no hay nada que evaluar.");
            return;
        }

        int proceso = Math.max(0, cmbProceso.getSelectionModel().getSelectedIndex());
        int[][] need = Banquero.calcularNeed(datos.max(), datos.allocation());
        ResultadoSolicitud resultado = Banquero.solicitarRecurso(
                proceso, solicitud, datos.available(), datos.allocation(), need);

        marcarProcesos(null);
        List<Linea> lineas = new ArrayList<>();
        lineas.add(new Linea("titulo", "SOLICITUD: P" + proceso + " pide " + Arrays.toString(solicitud)));
        lineas.addAll(resultado.registro());

        switch (resultado.estado()) {
            case CONCEDIDA -> {
                // Se guarda el estado anterior por si el usuario quiere deshacer
                respaldoAvailable = datos.available().clone();
                respaldoAllocation = copiar(datos.allocation());
                aplicarEstado(resultado.available(), resultado.allocation());
                btnDeshacer.setDisable(false);
                marcarProcesos(resultado.secuencia());
                mostrarBanner("SOLICITUD CONCEDIDA", VERDE,
                        "Secuencia segura:  " + formatoSecuencia(resultado.secuencia(), " → "));
                lineas.add(new Linea("info", "La solicitud se aplicó: Available y Allocation fueron actualizados."));
            }
            case ESPERA -> mostrarBanner("EL PROCESO DEBE ESPERAR", NARANJA, resultado.mensaje());
            case DENEGADA -> mostrarBanner("SOLICITUD DENEGADA", ROJO, resultado.mensaje());
            case RECHAZADA -> mostrarBanner("SOLICITUD RECHAZADA", ROJO, resultado.mensaje());
        }
        escribirRegistro(lineas);
    }

    /** Escribe 'available' y 'allocation' en las celdas sin disparar alCambiarDato. */
    private void aplicarEstado(int[] available, int[][] allocation) {
        cargando = true;
        for (int j = 0; j < available.length; j++) {
            avCeldas[j].setText(String.valueOf(available[j]));
        }
        for (int i = 0; i < allocation.length; i++) {
            for (int j = 0; j < allocation[i].length; j++) {
                alCeldas[i][j].setText(String.valueOf(allocation[i][j]));
            }
        }
        cargando = false;
        actualizarNeed();
    }

    /** Restaura Available y Allocation a como estaban antes de la última solicitud concedida. */
    void deshacerSolicitud() {
        if (respaldoAvailable == null) {
            return;
        }
        aplicarEstado(respaldoAvailable, respaldoAllocation);
        respaldoAvailable = null;
        respaldoAllocation = null;
        btnDeshacer.setDisable(true);
        reiniciarResultado();
        escribirRegistro(List.of(new Linea("titulo", "Se restauró el estado anterior a la última solicitud concedida.")));
    }

    /** Muestra las instrucciones básicas. */
    private void mostrarAyuda() {
        avisar(Alert.AlertType.INFORMATION, "Ayuda",
                "CÓMO USAR EL SIMULADOR\n\n"
                + "1. Indique el número de procesos (N) y de recursos (M) con las flechas, o escriba "
                + "el valor y pulse Enter. Las tablas se redibujan conservando los datos.\n"
                + "2. Escriba Allocation, Max y Available (enteros entre 0 y " + MAX_VALOR + "). "
                + "Use Tab para pasar a la siguiente celda. La matriz Need se calcula sola.\n"
                + "3. Pulse «Ejecutar algoritmo de seguridad». El recuadro de color indica si el estado "
                + "es SEGURO (verde) o INSEGURO (rojo) y el registro muestra el paso a paso.\n"
                + "4. Para probar una solicitud: elija el proceso, escriba el vector Request y pulse "
                + "«Evaluar solicitud». Si se concede, las tablas se actualizan; use «Deshacer» para volver atrás.\n"
                + "5. «Escenario SEGURO» y «Escenario INSEGURO» cargan ejemplos listos.\n\n"
                + "Celdas en rojo: dato inválido (vacío, negativo, no numérico) o Allocation mayor que Max.\n"
                + "En Need, «?» indica que falta un dato válido y «!» que Allocation supera a Max.\n"
                + "Nombres en verde: procesos que pueden terminar. En rojo: procesos bloqueados.");
    }

    // =========================================================================
    // UTILIDADES
    // =========================================================================

    /** Contenido actual de las celdas (aunque sea inválido), para conservarlo al redibujar. */
    private Textos textosActuales() {
        if (alCeldas == null) {
            return Textos.vacio();
        }
        int n = alCeldas.length;
        int m = avCeldas.length;
        String[][] allocation = new String[n][m];
        String[][] max = new String[n][m];
        String[] available = new String[m];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                allocation[i][j] = alCeldas[i][j].getText();
                max[i][j] = mxCeldas[i][j].getText();
            }
        }
        for (int j = 0; j < m; j++) {
            available[j] = avCeldas[j].getText();
        }
        return new Textos(allocation, max, available);
    }

    /** Mantiene un valor dentro de [1, tope]. */
    private static int limitar(int valor, int tope) {
        return Math.max(1, Math.min(valor, tope));
    }

    private static int[][] copiar(int[][] matriz) {
        int[][] copia = new int[matriz.length][];
        for (int i = 0; i < matriz.length; i++) {
            copia[i] = matriz[i].clone();
        }
        return copia;
    }

    private static String[][] textoDeMatriz(int[][] matriz) {
        String[][] texto = new String[matriz.length][];
        for (int i = 0; i < matriz.length; i++) {
            texto[i] = textoDeVector(matriz[i]);
        }
        return texto;
    }

    private static String[] textoDeVector(int[] vector) {
        String[] texto = new String[vector.length];
        for (int j = 0; j < vector.length; j++) {
            texto[j] = String.valueOf(vector[j]);
        }
        return texto;
    }
}
