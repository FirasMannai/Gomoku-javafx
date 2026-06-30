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
import ttt.view.ThemeManager;
import ttt.storage.XMLGameStorage;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.input.KeyEvent;
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
 * No game logic is duplicated - only reused from existing classes.
 */
public class GomokuControllerFX {

    /** The primary application stage, reused for scene switching. */
    private final Stage stage;

    /** Game mode: {@code "PP"} (Player vs Player), {@code "PC"} (Player vs AI),
     *  {@code "CC"} (AI vs AI), or {@code "PN"} (network). */
    private final String mode;

    /** AI strategy used for the computer player in PC mode and Player 1 (Black) in CC mode. Null for human sides. */
    private final IGameKI<Pair<Byte, Byte>> ai1;

    /** AI strategy for Player 2 (Red) in CC mode. Null in PP and PC modes. */
    private final IGameKI<Pair<Byte, Byte>> ai2;

    /** Dedicated AlphaBeta instance used only for hint computation on a cloned state. */
    private final IGameKI<Pair<Byte, Byte>> hintAI = new StrategyAlphaBeta<>();

    /** The live game state; replaced (not mutated) on every move. */
    private IRegularGame<Pair<Byte, Byte>> currentGame;

    /** Undo history — each entry is a cloned snapshot. Used only in PC mode. */
    private final List<IRegularGame<Pair<Byte, Byte>>> history = new ArrayList<>();

    /** The main game layout managed by this controller. */
    private GomokuGameView view;

    /** The board canvas managed by this controller. */
    private GomokuBoardFX boardFX;

    /** Running win counters shown in the sidebar score cards. */
    private int blackWins = 0;
    private int whiteWins = 0;

    /** Last hint move to highlight on the board; null when no hint is active. */
    private Pair<Byte, Byte> hintMove;

    /** Timeline driving CC auto-play; null in all other modes. */
    private Timeline ccTimeline;

    /** Active network connection for PN mode; null otherwise. */
    private GomokuNetwork net;

    /** True when it is this client's turn in a PN game. Volatile for cross-thread visibility. */
    private volatile boolean isMyNetworkTurn;

    /** Nanosecond timestamp of the start of the current human player's turn, used to display thinking time. */
    private long playerMoveStartNanos = System.nanoTime();

    /** Total number of stones placed so far this game; used to number the entries in the move-history list. */
    private int moveCount = 0;

    /** Nanosecond timestamp marking when the current game began, used for the elapsed clock. */
    private long gameStartNanos = System.nanoTime();

    /** One-second ticking timeline that refreshes the elapsed-time readout. */
    private Timeline elapsedTimeline;

