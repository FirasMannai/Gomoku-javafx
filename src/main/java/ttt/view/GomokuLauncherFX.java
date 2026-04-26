package ttt.view;

import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/**
 * Pure-view start screen for the Gomoku application.
 *
 * <p>Builds and owns every interactive control on the launcher: game-mode
 * toggles, board-size selector, Black and Red strategy pickers, the shared
 * AI search-depth slider, the debug checkbox, and the Start / Exit buttons.
 * It also contains the hero column on the left with the animated title, the
 * "vs" preview card, and the footer summary line.
 *
 * <p>This class contains <em>no game logic</em>. All state changes (enabling
 * controls, updating labels, reading selections) are driven by
 * {@link ttt.controller.GomokuLauncherController}, which wires its own
 * listeners to the groups and controls exposed via the public API below.
 */
public class GomokuLauncherFX extends StackPane {

    private final ToggleGroup modeGroup        = new ToggleGroup();
    private final ToggleGroup boardSizeGroup   = new ToggleGroup();
    private final ToggleGroup blackStrategyGroup = new ToggleGroup();
    private final ToggleGroup redStrategyGroup   = new ToggleGroup();

    private final ToggleButton pvpButton = createModeButton("PVP", "HUMAN", "black", "red");
    private final ToggleButton pvaButton = createModeButton("PVA", "AI",    "black", "ai");
    private final ToggleButton avaButton = createModeButton("AVA", "DUEL",  "ai",    "ai");

    private final ToggleButton board13Button = createBoardSizeButton(13, "QUICK");
    private final ToggleButton board15Button = createBoardSizeButton(15, "STD");
    private final ToggleButton board17Button = createBoardSizeButton(17, "LARGE");
    private final ToggleButton board19Button = createBoardSizeButton(19, "EPIC");

    private final ToggleButton blackRandomButton  = createStrategyButton("Random",    "RANDOM",    "S1");
    private final ToggleButton blackBlockButton   = createStrategyButton("Block",     "BLOCK",     "S2");
    private final ToggleButton blackMinimaxButton = createStrategyButton("Minimax",   "MINIMAX",   "S3");
    private final ToggleButton blackAlphaButton   = createStrategyButton("AlphaBeta", "ALPHABETA", "S4");

    private final ToggleButton redRandomButton  = createStrategyButton("Random",    "RANDOM",    "S1");
    private final ToggleButton redBlockButton   = createStrategyButton("Block",     "BLOCK",     "S2");
    private final ToggleButton redMinimaxButton = createStrategyButton("Minimax",   "MINIMAX",   "S3");
    private final ToggleButton redAlphaButton   = createStrategyButton("AlphaBeta", "ALPHABETA", "S4");

    private final Slider   depthSlider  = new Slider(1, 8, 4);
    private final CheckBox debugCheckBox = new CheckBox("Debug overlay");
    private final Button   startButton  = new Button();
    private final Button   exitButton   = new Button("Exit");

    private final Label boardSizeHintLabel   = new Label("standard 15×15");
    private final Label blackStrategyHintLabel = new Label("disabled (human)");
    private final Label redStrategyHintLabel   = new Label("select");
    private final Label blackNameLabel       = new Label("Player One");
    private final Label redNameLabel         = new Label("CPU");
    private final Label blackStrategyLabel   = new Label("HUMAN");
    private final Label redStrategyLabel     = new Label("ALPHABETA D4");
    private final Label depthValueLabel      = new Label("4 ply");
    private final Label depthDescriptorLabel = new Label("Balanced");
    private final Label summaryLabel         = new Label("PVA · 15×15 · ALPHABETA D4");

    // Emoji avatar icons updated by the controller on each refresh
    private final Label p1IconLabel = new Label("🐉"); // dragon
    private final Label p2IconLabel = new Label("🤖"); // robot

    private final VBox blackStrategyCard;
    private final VBox redStrategyCard;
    private final VBox depthCard;

