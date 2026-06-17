package ttt.model;

/**
 * A grid-based {@link IGame} played on a fixed rows x cols board, where each
 * cell holds one of three markers: empty, player&nbsp;1, or player&nbsp;2.
 *
 * <p>Adds board geometry and cell access on top of {@link IGame}, which the
 * JavaFX view relies on to render the board and the {@code ttt.storage} layer
 * relies on to persist and restore state.
 *
 * @param <M> the move type (for Gomoku, a {@link Pair} of row/column)
 */
public interface IRegularGame<M> extends IGame<M> {

    /** @return the number of board rows. */
    byte getRows();

    /** @return the number of board columns. */
    byte getCols();

    /** @return the marker value for player&nbsp;1 (Black). */
    byte getPlayer1();

    /** @return the marker value for player&nbsp;2 (Red). */
    byte getPlayer2();

    /** @return the marker value for an empty cell. */
    byte getPlayerNone();

    /**
     * @param row board row (0-indexed)
     * @param col board column (0-indexed)
     * @return the marker currently at the given cell
     */
    byte getAtPosition(byte row, byte col);

    /**
     * Places the current player's stone at the given cell, if the move is legal.
     *
     * @param row board row (0-indexed)
     * @param col board column (0-indexed)
     * @return the resulting game state, or this unchanged state if the move is illegal
     */
    IRegularGame<M> setAtPosition(byte row, byte col);
}
