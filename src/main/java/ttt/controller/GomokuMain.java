package ttt.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * GomokuMain is the CLI entry point for the Gomoku application.
 * <p>
 * Handles help output, validates the command-line syntax, and delegates all
 * valid arguments to GomokuFXApp.
 */
public class GomokuMain {

    // --- CLI syntax validation (regex-based, per assignment requirement) ---
    // A single compiled pattern expresses every legal command shape, so the
    // syntax check is one Pattern.matches() call rather than an if/else chain.
    // The optional debug flag "D" is encoded as alternatives, which lets it
    // appear in any valid slot (e.g. "PC D S3" and "PC S3 D" both match).
    private static final Pattern COMMAND_PATTERN = Pattern.compile(
              "PP( D)?"
            + "|PC( D S[1-4]| S[1-4]( D)?)"
            + "|CC( S[1-4] S[1-4]| D S[1-4] S[1-4]| S[1-4] D S[1-4]| S[1-4] S[1-4] D)"
            + "|SERVER \\d{1,5}"
            + "|CLIENT \\S+ \\d{1,5}");

    /** Matches a single strategy token (S1–S4); used for the PP logic check. */
    private static final Pattern STRATEGY_TOKEN = Pattern.compile("S[1-4]");

    /**
     * Main method: parses arguments, selects game mode, and launches the JavaFX app.
     *
     * @param args Command-line arguments for mode, debug, strategies, and load path.
     */
    public static void main(String[] args) {
        // If no arguments, launch the JavaFX app
        if (args.length == 0) {
            GomokuFXApp.main(args);
            return;
        }

        // Show help if requested
        if (args.length == 1 && (
            args[0].equalsIgnoreCase("H") ||
            args[0].equalsIgnoreCase("--help") ||
            args[0].equalsIgnoreCase("-h"))) {
            printHelpAndExit();
        }

        // Validate the command-line syntax before touching JavaFX, so bad input
        // prints an error + usage and exits cleanly instead of crashing later.
        validateArgs(args);

        // Arguments are valid — launch the JavaFX app, which does the positional
        // parsing (mode, strategies, host/port, plus the D / load= options).
        GomokuFXApp.main(args);
    }

    /**
     * Validates the command-line argument syntax and a couple of logic rules.
     * On any violation, prints a clear error message plus usage help and exits
     * with a non-zero status; returns normally when the arguments are valid.
     * <p>
     * The syntax check is performed with {@link #COMMAND_PATTERN} (a regular
     * expression) rather than nested if/else logic. The free-position
     * {@code load=<path>} option is removed first, and the {@code DEBUG} alias is
     * folded to {@code D}, so the remaining tokens form the core command string
     * that the regex matches against (case-insensitively, via upper-casing).
     *
     * @param args the raw command-line arguments (already known to be non-empty
     *             and not the help flag)
     */
    private static void validateArgs(String[] args) {
        // Strip the free-position load=<path> option; normalize case and fold the
        // DEBUG alias to D. The result is the core "mode [...]" command tokens.
        List<String> tokens = new ArrayList<>();
        for (String arg : args) {
            if (arg.toUpperCase().startsWith("LOAD=")) {
                continue; // free-position option, not part of the syntax grammar
            }
            String token = arg.toUpperCase();
            if (token.equals("DEBUG")) {
                token = "D";
            }
            tokens.add(token);
        }

        String command = String.join(" ", tokens);

        // Logic check (beyond syntax): a strategy in PP mode makes no sense.
        // Caught explicitly so the user gets a precise message rather than the
        // generic "invalid command" that the regex would otherwise produce.
        if (!tokens.isEmpty() && tokens.get(0).equals("PP")) {
            for (String token : tokens) {
                if (STRATEGY_TOKEN.matcher(token).matches()) {
                    fail("Player-vs-Player (PP) takes no strategy — '" + token
                            + "' is not allowed in PP mode.");
                }
            }
        }

        // Syntax check: the whole command must match one legal shape.
        if (!COMMAND_PATTERN.matcher(command).matches()) {
            fail("Invalid command: \"" + String.join(" ", args) + "\"");
        }

        // Logic check: SERVER/CLIENT port must be a usable TCP port (1–65535).
        // Safe to parse here because the regex already guaranteed 1–5 digits.
        if (tokens.get(0).equals("SERVER") || tokens.get(0).equals("CLIENT")) {
            int port = Integer.parseInt(tokens.get(tokens.size() - 1));
            if (port < 1 || port > 65535) {
                fail("Port out of range (1–65535): " + port);
            }
        }
    }

    /**
     * Prints an error message followed by the usage help, then exits with a
     * non-zero status code.
     *
     * @param message the human-readable description of what was wrong
     */
    private static void fail(String message) {
        System.err.println("\n[ERROR] " + message);
        printUsage();
        System.exit(1);
    }

    /**
     * Prints the help message and exits the application normally.
     */
    private static void printHelpAndExit() {
        printUsage();
        System.exit(0);
    }

    /**
     * Prints the usage / help text. Shared by the {@code -h} help path and by
     * {@link #fail(String)} when reporting an invalid command.
     */
    private static void printUsage() {
        System.out.println("\n============ Gomoku Help ============\n");
        System.out.println("Usage: java -jar target/Gomokufx-1.0-SNAPSHOT.jar [MODE] [OPTIONS]");
        System.out.println("\nModes:");
        System.out.println("  PC             Player vs Computer");
        System.out.println("  PP             Player vs Player (local, one window)");
        System.out.println("  CC             Computer vs Computer (AI vs AI)");
        System.out.println("\nOptions:");
        System.out.println("  D              Debug: log moves to console and to file (gomoku_debug.txt)");
        System.out.println("  S1..S4         Select AI strategy (S1=Random, S2=Block, S3=Minimax, S4=AlphaBeta)");
        System.out.println("  load=path      Load a saved XML game (e.g. load=savedgame.xml)");
        System.out.println("\nNetwork:");
        System.out.println("  SERVER <port>          Host a network game (you play Black)");
        System.out.println("  CLIENT <host> <port>   Join a network game (you play Red)");
        System.out.println("\nExamples:");
        System.out.println("  java -jar target/Gomokufx-1.0-SNAPSHOT.jar PC S1 D");
        System.out.println("  java -jar target/Gomokufx-1.0-SNAPSHOT.jar CC S2 S4");
        System.out.println("  java -jar target/Gomokufx-1.0-SNAPSHOT.jar PC D load=savedgame.xml");
        System.out.println("  java -jar target/Gomokufx-1.0-SNAPSHOT.jar SERVER 5000");
        System.out.println("  java -jar target/Gomokufx-1.0-SNAPSHOT.jar CLIENT 192.168.1.5 5000");
        System.out.println("\n======================================\n");
    }

}