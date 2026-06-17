package ttt.model;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;

/**
 * Concrete Gomoku (Five-in-a-Row) game on a square board (Template Method:
 * fills in the rules left abstract by {@link ARegularGame}).
 *
 * <p>A player wins by placing five of their stones in an unbroken horizontal,
 * vertical, or diagonal line. Each move returns a new cloned game state, so the
 * object is effectively immutable and safe for AI tree search.
 *
 * <p>The board defaults to {@value #DEFAULT_SIZE} x {@value #DEFAULT_SIZE} but
 * any square size is supported via {@link #Gomoku(byte, byte)}.
 */
public class Gomoku extends ARegularGame<Pair<Byte, Byte>> {

    /** Default board dimension (15 x 15). */
    public static final byte DEFAULT_SIZE = 15;

    /** The five winning stones, populated by {@link #wins(byte)} when a line is found. */
    private List<Pair<Byte, Byte>> winningStones = new ArrayList<>();

    /** Creates a standard {@value #DEFAULT_SIZE} x {@value #DEFAULT_SIZE} board. */
    public Gomoku() {
        this(DEFAULT_SIZE, DEFAULT_SIZE);
    }

    /**
     * Creates a {@code rows} x {@code cols} board.
     *
     * @param rows number of board rows
     * @param cols number of board columns
     */
    public Gomoku(byte rows, byte cols) {
        super(rows, cols);
    }

    /**
     * Positional bonus that favours the centre of the board: cells closer to the
     * centre score higher, encouraging more useful early moves.
     *
     * @param row cell row
     * @param col cell column
     * @return a weight that decreases with distance from the centre
     */
    private int centerWeight(byte row, byte col) {
        int centerRow = ROWS / 2;
        int centerCol = COLS / 2;
        int maxDistance = Math.max(Math.abs(row - centerRow), Math.abs(col - centerCol));
        return Math.max(ROWS, COLS) - maxDistance;
    }

    /**
     * Scores a single occupied position for the given player by combining the
     * centre weight with the potential of the four line directions through it.
     *
     * @param player the player owning the stone at this position
     * @param row    cell row
     * @param col    cell column
     * @return the position's contribution to the player's score
     */
    public int evalStateForPosition(byte player, byte row, byte col) {
        int score = centerWeight(row, col);
        int[][] directions = {
            { 0, 1 },  // horizontal
            { 1, 0 },  // vertical
            { 1, 1 },  // diagonal down-right
            { 1, -1 }  // diagonal up-right
        };
        for (int[] dir : directions) {
            score += evaluatePotentialSequence(player, row, col, dir[0], dir[1]);
        }
        return score;
    }

    /**
     * Looks up to four cells either way along one direction and rewards lines
     * that have several of the player's stones plus room to grow to five.
     *
     * @param player the player to evaluate for
     * @param r      starting row
     * @param c      starting column
     * @param dr     row step of the direction
     * @param dc     column step of the direction
     * @return a partial score for this direction
     */
    private int evaluatePotentialSequence(byte player, int r, int c, int dr, int dc) {
        int playerCount = 0;
        int emptyCount = 0;
        for (int i = -4; i <= 4; i++) {
            if (i == 0) continue;
            int nr = r + dr * i;
            int nc = c + dc * i;
            if (nr < 0 || nr >= ROWS || nc < 0 || nc >= COLS) break;
            byte occupant = board[nr][nc];
            if (occupant == player) {
                playerCount++;
            } else if (occupant == NONE) {
                emptyCount++;
            } else {
                break;
            }
        }
        if (playerCount >= 3 && emptyCount >= 2) return 100;
        if (playerCount >= 2 && emptyCount >= 3) return 50;
        if (playerCount >= 1) return 10;
        return 0;
    }