    /**
     * Constructs the start screen, builds the full scene graph, wires default
     * selections, and adds the root layout to this {@code StackPane}.
     */
    public GomokuLauncherFX() {
        getStyleClass().add("gomoku-start-root");

        pvpButton.setUserData("PVP");
        pvaButton.setUserData("PVA");
        avaButton.setUserData("AVA");

        configureToggleGroup(modeGroup,          pvpButton,   pvaButton,         avaButton);
        configureToggleGroup(boardSizeGroup,     board13Button, board15Button, board17Button, board19Button);
        configureToggleGroup(blackStrategyGroup, blackRandomButton, blackBlockButton, blackMinimaxButton, blackAlphaButton);
        configureToggleGroup(redStrategyGroup,   redRandomButton,  redBlockButton,  redMinimaxButton,  redAlphaButton);

        pvaButton.setSelected(true);
        board15Button.setSelected(true);
        blackAlphaButton.setSelected(true);
        redAlphaButton.setSelected(true);

        depthSlider.setShowTickMarks(true);
        depthSlider.setShowTickLabels(true);
        depthSlider.setMajorTickUnit(1);
        depthSlider.setMinorTickCount(0);
        depthSlider.setSnapToTicks(true);
        depthSlider.getStyleClass().add("depth-slider");

        // Start button: graphic carries the text + keyboard hint
        Label startText = new Label("Start Match");
        startText.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:#ffffff;");
        Label startKbd = new Label("↵ ENTER");
        startKbd.setStyle(
            "-fx-font-family:'Consolas',monospace;-fx-font-size:10px;-fx-text-fill:rgba(255,255,255,0.5);" +
            "-fx-background-color:rgba(0,0,0,0.25);-fx-background-radius:4;-fx-padding:2 6 2 6;");
        HBox startContent = new HBox(8, startText, startKbd);
        startContent.setAlignment(Pos.CENTER);
        startButton.setGraphic(startContent);
        startButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

        blackStrategyCard = createStrategyCard("Strategy · Black", blackStrategyHintLabel,
            blackRandomButton, blackBlockButton, blackMinimaxButton, blackAlphaButton);
        redStrategyCard = createStrategyCard("Strategy · Red", redStrategyHintLabel,
            redRandomButton, redBlockButton, redMinimaxButton, redAlphaButton);
        depthCard = createDepthCard();

        getChildren().add(createLayout());
    }

    private GridPane createLayout() {
        GridPane layout = new GridPane();
        layout.getStyleClass().add("gomoku-start-stage");
        layout.setHgap(28);
        layout.setPadding(new Insets(30, 40, 24, 40));

        ColumnConstraints left = new ColumnConstraints();
        left.setPercentWidth(40);
        left.setHgrow(Priority.ALWAYS);

        ColumnConstraints right = new ColumnConstraints();
        right.setPercentWidth(60);
        right.setHgrow(Priority.ALWAYS);

        RowConstraints row = new RowConstraints();
        row.setVgrow(Priority.ALWAYS);

        layout.getColumnConstraints().addAll(left, right);
        layout.getRowConstraints().add(row);

        layout.add(createHeroColumn(), 0, 0);
        layout.add(createSettingsPanel(), 1, 0);
        return layout;
    }

    private VBox createHeroColumn() {
        // Eyebrow: pulsing dot + version text
        Circle pulseDot = createPulseDot();
        Label eyebrowText = new Label("v 2.6 — javafx edition");
        eyebrowText.getStyleClass().add("launcher-eyebrow");
        HBox eyebrow = new HBox(10, pulseDot, eyebrowText);
        eyebrow.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("GOMOKU 🎯");
        title.getStyleClass().add("launcher-title");
        HBox titleRow = new HBox(title);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        VBox.setMargin(titleRow, new Insets(16, 0, 0, 0));

        Label subtitle = new Label(
            "Five in a row. Centuries of patterns, played on a sized board. Pick your side, your opponent, your weapon.");
        subtitle.getStyleClass().add("launcher-subtitle");
        subtitle.setWrapText(true);

        // VS preview card
        GridPane previewCard = new GridPane();
        previewCard.getStyleClass().add("vs-preview-card");
        previewCard.setHgap(12);
        previewCard.setAlignment(Pos.CENTER);

        ColumnConstraints sideCol = new ColumnConstraints();
        sideCol.setPercentWidth(42);
        ColumnConstraints midCol = new ColumnConstraints();
        midCol.setPercentWidth(16);
        previewCard.getColumnConstraints().addAll(sideCol, midCol, sideCol);

        previewCard.add(createPlayerPreview("BLACK · P1", blackNameLabel, blackStrategyLabel, "black", p1IconLabel), 0, 0);

        Label versus = new Label("vs");
        versus.getStyleClass().add("vs-separator");
        GridPane.setHalignment(versus, javafx.geometry.HPos.CENTER);
        previewCard.add(versus, 1, 0);

        previewCard.add(createPlayerPreview("RED · P2", redNameLabel, redStrategyLabel, "red", p2IconLabel), 2, 0);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        summaryLabel.getStyleClass().add("launcher-footer-summary");

        VBox.setMargin(subtitle, new Insets(10, 0, 0, 0));
        VBox.setMargin(previewCard, new Insets(28, 0, 0, 0));
        VBox hero = new VBox(14, eyebrow, titleRow, subtitle, previewCard, spacer, summaryLabel);
        hero.getStyleClass().add("hero-column");
        hero.setAlignment(Pos.TOP_LEFT);
        return hero;
    }

