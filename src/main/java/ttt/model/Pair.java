package ttt.model;

/**
 * A simple immutable ordered pair of two values.
 *
 * <p>Throughout this project it represents a board coordinate as
 * {@code Pair<Byte, Byte>} — the row in {@link #first} and the column in
 * {@link #second}.
 *
 * @param <A> type of the first element
 * @param <B> type of the second element
 */
public class Pair<A, B> {

    /** The first element (the row, when used as a board coordinate). */
    public final A first;

    /** The second element (the column, when used as a board coordinate). */
    public final B second;

    /**
     * Creates an immutable pair.
     *
     * @param first  the first element
     * @param second the second element
     */
    public Pair(A first, B second) {
        this.first = first;
        this.second = second;
    }
}
