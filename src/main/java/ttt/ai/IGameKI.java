package ttt.ai;

import ttt.model.IGame;

/**
 * Strategy interface for a Gomoku AI player (Strategy pattern).
 *
 * <p>Each implementation decides a move for the player to move in a given game
 * state. Concrete strategies range from trivial ({@link StrategyRandom}) to a
 * full alpha-beta search ({@link StrategyAlphaBeta}); the controller holds an
 * {@code IGameKI} reference and invokes it polymorphically, never depending on
 * the concrete algorithm.
 *
 * @param <M> the move type (for Gomoku, a {@code Pair<Byte, Byte>})
 */
public interface IGameKI<M> {

    /**
     * Chooses the best move and applies it.
     *
     * @param game the current game state
     * @return the game state after the chosen move, or {@code game} unchanged if
     *         no move is available
     */
    IGame<M> doBestMove(IGame<M> game);

    /**
     * Chooses the best move without applying it.
     *
     * @param game the current game state
     * @return the chosen move, or {@code null} if no move is available
     */
    M bestMove(IGame<M> game);
}
