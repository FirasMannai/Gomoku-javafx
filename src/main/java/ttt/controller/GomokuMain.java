/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package ttt.controller;

/**
 * GomokuMain is the CLI entry point for the Gomoku application.
 * <p>
 * Handles help output and delegates all other arguments to GomokuFXApp.
 */
public class GomokuMain {

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

        // For all other cases, just launch the JavaFX app
        // (CLI game modes can be handled later if needed)
        GomokuFXApp.main(args);
    }

    /**
     * Prints the help message and exits the application.
     */
    private static void printHelpAndExit() {
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
        System.exit(0);
    }

}