    /**
     * Constructs the controller, creates the board and view, and switches
     * the stage to the game scene.
     *
     * @param game  The initial game state.
     * @param ai1   AI for Player 1 (Black). May be null in PP mode.
     * @param ai2   AI for Player 2 (Red). May be null in PP/PC mode.
     * @param mode  Game mode: "PP", "PC", "CC", or "PN" (network).
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

        // Use a no-arg Scene (passing explicit width/height would force-resize and
        // un-maximize the stage); sizing is delegated to the shared, screen-aware
        // helper so the window keeps its size across the scene switch and stays
        // within the display bounds.
        Scene gameScene = new Scene(view);
        gameScene.getStylesheets().add(getClass().getResource("/ttt/view/style.css").toExternalForm());
        ThemeManager.getInstance().register(view);
        installShortcuts(gameScene);
        stage.setScene(gameScene);
        GomokuFXApp.applyStageBounds(stage);

        updateUI();

        // Start CC auto-play if applicable
        if ("CC".equals(mode)) {
            startCCMode();
        }

        playerMoveStartNanos = System.nanoTime();
        startElapsedTimer();
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

            clearHint();

            // Human move
            byte currentPlayer = g.currentPlayer();
            if (Debugger.isDebug())
                Debugger.log(currentPlayer, row, col);

            IRegularGame<Pair<Byte, Byte>> next = g.setAtPosition(row, col);
            currentGame = next;
            boardFX.setGame(currentGame);
            recordMove(currentPlayer, row, col, String.format("%.1f s", playerSeconds));

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

        @SuppressWarnings("unchecked")
        IRegularGame<Pair<Byte, Byte>> after = (IRegularGame<Pair<Byte, Byte>>) nextAI;

        byte aiPlayer = before.currentPlayer();
        Pair<Byte, Byte> move = findLastMove(before, after, aiPlayer);

        currentGame = after;
        boardFX.setGame(currentGame);
        if (move != null) {
            if (Debugger.isDebug())
                Debugger.log(aiPlayer, move.first, move.second);
            recordMove(aiPlayer, move.first, move.second, String.format("%.3f s", seconds));
        }
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

                @SuppressWarnings("unchecked")
                IRegularGame<Pair<Byte, Byte>> after = (IRegularGame<Pair<Byte, Byte>>) next;

                byte aiPlayer = before.currentPlayer();
                Pair<Byte, Byte> move = findLastMove(before, after, aiPlayer);

                currentGame = after;
                boardFX.setGame(currentGame);
                if (move != null) {
                    if (Debugger.isDebug())
                        Debugger.log(aiPlayer, move.first, move.second);
                    recordMove(aiPlayer, move.first, move.second, String.format("%.3f s", seconds));
                }
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
        resetTracking();
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
        view.setStatus("Undo used");
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

            resetTracking();
            moveCount = countStones(currentGame);
            view.setStatus("\uD83D\uDCC2 Game loaded from savedgame.xml");
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

    /**
     * Starts a daemon thread that blocks on {@link GomokuNetwork#receiveMove()} and
     * dispatches each incoming move to the JavaFX thread via {@code Platform.runLater}.
     */
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

    /**
     * Applies an opponent's move received over the network and updates the UI.
     * Must be called on the JavaFX Application Thread.
     *
     * @param row Board row of the opponent's move (0-indexed).
     * @param col Board column of the opponent's move (0-indexed).
     */
    private void applyNetworkMove(int row, int col) {
        if (currentGame.endedGame()) return;
        byte mover = currentGame.currentPlayer();
        currentGame = currentGame.setAtPosition((byte) row, (byte) col);
        boardFX.setGame(currentGame);
        recordMove(mover, (byte) row, (byte) col, "");
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
        if (elapsedTimeline != null)
            elapsedTimeline.stop();
        if (net != null) {
            try { net.close(); } catch (IOException ignored) {}
        }
        GomokuFXApp.showLauncher(stage);
    }

