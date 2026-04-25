package ttt.view;

import ttt.controller.GomokuControllerFX;

import ttt.model.Gomoku;
import ttt.model.IRegularGame;
import ttt.model.Pair;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.input.MouseEvent;

import java.util.List;

/**
 * GomokuBoardFX is a JavaFX Canvas that renders the Gomoku board, stones,
 * hover preview, hint highlight, and winning sequence highlight.
 * <p>
 * Replaces the Swing-based GomokuBoard for the JavaFX UI without modifying
 * any game logic. Reads game state via IRegularGame and delegates click
 * events to GomokuControllerFX.
 */
public class GomokuBoardFX extends Canvas {

    private static final double MARGIN = 28;

    private final GomokuControllerFX controller;
    private IRegularGame<Pair<Byte, Byte>> game;
    private int previewRow = -1;
    private int previewCol = -1;
    private Pair<Byte, Byte> hintMove;

    /**
     * Constructs a GomokuBoardFX linked to the given controller.
     * Sets up resize listeners and mouse event handlers.
     *
     * @param controller The game controller for event delegation.
     */
    public GomokuBoardFX(GomokuControllerFX controller) {
        this.controller = controller;
        this.game = controller.getGame();

        // Redraw on resize
        widthProperty().addListener((obs, o, n) -> redraw());
        heightProperty().addListener((obs, o, n) -> redraw());

        // Mouse events
        setOnMouseMoved(this::handleMouseMove);
        setOnMouseClicked(this::handleMouseClick);
        setOnMouseExited(e -> {
            previewRow = -1;
            previewCol = -1;
            redraw();
        });
    }

    @Override
    public boolean isResizable() {
        return true;
    }

    @Override
    public double prefWidth(double height) {
        return getWidth();
    }

    @Override
    public double prefHeight(double width) {
        return getHeight();
    }

    /**
     * Updates the game state reference and redraws the board.
     */
    public void setGame(IRegularGame<Pair<Byte, Byte>> game) {
        this.game = game;
        redraw();
    }

    /**
     * Sets or clears the hint move position. Pass null to clear.
     */
    public void setHintMove(Pair<Byte, Byte> move) {
        this.hintMove = move;
        redraw();
    }

