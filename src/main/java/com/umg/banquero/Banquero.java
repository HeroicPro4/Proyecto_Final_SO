package com.umg.banquero;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * LÓGICA DEL ALGORITMO DEL BANQUERO.
 *
 * Esta clase NO usa JavaFX: solo recibe arreglos, calcula y devuelve resultados.
 * Así la lógica queda separada de la interfaz (App.java) y es fácil de probar.
 *
 * Notación (N = número de procesos, M = tipos de recursos):
 *   available  : int[M]    instancias libres de cada recurso.
 *   max        : int[N][M] demanda máxima de cada proceso.
 *   allocation : int[N][M] recursos que cada proceso tiene asignados ahora.
 *   need       : int[N][M] lo que cada proceso todavía puede pedir (max - allocation).
 *   work       : int[M]    copia de available que se actualiza en el algoritmo de seguridad.
 *   finish     : boolean[N] true cuando el proceso ya pudo terminar en la simulación.
 *
 * La fila i de cada matriz es el proceso Pi y la columna j es el recurso Rj.
 */
public final class Banquero {

    /**
     * Una línea del "registro paso a paso". El campo 'tipo' ("info", "ronda",
     * "ok", "espera", "calc", "exito", "error", "titulo") sirve para que la
     * interfaz decida el color con el que se muestra el texto.
     */
    public record Linea(String tipo, String texto) { }

    /** Resultado del algoritmo de seguridad. */
    public record ResultadoSeguridad(boolean seguro, List<Integer> secuencia, List<Linea> registro) { }

    /** Posibles respuestas a una solicitud de recursos. */
    public enum EstadoSolicitud { RECHAZADA, ESPERA, DENEGADA, CONCEDIDA }

    /**
     * Resultado de evaluar una solicitud. Los campos available, allocation y
     * secuencia solo tienen valor cuando la solicitud fue CONCEDIDA (si no, son null).
     */
    public record ResultadoSolicitud(EstadoSolicitud estado, String mensaje, List<Linea> registro,
                                     int[] available, int[][] allocation, List<Integer> secuencia) { }

    private Banquero() { } // Clase de utilidades: no se instancia

    // -------------------------------------------------------------------------
    // Cálculos básicos
    // -------------------------------------------------------------------------

