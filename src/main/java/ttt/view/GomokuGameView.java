package ttt.view;

import ttt.controller.GomokuControllerFX;

import ttt.ai.IGameKI;
import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * GomokuGameView is the main game window layout using a BorderPane.
 * <p>
 * TOP → Toolbar with Restart, Undo, Hint, Quit + title
 * CENTER → GomokuBoardFX canvas (responsive)
 * RIGHT → Sidebar with game status, mode, info cards + Save/Load
 * BOTTOM → Status bar
 * <p>
 * All button actions delegate to GomokuControllerFX.
 * The controller calls back via public setter methods to update labels.
 */
public class GomokuGameView extends BorderPane {

    /** Pseudo-class toggled on the turn badge so CSS can tint it red on Red's turn. */
    private static final javafx.css.PseudoClass RED_TURN =
            javafx.css.PseudoClass.getPseudoClass("red-turn");

    private final GomokuControllerFX controller;
    private final GomokuBoardFX boardFX;

    // Dynamic labels updated by the controller
    private Label blackScoreLabel;
    private Label redScoreLabel;
    private Label lastMoveLabel;
    private Label aiThinkLabel;
    private Label playerTimeLabel;
    private Label moveCountLabel;
    private Label elapsedLabel;
    private Label statusLabel;
    private Button undoButton;

    // Turn badge (prominent "to move" indicator)
    private Circle turnDot;
    private Label turnBadgeLabel;
    private HBox turnBadge;

    // Scrollable move history
    private ListView<String> moveListView;

    /**
     * Constructs the game view with all UI regions.
     *
     * @param controller The game controller for action delegation.
     * @param boardFX    The canvas board renderer.
     * @param stage      The primary stage (for window references).
     */
    public GomokuGameView(GomokuControllerFX controller, GomokuBoardFX boardFX, Stage stage) {
        this.controller = controller;
        this.boardFX = boardFX;

        getStyleClass().addAll("root", "gomoku-start-stage");

        setTop(createToolbar());
        setCenter(createCenterBoard());
        setRight(createSidebar());
        setBottom(createStatusBar());
    }

    // ==================== TOOLBAR ====================

