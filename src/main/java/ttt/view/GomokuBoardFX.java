package ttt.view;

import ttt.controller.GomokuControllerFX;

import ttt.model.Gomoku;
import ttt.model.IRegularGame;
import ttt.model.Pair;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.input.MouseEvent;

import java.util.List;

/**
 * GomokuBoardFX is a JavaFX Canvas that renders the Gomoku board, stones,
 * hover preview, hint highlight, last-move marker, and winning sequence highlight.
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
    private Pair<Byte, Byte> lastMove;

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

        // Repaint when the user switches light/dark theme (the canvas surface
        // around the wooden board is not reachable via CSS).
        ThemeManager.getInstance().themeProperty().addListener((obs, o, n) -> redraw());
    }

    /**
     * Makes this Canvas behave as a proper resizable node inside a BorderPane/StackPane.
     *
     * <p>By default, {@code Canvas} is not resizable and its {@code prefWidth}/{@code prefHeight}
     * return the current pixel size, which causes the layout system to clamp or fight the
     * canvas dimensions. The overrides below hand full layout control to the parent:
     * <ul>
     *   <li>{@code prefWidth/Height} return 0 — the canvas does not request any specific size.</li>
     *   <li>{@code minWidth/Height} return 200 — prevents the canvas from collapsing to zero.</li>
     *   <li>{@code maxWidth/Height} return {@code MAX_VALUE} — allows unlimited growth.</li>
     *   <li>{@code resize()} sets the canvas pixel size directly; the existing
     *       {@code widthProperty} listener then triggers a full {@link #redraw()}.</li>
     * </ul>
     */
    @Override
    public boolean isResizable() {
        return true;
    }

    @Override
    public double prefWidth(double height) {
        return 0;
    }

    @Override
    public double prefHeight(double width) {
        return 0;
    }

    @Override
    public double minWidth(double height) {
        return 200;
    }

    @Override
    public double minHeight(double width) {
        return 200;
    }

    @Override
    public double maxWidth(double height) {
        return Double.MAX_VALUE;
    }

    @Override
    public double maxHeight(double width) {
        return Double.MAX_VALUE;
    }

    @Override
    public void resize(double width, double height) {
        setWidth(width);
        setHeight(height);
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
     * Marks the most recently placed stone so it can be highlighted. Pass null to clear.
     */
    public void setLastMove(Pair<Byte, Byte> move) {
        this.lastMove = move;
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

        ThemeManager.BoardPalette pal = ThemeManager.getInstance().boardPalette();

        // ---- Clear canvas (matches the themed board-wrap behind it) ----
        gc.setFill(pal.bg);
        gc.fillRect(0, 0, w, h);

        // ---- Board face (themed; wood, paper, glass, or neon) ----
        double pad = cellSize * 0.55;
        double bx = startX - pad, by = startY - pad;
        double bw = boardW + 2 * pad, bh = boardH + 2 * pad;
        gc.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, pal.boardTop), new Stop(1, pal.boardBottom)));
        gc.fillRoundRect(bx, by, bw, bh, 10, 10);

        // Board border
        gc.setStroke(pal.boardBorder);
        gc.setLineWidth(pal.boardBorderWidth);
        gc.strokeRoundRect(bx, by, bw, bh, 10, 10);

        // ---- Grid lines ----
        gc.setStroke(pal.grid);
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
            gc.setFill(pal.star);
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
                    drawStone(gc, cx, cy, halfStone, stoneDiam, p == game.getPlayer1(), pal);
                }
            }
        }

        // ---- Last-move marker (small bright dot at the centre of the newest stone) ----
        if (lastMove != null
                && lastMove.first < rows && lastMove.second < cols
                && game.getAtPosition(lastMove.first, lastMove.second) != game.getPlayerNone()) {
            double mx = startX + lastMove.second * cellSize;
            double my = startY + lastMove.first * cellSize;
            double dot = Math.max(5, cellSize / 7);
            gc.setFill(Color.web("#ffe9a8"));
            gc.fillOval(mx - dot / 2, my - dot / 2, dot, dot);
            gc.setStroke(Color.web("#b8860b"));
            gc.setLineWidth(1);
            gc.strokeOval(mx - dot / 2, my - dot / 2, dot, dot);
        }

        // ---- Ghost preview ----
        if (previewRow >= 0 && previewCol >= 0
                && game.getAtPosition((byte) previewRow, (byte) previewCol) == game.getPlayerNone()
                && !game.endedGame()) {
            double cx = startX + previewCol * cellSize;
            double cy = startY + previewRow * cellSize;
            gc.setGlobalAlpha(0.35);
            Color[] ghost = game.currentPlayer() == game.getPlayer1() ? pal.p1 : pal.p2;
            gc.setFill(ghost[1]);
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
     * Draws a single stone using the active theme's palette. Player-1 and
     * player-2 stones each get a three-stop radial gradient for a 3D look; themes
     * that opt into {@code glow} get a soft drop-shadow halo in the stone's hue.
     */
    private void drawStone(GraphicsContext gc, double cx, double cy,
            double r, double d, boolean isPlayer1, ThemeManager.BoardPalette pal) {
        double x = cx - r;
        double y = cy - r;
        Color[] stops = isPlayer1 ? pal.p1 : pal.p2;

        if (pal.glow) {
            DropShadow halo = new DropShadow();
            halo.setColor(stops[1]);
            halo.setRadius(Math.max(6, r * 0.9));
            halo.setSpread(0.25);
            gc.setEffect(halo);
        }

        RadialGradient grad = new RadialGradient(
                0, 0, cx - r * 0.3, cy - r * 0.3, r,
                false, CycleMethod.NO_CYCLE,
                new Stop(0, stops[0]),
                new Stop(0.6, stops[1]),
                new Stop(1, stops[2]));
        gc.setFill(grad);
        gc.fillOval(x, y, d, d);
        gc.setEffect(null);

        gc.setStroke(pal.stoneStroke);
        gc.setLineWidth(pal.stoneStrokeWidth);
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
