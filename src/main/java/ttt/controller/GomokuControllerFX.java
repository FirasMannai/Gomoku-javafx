package ttt.controller;

import ttt.model.Gomoku;
import ttt.util.Debugger;
import ttt.model.IGame;
import ttt.ai.IGameKI;
import ttt.model.IRegularGame;
import ttt.model.Pair;
import ttt.ai.StrategyAlphaBeta;
import ttt.network.GomokuNetwork;
import ttt.view.GomokuGameView;
import ttt.view.GomokuBoardFX;
import ttt.storage.XMLGameStorage;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * GomokuControllerFX is the bridge between the JavaFX UI layer and the
 * existing Gomoku game logic.
 * <p>
 * Responsibilities:
 * <ul>
 * <li>Manages game state via existing Gomoku / IRegularGame</li>
 * <li>Calls AI strategies (doBestMove) for PC and CC modes</li>
 * <li>Computes hints using StrategyAlphaBeta on a cloned state</li>
 * <li>Tracks player and AI thinking time</li>
 * <li>Handles undo via history list</li>
 * <li>Delegates save/load to existing XMLGameStorage</li>
 * <li>Logs via existing Debugger</li>
 * <li>Updates GomokuGameView labels and GomokuBoardFX rendering</li>
 * </ul>
 * <p>
 * No game logic is duplicated â€” only reused from existing classes.
 */
public class GomokuControllerFX {

    private final Stage stage;
    private final String mode;
    private final IGameKI<Pair<Byte, Byte>> ai1;
    private final IGameKI<Pair<Byte, Byte>> ai2;
    private final IGameKI<Pair<Byte, Byte>> hintAI = new StrategyAlphaBeta<>();

    private IRegularGame<Pair<Byte, Byte>> currentGame;
    private final List<IRegularGame<Pair<Byte, Byte>>> history = new ArrayList<>();

    private GomokuGameView view;
    private GomokuBoardFX boardFX;

    private int blackWins = 0;
    private int whiteWins = 0;

    private Pair<Byte, Byte> hintMove;
    private Timeline ccTimeline;

    private GomokuNetwork net;
    private volatile boolean isMyNetworkTurn;

    private long playerMoveStartNanos = System.nanoTime();

    /**
     * Constructs the controller, creates the board and view, and switches
     * the stage to the game scene.
     *
     * @param game  The initial game state.
     * @param ai1   AI for Player 1 (Black). May be null in PP mode.
     * @param ai2   AI for Player 2 (Red). May be null in PP/PC mode.
     * @param mode  Game mode: "PP", "PC", or "CC".
     * @param stage The primary stage for scene switching.
     */
    public GomokuControllerFX(IRegularGame<Pair<Byte, Byte>> game,
            IGameKI<Pair<Byte, Byte>> ai1,
            IGameKI<Pair<Byte, Byte>> ai2,
            String mode,
            Stage stage) {
        this.currentGame = game;
        this.ai1 = ai1;
        this.ai2 = ai2;
        this.mode = mode;
        this.stage = stage;

        // Create board and view
        this.boardFX = new GomokuBoardFX(this);
        this.view = new GomokuGameView(this, boardFX, stage);

        // Initialize undo history for PC mode
        if ("PC".equals(mode)) {
            history.clear();
            history.add(cloneGame(currentGame));
        }

        if (Debugger.isDebug()) {
            Debugger.printMoveHeader();
        }

        // Show the game scene
        Scene gameScene = new Scene(view, 1060, 730);
        gameScene.getStylesheets().add(getClass().getResource("/ttt/view/style.css").toExternalForm());
        stage.setScene(gameScene);
        stage.setMinWidth(900);
        stage.setMinHeight(650);

        updateUI();

        // Start CC auto-play if applicable
        if ("CC".equals(mode)) {
            startCCMode();
        }

        playerMoveStartNanos = System.nanoTime();
    }

    // ==================== GETTERS ====================

    /** Returns the current game state. */
    public IRegularGame<Pair<Byte, Byte>> getGame() {
        return currentGame;
    }