    /** Need = Max - Allocation, elemento por elemento. */
    public static int[][] calcularNeed(int[][] max, int[][] allocation) {
        int n = max.length;
        int m = max[0].length;
        int[][] need = new int[n][m];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                need[i][j] = max[i][j] - allocation[i][j];
            }
        }
        return need;
    }

    /**
     * Devuelve true si 'vector' es menor o igual que 'limite' en TODAS las
     * posiciones. Se usa para la condición Need[i] <= Work (y también para
     * Request <= Need y Request <= Available).
     */
    static boolean cabeEn(int[] vector, int[] limite) {
        for (int j = 0; j < limite.length; j++) {
            if (vector[j] > limite[j]) {
                return false;
            }
        }
        return true;
    }

    /** Devuelve una copia independiente de una matriz. */
    private static int[][] copiarMatriz(int[][] matriz) {
        int[][] copia = new int[matriz.length][];
        for (int i = 0; i < matriz.length; i++) {
            copia[i] = matriz[i].clone();
        }
        return copia;
    }

    // -------------------------------------------------------------------------
    // Algoritmo de seguridad
    // -------------------------------------------------------------------------

    /**
     * ALGORITMO DE SEGURIDAD (Safety Algorithm).
     *
     *   PASO 1. Work = Available y Finish[i] = false para todo i.
     *   PASO 2. Buscar un proceso i con Finish[i] = false y Need[i] <= Work.
     *   PASO 3. Si existe: el proceso termina y devuelve sus recursos, así que
     *           Work = Work + Allocation[i]; Finish[i] = true. Volver al paso 2.
     *   PASO 4. Si no existe ninguno: el estado es SEGURO si todos los Finish son
     *           true; en caso contrario es INSEGURO.
     *
     * Los arreglos recibidos NO se modifican. Complejidad: O(M * N^2).
     */
    public static ResultadoSeguridad algoritmoSeguridad(int[] available, int[][] allocation, int[][] need) {
        int n = allocation.length;
        int m = available.length;

        // PASO 1: inicialización
        int[] work = available.clone();          // copia: no se altera el original
        boolean[] finish = new boolean[n];       // ningún proceso ha terminado todavía
        List<Integer> secuencia = new ArrayList<>(); // aquí se arma la secuencia segura
        List<Linea> registro = new ArrayList<>();
        registro.add(new Linea("info", "Work inicial = Available = " + Arrays.toString(work)));

        int ronda = 0;
        boolean hayAvance;

        // PASOS 2 y 3: se recorren los procesos en rondas hasta que ya no haya avance
        do {
            ronda++;
            hayAvance = false; // ¿terminó algún proceso en esta ronda?
            registro.add(new Linea("ronda", "Ronda " + ronda + "   (Work = " + Arrays.toString(work) + ")"));

            for (int i = 0; i < n; i++) {
                if (finish[i]) {
                    continue; // los procesos que ya terminaron se saltan
                }

                if (cabeEn(need[i], work)) {
                    // Need[i] <= Work: Pi puede ejecutarse hasta el final y, al
                    // terminar, devuelve todos sus recursos asignados al sistema.
                    int[] nuevoWork = new int[m];
                    for (int j = 0; j < m; j++) {
                        nuevoWork[j] = work[j] + allocation[i][j];
                    }
                    registro.add(new Linea("ok", "  P" + i + ": Need " + Arrays.toString(need[i])
                            + " <= Work " + Arrays.toString(work)
                            + "  ->  TERMINA y libera " + Arrays.toString(allocation[i])));
                    registro.add(new Linea("calc", "        Work = " + Arrays.toString(work)
                            + " + " + Arrays.toString(allocation[i]) + " = " + Arrays.toString(nuevoWork)));
                    work = nuevoWork;
                    finish[i] = true;
                    secuencia.add(i); // Pi entra a la secuencia segura
                    hayAvance = true;
                } else {
                    // Algún recurso no alcanza: Pi debe esperar a otra ronda
                    registro.add(new Linea("espera", "  P" + i + ": Need " + Arrays.toString(need[i])
                            + " NO es <= Work " + Arrays.toString(work) + "  ->  espera"));
                }
            }
            // PASO 4: se detiene si todos terminaron o si ya nadie puede avanzar
        } while (secuencia.size() < n && hayAvance);

        return new ResultadoSeguridad(secuencia.size() == n, secuencia, registro);
    }

    // -------------------------------------------------------------------------
    // Solicitud de recursos
    // -------------------------------------------------------------------------

    /**
     * ALGORITMO DE SOLICITUD DE RECURSOS (Resource-Request).
     *
     *   PASO 1. Si Request > Need[proceso]  -> RECHAZADA (pidió más de su máximo).
     *   PASO 2. Si Request > Available      -> el proceso debe ESPERAR.
     *   PASO 3. Se simula la asignación sobre COPIAS de los datos:
     *              Available  = Available  - Request
     *              Allocation = Allocation + Request   (solo la fila del proceso)
     *              Need       = Need       - Request   (solo la fila del proceso)
     *   PASO 4. Se ejecuta el algoritmo de seguridad sobre el estado simulado:
     *              seguro   -> CONCEDIDA
     *              inseguro -> DENEGADA (el estado real no cambia)
     *
     * Este método NO modifica los arreglos recibidos: si la solicitud es concedida,
     * devuelve en el resultado los nuevos available y allocation para que quien
     * llama los aplique.
     */
    public static ResultadoSolicitud solicitarRecurso(int proceso, int[] solicitud, int[] available,
                                                      int[][] allocation, int[][] need) {
        int m = available.length;
        String p = "P" + proceso;
        String pide = Arrays.toString(solicitud);
        List<Linea> registro = new ArrayList<>();

        // PASO 1: la solicitud no puede superar lo que el proceso aún puede pedir
        if (!cabeEn(solicitud, need[proceso])) {
            registro.add(new Linea("error", "Paso 1: Request > Need -> la solicitud excede el máximo declarado."));
            registro.add(new Linea("error", "SOLICITUD RECHAZADA. No se cambia nada."));
            String mensaje = p + " pide " + pide + " pero su Need es " + Arrays.toString(need[proceso])
                    + ": excede el máximo que declaró.";
            return new ResultadoSolicitud(EstadoSolicitud.RECHAZADA, mensaje, registro, null, null, null);
        }
        registro.add(new Linea("info", "Paso 1: Request " + pide + " <= Need "
                + Arrays.toString(need[proceso]) + " -> correcto"));

        // PASO 2: tiene que haber recursos libres suficientes
        if (!cabeEn(solicitud, available)) {
            registro.add(new Linea("espera", "Paso 2: Request " + pide + " NO es <= Available "
                    + Arrays.toString(available)));
            registro.add(new Linea("espera", p + " DEBE ESPERAR. No se cambia nada."));
            String mensaje = p + " pide " + pide + " pero solo hay disponible " + Arrays.toString(available) + ".";
            return new ResultadoSolicitud(EstadoSolicitud.ESPERA, mensaje, registro, null, null, null);
        }
        registro.add(new Linea("info", "Paso 2: Request " + pide + " <= Available "
                + Arrays.toString(available) + " -> correcto"));

        // PASO 3: asignación hipotética sobre copias (el estado real no se toca)
        int[] availableHip = new int[m];
        for (int j = 0; j < m; j++) {
            availableHip[j] = available[j] - solicitud[j];
        }
        int[][] allocationHip = copiarMatriz(allocation);
        int[][] needHip = copiarMatriz(need);
        for (int j = 0; j < m; j++) {
            allocationHip[proceso][j] += solicitud[j];
            needHip[proceso][j] -= solicitud[j];
        }
        registro.add(new Linea("info", "Paso 3: asignación hipotética:"));
        registro.add(new Linea("calc", "   Available = " + Arrays.toString(availableHip)));
        registro.add(new Linea("calc", "   Allocation[" + p + "] = " + Arrays.toString(allocationHip[proceso])
                + "    Need[" + p + "] = " + Arrays.toString(needHip[proceso])));
        registro.add(new Linea("info", "Paso 4: se verifica si el estado resultante es seguro:"));

        // PASO 4: se prueba el estado hipotético con el algoritmo de seguridad
        ResultadoSeguridad seguridad = algoritmoSeguridad(availableHip, allocationHip, needHip);
        registro.addAll(seguridad.registro());

        if (!seguridad.seguro()) {
            registro.add(new Linea("error", "SOLICITUD DENEGADA: el estado resultante es INSEGURO."));
            String mensaje = "Conceder " + pide + " a " + p
                    + " dejaría el sistema en estado inseguro. Se mantiene el estado original.";
            return new ResultadoSolicitud(EstadoSolicitud.DENEGADA, mensaje, registro, null, null, null);
        }

        registro.add(new Linea("exito", "SOLICITUD CONCEDIDA: el estado resultante es SEGURO."));
        return new ResultadoSolicitud(EstadoSolicitud.CONCEDIDA, "El estado resultante es seguro.",
                registro, availableHip, allocationHip, seguridad.secuencia());
    }
}