    private HBox createToolbar() {
        HBox toolbar = new HBox(12);
        toolbar.getStyleClass().add("toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(10, 14, 10, 14));

        // Eyebrow: pulsing gold dot + section label
        Circle pulseDot = createPulseDot();
        Label eyebrow = new Label("IN GAME");
        eyebrow.getStyleClass().add("launcher-eyebrow");
        HBox eyebrowBox = new HBox(7, pulseDot, eyebrow);
        eyebrowBox.setAlignment(Pos.CENTER);

        Button restartBtn = createToolbarButton("🔄 Restart", "btn-restart");
        restartBtn.setOnAction(e -> controller.restartGame());
        restartBtn.setTooltip(new Tooltip("Start a fresh board  (R)"));

        undoButton = createToolbarButton("↩ Undo", "btn-undo");
        undoButton.setOnAction(e -> controller.undoTwoMoves());
        undoButton.setTooltip(new Tooltip("Take back your last move  (Ctrl+Z)"));

        Button hintBtn = createToolbarButton("💡 Hint", "btn-hint");
        hintBtn.setOnAction(e -> controller.computeHint());
        hintBtn.setTooltip(new Tooltip("Suggest a strong move  (H)"));

        Button quitBtn = createToolbarButton("❌ Quit", "btn-quit");
        quitBtn.setOnAction(e -> controller.quitToLauncher());
        quitBtn.setTooltip(new Tooltip("Return to the start screen  (Esc)"));

        ComboBox<ThemeManager.Theme> themeSelector = ThemeManager.getInstance().createThemeSelector();

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label title = new Label("🎯 GOMOKU");
        title.getStyleClass().add("title-label");

        toolbar.getChildren().addAll(eyebrowBox, restartBtn, undoButton, hintBtn, quitBtn, themeSelector, spacer, title);
        return toolbar;
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

    private Button createToolbarButton(String text, String styleClass) {
        Button btn = new Button(text);
        btn.getStyleClass().addAll("styled-button", styleClass);
        return btn;
    }

    // ==================== CENTER BOARD ====================

    private Pane createCenterBoard() {
        StackPane boardWrapper = new StackPane(boardFX);
        boardWrapper.getStyleClass().add("board-wrap");
        boardWrapper.setPadding(new Insets(12));
        boardWrapper.setMaxWidth(Double.MAX_VALUE);
        boardWrapper.setMaxHeight(Double.MAX_VALUE);

        return boardWrapper;
    }

    // ==================== SIDEBAR ====================

    private VBox createSidebar() {
        VBox sidebar = new VBox(12);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(260);
        sidebar.setPadding(new Insets(15));

        // Section heading
        Label heading = new Label("02  GAME STATUS");
        heading.getStyleClass().add("settings-heading");

        // ---- Prominent "to move" badge ----
        VBox turnBox = createTurnBadge();

        // ---- Score card with vs row ----
        blackScoreLabel = createValueLabel("0");
        redScoreLabel = createValueLabel("0");
        VBox scoreCard = createScoreCard();

        // ---- Game Mode card ----
        String mode = controller.getMode();
        String s1Name = getStrategyName(controller.getAI1());
        String s2Name = getStrategyName(controller.getAI2());
        if ("PP".equals(mode)) {
            s1Name = "Player 1";
            s2Name = "Player 2";
        } else if ("PC".equals(mode)) {
            s1Name = "Player";
            s2Name = getStrategyName(controller.getAI1());
        }

        VBox modeCard = createCard("🎮 Game Mode",
                new String[] { "Mode:", "Black:", "Red:" },
                new Label[] { createValueLabel(modeLabel(mode)), createValueLabel(s1Name), createValueLabel(s2Name) });

        // ---- Game Info card ----
        moveCountLabel = createValueLabel("0");
        elapsedLabel = createValueLabel("00:00");
        lastMoveLabel = createValueLabel("-");
        aiThinkLabel = createValueLabel("-");
        playerTimeLabel = createValueLabel("-");

        VBox infoCard = createCard("⏱ Game Info",
                new String[] { "Moves:", "Elapsed:", "Last Move:", "AI Think:", "Your Time:" },
                new Label[] { moveCountLabel, elapsedLabel, lastMoveLabel, aiThinkLabel, playerTimeLabel });

        // ---- Move history (scrollable, grows to fill) ----
        VBox historyCard = createHistoryCard();
        VBox.setVgrow(historyCard, Priority.ALWAYS);

        // ---- Save / Load buttons ----
        Button saveBtn = new Button("💾 Save Game");
        saveBtn.getStyleClass().addAll("styled-button", "btn-save");
        saveBtn.setMaxWidth(Double.MAX_VALUE);
        saveBtn.setTooltip(new Tooltip("Save the current game  (Ctrl+S)"));
        saveBtn.setOnAction(e -> controller.saveGame());

        Button loadBtn = new Button("📂 Load Game");
        loadBtn.getStyleClass().addAll("styled-button", "btn-load");
        loadBtn.setMaxWidth(Double.MAX_VALUE);
        loadBtn.setTooltip(new Tooltip("Load a saved game  (Ctrl+L)"));
        loadBtn.setOnAction(e -> controller.loadGame());

        HBox fileRow = new HBox(8, saveBtn, loadBtn);
        HBox.setHgrow(saveBtn, Priority.ALWAYS);
        HBox.setHgrow(loadBtn, Priority.ALWAYS);

        sidebar.getChildren().addAll(heading, turnBox, scoreCard, modeCard, infoCard, historyCard, fileRow);
        return sidebar;
    }

    /**
     * Builds the prominent turn badge: a stone-colored dot plus a "to move" label.
     * Updated via {@link #setTurn(boolean)}.
     */
    private VBox createTurnBadge() {
        turnDot = new Circle(7, Color.web("#1a1d20"));
        turnDot.setStroke(Color.web("#3a4553"));
        turnDot.setStrokeWidth(1);

        turnBadgeLabel = new Label("Black to move");
        turnBadgeLabel.getStyleClass().add("turn-badge-text");

        turnBadge = new HBox(10, turnDot, turnBadgeLabel);
        turnBadge.setAlignment(Pos.CENTER_LEFT);
        turnBadge.getStyleClass().add("turn-badge");
        turnBadge.setMaxWidth(Double.MAX_VALUE);

        VBox box = new VBox(turnBadge);
        return box;
    }

    /** Score card showing both running win counters side by side. */
    private VBox createScoreCard() {
        VBox card = new VBox(8);
        card.getStyleClass().add("card-panel");
        card.setPadding(new Insets(11));

        Label titleLabel = new Label("🏆 Score");
        titleLabel.getStyleClass().add("card-title");

        Label blackKey = new Label("⚫ Black");
        blackKey.getStyleClass().add("card-label");
        Label redKey = new Label("🔴 Red");
        redKey.getStyleClass().add("card-label");

        VBox blackCol = new VBox(2, blackKey, blackScoreLabel);
        blackCol.setAlignment(Pos.CENTER);
        VBox redCol = new VBox(2, redKey, redScoreLabel);
        redCol.setAlignment(Pos.CENTER);
        HBox.setHgrow(blackCol, Priority.ALWAYS);
        HBox.setHgrow(redCol, Priority.ALWAYS);
        blackCol.setMaxWidth(Double.MAX_VALUE);
        redCol.setMaxWidth(Double.MAX_VALUE);

        Label dash = new Label("–");
        dash.getStyleClass().add("card-label");

        HBox row = new HBox(8, blackCol, dash, redCol);
        row.setAlignment(Pos.CENTER);

        card.getChildren().addAll(titleLabel, row);
        return card;
    }

    /** Move-history card wrapping a styled, scrollable list. */
    private VBox createHistoryCard() {
        VBox card = new VBox(8);
        card.getStyleClass().add("card-panel");
        card.setPadding(new Insets(11));

        Label titleLabel = new Label("📜 Move History");
        titleLabel.getStyleClass().add("card-title");

        moveListView = new ListView<>();
        moveListView.getStyleClass().add("move-list");
        moveListView.setFocusTraversable(false);
        moveListView.setPlaceholder(placeholderLabel("No moves yet"));
        VBox.setVgrow(moveListView, Priority.ALWAYS);

        card.getChildren().addAll(titleLabel, moveListView);
        return card;
    }

    /** Builds the muted placeholder shown when the move-history list is empty. */
    private Label placeholderLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("card-label");
        return label;
    }

    /** Maps the internal mode code to a friendly label. */
    private String modeLabel(String mode) {
        switch (mode) {
            case "PP": return "Human vs Human";
            case "PC": return "Human vs AI";
            case "CC": return "AI vs AI";
            case "PN": return "Network";
            default:   return mode;
        }
    }

    /**
     * Creates a card panel with a title and key-value rows.
     */
    private VBox createCard(String title, String[] keys, Label[] values) {
        VBox card = new VBox(8);
        card.getStyleClass().add("card-panel");
        card.setPadding(new Insets(11));

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("card-title");
        card.getChildren().add(titleLabel);

        for (int i = 0; i < keys.length; i++) {
            Label keyLabel = new Label(keys[i]);
            keyLabel.getStyleClass().add("card-label");
            keyLabel.setMinWidth(90);

            Region fill = new Region();
            HBox.setHgrow(fill, Priority.ALWAYS);

            HBox row = new HBox(keyLabel, fill, values[i]);
            row.setAlignment(Pos.CENTER_LEFT);
            card.getChildren().add(row);
        }
        return card;
    }

    /** Creates a value-styled label for the right-hand side of a card key/value row. */
    private Label createValueLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("card-value");
        return label;
    }

