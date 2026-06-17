package ttt.ai;

import ttt.model.IGame;

import java.util.List;

/**
 * One-ply tactical strategy: take an immediate win, otherwise block the
 * opponent's immediate win, otherwise play randomly.
 *
 * <p>Looks only a single move ahead (no depth search). Stronger than
 * {@link StrategyRandom} but easily out-played by the search-based strategies.
 *
 * @param <M> the move type
 */
public class StrategyBlock<M> implements IGameKI<M> {

    /**
     * {@inheritDoc}
     *
     * <p>Applies the move selected by {@link #bestMove(IGame)}, or returns the
     * game unchanged if no move is available (e.g. the board is full).
     */
    @Override
    public IGame<M> doBestMove(IGame<M> game) {
        M move = bestMove(game);
        if (move == null) return game;
        return game.doMove(move);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Selection order: (1) a move that wins immediately, (2) a move that
     * blocks the opponent's immediate win, (3) a random move.
     */
    @Override
    public M bestMove(IGame<M> game) {
        byte me = game.currentPlayer();
        byte opp = game.otherPlayer(me);
        List<M> moves = game.moves();

        // 1. Check for immediate win
        for (M move : moves) {
            IGame<M> next = game.doMove(move);
            if (next.wins(me)) {
                return move;
            }
        }

        // 2. Block opponent's immediate win
        for (M move : moves) {
            IGame<M> next = game.doMove(move);
            if (next.wins(opp)) {
                return move;
            }
        }

        // 3. Fallback to random
        return new StrategyRandom<M>().bestMove(game);
    }
}
