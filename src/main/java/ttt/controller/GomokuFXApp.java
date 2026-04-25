package ttt.controller;

import ttt.util.Debugger;
import ttt.model.Gomoku;
import ttt.network.GomokuNetwork;
import ttt.view.GomokuLauncherFX;
import ttt.ai.IGameKI;
import ttt.model.Pair;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;

/**
 * GomokuFXApp is the JavaFX entry point for the Gomoku application.
 * <p>
 * With no arguments: shows the launcher (start GUI).
 * With arguments: starts a game directly.
 * <pre>
 *   java -jar target/Gomokufx-1.0-SNAPSHOT.jar                      -&gt; launcher
 *   java -jar target/Gomokufx-1.0-SNAPSHOT.jar PP                   -&gt; Player vs Player
 *   java -jar target/Gomokufx-1.0-SNAPSHOT.jar PC S2                -&gt; Player vs AI (S1-S4)
 *   java -jar target/Gomokufx-1.0-SNAPSHOT.jar CC S2 S4             -&gt; AI vs AI
 *   java -jar target/Gomokufx-1.0-SNAPSHOT.jar SERVER 5000          -&gt; host network game on port 5000
 *   java -jar target/Gomokufx-1.0-SNAPSHOT.jar CLIENT 192.168.x.x 5000  -&gt; join network game
 * </pre>
 */
public class GomokuFXApp extends Application {

    @Override
    public void start(Stage stage) {
        stage.setTitle("\uD83C\uDFAF Gomoku");

        List<String> args = getParameters().getRaw();
        if (!args.isEmpty()) {
            String mode = args.get(0).toUpperCase();
            if ("SERVER".equals(mode)) {
                int port = args.size() >= 2 ? Integer.parseInt(args.get(1)) : 5000;
                startNetworkGame(stage, "server", port);
            } else if ("CLIENT".equals(mode)) {
                String host = args.size() >= 2 ? args.get(1) : "localhost";
                int port = args.size() >= 3 ? Integer.parseInt(args.get(2)) : 5000;
                startNetworkGame(stage, host, port);
            } else {
                String strat1Code = args.size() >= 2 ? args.get(1).toUpperCase() : null;
                String strat2Code = args.size() >= 3 ? args.get(2).toUpperCase() : null;
                startGame(stage, mode, strat1Code, strat2Code);
            }
        } else {
            showLauncher(stage);
        }

        stage.show();
    }

    /**
     * Starts a game directly without showing the launcher.
     *
     * @param stage      The primary stage.
     * @param mode       Game mode: "PP", "PC", or "CC".
     * @param strat1Code AI strategy code for player 1 (S1-S4), or null.
     * @param strat2Code AI strategy code for player 2 (S1-S4), or null.
     */
    public static void startGame(Stage stage, String mode, String strat1Code, String strat2Code) {
        try {
            Debugger.init(false);
        } catch (IOException ex) {
            System.err.println("[ERROR] Debug init failed: " + ex.getMessage());
        }

        IGameKI<Pair<Byte, Byte>> ai1 = GomokuLauncherFX.createStrategy(strat1Code);
        IGameKI<Pair<Byte, Byte>> ai2 = GomokuLauncherFX.createStrategy(strat2Code);
        new GomokuControllerFX(new Gomoku(), ai1, ai2, mode, stage);
    }

    /**
     * Starts a network game. The connection is established on a background thread
     * so the JavaFX UI stays responsive while the server waits for a client.
     * Server plays Black (goes first); client plays Red (goes second).
     *
     * @param stage        The primary stage.
     * @param hostOrServer "server" to host, or the server's hostname/IP to join.
     * @param port         The network port to use.
     */
    public static void startNetworkGame(Stage stage, String hostOrServer, int port) {
        try {
            Debugger.init(false);
        } catch (IOException ex) {
            System.err.println("[ERROR] Debug init failed: " + ex.getMessage());
        }

        boolean isServer = "server".equalsIgnoreCase(hostOrServer);
        GomokuControllerFX controller = new GomokuControllerFX(new Gomoku(), null, null, "PN", stage);
        controller.setStatus(isServer
                ? "Waiting for opponent to connect on port " + port + "..."
                : "Connecting to " + hostOrServer + ":" + port + "...");

        new Thread(() -> {
            try {
                GomokuNetwork net = isServer
                        ? new GomokuNetwork(port)
                        : new GomokuNetwork(hostOrServer, port);
                Platform.runLater(() -> controller.initNetwork(net, isServer));
            } catch (IOException ex) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Network error: " + ex.getMessage());
                    alert.setTitle("Connection Failed");
                    alert.setHeaderText(null);
                    alert.showAndWait();
                    showLauncher(stage);
                });
            }
        }).start();
    }

    /**
     * Switches the given stage to the launcher scene.
     * Called at startup and when the user quits a game to return to the launcher.
     *
     * @param stage The primary stage.
     */
    public static void showLauncher(Stage stage) {
        GomokuLauncherFX launcher = new GomokuLauncherFX(stage);
        Scene scene = new Scene(launcher, 560, 660);
        scene.getStylesheets().add(GomokuFXApp.class.getResource("/ttt/view/style.css").toExternalForm());
        stage.setScene(scene);
        stage.setMinWidth(500);
        stage.setMinHeight(600);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
