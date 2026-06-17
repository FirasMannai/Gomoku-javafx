package ttt.model;

import java.util.List;

/**
 * Root abstraction for a two-player, turn-based board game.
 *
 * <p>Defines the minimal contract the AI layer ({@code ttt.ai}) needs to reason
 * about a game: enumerating legal moves, applying a move to obtain the next
 * state, querying the current player, and evaluating / detecting terminal
 * positions. Implementations are effectively immutable — each
 * {@link #doMove(Object)} returns a new state rather than mutating this one,
 * which lets the search strategies explore branches safely.
 *
 * @param <M> the move type (for Gomoku, a {@link Pair} of row/column)
 */
public interface IGame<M> {

    /** @return all legal moves from the current state. */
    List<M> moves();

    /**
     * Applies a move and returns the resulting game state.
     *
     * @param move the move to play
     * @return a new game state with the move applied (this state is unchanged)
     */
    IGame<M> doMove(M move);

    /** @return the player whose turn it is ({@code 1} or {@code 2}). */
    byte currentPlayer();

    /**
     * @param player a player marker
     * @return the opposing player's marker
     */
    byte otherPlayer(byte player);

    /** @return {@code true} if no further moves are possible (board full). */
    boolean noMoreMove();

    /** @return {@code true} if the game has ended (a win or no moves left). */
    boolean endedGame();

    /**
     * @param player the player to test
     * @return {@code true} if {@code player} has a winning configuration
     */
    boolean wins(byte player);

    /**
     * Heuristically scores the current position from {@code player}'s viewpoint;
     * higher is better. Used by the minimax / alpha-beta strategies.
     *
     * @param player the player whose perspective to score from
     * @return the heuristic score
     */
    int evalState(byte player);
}