    /** Updates the status bar text. */
    public void setStatus(String msg) {
        view.setStatus(msg);
    }

    /** Returns the game mode string. */
    public String getMode() {
        return mode;
    }

    /** Returns AI 1 (may be null). */
    public IGameKI<Pair<Byte, Byte>> getAI1() {
        return ai1;
    }

    /** Returns AI 2 (may be null). */
    public IGameKI<Pair<Byte, Byte>> getAI2() {
        return ai2;
    }

    // ==================== BOARD CLICK ====================

    /**
     * Handles a board click at row/col. For PP mode, places a stone.
     * For PC mode, places the human stone then triggers AI response.
     * CC mode ignores clicks (auto-play).
     *
     * @param row Board row (0-indexed).
     * @param col Board column (0-indexed).
     */
    public void onBoardClicked(byte row, byte col) {
        if ("CC".equals(mode))
            return;
        if ("PN".equals(mode) && !isMyNetworkTurn)
            return;

        IRegularGame<Pair<Byte, Byte>> g = currentGame;
        if (row < 0 || row >= g.getRows() || col < 0 || col >= g.getCols())
            return;

        if (g.endedGame()) {
            showResult();
            return;
        }

        if (g.getAtPosition(row, col) == g.getPlayerNone()) {
            // Measure player thinking time
            long playerEnd = System.nanoTime();
            double playerSeconds = (playerEnd - playerMoveStartNanos) / 1_000_000_000.0;
            view.setPlayerTime(String.format("%.1f s", playerSeconds));

            clearHint();

            // Human move
            byte currentPlayer = g.currentPlayer();
            if (Debugger.isDebug())
                Debugger.log(currentPlayer, row, col);

            IRegularGame<Pair<Byte, Byte>> next = g.setAtPosition(row, col);
            currentGame = next;
            boardFX.setGame(currentGame);
            view.setLastMove((row + 1) + "," + (col + 1));

            if ("PC".equals(mode))
                addToHistory(next);
            updateUI();

            if ("PN".equals(mode) && net != null) {
                net.sendMove(row, col);
                isMyNetworkTurn = false;
                if (!currentGame.endedGame())
                    view.setStatus("Waiting for opponent...");
            }

            if (currentGame.endedGame()) {
                showResult();
                return;
            }

            // PC mode: AI responds after human move
            if ("PC".equals(mode)) {
                doAIMove();
            }

            // Reset player timer
            playerMoveStartNanos = System.nanoTime();
        }
    }

    /**
     * Executes a single AI move (used in PC mode after the human plays).
     */
    private void doAIMove() {
        IRegularGame<Pair<Byte, Byte>> before = currentGame;

        long start = System.nanoTime();
        @SuppressWarnings("unchecked")
        IGame<Pair<Byte, Byte>> nextAI = ai1.doBestMove(before);
        long end = System.nanoTime();
        double seconds = (end - start) / 1_000_000_000.0;
        view.setAIThinkTime(String.format("%.3f s", seconds));

        @SuppressWarnings("unchecked")
        IRegularGame<Pair<Byte, Byte>> after = (IRegularGame<Pair<Byte, Byte>>) nextAI;

        byte aiPlayer = before.currentPlayer();
        Pair<Byte, Byte> move = findLastMove(before, after, aiPlayer);
        if (move != null) {
            if (Debugger.isDebug())
                Debugger.log(aiPlayer, move.first, move.second);
            view.setLastMove((move.first + 1) + "," + (move.second + 1));
        }

        currentGame = after;
        boardFX.setGame(currentGame);
        addToHistory(after);
        updateUI();

        if (currentGame.endedGame()) {
            showResult();
        }
    }

    // ==================== CC MODE ====================

