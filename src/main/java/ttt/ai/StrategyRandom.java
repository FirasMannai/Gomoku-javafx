package ttt.ai;

import ttt.model.IGame;
import java.util.List;
import java.util.Random;

/**
 * Weakest AI strategy: plays a uniformly random legal move.
 *
 * <p>Ignores search depth entirely. It also serves as the fallback for
 * {@link StrategyBlock} once no immediate win or block is available.
 *
 * @param <M> the move type
 */
public class StrategyRandom<M> implements IGameKI<M> {

    /** Source of randomness for move selection. */
    private final Random rand = new Random();

    /**
     * {@inheritDoc}
     *
     * <p>Applies a randomly chosen legal move.
     */
    @Override
    public IGame<M> doBestMove(IGame<M> game) {
        M move = bestMove(game);
        if (move == null) return game; // No moves: return unchanged
        return game.doMove(move);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns a uniformly random move from the legal moves, or {@code null}
     * if there are none (e.g. the game has ended).
     */
    @Override
    public M bestMove(IGame<M> game) {
        List<M> moves = game.moves();
        if (moves.isEmpty()) return null; // prevents crash at game end
        return moves.get(rand.nextInt(moves.size()));
    }
}
