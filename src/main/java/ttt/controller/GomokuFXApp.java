package ttt.controller;

import ttt.util.Debugger;
import ttt.model.Gomoku;
import ttt.model.IRegularGame;
import ttt.network.GomokuNetwork;
import ttt.storage.XMLGameStorage;
import ttt.view.GomokuLauncherFX;
import ttt.view.ThemeManager;
import ttt.controller.GomokuLauncherController.StartSettings;
import ttt.ai.IGameKI;
import ttt.model.Pair;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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
 * <p>
 * Two options may appear in any position among the arguments:
 * <ul>
 *   <li><b>D</b> / <b>DEBUG</b> — enable the move trace to {@code gomoku_debug.txt}</li>
 *   <li><b>load=&lt;path&gt;</b> — resume from a saved XML game (PP/PC/CC modes)</li>
 * </ul>
 * e.g. {@code PC S4 D load=savedgame.xml}
 */
public class GomokuFXApp extends Application {

    @Override
    public void start(Stage stage) {
        stage.setTitle("\uD83C\uDFAF Gomoku");

        // Pull option tokens ("D"/"DEBUG" and "load=<path>") out of the argument
        // list from any position, leaving only the positional arguments (mode,
        // strategies, or host/port). This lets e.g. "PC D load=savedgame.xml"
        // both enable debug and load a saved game regardless of token order.
        boolean debug = false;
        String loadPath = null;
        List<String> args = new ArrayList<>();
        for (String arg : getParameters().getRaw()) {
            String upper = arg.toUpperCase();
            if (upper.equals("D") || upper.equals("DEBUG")) {
                debug = true;
            } else if (upper.startsWith("LOAD=")) {
                loadPath = arg.substring(arg.indexOf('=') + 1);
            } else {
                args.add(arg);
            }
        }

        if (!args.isEmpty()) {
            String mode = args.get(0).toUpperCase();
            if ("SERVER".equals(mode)) {
                int port = args.size() >= 2 ? Integer.parseInt(args.get(1)) : 5000;
                startNetworkGame(stage, "server", port, debug);
            } else if ("CLIENT".equals(mode)) {
                String host = args.size() >= 2 ? args.get(1) : "localhost";
                int port = args.size() >= 3 ? Integer.parseInt(args.get(2)) : 5000;
                startNetworkGame(stage, host, port, debug);
            } else {
                String strat1Code = args.size() >= 2 ? args.get(1).toUpperCase() : null;
                String strat2Code = args.size() >= 3 ? args.get(2).toUpperCase() : null;
                startGame(stage, mode, strat1Code, strat2Code, debug, loadPath);
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
        startGame(stage, mode, strat1Code, strat2Code, false);
    }

    /**
     * Starts a game directly without showing the launcher.
     *
     * @param stage      The primary stage.
     * @param mode       Game mode: "PP", "PC", or "CC".
     * @param strat1Code AI strategy code for player 1 (S1-S4), or null.
     * @param strat2Code AI strategy code for player 2 (S1-S4), or null.
     * @param debug      Whether to enable the debug move trace (gomoku_debug.txt).
     */
    public static void startGame(Stage stage, String mode, String strat1Code, String strat2Code, boolean debug) {
        startGame(stage, mode, strat1Code, strat2Code, debug, null);
    }

    /**
     * Starts a game directly without showing the launcher, optionally resuming
     * from a saved game.
     *
     * @param stage      The primary stage.
     * @param mode       Game mode: "PP", "PC", or "CC".
     * @param strat1Code AI strategy code for player 1 (S1-S4), or null.
     * @param strat2Code AI strategy code for player 2 (S1-S4), or null.
     * @param debug      Whether to enable the debug move trace (gomoku_debug.txt).
     * @param loadPath   Path to an XML save to resume from, or null for a fresh board.
     *                   On any load error a warning is printed and a fresh board is used.
     */
    public static void startGame(Stage stage, String mode, String strat1Code, String strat2Code,
            boolean debug, String loadPath) {
        try {
            Debugger.init(debug);
        } catch (IOException ex) {
            System.err.println("[ERROR] Debug init failed: " + ex.getMessage());
        }

        IRegularGame<Pair<Byte, Byte>> game = new Gomoku();
        if (loadPath != null) {
            try {
                game = XMLGameStorage.load(new File(loadPath));
                System.out.println("[INFO] Loaded game from " + loadPath);
            } catch (Exception ex) {
                System.err.println("[ERROR] Failed to load '" + loadPath + "': " + ex.getMessage()
                        + " — starting a new game instead.");
                game = new Gomoku();
            }
        }

        IGameKI<Pair<Byte, Byte>> ai1 = GomokuLauncherController.createStrategy(strat1Code, 4);
        IGameKI<Pair<Byte, Byte>> ai2 = GomokuLauncherController.createStrategy(strat2Code, 4);
        new GomokuControllerFX(game, ai1, ai2, mode, stage);
    }

    public static void startGame(Stage stage, StartSettings settings) {
        GomokuLauncherController.initializeGame(stage, settings);
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
        startNetworkGame(stage, hostOrServer, port, false);
    }

    /**
     * Starts a network game with an explicit debug flag. See
     * {@link #startNetworkGame(Stage, String, int)} for connection details.
     *
     * @param stage        The primary stage.
     * @param hostOrServer "server" to host, or the server's hostname/IP to join.
     * @param port         The network port to use.
     * @param debug        Whether to enable the debug move trace (gomoku_debug.txt).
     */
    public static void startNetworkGame(Stage stage, String hostOrServer, int port, boolean debug) {
        try {
            Debugger.init(debug);
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
        boolean wasMaximized = stage.isMaximized();
        double prevW = Double.isNaN(stage.getWidth())  || stage.getWidth()  <= 0 ? 1100 : stage.getWidth();
        double prevH = Double.isNaN(stage.getHeight()) || stage.getHeight() <= 0 ? 700  : stage.getHeight();

        GomokuLauncherFX launcher = new GomokuLauncherFX();
        new GomokuLauncherController(launcher, stage);
        Scene scene = new Scene(launcher);
        scene.getStylesheets().add(GomokuFXApp.class.getResource("/ttt/view/style.css").toExternalForm());
        ThemeManager.getInstance().register(launcher);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.T) {
                ThemeManager.getInstance().cycle();
                event.consume();
            }
        });
        stage.setScene(scene);
        stage.setMinWidth(940);
        stage.setMinHeight(620);
        if (wasMaximized) {
            stage.setMaximized(true);
        } else {
            stage.setWidth(Math.max(1100, prevW));
            stage.setHeight(Math.max(700, prevH));
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