    /**
     * Starts the CC (Computer vs Computer) auto-play mode using a Timeline.
     * Each tick performs one AI move with a 500ms delay.
     */
    private void startCCMode() {
        ccTimeline = new Timeline(new KeyFrame(Duration.millis(500), e -> {
            if (!currentGame.endedGame()) {
                byte current = currentGame.currentPlayer();
                @SuppressWarnings("unchecked")
                IGameKI<Pair<Byte, Byte>> ai = (current == currentGame.getPlayer1()) ? ai1 : ai2;

                IRegularGame<Pair<Byte, Byte>> before = currentGame;

                long start = System.nanoTime();
                @SuppressWarnings("unchecked")
                IGame<Pair<Byte, Byte>> next = ai.doBestMove(before);
                long end = System.nanoTime();
                double seconds = (end - start) / 1_000_000_000.0;
                view.setAIThinkTime(String.format("%.3f s", seconds));

                @SuppressWarnings("unchecked")
                IRegularGame<Pair<Byte, Byte>> after = (IRegularGame<Pair<Byte, Byte>>) next;

                byte aiPlayer = before.currentPlayer();
                Pair<Byte, Byte> move = findLastMove(before, after, aiPlayer);
                if (move != null) {
                    if (Debugger.isDebug())
                        Debugger.log(aiPlayer, move.first, move.second);
                    view.setLastMove((move.first + 1) + "," + (move.second + 1));
                }

                currentGame = after;
                boardFX.setGame(currentGame);
                updateUI();

                if (currentGame.endedGame()) {
                    ccTimeline.stop();
                    showResult();
                }
            }
        }));
        ccTimeline.setCycleCount(Timeline.INDEFINITE);
        ccTimeline.play();
    }

    // ==================== ACTIONS ====================

    /**
     * Restarts the game with a fresh board, preserving mode and AI settings.
     */
    public void restartGame() {
        if ("PN".equals(mode)) {
            showInfoAlert("Network Game", "Cannot restart a network game.");
            return;
        }
        if (ccTimeline != null)
            ccTimeline.stop();

        currentGame = new Gomoku(currentGame.getRows(), currentGame.getCols());
        boardFX.setGame(currentGame);

        if ("PC".equals(mode)) {
            history.clear();
            history.add(cloneGame(currentGame));
        }

        if (Debugger.isDebug())
            Debugger.printMoveHeader();

        clearHint();
        view.setLastMove("-");
        view.setAIThinkTime("-");
        view.setPlayerTime("-");
        view.setStatus("Ready to play");
        playerMoveStartNanos = System.nanoTime();
        updateUI();

        if ("CC".equals(mode))
            startCCMode();
    }

    /**
     * Undoes the last two moves (human + AI response) in PC mode.
     * Mirrors the undo logic from the existing GomokuControl.
     */
    public void undoTwoMoves() {
        if (!"PC".equals(mode) || currentGame.endedGame())
            return;
        if (history.size() <= 1)
            return;

        // Remove last two entries
        if (history.size() > 1)
            history.remove(history.size() - 1);
        if (history.size() > 1)
            history.remove(history.size() - 1);

        IRegularGame<Pair<Byte, Byte>> prev = history.get(history.size() - 1);
        // Ensure it's the human's turn
        if (prev.currentPlayer() != prev.getPlayer1() && history.size() > 1) {
            history.remove(history.size() - 1);
            prev = history.get(history.size() - 1);
        }

        currentGame = cloneGame(prev);
        boardFX.setGame(currentGame);
        clearHint();
        view.setLastMove("Undo used");
        playerMoveStartNanos = System.nanoTime();
        updateUI();
    }

    /**
     * Computes a hint using StrategyAlphaBeta on a cloned game state.
     * The hint move is displayed on the board without being applied.
     */
    public void computeHint() {
        if (currentGame.endedGame() || "CC".equals(mode)) {
            view.setStatus("No hint available.");
            return;
        }
        try {
            IRegularGame<Pair<Byte, Byte>> before = cloneGame(currentGame);
            long start = System.nanoTime();
            @SuppressWarnings("unchecked")
            IGame<Pair<Byte, Byte>> after = hintAI.doBestMove(before);
            long end = System.nanoTime();
            double seconds = (end - start) / 1_000_000_000.0;

            byte currentPlayer = before.currentPlayer();
            @SuppressWarnings("unchecked")
            Pair<Byte, Byte> move = findLastMove(before,
                    (IRegularGame<Pair<Byte, Byte>>) after, currentPlayer);

            if (move != null) {
                hintMove = move;
                boardFX.setHintMove(hintMove);
                view.setStatus("\uD83D\uDCA1 Hint: (" + (move.first + 1) + ","
                        + (move.second + 1) + ") in "
                        + String.format("%.3f s", seconds));
            } else {
                view.setStatus("Hint: AI could not find a move.");
            }
        } catch (Exception ex) {
            view.setStatus("Hint error: " + ex.getMessage());
        }
    }