    // ==================== STATUS BAR ====================

    private HBox createStatusBar() {
        HBox statusBar = new HBox();
        statusBar.getStyleClass().add("status-bar");
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(5, 12, 5, 12));

        statusLabel = new Label("Ready to play");
        statusLabel.getStyleClass().add("status-text");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label boardSizeLabel = new Label("Board: " + controller.getGame().getRows()
                + "×" + controller.getGame().getCols());
        boardSizeLabel.getStyleClass().add("status-text");

        statusBar.getChildren().addAll(statusLabel, spacer, boardSizeLabel);
        return statusBar;
    }

    // ==================== PUBLIC SETTERS (called by controller) ====================

    /**
     * Updates the prominent turn badge to reflect whose move it is.
     *
     * @param isBlack {@code true} if it is Black's turn, {@code false} for Red
     */
    public void setTurn(boolean isBlack) {
        if (isBlack) {
            turnDot.setFill(Color.web("#1a1d20"));
            turnDot.setStroke(Color.web("#3a4553"));
            turnBadgeLabel.setText("Black to move");
        } else {
            turnDot.setFill(Color.web("#d02a1a"));
            turnDot.setStroke(Color.web("#7a1a10"));
            turnBadgeLabel.setText("Red to move");
        }
        turnBadge.pseudoClassStateChanged(RED_TURN, !isBlack);
    }

    /** Shows an end-of-game message in the turn badge (e.g. "Black wins!"). */
    public void setTurnResult(String text) {
        turnBadgeLabel.setText(text);
    }

    /** Updates the total move counter in the info card. */
    public void setMoveCount(int count) {
        moveCountLabel.setText(String.valueOf(count));
    }

    /** Updates the elapsed-time readout (formatted MM:SS). */
    public void setElapsed(String text) {
        elapsedLabel.setText(text);
    }

    /**
     * Appends a move to the history list and scrolls it into view.
     *
     * @param number  the 1-based move number
     * @param isBlack whether Black made the move
     * @param coord   the coordinate label (e.g. "8,8")
     */
    public void addMove(int number, boolean isBlack, String coord) {
        String entry = String.format("%2d.  %s  %s", number, isBlack ? "⚫" : "🔴", coord);
        moveListView.getItems().add(entry);
        moveListView.scrollTo(moveListView.getItems().size() - 1);
    }

    /** Clears the move history list (used on restart / load). */
    public void clearHistory() {
        moveListView.getItems().clear();
    }

    /**
     * Updates Black's running win count in the score card.
     *
     * @param text the score to display
     */
    public void setBlackScore(String text) {
        blackScoreLabel.setText(text);
    }

    /**
     * Updates Red's running win count in the score card.
     *
     * @param text the score to display
     */
    public void setRedScore(String text) {
        redScoreLabel.setText(text);
    }

    /**
     * Updates the "Last Move" readout in the info card.
     *
     * @param text the coordinate label (e.g. "H8") or a status word
     */
    public void setLastMove(String text) {
        lastMoveLabel.setText(text);
    }

    /**
     * Updates the AI thinking-time readout in the info card.
     *
     * @param text the formatted duration (e.g. "0.123 s")
     */
    public void setAIThinkTime(String text) {
        aiThinkLabel.setText(text);
    }

    /**
     * Updates the human player's thinking-time readout in the info card.
     *
     * @param text the formatted duration (e.g. "1.5 s")
     */
    public void setPlayerTime(String text) {
        playerTimeLabel.setText(text);
    }

    /**
     * Updates the message shown in the bottom status bar.
     *
     * @param text the status message
     */
    public void setStatus(String text) {
        statusLabel.setText(text);
    }

    /**
     * Enables or disables the toolbar Undo button.
     *
     * @param enabled {@code true} to enable Undo, {@code false} to disable it
     */
    public void setUndoEnabled(boolean enabled) {
        undoButton.setDisable(!enabled);
    }

    // ==================== HELPERS ====================

    /**
     * Returns a short display name for an AI strategy (its class name without the
     * {@code "Strategy"} prefix), or {@code "Player"} for a human side.
     *
     * @param ai the strategy instance, or {@code null} for a human player
     * @return the display name
     */
    private String getStrategyName(IGameKI<?> ai) {
        if (ai == null)
            return "Player";
        return ai.getClass().getSimpleName().replace("Strategy", "");
    }
}