    /**
     * Evaluates the whole board from {@code player}'s perspective: a guaranteed
     * win/loss returns the extreme score, otherwise the player's positional
     * value minus the opponent's. Drives the minimax / alpha-beta search.
     *
     * @param player the player whose perspective to score from
     * @return the heuristic board score (higher is better for {@code player})
     */
    @Override
    public int evalState(byte player) {
        if (wins(player)) return Integer.MAX_VALUE;
        byte opponent = otherPlayer(player);
        if (wins(opponent)) return Integer.MIN_VALUE;

        int score = 0;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                byte occupant = board[r][c];
                if (occupant == player) {
                    score += evalStateForPosition(player, (byte) r, (byte) c);
                } else if (occupant == opponent) {
                    score -= evalStateForPosition(opponent, (byte) r, (byte) c);
                }
            }
        }
        return score;
    }

    /**
     * Generates the candidate moves: the very first move is the board centre,
     * afterwards only empty cells adjacent (including diagonally) to an existing
     * stone are considered, which keeps the AI's branching factor manageable.
     *
     * @return the list of legal candidate moves
     */
    @Override
    public List<Pair<Byte, Byte>> moves() {
        if (movesDone == 0) {
            byte mid = (byte)(ROWS / 2);
            return List.of(new Pair<>(mid, mid));
        }
        Set<Pair<Byte, Byte>> candidates = new HashSet<>();
        for (byte r = 0; r < ROWS; r++) {
            for (byte c = 0; c < COLS; c++) {
                if (board[r][c] != NONE) {
                    for (int dr = -1; dr <= 1; dr++) {
                        for (int dc = -1; dc <= 1; dc++) {
                            int nr = r + dr, nc = c + dc;
                            if (nr >= 0 && nr < ROWS && nc >= 0 && nc < COLS) {
                                if (board[nr][nc] == NONE) {
                                    candidates.add(new Pair<>((byte) nr, (byte) nc));
                                }
                            }
                        }
                    }
                }
            }
        }
        return new ArrayList<>(candidates);
    }

    /**
     * Places the current player's stone at the given cell if it is on the board
     * and empty; otherwise the move is rejected and this state is returned.
     *
     * @param row board row (0-indexed)
     * @param col board column (0-indexed)
     * @return the resulting state, or this unchanged state for an illegal move
     */
    @Override
    public IRegularGame<Pair<Byte, Byte>> setAtPosition(byte row, byte col) {
        if (row < 0 || row >= ROWS || col < 0 || col >= COLS || board[row][col] != NONE) {
            return this; // invalid move
        }
        return doMove(new Pair<>(row, col));
    }

    /**
     * Applies a move on a clone of this game: places the current player's stone,
     * records it as the last move, increments the counter, and hands the turn to
     * the opponent. This state is left unchanged.
     *
     * @param move the (row, column) to play
     * @return the new game state after the move
     */
    @Override
    public IRegularGame<Pair<Byte, Byte>> doMove(Pair<Byte, Byte> move) {
        try {
            Gomoku copy = (Gomoku) this.clone();
            byte current = copy.currentPlayer();
            copy.board[move.first][move.second] = current;
            copy.lastRow = move.first;
            copy.lastCol = move.second;
            copy.movesDone++;
            copy.player = otherPlayer(current);
            // winningStones is recomputed on demand by wins()
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException("Clone failed", e);
        }
    }

    /**
     * @return {@code true} if every cell is occupied (no further moves possible)
     */
    @Override
    public boolean noMoreMove() {
        return movesDone >= ROWS * COLS;
    }

    /**
     * Checks whether {@code player} has five in a row through the last-played
     * cell. As a side effect, on a win it stores the five stones, retrievable via
     * {@link #getWinningStones()} for the win highlight.
     *
     * @param player the player to test
     * @return {@code true} if {@code player} has a winning line of five
     */
    @Override
    public boolean wins(byte player) {
        winningStones.clear();
        int[][] directions = {
            { 1,  0},  // vertical
            { 0,  1},  // horizontal
            { 1,  1},  // diagonal down-right
            { 1, -1}   // diagonal up-right
        };
        for (int[] d : directions) {
            List<Pair<Byte, Byte>> stones = new ArrayList<>();
            stones.add(new Pair<>(lastRow, lastCol));
            // Forward
            for (int step = 1; step < 5; step++) {
                int nr = lastRow + d[0] * step;
                int nc = lastCol + d[1] * step;
                if (nr < 0 || nr >= ROWS || nc < 0 || nc >= COLS) break;
                if (board[nr][nc] != player) break;
                stones.add(new Pair<>((byte) nr, (byte) nc));
            }
            // Backward
            for (int step = 1; step < 5; step++) {
                int nr = lastRow - d[0] * step;
                int nc = lastCol - d[1] * step;
                if (nr < 0 || nr >= ROWS || nc < 0 || nc >= COLS) break;
                if (board[nr][nc] != player) break;
                stones.add(0, new Pair<>((byte) nr, (byte) nc));
            }
            if (stones.size() >= 5) {
                winningStones = stones.subList(0, 5);
                return true;
            }
        }
        return false;
    }

    /**
     * @return a copy of the five winning stones from the most recent
     *         {@link #wins(byte)} call, or an empty list if there was no win
     */
    public List<Pair<Byte, Byte>> getWinningStones() {
        return new ArrayList<>(winningStones);
    }
}
