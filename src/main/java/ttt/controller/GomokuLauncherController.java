package ttt.controller;

import java.io.IOException;

import javafx.scene.control.Toggle;
import javafx.stage.Stage;
import ttt.ai.IGameKI;
import ttt.ai.StrategyAlphaBeta;
import ttt.ai.StrategyBlock;
import ttt.ai.StrategyMinimax;
import ttt.ai.StrategyRandom;
import ttt.model.Gomoku;
import ttt.model.Pair;
import ttt.util.Debugger;
import ttt.view.GomokuLauncherFX;

/**
 * Controller for the Gomoku start screen.
 *
 * Reads selected UI settings, updates launcher labels, and delegates match
 * startup to the main JavaFX application. No AI move computation happens here.
 */
public class GomokuLauncherController {

    private static final String PVP = "PVP";
    private static final String PVA = "PVA";
    private static final String AVA = "AVA";

    private final GomokuLauncherFX view;
    private final Stage stage;

    /**
     * Creates the controller, wires all UI event listeners, and runs an initial
     * refresh so the view reflects the default selections.
     *
     * @param view  the start-screen view to control
     * @param stage the primary stage (used to close the window on Exit)
     */
    public GomokuLauncherController(GomokuLauncherFX view, Stage stage) {
        this.view = view;
        this.stage = stage;
        wireEvents();
        refresh();
    }

