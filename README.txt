SIMULADOR DEL ALGORITMO DEL BANQUERO - Java + JavaFX
Universidad Mariano Galvez de Guatemala - Sistemas Operativos 1

REQUISITOS
  - JDK 17 o superior (el codigo usa "record").
  - Maven (NetBeans ya lo incluye) e internet la primera vez, para descargar JavaFX.

COMO ABRIRLO Y EJECUTARLO EN NETBEANS
  1. Archivo > Abrir proyecto... y elegir la carpeta "banquero-fx".
  2. Clic derecho sobre el proyecto > Run (o tecla F5).

COMO EJECUTARLO DESDE LA TERMINAL
  Dentro de la carpeta banquero-fx:
      mvn clean javafx:run

ESTRUCTURA
  pom.xml                                   Configuracion de Maven y JavaFX
  src/main/java/com/umg/banquero/
      Launcher.java    Punto de entrada (arranca la aplicacion)
      App.java         Ventana JavaFX y eventos (interfaz grafica)
      Banquero.java    Algoritmo de seguridad y de solicitud (sin interfaz)
      Escenarios.java  Casos de prueba: uno seguro y uno inseguro
