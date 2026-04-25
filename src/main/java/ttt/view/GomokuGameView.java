package ttt.view;

import ttt.controller.GomokuControllerFX;

import ttt.ai.IGameKI;
import ttt.model.Pair;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

/**
 * GomokuGameView is the main game window layout using a BorderPane.
 * <p>
 * TOP â†’ Toolbar with Restart, Undo, Hint, Quit + title
 * CENTER â†’ GomokuBoardFX canvas (responsive)
 * RIGHT â†’ Sidebar with game status, mode, info cards + Save/Load
 * BOTTOM â†’ Status bar
 * <p>
 * All button actions delegate to GomokuControllerFX.
 * The controller calls back via public setter methods to update labels.
 */
public class GomokuGameView extends BorderPane {

    private final GomokuControllerFX controller;
    private final GomokuBoardFX boardFX;

    // Dynamic labels updated by the controller
    private Label turnLabel;
    private Label blackScoreLabel;
    private Label redScoreLabel;
    private Label lastMoveLabel;
    private Label aiThinkLabel;
    private Label playerTimeLabel;
    private Label statusLabel;
    private Button undoButton;

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

        getStyleClass().add("root");

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
        toolbar.setPadding(new Insets(8, 14, 8, 14));

        Button restartBtn = createToolbarButton("\uD83D\uDD04 Restart", "btn-restart");
        restartBtn.setOnAction(e -> controller.restartGame());

        undoButton = createToolbarButton("\u21A9 Undo", "btn-undo");
        undoButton.setOnAction(e -> controller.undoTwoMoves());

        Button hintBtn = createToolbarButton("\uD83D\uDCA1 Hint", "btn-hint");
        hintBtn.setOnAction(e -> controller.computeHint());

        Button quitBtn = createToolbarButton("\u274C Quit", "btn-quit");
        quitBtn.setOnAction(e -> controller.quitToLauncher());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label title = new Label("\uD83C\uDFAF GOMOKU");
        title.getStyleClass().add("title-label");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 26));
        title.setEffect(new DropShadow(12, Color.web("#FFD70080")));

        toolbar.getChildren().addAll(restartBtn, undoButton, hintBtn, quitBtn, spacer, title);
        return toolbar;
    }

    private Button createToolbarButton(String text, String styleClass) {
        Button btn = new Button(text);
        btn.getStyleClass().addAll("styled-button", styleClass);
        return btn;
    }

    // ==================== CENTER BOARD ====================

    private Pane createCenterBoard() {
        StackPane boardWrapper = new StackPane(boardFX);
        boardWrapper.setStyle("-fx-background-color: #1e1e28;");
        boardWrapper.setPadding(new Insets(12));

        // Bind canvas to fill available space
        boardFX.widthProperty().bind(boardWrapper.widthProperty().subtract(24));
        boardFX.heightProperty().bind(boardWrapper.heightProperty().subtract(24));

        return boardWrapper;
    }

    // ==================== SIDEBAR ====================

    private VBox createSidebar() {
        VBox sidebar = new VBox(15);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(260);
        sidebar.setPadding(new Insets(15));

        // ---- Game Status card ----
        blackScoreLabel = createValueLabel("0");
        redScoreLabel = createValueLabel("0");
        turnLabel = createValueLabel("\u26AB Black");

        VBox statusCard = createCard("\uD83D\uDCCA Game Status",
                new String[] { "Black Score:", "Red Score:", "Turn:" },
                new Label[] { blackScoreLabel, redScoreLabel, turnLabel });

        // ---- Game Mode card ----
        String mode = controller.getMode();
        String s1Name = getStrategyName(controller.getAI1());
        String s2Name = getStrategyName(controller.getAI2());
        if ("PP".equals(mode)) {
            s1Name = "Player 1";
            s2Name = "Player 2";
        } else if ("PC".equals(mode)) {
            s2Name = "Player";
        }

        VBox modeCard = createCard("\uD83C\uDFAE Game Mode",
                new String[] { "Mode:", "Black:", "Red:" },
                new Label[] { createValueLabel(mode), createValueLabel(s1Name), createValueLabel(s2Name) });

        // ---- Game Info card ----
        lastMoveLabel = createValueLabel("-");
        aiThinkLabel = createValueLabel("-");
        playerTimeLabel = createValueLabel("-");

        VBox infoCard = createCard("\u23F1 Game Info",
                new String[] { "Last Move:", "AI Think:", "Your Time:" },
                new Label[] { lastMoveLabel, aiThinkLabel, playerTimeLabel });

        // ---- Save / Load buttons ----
        Button saveBtn = new Button("\uD83D\uDCBE Save Game");
        saveBtn.getStyleClass().addAll("styled-button", "btn-save");
        saveBtn.setMaxWidth(Double.MAX_VALUE);
        saveBtn.setOnAction(e -> controller.saveGame());

        Button loadBtn = new Button("\uD83D\uDCC2 Load Game");
        loadBtn.getStyleClass().addAll("styled-button", "btn-load");
        loadBtn.setMaxWidth(Double.MAX_VALUE);
        loadBtn.setOnAction(e -> controller.loadGame());

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        sidebar.getChildren().addAll(statusCard, modeCard, infoCard, spacer, saveBtn, loadBtn);
        return sidebar;
    }

    /**
     * Creates a card panel with a title and key-value rows.
     */
    private VBox createCard(String title, String[] keys, Label[] values) {
        VBox card = new VBox(8);
        card.getStyleClass().add("card-panel");
        card.setPadding(new Insets(12));

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("card-title");
        card.getChildren().add(titleLabel);

        for (int i = 0; i < keys.length; i++) {
            Label keyLabel = new Label(keys[i]);
            keyLabel.getStyleClass().add("card-label");
            keyLabel.setMinWidth(95);

            Region fill = new Region();
            HBox.setHgrow(fill, Priority.ALWAYS);

            HBox row = new HBox(keyLabel, fill, values[i]);
            row.setAlignment(Pos.CENTER_LEFT);
            card.getChildren().add(row);
        }
        return card;
    }

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
                + "\u00D7" + controller.getGame().getCols());
        boardSizeLabel.getStyleClass().add("status-text");

        statusBar.getChildren().addAll(statusLabel, spacer, boardSizeLabel);
        return statusBar;
    }

    // ==================== PUBLIC SETTERS (called by controller)
    // ====================

    public void setTurnText(String text) {
        turnLabel.setText(text);
    }

    public void setBlackScore(String text) {
        blackScoreLabel.setText(text);
    }

    public void setRedScore(String text) {
        redScoreLabel.setText(text);
    }

    public void setLastMove(String text) {
        lastMoveLabel.setText(text);
    }

    public void setAIThinkTime(String text) {
        aiThinkLabel.setText(text);
    }

    public void setPlayerTime(String text) {
        playerTimeLabel.setText(text);
    }

    public void setStatus(String text) {
        statusLabel.setText(text);
    }

    public void setUndoEnabled(boolean enabled) {
        undoButton.setDisable(!enabled);
    }

    // ==================== HELPERS ====================

    private String getStrategyName(IGameKI<?> ai) {
        if (ai == null)
            return "Player";
        return ai.getClass().getSimpleName().replace("Strategy", "");
    }
}
