package com.umg.banquero;

/**
 * ESCENARIOS DE PRUEBA PREDEFINIDOS.
 *
 * Cada escenario trae las tres entradas del problema (Available, Allocation y
 * Max). Sirven para probar el programa sin escribir todos los datos a mano.
 */
public final class Escenarios {

    /** Un escenario completo: nombre y los tres datos de entrada. */
    public record Escenario(String nombre, int[] available, int[][] allocation, int[][] max) { }

    /** Caso SEGURO: ejemplo clásico de 5 procesos y 3 recursos. */
    public static final Escenario SEGURO = new Escenario(
            "Caso SEGURO - 5 procesos y 3 recursos (ejemplo clásico)",
            new int[] {3, 3, 2},
            new int[][] {{0, 1, 0}, {2, 0, 0}, {3, 0, 2}, {2, 1, 1}, {0, 0, 2}},
            new int[][] {{7, 5, 3}, {3, 2, 2}, {9, 0, 2}, {2, 2, 2}, {4, 3, 3}});

    /**
     * Caso INSEGURO: P0 alcanza a terminar, pero P1, P2 y P3 retienen cada uno
     * una instancia de R1, piden más y no queda ninguna libre.
     */
    public static final Escenario INSEGURO = new Escenario(
            "Caso INSEGURO - 4 procesos y 3 recursos (P1, P2 y P3 se bloquean)",
            new int[] {1, 0, 1},
            new int[][] {{1, 0, 0}, {0, 1, 1}, {1, 1, 0}, {0, 1, 0}},
            new int[][] {{2, 0, 1}, {0, 2, 1}, {3, 2, 0}, {0, 3, 1}});

    private Escenarios() { } // Clase de utilidades: no se instancia
}
