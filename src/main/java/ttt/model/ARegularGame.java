package ttt.model;

import java.util.List;

/**
 * Abstract base class for {@link IRegularGame} implementations (Template Method
 * pattern).
 *
 * <p>Holds the board state shared by all grid games — dimensions, the
 * {@code byte[][]} grid, the current player, the move count, and the last move
 * played — and implements the common book-keeping (turn switching, cell access,
 * deep {@link #clone()}, end-of-game detection). Concrete subclasses such as
 * {@link Gomoku} supply the game-specific rules: {@link #moves()},
 * {@link #doMove(Object)}, {@link #wins(byte)} and {@link #evalState(byte)}.
 *
 * <p>Cell markers are {@code 0} = empty ({@link #NONE}), {@code 1} =
 * player&nbsp;1 ({@link #ONE}), {@code 2} = player&nbsp;2 ({@link #TWO}).
 *
 * <p>The public setters ({@link #setPlayer}, {@link #setMovesDone},
 * {@link #setBoardPosition}, {@link #setLastPosition}) exist so the
 * {@code ttt.storage} layer can rebuild a game loaded from XML.
 *
 * @param <M> the move type
 */
public abstract class ARegularGame<M> implements IRegularGame<M>, Cloneable {

    /** Marker for an empty cell. */
    protected static final byte NONE = 0;
    /** Marker for player&nbsp;1 (Black). */
    protected static final byte ONE = 1;
    /** Marker for player&nbsp;2 (Red). */
    protected static final byte TWO = 2;

    /** Number of board rows (fixed at construction). */
    protected final byte ROWS;
    /** Number of board columns (fixed at construction). */
    protected final byte COLS;
    /** The player whose turn it currently is. */
    protected byte player;
    /** The board grid; {@code board[row][col]} holds a cell marker. */
    protected byte[][] board;
    /** Number of stones placed so far. */
    protected int movesDone;
    /** Row of the most recently played move ({@code -1} if none yet). */
    protected byte lastRow;
    /** Column of the most recently played move ({@code -1} if none yet). */
    protected byte lastCol;

    /**
     * Creates an empty {@code rows} x {@code cols} board with player&nbsp;1 to move.
     *
     * @param rows number of board rows
     * @param cols number of board columns
     */
    public ARegularGame(byte rows, byte cols) {
        this.ROWS = rows;
        this.COLS = cols;
        this.player = ONE;               // Player 1 starts
        this.board = new byte[rows][cols];
        this.movesDone = 0;
        this.lastRow = -1;
        this.lastCol = -1;
    }

    @Override public byte getRows() { return ROWS; }
    @Override public byte getCols() { return COLS; }
    @Override public byte getPlayer1() { return ONE; }
    @Override public byte getPlayer2() { return TWO; }
    @Override public byte getPlayerNone() { return NONE; }

    @Override public byte currentPlayer() {
        return player;
    }

    @Override public byte otherPlayer(byte p) {
        return p == ONE ? TWO : ONE;
    }

    /** @return the player who made the most recent move (the non-current player). */
    protected byte lastPlayer() {
        return otherPlayer(currentPlayer());
    }

    @Override public byte getAtPosition(byte row, byte col) {
        return board[row][col];
    }

    /** @return the number of stones placed so far. */
    public int getMovesDone() { return movesDone; }

    /**
     * Sets the player to move (used when restoring a saved game).
     *
     * @param player the player marker to make current
     */
    public void setPlayer(byte player) { this.player = player; }

    /**
     * Sets the move count (used when restoring a saved game).
     *
     * @param movesDone the number of stones placed
     */
    public void setMovesDone(int movesDone) { this.movesDone = movesDone; }

    /**
     * Writes a marker directly into a board cell (used when restoring a saved game).
     *
     * @param row   board row (0-indexed)
     * @param col   board column (0-indexed)
     * @param value the marker to store
     */
    public void setBoardPosition(byte row, byte col, byte value) { this.board[row][col] = value; }

    /**
     * Records the last-played coordinate (used when restoring a saved game).
     *
     * @param row last move's row
     * @param col last move's column
     */
    public void setLastPosition(byte row, byte col) { this.lastRow = row; this.lastCol = col; }

    @Override public boolean endedGame() {
        return noMoreMove() || wins(lastPlayer());
    }

    /**
     * Returns a deep copy of this game: the {@code board} array is cloned so the
     * copy can be mutated independently. This lets each move produce a fresh
     * state and lets the AI explore move trees without side effects.
     *
     * @return an independent deep copy of this game state
     * @throws CloneNotSupportedException never (this type is {@link Cloneable})
     */
    @Override public ARegularGame<M> clone() throws CloneNotSupportedException {
        ARegularGame<M> copy = (ARegularGame<M>) super.clone();
        copy.board = new byte[ROWS][COLS];
        for (int r = 0; r < ROWS; r++) {
            System.arraycopy(this.board[r], 0, copy.board[r], 0, COLS);
        }
        copy.player = this.player;
        copy.movesDone = this.movesDone;
        copy.lastRow = this.lastRow;
        copy.lastCol = this.lastCol;
        return copy;
    }

    @Override public abstract List<M> moves();
    @Override public abstract IRegularGame<M> setAtPosition(byte row, byte col);
    @Override public abstract IRegularGame<M> doMove(M move);
    @Override public abstract boolean noMoreMove();
    @Override public abstract boolean wins(byte player);
    @Override public abstract int evalState(byte player);
}