    /**
     * Redraws the entire board: background, grid, star points, stones,
     * ghost preview, hint indicator, and winning highlight.
     */
    public void redraw() {
        double w = getWidth();
        double h = getHeight();
        if (w <= 0 || h <= 0 || game == null)
            return;

        GraphicsContext gc = getGraphicsContext2D();
        int rows = game.getRows();
        int cols = game.getCols();

        double availW = w - 2 * MARGIN;
        double availH = h - 2 * MARGIN;
        double cellSize = Math.min(availW / (cols - 1), availH / (rows - 1));

        double boardW = (cols - 1) * cellSize;
        double boardH = (rows - 1) * cellSize;
        double startX = (w - boardW) / 2;
        double startY = (h - boardH) / 2;

        // ---- Clear canvas ----
        gc.setFill(Color.web("#1e1e28"));
        gc.fillRect(0, 0, w, h);

        // ---- Board background (wooden) ----
        double pad = cellSize * 0.55;
        gc.setFill(Color.web("#DEB887"));
        gc.fillRoundRect(startX - pad, startY - pad,
                boardW + 2 * pad, boardH + 2 * pad, 10, 10);

        // Wood border
        gc.setStroke(Color.web("#8B4513"));
        gc.setLineWidth(5);
        gc.strokeRoundRect(startX - pad, startY - pad,
                boardW + 2 * pad, boardH + 2 * pad, 10, 10);

        // ---- Grid lines ----
        gc.setStroke(Color.web("#222222"));
        gc.setLineWidth(1);
        for (int r = 0; r < rows; r++) {
            double y = startY + r * cellSize;
            gc.strokeLine(startX, y, startX + boardW, y);
        }
        for (int c = 0; c < cols; c++) {
            double x = startX + c * cellSize;
            gc.strokeLine(x, startY, x, startY + boardH);
        }

        // ---- Star points (standard 15x15) ----
        if (rows == 15 && cols == 15) {
            int[][] stars = { { 3, 3 }, { 3, 11 }, { 7, 7 }, { 11, 3 }, { 11, 11 } };
            gc.setFill(Color.web("#222222"));
            for (int[] s : stars) {
                double cx = startX + s[1] * cellSize;
                double cy = startY + s[0] * cellSize;
                double dotSize = Math.max(5, cellSize / 6);
                gc.fillOval(cx - dotSize / 2, cy - dotSize / 2, dotSize, dotSize);
            }
        }

        // ---- Stones ----
        double stoneDiam = cellSize * 0.74;
        double halfStone = stoneDiam / 2;

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                byte p = game.getAtPosition((byte) r, (byte) c);
                if (p != game.getPlayerNone()) {
                    double cx = startX + c * cellSize;
                    double cy = startY + r * cellSize;
                    drawStone(gc, cx, cy, halfStone, stoneDiam, p == game.getPlayer1());
                }
            }
        }

        // ---- Ghost preview ----
        if (previewRow >= 0 && previewCol >= 0
                && game.getAtPosition((byte) previewRow, (byte) previewCol) == game.getPlayerNone()
                && !game.endedGame()) {
            double cx = startX + previewCol * cellSize;
            double cy = startY + previewRow * cellSize;
            gc.setGlobalAlpha(0.35);
            gc.setFill(game.currentPlayer() == game.getPlayer1() ? Color.BLACK : Color.RED);
            gc.fillOval(cx - halfStone, cy - halfStone, stoneDiam, stoneDiam);
            gc.setGlobalAlpha(1.0);
        }

        // ---- Hint indicator (green ring + dot) ----
        if (hintMove != null) {
            double hx = startX + hintMove.second * cellSize;
            double hy = startY + hintMove.first * cellSize;
            double hintSize = cellSize * 0.82;
            double halfHint = hintSize / 2;
            gc.setGlobalAlpha(0.75);
            gc.setStroke(Color.web("#00D060"));
            gc.setLineWidth(3.5);
            gc.strokeOval(hx - halfHint, hy - halfHint, hintSize, hintSize);
            double dotSize = Math.max(7, cellSize / 5);
            gc.setFill(Color.web("#00D060"));
            gc.fillOval(hx - dotSize / 2, hy - dotSize / 2, dotSize, dotSize);
            gc.setGlobalAlpha(1.0);
            gc.setLineWidth(1);
        }

        // ---- Winning stones highlight (gold glow rings) ----
        if (game instanceof Gomoku) {
            List<Pair<Byte, Byte>> winning = ((Gomoku) game).getWinningStones();
            if (winning != null && winning.size() == 5) {
                gc.setStroke(Color.web("#FFD700"));
                gc.setLineWidth(3.5);
                gc.setGlobalAlpha(0.92);
                double glowSize = cellSize * 0.88;
                double halfGlow = glowSize / 2;
                for (Pair<Byte, Byte> win : winning) {
                    double cx = startX + win.second * cellSize;
                    double cy = startY + win.first * cellSize;
                    gc.strokeOval(cx - halfGlow, cy - halfGlow, glowSize, glowSize);
                }
                gc.setGlobalAlpha(1.0);
                gc.setLineWidth(1);
            }
        }
    }

    /**
     * Draws a single stone with a radial gradient for a 3D appearance.
     */
    private void drawStone(GraphicsContext gc, double cx, double cy,
            double r, double d, boolean isBlack) {
        double x = cx - r;
        double y = cy - r;
        if (isBlack) {
            RadialGradient grad = new RadialGradient(
                    0, 0, cx - r * 0.3, cy - r * 0.3, r,
                    false, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#606060")),
                    new Stop(0.6, Color.web("#2a2a2a")),
                    new Stop(1, Color.web("#0a0a0a")));
            gc.setFill(grad);
        } else {
            RadialGradient grad = new RadialGradient(
                    0, 0, cx - r * 0.3, cy - r * 0.3, r,
                    false, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#FF7777")),
                    new Stop(0.6, Color.web("#DD2222")),
                    new Stop(1, Color.web("#AA0000")));
            gc.setFill(grad);
        }
        gc.fillOval(x, y, d, d);
        gc.setStroke(Color.web("#1a1a1a"));
        gc.setLineWidth(1.2);
        gc.strokeOval(x, y, d, d);
    }

    // ---- Mouse handling ----

    private void handleMouseMove(MouseEvent e) {
        int[] rc = getRowColFromMouse(e.getX(), e.getY());
        int r = rc[0], c = rc[1];
        if (r >= 0 && r < game.getRows() && c >= 0 && c < game.getCols()
                && game.getAtPosition((byte) r, (byte) c) == game.getPlayerNone()) {
            previewRow = r;
            previewCol = c;
        } else {
            previewRow = -1;
            previewCol = -1;
        }
        redraw();
    }

    private void handleMouseClick(MouseEvent e) {
        int[] rc = getRowColFromMouse(e.getX(), e.getY());
        controller.onBoardClicked((byte) rc[0], (byte) rc[1]);
    }

    /**
     * Converts mouse pixel coordinates to board row/col, snapping to the
     * nearest intersection. Returns [-1,-1] if outside the board area.
     */
    private int[] getRowColFromMouse(double mouseX, double mouseY) {
        int rows = game.getRows();
        int cols = game.getCols();
        double w = getWidth();
        double h = getHeight();

        double availW = w - 2 * MARGIN;
        double availH = h - 2 * MARGIN;
        double cellSize = Math.min(availW / (cols - 1), availH / (rows - 1));

        double boardW = (cols - 1) * cellSize;
        double boardH = (rows - 1) * cellSize;
        double startX = (w - boardW) / 2;
        double startY = (h - boardH) / 2;

        int c = (int) ((mouseX - startX + cellSize / 2) / cellSize);
        int r = (int) ((mouseY - startY + cellSize / 2) / cellSize);

        if (r >= 0 && r < rows && c >= 0 && c < cols) {
            return new int[] { r, c };
        }
        return new int[] { -1, -1 };
    }
}