    private Circle createPulseDot() {
        Circle dot = new Circle(4.5, Color.web("#f5a623"));
        FadeTransition ft = new FadeTransition(Duration.millis(1600), dot);
        ft.setFromValue(1.0);
        ft.setToValue(0.25);
        ft.setCycleCount(FadeTransition.INDEFINITE);
        ft.setAutoReverse(true);
        ft.play();
        return dot;
    }

    private VBox createPlayerPreview(String sideText, Label nameLabel, Label stratLabel, String colorClass, Label iconLabel) {
        // Stone circle with emoji avatar on top
        Region stone = new Region();
        stone.getStyleClass().addAll("preview-stone", colorClass);
        stone.setMinSize(58, 58);
        stone.setMaxSize(58, 58);

        iconLabel.setStyle("-fx-font-size:22px;");

        StackPane avatar = new StackPane(stone, iconLabel);
        avatar.setMinSize(58, 58);
        avatar.setMaxSize(58, 58);

        Label side = new Label(sideText);
        side.getStyleClass().add("preview-side");

        nameLabel.getStyleClass().add("preview-name");
        stratLabel.getStyleClass().add("preview-strategy");

        VBox preview = new VBox(6, avatar, side, nameLabel, stratLabel);
        preview.getStyleClass().add("preview-side-card");
        preview.setAlignment(Pos.CENTER);
        return preview;
    }

