package ttt.view;

import ttt.controller.GomokuControllerFX;

import ttt.model.Gomoku;
import ttt.util.Debugger;
import ttt.ai.IGameKI;
import ttt.model.IRegularGame;
import ttt.model.Pair;
import ttt.ai.StrategyAlphaBeta;
import ttt.ai.StrategyBlock;
import ttt.ai.StrategyMinimax;
import ttt.ai.StrategyRandom;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Glow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * GomokuLauncherFX is the JavaFX start screen for configuring and launching a
 * Gomoku game.
 * <p>
 * Allows the user to select game mode (PP, PC, CC), AI strategies (S1â€“S4),
 * and debug mode. Pressing Start creates a GomokuControllerFX which transitions
 * the stage to the game view.
 */
public class GomokuLauncherFX extends VBox {

    private final Stage stage;
    private ComboBox<String> modeBox;
    private ComboBox<String> strat1Box;
    private ComboBox<String> strat2Box;
    private CheckBox debugBox;

    /**
     * Constructs the launcher UI and attaches it to the given stage.
     *
     * @param stage The primary application stage.
     */
    public GomokuLauncherFX(Stage stage) {
        this.stage = stage;
        getStyleClass().add("launcher-root");
        setAlignment(Pos.TOP_CENTER);
        setSpacing(22);
        setPadding(new Insets(36, 32, 32, 32));
        initUI();
    }

    private void initUI() {
        // ---- Logo ----
        Label logo = new Label("\uD83C\uDFAF GOMOKU");
        logo.setFont(Font.font("Segoe UI", FontWeight.BOLD, 44));
        logo.setTextFill(Color.web("#FFD700"));
        DropShadow logoShadow = new DropShadow(20, Color.web("#FFD70088"));
        logoShadow.setInput(new Glow(0.3));
        logo.setEffect(logoShadow);

        Label subtitle = new Label("Classic Five-in-a-Row \u2022 Board Game");
        subtitle.setFont(Font.font("Segoe UI", 15));
        subtitle.setTextFill(Color.web("#9898b4"));

        VBox titleBox = new VBox(8, logo, subtitle);
        titleBox.setAlignment(Pos.CENTER);
        titleBox.setPadding(new Insets(0, 0, 8, 0));

        // ---- Settings Card ----
        VBox settingsCard = new VBox(14);
        settingsCard.getStyleClass().add("card-panel");
        settingsCard.setPadding(new Insets(18));

        Label settingsTitle = new Label("\u2699 Game Settings");
        settingsTitle.getStyleClass().add("card-title");
        settingsTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 17));

        // Mode
        modeBox = new ComboBox<>();
        modeBox.getItems().addAll("PP", "PC", "CC");
        modeBox.setValue("PP");
        modeBox.setMaxWidth(Double.MAX_VALUE);
        HBox modeRow = createFormRow("\uD83C\uDFAE Game Mode:", modeBox);

        // Strategy 1
        strat1Box = new ComboBox<>();
        strat1Box.getItems().addAll("S1 Random", "S2 Block", "S3 Minimax", "S4 AlphaBeta");
        strat1Box.setValue("S1 Random");
        strat1Box.setMaxWidth(Double.MAX_VALUE);
        strat1Box.setDisable(true);
        HBox strat1Row = createFormRow("\uD83E\uDD16 AI Strategy 1:", strat1Box);

        // Strategy 2
        strat2Box = new ComboBox<>();
        strat2Box.getItems().addAll("S1 Random", "S2 Block", "S3 Minimax", "S4 AlphaBeta");
        strat2Box.setValue("S2 Block");
        strat2Box.setMaxWidth(Double.MAX_VALUE);
        strat2Box.setDisable(true);
        HBox strat2Row = createFormRow("\uD83E\uDD16 AI Strategy 2:", strat2Box);

        // Debug
        debugBox = new CheckBox("Enable Debug Mode");
        debugBox.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        HBox debugRow = new HBox(debugBox);
        debugRow.setPadding(new Insets(4, 0, 0, 0));

        settingsCard.getChildren().addAll(settingsTitle, modeRow, strat1Row, strat2Row, debugRow);

        // Mode change logic
        modeBox.setOnAction(e -> {
            String mode = modeBox.getValue();
            strat1Box.setDisable("PP".equals(mode));
            strat2Box.setDisable(!"CC".equals(mode));
        });

        // ---- Buttons ----
        Button startBtn = new Button("\uD83D\uDE80 Start Game");
        startBtn.getStyleClass().addAll("styled-button", "btn-restart");
        startBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        startBtn.setPrefSize(190, 52);
        startBtn.setOnAction(e -> onStartGame());

        Button exitBtn = new Button("\u274C Exit");
        exitBtn.getStyleClass().addAll("styled-button", "btn-quit");
        exitBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        exitBtn.setPrefSize(130, 52);
        exitBtn.setOnAction(e -> stage.close());

        HBox buttonRow = new HBox(22, startBtn, exitBtn);
        buttonRow.setAlignment(Pos.CENTER);
        buttonRow.setPadding(new Insets(12, 0, 0, 0));

        getChildren().addAll(titleBox, settingsCard, buttonRow);
    }

    private HBox createFormRow(String labelText, ComboBox<?> combo) {
        Label label = new Label(labelText);
        label.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        label.setTextFill(Color.web("#dcdcf0"));
        label.setMinWidth(170);
        HBox row = new HBox(12, label, combo);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(combo, Priority.ALWAYS);
        return row;
    }

    /**
     * Handles the Start Game button: reads settings, initializes Debugger,
     * creates the game and controller, which switches the scene.
     */
    private void onStartGame() {
        String mode = modeBox.getValue();
        String strat1Code = strat1Box.getValue().split(" ")[0];
        String strat2Code = strat2Box.getValue().split(" ")[0];
        boolean debug = debugBox.isSelected();

        // Validate strategies based on mode
        if ("PP".equals(mode)) {
            strat1Code = null;
            strat2Code = null;
        }
        if ("PC".equals(mode)) {
            strat2Code = null;
        }

        try {
            Debugger.init(debug);
        } catch (IOException ex) {
            System.err.println("[ERROR] Debug init failed: " + ex.getMessage());
        }

        Gomoku game = new Gomoku();
        IGameKI<Pair<Byte, Byte>> ai1 = createStrategy(strat1Code);
        IGameKI<Pair<Byte, Byte>> ai2 = createStrategy(strat2Code);

        // Controller creates the game view and switches the scene
        new GomokuControllerFX(game, ai1, ai2, mode, stage);
    }

    /**
     * Factory method to create an AI strategy from a code string.
     *
     * @param code Strategy code (S1â€“S4), or null for no AI.
     * @return The IGameKI instance, or null if code is null.
     */
    public static IGameKI<Pair<Byte, Byte>> createStrategy(String code) {
        if (code == null)
            return null;
        switch (code) {
            case "S2":
                return new StrategyBlock<>();
            case "S3":
                return new StrategyMinimax<>();
            case "S4":
                return new StrategyAlphaBeta<>();
            default:
                return new StrategyRandom<>();
        }
    }
}