    /**
     * Installs in-game keyboard shortcuts on the game scene:
     * <ul>
     *   <li><b>R</b> — restart</li>
     *   <li><b>H</b> — hint</li>
     *   <li><b>Ctrl+Z</b> — undo</li>
     *   <li><b>Ctrl+S</b> — save</li>
     *   <li><b>Ctrl+L</b> — load</li>
     *   <li><b>T</b> — toggle light/dark theme</li>
     *   <li><b>Esc</b> — quit to launcher</li>
     * </ul>
     *
     * @param scene the game scene to attach the key handler to
     */
    private void installShortcuts(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown()) {
                switch (event.getCode()) {
                    case Z: undoTwoMoves(); event.consume(); return;
                    case S: saveGame();     event.consume(); return;
                    case L: loadGame();     event.consume(); return;
                    default: return;
                }
            }
            switch (event.getCode()) {
                case R:      restartGame();     event.consume(); break;
                case H:      computeHint();     event.consume(); break;
                case T:      ThemeManager.getInstance().cycle(); event.consume(); break;
                case ESCAPE: quitToLauncher();  event.consume(); break;
                default: break;
            }
        });
    }

    // ==================== UI UPDATE ====================

    /**
     * Pushes all current state to the view labels and board.
     */
    private void updateUI() {
        if (currentGame != null) {
            byte cur = currentGame.currentPlayer();
            boolean isP1 = (cur == currentGame.getPlayer1());
            view.setTurn(isP1);
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

        if (elapsedTimeline != null)
            elapsedTimeline.stop();

        String msg = currentGame.wins(currentGame.getPlayer1()) ? "\u26AB Black wins!"
                : currentGame.wins(currentGame.getPlayer2()) ? "\uD83D\uDD34 Red wins!"
                        : "Draw!";
        view.setTurnResult(msg);

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

        // Append the winning line (board coordinates, e.g. "H9 \u00b7 I9 \u00b7 ...") to the message.
        String dialogText = msg;
        if (winner != 0 && currentGame instanceof Gomoku) {
            List<Pair<Byte, Byte>> winning = ((Gomoku) currentGame).getWinningStones();
            if (winning != null && winning.size() == 5) {
                StringBuilder line = new StringBuilder();
                for (int i = 0; i < winning.size(); i++) {
                    Pair<Byte, Byte> s = winning.get(i);
                    line.append(i > 0 ? " \u00b7 " : "").append(coordLabel(s.first, s.second));
                }
                dialogText += "\nWinning line: " + line;
            }
        }
        dialogText += "\nPlay again?";

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                dialogText, ButtonType.YES, ButtonType.NO);
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
     * Records a single placed stone: bumps the move counter, appends it (with its
     * duration) to the move-history list, and marks the stone as the most recent
     * one on the board.
     *
     * @param player   the player who placed the stone
     * @param row      board row (0-indexed)
     * @param col      board column (0-indexed)
     * @param duration the time the mover took, formatted for the history list
     *                 (e.g. "0.412 s" / "3.1 s"); may be empty when unknown
     */
    private void recordMove(byte player, byte row, byte col, String duration) {
        moveCount++;
        boolean isBlack = player == currentGame.getPlayer1();
        String coord = coordLabel(row, col);
        view.addMove(moveCount, isBlack, coord, duration);
        boardFX.setLastMove(new Pair<>(row, col));
    }

    /** Formats a board position as a chess-style coordinate, e.g. (7,7) → "H8". */
    private String coordLabel(int row, int col) {
        char file = (char) ('A' + col);
        return "" + file + (row + 1);
    }

    /** Counts the stones currently on the board (used to seed the move counter after a load). */
    private int countStones(IRegularGame<Pair<Byte, Byte>> g) {
        int count = 0;
        for (byte r = 0; r < g.getRows(); r++) {
            for (byte c = 0; c < g.getCols(); c++) {
                if (g.getAtPosition(r, c) != g.getPlayerNone()) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Resets per-game tracking (move counter, history list, last-move marker) and
     * restarts the elapsed clock. Called when a new board begins.
     */
    private void resetTracking() {
        moveCount = 0;
        view.clearHistory();
        view.setElapsed("00:00");
        boardFX.setLastMove(null);
        startElapsedTimer();
    }

    /** (Re)starts the one-second elapsed-time clock from now. */
    private void startElapsedTimer() {
        gameStartNanos = System.nanoTime();
        if (elapsedTimeline == null) {
            elapsedTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateElapsed()));
            elapsedTimeline.setCycleCount(Timeline.INDEFINITE);
        }
        elapsedTimeline.playFromStart();
        updateElapsed();
    }

    /** Pushes the current elapsed time (MM:SS) to the sidebar. */
    private void updateElapsed() {
        long secs = (System.nanoTime() - gameStartNanos) / 1_000_000_000L;
        view.setElapsed(String.format("%02d:%02d", secs / 60, secs % 60));
    }

    /**
     * Finds the move made between two game states by scanning for a cell that
     * changed from non-{@code player} to {@code player}.
     *
     * @param before Game state before the move.
     * @param after  Game state after the move.
     * @param player The player whose stone was just placed.
     * @return The (row, col) of the new stone, or {@code null} if not found.
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
     * Deep-clones a game state by invoking its {@code clone()} method via reflection.
     *
     * @param game The game state to clone.
     * @return A deep copy of {@code game}.
     * @throws RuntimeException if cloning fails.
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

    /**
     * Displays a modal information dialog and waits for the user to dismiss it.
     *
     * @param title   The dialog window title.
     * @param content The message body shown inside the dialog.
     */
    private void showInfoAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, content, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    /**
     * Displays a modal error dialog and waits for the user to dismiss it.
     *
     * @param content The error message shown inside the dialog.
     */
    private void showErrorAlert(String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR, content, ButtonType.OK);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
