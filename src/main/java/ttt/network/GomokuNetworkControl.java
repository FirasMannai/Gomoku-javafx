package ttt.network;

import ttt.model.IRegularGame;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.layout.StackPane;
import java.io.IOException;

/**
 * GomokuNetworkControl manages networked Gomoku gameplay between two players
 * (server/client).
 * <p>
 * Handles game board display, move synchronization over the network,
 * and turn management. Uses a background thread to listen for opponent moves
 * and updates the JavaFX UI safely using Platform.runLater.
 * <p>
 * Server acts as Player 1 (Black), client as Player 2 (Red). Moves are sent and
 * received to keep both boards synchronized.
 *
 * @param <M> The move type for the game.
 */
public class GomokuNetworkControl<M> extends StackPane {
    /**
     * The current game state.
     */
    protected IRegularGame<M> game;

    /**
     * Network handler for sending and receiving moves.
     */
    private final GomokuNetwork net;

    /**
     * True if this instance is the server (Player 1), false if client (Player 2).
     */
    private final boolean isServer;

    /**
     * Indicates if it is currently this player's turn.
     */
    private volatile boolean myTurn;

    /**
     * Constructs a GomokuNetworkControl for networked gameplay.
     * <p>
     * Initializes the game state, sets up network connection as server or client,
     * and starts a background thread for move synchronization.
     *
     * @param game         The initial game state.
     * @param hostOrServer "server" to host, or hostname/IP to connect as client.
     * @param port         The network port to use.
     * @throws Exception If network setup fails.
     */
    public GomokuNetworkControl(IRegularGame<M> game, String hostOrServer, int port) throws Exception {
        this.game = game;

        if ("server".equalsIgnoreCase(hostOrServer)) {
            net = new GomokuNetwork(port);
            isServer = true;
            myTurn = true; // Server (Black/P1) starts
        } else {
            net = new GomokuNetwork(hostOrServer, port);
            isServer = false;
            myTurn = false; // Client (Red/P2) waits
        }

        // Background thread: listens for opponent's moves from the network.
        // On receiving a move, updates the board and turn using
        // Platform.runLater to ensure thread-safe UI updates.
        new Thread(() -> {
            try {
                while (true) {
                    int[] move = net.receiveMove();
                    if (move == null)
                        break; // connection closed
                    Platform.runLater(() -> {
                        if (!game.endedGame()) {
                            this.game = game.setAtPosition((byte) move[0], (byte) move[1]);
                            myTurn = true;
                            checkResult();
                        }
                    });
                }
            } catch (IOException ex) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Connection lost!");
                    alert.setTitle("Error");
                    alert.setHeaderText(null);
                    alert.showAndWait();
                });
            }
        }).start();
    }

    /**
     * Applies a move made by the local player: updates the board, sends the move
     * to the peer, and passes the turn. Ignored if it is not this player's turn,
     * the game is over, or the target cell is off-board or already occupied.
     *
     * @param r the row of the move (0-indexed)
     * @param c the column of the move (0-indexed)
     */
    public void makeLocalMove(int r, int c) {
        if (!myTurn || game.endedGame()) return;

        if (r < 0 || r >= game.getRows() || c < 0 || c >= game.getCols()) return;
        if (game.getAtPosition((byte) r, (byte) c) == game.getPlayerNone()) {
            game = game.setAtPosition((byte) r, (byte) c);
            net.sendMove(r, c);
            myTurn = false;
            checkResult();
        }
    }

    /**
     * Checks if the game has ended and displays the result.
     * Closes the network connection after game completion.
     */
    private void checkResult() {
        if (game.endedGame()) {
            String msg = game.wins(game.getPlayer1()) ? "Black wins!"
                    : game.wins(game.getPlayer2()) ? "Red wins!" : "Draw!";
            Alert alert = new Alert(Alert.AlertType.INFORMATION, msg);
            alert.setTitle("Game Over");
            alert.setHeaderText(null);
            alert.showAndWait();
            try {
                net.close();
            } catch (Exception ignored) {
            }
        }
    }

    /** Returns the current game state. */
    public IRegularGame<M> getGame() {
        return game;
    }

    /** Returns whether it is this player's turn. */
    public boolean isMyTurn() {
        return myTurn;
    }
}