    // Draws a mini wood-colored board grid for the board-size button graphic
    private Canvas buildMiniGrid(int n, double size) {
        Canvas canvas = new Canvas(size, size);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.web("#c99a55"));
        gc.fillRoundRect(0, 0, size, size, 3, 3);
        double pad  = 2.5;
        double step = (size - pad * 2) / Math.max(n - 1, 1);
        gc.setStroke(Color.web("#2a2119", 0.6));
        gc.setLineWidth(0.5);
        for (int i = 0; i < n; i++) {
            double pos = pad + i * step;
            gc.strokeLine(pos, pad, pos, size - pad);
            gc.strokeLine(pad, pos, size - pad, pos);
        }
        return canvas;
    }

    private VBox createSettingsPanel() {
        Label heading = new Label("01  MATCH SETUP");
        heading.getStyleClass().add("settings-heading");

        GridPane firstRow = new GridPane();
        firstRow.setHgap(12);
        firstRow.getColumnConstraints().addAll(equalColumn(), equalColumn());
        firstRow.add(createModeCard(), 0, 0);
        firstRow.add(createBoardSizeCard(), 1, 0);

        GridPane secondRow = new GridPane();
        secondRow.setHgap(12);
        secondRow.getColumnConstraints().addAll(equalColumn(), equalColumn());
        secondRow.add(blackStrategyCard, 0, 0);
        secondRow.add(redStrategyCard, 1, 0);

        HBox debugRow = new HBox(debugCheckBox);
        debugRow.getStyleClass().add("meta-row");
        debugRow.setAlignment(Pos.CENTER_LEFT);

        startButton.getStyleClass().addAll("launcher-button", "primary");
        exitButton.getStyleClass().addAll("launcher-button", "ghost");
        startButton.setMaxWidth(Double.MAX_VALUE);

        HBox buttonRow = new HBox(10, startButton, exitButton);
        buttonRow.getStyleClass().add("cta-row");
        buttonRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(startButton, Priority.ALWAYS);

        VBox panel = new VBox(12, heading, firstRow, secondRow, depthCard, debugRow, buttonRow);
        panel.getStyleClass().add("settings-panel");
        return panel;
    }

    private VBox createModeCard() {
        HBox buttons = new HBox(6, pvpButton, pvaButton, avaButton);
        buttons.getStyleClass().add("segmented-row");
        setEqualButtonGrowth(buttons);
        return createCard("Game Mode", "choose one", buttons);
    }

    private VBox createBoardSizeCard() {
        HBox buttons = new HBox(6, board13Button, board15Button, board17Button, board19Button);
        buttons.getStyleClass().add("size-row");
        setEqualButtonGrowth(buttons);
        return createCard("Board Size", boardSizeHintLabel, buttons);
    }

    private VBox createStrategyCard(String title, Label hintLabel, ToggleButton... buttons) {
        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);
        grid.getColumnConstraints().addAll(equalColumn(), equalColumn());
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].setMaxWidth(Double.MAX_VALUE);
            grid.add(buttons[i], i % 2, i / 2);
        }
        return createCard(title, hintLabel, grid);
    }

    private VBox createDepthCard() {
        Label title = new Label("AI Search Depth");
        title.getStyleClass().add("field-title");
        depthValueLabel.getStyleClass().add("depth-value");
        depthDescriptorLabel.getStyleClass().add("field-hint");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(10, title, spacer, depthDescriptorLabel, depthValueLabel);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(8, header, depthSlider);
        card.getStyleClass().add("settings-card");
        return card;
    }

    private VBox createCard(String title, String hint, javafx.scene.Node content) {
        Label hintLabel = new Label(hint);
        hintLabel.getStyleClass().add("field-hint");
        return createCard(title, hintLabel, content);
    }

    private VBox createCard(String title, Label hintLabel, javafx.scene.Node content) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("field-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        hintLabel.getStyleClass().add("field-hint");

        HBox header = new HBox(10, titleLabel, spacer, hintLabel);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(10, header, content);
        card.getStyleClass().add("settings-card");
        return card;
    }

    private ToggleButton createModeButton(String text, String detail, String dot1, String dot2) {
        HBox dots = new HBox(4, makeStoneDot(dot1), makeStoneDot(dot2));
        dots.setAlignment(Pos.CENTER);

        ToggleButton button = new ToggleButton(text + "\n" + detail);
        button.setGraphic(dots);
        button.setContentDisplay(ContentDisplay.TOP);
        button.getStyleClass().addAll("launcher-toggle", "mode-toggle");
        button.setMaxWidth(Double.MAX_VALUE);
        return button;
    }

    private Circle makeStoneDot(String type) {
        Circle c = new Circle(5);
        switch (type) {
            case "black": c.setFill(Color.web("#1a1d20")); c.setStroke(Color.web("#3a4553")); break;
            case "red":   c.setFill(Color.web("#d02a1a")); c.setStroke(Color.web("#7a1a10")); break;
            case "ai":    c.setFill(Color.web("#ef4444")); c.setStroke(Color.web("#5a1414")); break;
            default:      c.setFill(Color.web("#3a4553")); c.setStroke(Color.TRANSPARENT);    break;
        }
        c.setStrokeWidth(0.5);
        return c;
    }

    private ToggleButton createBoardSizeButton(int n, String label) {
        Canvas miniGrid = buildMiniGrid(n, 28);

        Label numLabel = new Label(String.valueOf(n));
        numLabel.setStyle(
            "-fx-text-fill:#e6edf3;-fx-font-family:'Consolas',monospace;-fx-font-size:11px;-fx-font-weight:bold;");

        Label nameLabel = new Label(label);
        nameLabel.setStyle("-fx-text-fill:#5a6776;-fx-font-size:8px;-fx-font-weight:bold;");

        VBox graphic = new VBox(2, miniGrid, numLabel, nameLabel);
        graphic.setAlignment(Pos.CENTER);

        ToggleButton button = new ToggleButton();
        button.setGraphic(graphic);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.setUserData(n);
        button.getStyleClass().addAll("launcher-toggle", "size-toggle");
        button.setMaxWidth(Double.MAX_VALUE);
        return button;
    }

    private ToggleButton createStrategyButton(String text, String code, String tag) {
        ToggleButton button = new ToggleButton(tag + "\n" + text);
        button.setUserData(code);
        button.getStyleClass().addAll("launcher-toggle", "strategy-toggle");
        button.setMaxWidth(Double.MAX_VALUE);
        return button;
    }

    private void configureToggleGroup(ToggleGroup group, ToggleButton... buttons) {
        for (ToggleButton b : buttons) b.setToggleGroup(group);
    }

    private ColumnConstraints equalColumn() {
        ColumnConstraints c = new ColumnConstraints();
        c.setPercentWidth(50);
        c.setHgrow(Priority.ALWAYS);
        return c;
    }

    private void setEqualButtonGrowth(HBox row) {
        for (javafx.scene.Node node : row.getChildren()) {
            if (node instanceof Region) ((Region) node).setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(node, Priority.ALWAYS);
        }
    }

    // --- Public API for controller ---

    /** @return the toggle group controlling PVP / PVA / AVA mode selection. */
    public ToggleGroup getModeGroup()          { return modeGroup; }

    /** @return the toggle group controlling 13 / 15 / 17 / 19 board-size selection. */
    public ToggleGroup getBoardSizeGroup()     { return boardSizeGroup; }

    /** @return the toggle group for Black's AI strategy (S1–S4). */
    public ToggleGroup getBlackStrategyGroup() { return blackStrategyGroup; }

    /** @return the toggle group for Red's AI strategy (S1–S4). */
    public ToggleGroup getRedStrategyGroup()   { return redStrategyGroup; }

    /** @return the shared AI search-depth slider (range 1–8, snaps to integers). */
    public Slider      getDepthSlider()        { return depthSlider; }

    /** @return the debug-overlay checkbox. */
    public CheckBox    getDebugCheckBox()      { return debugCheckBox; }

    /** @return the primary "Start Match" button. */
    public Button      getStartButton()        { return startButton; }

    /** @return the "Exit" button that closes the launcher window. */
    public Button      getExitButton()         { return exitButton; }

    /**
     * Updates the hint label shown next to the board-size selector (e.g. "standard 15×15").
     *
     * @param text the hint string to display
     */
    public void setBoardSizeHint(String text) {
        boardSizeHintLabel.setText(text);
    }

    /**
     * Enables or disables the strategy picker cards for Black and Red.
     * Disabled cards are also dimmed and their hint text is updated accordingly.
     *
     * @param blackEnabled {@code true} if Black's strategy card should be interactive
     * @param redEnabled   {@code true} if Red's strategy card should be interactive
     */
    public void setStrategyControlsEnabled(boolean blackEnabled, boolean redEnabled) {
        blackStrategyCard.setDisable(!blackEnabled);
        redStrategyCard.setDisable(!redEnabled);
        blackStrategyHintLabel.setText(blackEnabled ? "select" : "disabled (human)");
        redStrategyHintLabel.setText(redEnabled ? "select" : "disabled (human)");
    }

    /**
     * Shows or hides the AI search-depth card.
     * When hidden the card is also removed from the layout flow ({@code setManaged(false)})
     * so it does not occupy space. Only shown when at least one AI side uses a
     * depth-based strategy (Minimax or AlphaBeta).
     *
     * @param enabled {@code true} to show the depth card, {@code false} to hide it
     */
    public void setDepthEnabled(boolean enabled) {
        depthCard.setDisable(!enabled);
        depthCard.setVisible(enabled);
        depthCard.setManaged(enabled);
    }

    /**
     * Updates the depth value label and descriptor label inside the depth card.
     *
     * @param depth      the integer depth value (1–8) shown as "{@code N ply}"
     * @param descriptor the human-readable label for that depth (e.g. "Balanced · ~0.8s")
     */
    public void setDepthDisplay(int depth, String descriptor) {
        depthValueLabel.setText(depth + " ply");
        depthDescriptorLabel.setText(descriptor);
    }

    /**
     * Updates all four labels in the VS preview card.
     *
     * @param blackName     display name for the Black side (e.g. "Player One" or "CPU")
     * @param blackStrategy strategy label for Black (e.g. "HUMAN" or "ALPHABETA·D4")
     * @param redName       display name for the Red side
     * @param redStrategy   strategy label for Red
     */
    public void setPreview(String blackName, String blackStrategy, String redName, String redStrategy) {
        blackNameLabel.setText(blackName);
        blackStrategyLabel.setText(blackStrategy);
        redNameLabel.setText(redName);
        redStrategyLabel.setText(redStrategy);
    }

    /**
     * Updates the one-line footer summary shown at the bottom of the hero column
     * (e.g. "PVA · 15×15 · ALPHABETA·D4").
     *
     * @param summary the formatted summary string produced by the controller
     */
    public void setSummary(String summary) {
        summaryLabel.setText(summary);
    }

    /**
     * Updates the emoji avatar displayed inside each player's stone circle in the VS card.
     * The controller calls this on every refresh to swap between the dragon (human) and
     * robot (AI) icons depending on the selected mode.
     *
     * @param p1Emoji emoji for the Black / P1 stone (e.g. "🐉" or "🤖")
     * @param p2Emoji emoji for the Red / P2 stone
     */
    public void setPlayerIcons(String p1Emoji, String p2Emoji) {
        p1IconLabel.setText(p1Emoji);
        p2IconLabel.setText(p2Emoji);
    }
}