    /** Attaches listeners to every interactive control in the view. */
    private void wireEvents() {
        view.getModeGroup().selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            keepSelection(oldToggle, newToggle);
            refresh();
        });
        view.getBoardSizeGroup().selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            keepSelection(oldToggle, newToggle);
            refresh();
        });
        view.getBlackStrategyGroup().selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            keepSelection(oldToggle, newToggle);
            refresh();
        });
        view.getRedStrategyGroup().selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            keepSelection(oldToggle, newToggle);
            refresh();
        });
        view.getDepthSlider().valueProperty().addListener((obs, oldValue, newValue) -> {
            view.getDepthSlider().setValue(Math.round(newValue.doubleValue()));
            refresh();
        });
        view.getDebugCheckBox().selectedProperty().addListener((obs, oldValue, newValue) -> refresh());
        view.getStartButton().setOnAction(event -> startMatch());
        view.getExitButton().setOnAction(event -> stage.close());
    }

    /** Prevents a toggle group from ending up with nothing selected. */
    private void keepSelection(Toggle oldToggle, Toggle newToggle) {
        if (newToggle == null && oldToggle != null) {
            oldToggle.setSelected(true);
        }
    }

    /**
     * Synchronises every dependent label and control in the view with the
     * current toggle selections. Called after any UI change.
     */
    private void refresh() {
        String mode = getSelectedMode();
        int boardSize = getSelectedBoardSize();
        int depth = getSelectedDepth();

        boolean blackAi = AVA.equals(mode);
        boolean redAi = PVA.equals(mode) || AVA.equals(mode);

        view.setBoardSizeHint(boardSizeHint(boardSize));
        view.setStrategyControlsEnabled(blackAi, redAi);
        boolean depthNeeded = (blackAi && usesDepth(getSelectedBlackStrategy()))
                           || (redAi   && usesDepth(getSelectedRedStrategy()));
        view.setDepthEnabled(depthNeeded);
        view.setDepthDisplay(depth, depthDescriptor(depth));

        String blackStrategy = blackAi ? strategyLabel(getSelectedBlackStrategy(), depth) : "HUMAN";
        String redStrategy = redAi ? strategyLabel(getSelectedRedStrategy(), depth) : "HUMAN";
        view.setPreview(blackAi ? "CPU" : "Player One", blackStrategy,
                redAi ? "CPU" : "Player Two", redStrategy);
        view.setPlayerIcons(blackAi ? "🤖" : "🐉",
                            redAi   ? "🤖" : "🐉");
        view.setSummary(buildSummary(mode, boardSize, blackAi, redAi, blackStrategy, redStrategy));
    }

    /** Reads current settings and hands off to the application to start the game. */
    private void startMatch() {
        StartSettings settings = readSettings();
        GomokuFXApp.startGame(stage, settings);
    }

    /**
     * Reads the current UI selections and packages them into a {@link StartSettings}.
     * Called by {@link #startMatch()} and also exposed so tests can inspect settings
     * without triggering game startup.
     *
     * @return an immutable snapshot of the user's chosen match configuration
     */
    public StartSettings readSettings() {
        String displayMode = getSelectedMode();
        boolean blackAi = AVA.equals(displayMode);
        boolean redAi = PVA.equals(displayMode) || AVA.equals(displayMode);

        String internalMode;
        if (PVP.equals(displayMode)) {
            internalMode = "PP";
        } else if (PVA.equals(displayMode)) {
            internalMode = "PC";
        } else {
            internalMode = "CC";
        }

        return new StartSettings(
                internalMode,
                displayMode,
                getSelectedBoardSize(),
                blackAi ? getSelectedBlackStrategy() : null,
                redAi ? getSelectedRedStrategy() : null,
                getSelectedDepth(),
                view.getDebugCheckBox().isSelected());
    }

    private String getSelectedMode() {
        return (String) view.getModeGroup().getSelectedToggle().getUserData();
    }

    private int getSelectedBoardSize() {
        return (Integer) view.getBoardSizeGroup().getSelectedToggle().getUserData();
    }

    private String getSelectedBlackStrategy() {
        return (String) view.getBlackStrategyGroup().getSelectedToggle().getUserData();
    }

    private String getSelectedRedStrategy() {
        return (String) view.getRedStrategyGroup().getSelectedToggle().getUserData();
    }

    private int getSelectedDepth() {
        return (int) Math.round(view.getDepthSlider().getValue());
    }

    /** Builds the one-line footer summary shown at the bottom of the start screen. */
    private String buildSummary(String mode, int boardSize, boolean blackAi, boolean redAi,
            String blackStrategy, String redStrategy) {
        String engine;
        if (!blackAi && !redAi) {
            engine = "HUMAN";
        } else if (!blackAi) {
            engine = redStrategy;
        } else {
            engine = blackStrategy + " / " + redStrategy;
        }
        String debug = view.getDebugCheckBox().isSelected() ? " - DEBUG" : "";
        return mode + " · " + boardSize + "×" + boardSize + " · " + engine + debug;
    }

    /** Returns the human-readable hint shown next to the board-size selector. */
    private String boardSizeHint(int boardSize) {
        switch (boardSize) {
            case 13:
                return "quick 13×13";
            case 17:
                return "large 17×17";
            case 19:
                return "epic 19×19";
            default:
                return "standard 15×15";
        }
    }

    /** Returns true only for strategies that actually use the depth search tree. */
    private boolean usesDepth(String code) {
        return "MINIMAX".equals(code) || "ALPHABETA".equals(code);
    }

    /** Converts a strategy code and depth into the display string shown in the VS card. */
    private String strategyLabel(String code, int depth) {
        switch (code) {
            case "BLOCK":
                return "BLOCK";
            case "MINIMAX":
                return "MINIMAX·D" + depth;
            case "ALPHABETA":
                return "ALPHABETA·D" + depth;
            default:
                return "RANDOM";
        }
    }

    /** Maps a numeric depth (1–8) to a descriptive label shown beside the slider. */
    private String depthDescriptor(int depth) {
        switch (depth) {
            case 1:
                return "Instant · ~0.05s";
            case 2:
                return "Shallow · ~0.1s";
            case 3:
                return "Quick · ~0.3s";
            case 5:
                return "Thoughtful · ~2s";
            case 6:
                return "Deep · ~6s";
            case 7:
                return "Expert · ~18s";
            case 8:
                return "Relentless · ~30s";
            default:
                return "Balanced · ~0.8s";
        }
    }

    /**
     * Initialises the debug logger, instantiates the AI strategies, and hands
     * control to {@link GomokuControllerFX} to start the match.
     * <p>
     * In PC mode ai1 drives Red (the computer side); ai2 is null.
     * In CC mode both ai1 (Black) and ai2 (Red) are created.
     *
     * @param stage    the primary stage to replace the launcher scene on
     * @param settings the match configuration produced by {@link #readSettings()}
     */
    public static void initializeGame(Stage stage, StartSettings settings) {
        try {
            Debugger.init(settings.isDebug());
        } catch (IOException ex) {
            System.err.println("[ERROR] Debug init failed: " + ex.getMessage());
        }

        IGameKI<Pair<Byte, Byte>> ai1;
        IGameKI<Pair<Byte, Byte>> ai2;
        if ("PC".equals(settings.getInternalMode())) {
            ai1 = createStrategy(settings.getRedStrategyCode(), settings.getAiDepth());
            ai2 = null;
        } else {
            ai1 = createStrategy(settings.getBlackStrategyCode(), settings.getAiDepth());
            ai2 = createStrategy(settings.getRedStrategyCode(), settings.getAiDepth());
        }

        byte boardSize = (byte) settings.getBoardSize();
        new GomokuControllerFX(new Gomoku(boardSize, boardSize), ai1, ai2, settings.getInternalMode(), stage);
    }

    /**
     * Factory method that maps a strategy code to a concrete AI implementation.
     * Accepts both short codes (S1–S4) and full names (RANDOM, BLOCK, MINIMAX, ALPHABETA).
     *
     * @param code  strategy identifier; {@code null} returns {@code null} (human side)
     * @param depth search depth passed to depth-limited strategies (Minimax, AlphaBeta)
     * @return the AI strategy instance, or {@code null} if {@code code} is {@code null}
     */
    public static IGameKI<Pair<Byte, Byte>> createStrategy(String code, int depth) {
        if (code == null) {
            return null;
        }

        String normalized = code.toUpperCase();
        switch (normalized) {
            case "BLOCK":
            case "S2":
                return new StrategyBlock<>();
            case "MINIMAX":
            case "S3":
                return new StrategyMinimax<>(depth);
            case "ALPHABETA":
            case "S4":
                return new StrategyAlphaBeta<>(depth);
            case "RANDOM":
            case "S1":
            default:
                return new StrategyRandom<>();
        }
    }

    /**
     * Immutable snapshot of the user's match configuration captured at the moment
     * "Start Match" is clicked. Passed from the launcher controller to
     * {@link GomokuFXApp#startGame(Stage, StartSettings)} and on to
     * {@link #initializeGame(Stage, StartSettings)}.
     */
    public static final class StartSettings {
        private final String internalMode;
        private final String displayMode;
        private final int boardSize;
        private final String blackStrategyCode;
        private final String redStrategyCode;
        private final int aiDepth;
        private final boolean debug;

        /**
         * @param internalMode      engine mode code: "PP", "PC", or "CC"
         * @param displayMode       display mode label: "PVP", "PVA", or "AVA"
         * @param boardSize         board dimension (e.g. 15 for a 15×15 board)
         * @param blackStrategyCode AI strategy for Black, or {@code null} if human
         * @param redStrategyCode   AI strategy for Red, or {@code null} if human
         * @param aiDepth           search depth for depth-limited strategies
         * @param debug             whether the debug overlay should be enabled
         */
        public StartSettings(String internalMode, String displayMode, int boardSize,
                String blackStrategyCode, String redStrategyCode, int aiDepth, boolean debug) {
            this.internalMode = internalMode;
            this.displayMode = displayMode;
            this.boardSize = boardSize;
            this.blackStrategyCode = blackStrategyCode;
            this.redStrategyCode = redStrategyCode;
            this.aiDepth = aiDepth;
            this.debug = debug;
        }

        public String getInternalMode() {
            return internalMode;
        }

        public String getDisplayMode() {
            return displayMode;
        }

        public int getBoardSize() {
            return boardSize;
        }

        public String getBlackStrategyCode() {
            return blackStrategyCode;
        }

        public String getRedStrategyCode() {
            return redStrategyCode;
        }

        public int getAiDepth() {
            return aiDepth;
        }

        public boolean isDebug() {
            return debug;
        }
    }
}
