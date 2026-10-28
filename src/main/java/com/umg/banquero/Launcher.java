package com.umg.banquero;

/**
 * PUNTO DE ENTRADA DEL PROGRAMA.
 *
 * Esta clase existe solo para arrancar la aplicación. Si el método main
 * estuviera directamente en App (que extiende Application), al ejecutar el
 * proyecto desde NetBeans o desde un .jar puede aparecer el error
 * "JavaFX runtime components are missing". Con esta clase intermedia, que NO
 * extiende Application, el programa arranca siempre sin problemas.
 */
public class Launcher {

    public static void main(String[] args) {
        App.main(args);
    }
}