    /**
     * Saves the current game state to savedgame.xml using XMLGameStorage.
     */
    @SuppressWarnings("unchecked")
    public void saveGame() {
        try {
            File saveFile = new File("savedgame.xml");
            XMLGameStorage.save(currentGame, saveFile);
            view.setStatus("\uD83D\uDCBE Game saved to savedgame.xml");
            showInfoAlert("Save", "Game saved to savedgame.xml");
        } catch (Exception ex) {
            view.setStatus("Save failed!");
            showErrorAlert("Save failed: " + ex.getMessage());
        }
    }

    /**
     * Loads a game state from savedgame.xml using XMLGameStorage.
     */
    public void loadGame() {
        try {
            File saveFile = new File("savedgame.xml");
            @SuppressWarnings("unchecked")
            IRegularGame<Pair<Byte, Byte>> loaded = XMLGameStorage.load(saveFile);
            currentGame = loaded;
            boardFX.setGame(currentGame);

            if ("PC".equals(mode)) {
                history.clear();
                history.add(cloneGame(loaded));
            }

            view.setStatus("\uD83D\uDCC2 Game loaded from savedgame.xml");
            view.setLastMove("-");
            updateUI();
            showInfoAlert("Load", "Game loaded from savedgame.xml");
        } catch (Exception ex) {
            view.setStatus("Load failed!");
            showErrorAlert("Load failed: " + ex.getMessage());
        }
    }

    /**
     * Called after construction for network (PN) mode.
     * Stores the open connection and starts the background listener thread.
     *
     * @param network The established network connection.
     * @param goFirst True if this player moves first (server = Black).
     */
    public void initNetwork(GomokuNetwork network, boolean goFirst) {
        this.net = network;
        this.isMyNetworkTurn = goFirst;
        view.setStatus(goFirst ? "Your turn (Black)" : "Connected! Waiting for opponent (Black)...");
        startNetworkListener();
    }

    private void startNetworkListener() {
        Thread t = new Thread(() -> {
            try {
                while (!currentGame.endedGame()) {
                    int[] move = net.receiveMove();
                    Platform.runLater(() -> applyNetworkMove(move[0], move[1]));
                }
            } catch (IOException ex) {
                Platform.runLater(() -> showErrorAlert("Connection lost: " + ex.getMessage()));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void applyNetworkMove(int row, int col) {
        if (currentGame.endedGame()) return;
        currentGame = currentGame.setAtPosition((byte) row, (byte) col);
        boardFX.setGame(currentGame);
        view.setLastMove((row + 1) + "," + (col + 1));
        updateUI();
        isMyNetworkTurn = true;
        if (currentGame.endedGame())
            showResult();
        else
            view.setStatus("Your turn");
    }

    /**
     * Returns to the launcher screen without exiting the application.
     */
    public void quitToLauncher() {
        if (ccTimeline != null)
            ccTimeline.stop();
        if (net != null) {
            try { net.close(); } catch (IOException ignored) {}
        }
        GomokuFXApp.showLauncher(stage);
    }

    // ==================== UI UPDATE ====================

    /**
     * Pushes all current state to the view labels and board.
     */
    private void updateUI() {
        if (currentGame != null) {
            byte cur = currentGame.currentPlayer();
            boolean isP1 = (cur == currentGame.getPlayer1());
            view.setTurnText(isP1 ? "\u26AB Black" : "\uD83D\uDD34 Red");
        }
        view.setBlackScore(String.valueOf(blackWins));
        view.setRedScore(String.valueOf(whiteWins));
        view.setUndoEnabled("PC".equals(mode) && history.size() > 1
                && !currentGame.endedGame());
        boardFX.redraw();
    }

    // ==================== RESULT ====================

    /**
     * Handles end-of-game: updates scores, logs via Debugger, shows alert.
     */
    private void showResult() {
        byte winner = 0;
        if (currentGame.wins(currentGame.getPlayer1())) {
            blackWins++;
            winner = currentGame.getPlayer1();
        } else if (currentGame.wins(currentGame.getPlayer2())) {
            whiteWins++;
            winner = currentGame.getPlayer2();
        }
        updateUI();
        boardFX.redraw();

        String msg = currentGame.wins(currentGame.getPlayer1()) ? "\u26AB Black wins!"
                : currentGame.wins(currentGame.getPlayer2()) ? "\uD83D\uDD34 Red wins!"
                        : "Draw!";

        // Debugger logging (same logic as existing GomokuControl)
        if (Debugger.isDebug()) {
            StringBuilder stats = new StringBuilder();
            stats.append("\n=== Game Stats ===\n");
            if (winner != 0) {
                stats.append("\nWinner: Player ").append(winner).append("\n");
                int winnerMoves = 0;
                for (int r = 0; r < currentGame.getRows(); r++) {
                    for (int c = 0; c < currentGame.getCols(); c++) {
                        if (currentGame.getAtPosition((byte) r, (byte) c) == winner)
                            winnerMoves++;
                    }
                }
                stats.append("Player ").append(winner).append(" made ")
                        .append(winnerMoves).append(" moves.\n");
                if (currentGame instanceof Gomoku) {
                    List<Pair<Byte, Byte>> winning = ((Gomoku) currentGame).getWinningStones();
                    if (!winning.isEmpty()) {
                        stats.append("Winning stones: ");
                        for (Pair<Byte, Byte> s : winning)
                            stats.append("(").append(s.first + 1).append(",").append(s.second + 1).append(") ");
                        stats.append("\n");
                    }
                }
            } else {
                stats.append("Game ended in a draw.\n");
            }
            Debugger.logMessage(stats.toString());
            Debugger.close();
        }

        view.setStatus("Game Over \u2014 " + msg);

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                msg + "\nPlay again?", ButtonType.YES, ButtonType.NO);
        alert.setTitle("Game Over");
        alert.setHeaderText(null);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            restartGame();
        }
    }

    // ==================== HELPERS ====================

    /**
     * Clears the current hint from the board.
     */
    private void clearHint() {
        hintMove = null;
        boardFX.setHintMove(null);
    }

    /**
     * Finds the move that was made between two game states by comparing boards.
     * Reuses the same difference-detection logic as the existing GomokuControl.
     */
    private Pair<Byte, Byte> findLastMove(IRegularGame<Pair<Byte, Byte>> before,
            IRegularGame<Pair<Byte, Byte>> after,
            byte player) {
        for (byte r = 0; r < before.getRows(); r++) {
            for (byte c = 0; c < before.getCols(); c++) {
                if (before.getAtPosition(r, c) != player
                        && after.getAtPosition(r, c) == player) {
                    return new Pair<>(r, c);
                }
            }
        }
        return null;
    }

    /**
     * Clones a game state using reflection (same approach as existing
     * GomokuControl.cloneGame).
     */
    @SuppressWarnings("unchecked")
    private IRegularGame<Pair<Byte, Byte>> cloneGame(
            IRegularGame<Pair<Byte, Byte>> game) {
        try {
            return (IRegularGame<Pair<Byte, Byte>>) game.getClass().getMethod("clone").invoke(game);
        } catch (Exception e) {
            throw new RuntimeException("Failed to clone game", e);
        }
    }

    /**
     * Adds a cloned game state to the undo history, capping at 40 entries.
     */
    private void addToHistory(IRegularGame<Pair<Byte, Byte>> game) {
        history.add(cloneGame(game));
        if (history.size() > 40)
            history.remove(0);
    }

    private void showInfoAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, content, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void showErrorAlert(String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR, content, ButtonType.OK);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
