package ttt.view;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import javafx.util.StringConverter;

/**
 * Central, application-wide theme controller supporting multiple visual themes.
 *
 * <p>The CSS palette is expressed entirely through JavaFX looked-up color tokens
 * (the {@code -c-*} tokens in {@code style.css}). Each {@link Theme} owns one
 * token block keyed by its {@link Theme#cssClass}. Switching themes only swaps a
 * single style class on the scene root, so every styled descendant re-resolves
 * its colors automatically.
 *
 * <p>The one surface CSS cannot reach — the {@link GomokuBoardFX} canvas — reads
 * the per-theme {@link BoardPalette} returned by {@link #boardPalette()} and
 * repaints whenever the theme changes.
 *
 * <p>The selected theme is persisted to a small file in the user's home
 * directory so it survives across application launches.
 */
public final class ThemeManager {

    /**
     * The supported visual themes. Each carries a human-readable display name
     * (shown in the picker) and the CSS class that activates its token block.
     */
    public enum Theme {
        DARK("Dark", "theme-dark"),
        LIGHT("Light", "theme-light"),
        BRUTALIST("Neo-Brutalist", "theme-brutalist"),
        AURORA("Aurora Glass", "theme-aurora"),
        GOBAN("Zen Goban", "theme-goban"),
        NEON("Neon Grid", "theme-neon"),
        MATERIAL("Material", "theme-material");

        private final String displayName;
        private final String cssClass;

        Theme(String displayName, String cssClass) {
            this.displayName = displayName;
            this.cssClass = cssClass;
        }

        /** @return the label shown to the user in the theme picker. */
        public String getDisplayName() {
            return displayName;
        }

        /** @return the CSS style class that activates this theme's token block. */
        public String getCssClass() {
            return cssClass;
        }
    }

    private static final ThemeManager INSTANCE = new ThemeManager();
    private static final Path PREF_FILE =
            Paths.get(System.getProperty("user.home", "."), ".gomokufx_theme");

    private final ObjectProperty<Theme> theme;

    private ThemeManager() {
        theme = new SimpleObjectProperty<>(loadPersistedTheme());
    }

    private static Theme loadPersistedTheme() {
        try {
            if (Files.exists(PREF_FILE)) {
                String stored = new String(Files.readAllBytes(PREF_FILE), StandardCharsets.UTF_8).trim();
                return Theme.valueOf(stored);
            }
        } catch (IOException | RuntimeException ignored) {
            // Missing/corrupt file — fall back to DARK.
        }
        return Theme.DARK;
    }

    /** @return the shared singleton instance. */
    public static ThemeManager getInstance() {
        return INSTANCE;
    }

    /** @return the observable theme property (used by the board to repaint on change). */
    public ObjectProperty<Theme> themeProperty() {
        return theme;
    }

    /** @return the currently active theme. */
    public Theme getTheme() {
        return theme.get();
    }

    /** @return {@code true} if the active theme is a dark-ish theme (kept for compatibility). */
    public boolean isDark() {
        return theme.get() == Theme.DARK;
    }

    /**
     * Switches to the given theme and persists the choice.
     *
     * @param t the theme to activate
     */
    public void setTheme(Theme t) {
        if (t == null || t == theme.get()) {
            return;
        }
        theme.set(t);
        try {
            Files.write(PREF_FILE, t.name().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // Persistence is best-effort; ignore write failures.
        }
    }

    /** Advances to the next theme in declaration order, wrapping around. */
    public void cycle() {
        Theme[] all = Theme.values();
        setTheme(all[(theme.get().ordinal() + 1) % all.length]);
    }

    /**
     * Binds a scene root to the current theme. Applies the correct theme style
     * class immediately and keeps it in sync whenever the theme changes.
     *
     * @param root the scene root node to theme
     */
    public void register(Parent root) {
        applyClass(root);
        theme.addListener((obs, oldTheme, newTheme) -> applyClass(root));
    }

    private void applyClass(Parent root) {
        for (Theme t : Theme.values()) {
            root.getStyleClass().remove(t.getCssClass());
        }
        root.getStyleClass().add(theme.get().getCssClass());
    }

    /**
     * Builds a theme picker drop-down wired bidirectionally to the active theme.
     * The same factory is used on both the launcher and the in-game toolbar, so
     * the control's font, sizing, and alignment match across screens.
     *
     * @return a {@link ComboBox} that selects the theme and reflects external changes
     */
    public ComboBox<Theme> createThemeSelector() {
        ComboBox<Theme> selector = new ComboBox<>();
        selector.getItems().setAll(Theme.values());
        selector.getStyleClass().add("theme-combo");
        selector.setValue(theme.get());
        selector.setFocusTraversable(false);
        selector.setTooltip(new Tooltip("Choose a visual theme  (T to cycle)"));
        selector.getTooltip().setShowDelay(Duration.millis(400));

        selector.setConverter(new StringConverter<Theme>() {
            @Override public String toString(Theme t) { return t == null ? "" : t.getDisplayName(); }
            @Override public Theme fromString(String s) { return null; }
        });

        // View -> model
        selector.valueProperty().addListener((obs, oldVal, newVal) -> setTheme(newVal));
        // Model -> view (keeps both pickers and the keyboard shortcut in sync)
        theme.addListener((obs, oldVal, newVal) -> {
            if (selector.getValue() != newVal) {
                selector.setValue(newVal);
            }
        });
        return selector;
    }

    /** @return the board-rendering palette for the currently active theme. */
    BoardPalette boardPalette() {
        switch (theme.get()) {
            case LIGHT:     return BoardPalette.LIGHT;
            case BRUTALIST: return BoardPalette.BRUTALIST;
            case AURORA:    return BoardPalette.AURORA;
            case GOBAN:     return BoardPalette.GOBAN;
            case NEON:      return BoardPalette.NEON;
            case MATERIAL:  return BoardPalette.MATERIAL;
            case DARK:
            default:        return BoardPalette.DARK;
        }
    }

    /**
     * Immutable bundle of the colors the canvas board needs for one theme:
     * the surrounding surface, the board face, grid/star lines, the two players'
     * stone gradients, the stone outline, and whether stones should glow.
     */
    static final class BoardPalette {
        final Color bg;            // canvas surface behind the board
        final Color boardTop;      // board face gradient (top)
        final Color boardBottom;   // board face gradient (bottom)
        final Color boardBorder;
        final double boardBorderWidth;
        final Color grid;
        final Color star;
        final Color[] p1;          // player-1 stone radial stops (3)
        final Color[] p2;          // player-2 stone radial stops (3)
        final Color stoneStroke;
        final double stoneStrokeWidth;
        final boolean glow;        // soft glow around stones

        private BoardPalette(Color bg, Color boardTop, Color boardBottom, Color boardBorder, double boardBorderWidth,
                             Color grid, Color star, Color[] p1, Color[] p2,
                             Color stoneStroke, double stoneStrokeWidth, boolean glow) {
            this.bg = bg;
            this.boardTop = boardTop;
            this.boardBottom = boardBottom;
            this.boardBorder = boardBorder;
            this.boardBorderWidth = boardBorderWidth;
            this.grid = grid;
            this.star = star;
            this.p1 = p1;
            this.p2 = p2;
            this.stoneStroke = stoneStroke;
            this.stoneStrokeWidth = stoneStrokeWidth;
            this.glow = glow;
        }

        private static Color[] stops(String a, String b, String c) {
            return new Color[] { Color.web(a), Color.web(b), Color.web(c) };
        }

        static final BoardPalette DARK = new BoardPalette(
                Color.web("#0d1218"), Color.web("#DEB887"), Color.web("#DEB887"), Color.web("#8B4513"), 5,
                Color.web("#222222"), Color.web("#222222"),
                stops("#606060", "#2a2a2a", "#0a0a0a"), stops("#FF7777", "#DD2222", "#AA0000"),
                Color.web("#1a1a1a"), 1.2, false);

        static final BoardPalette LIGHT = new BoardPalette(
                Color.web("#dde3ea"), Color.web("#E8C99A"), Color.web("#DEB887"), Color.web("#8B4513"), 5,
                Color.web("#3a3a3a"), Color.web("#3a3a3a"),
                stops("#606060", "#2a2a2a", "#0a0a0a"), stops("#FF7777", "#DD2222", "#AA0000"),
                Color.web("#1a1a1a"), 1.2, false);

        static final BoardPalette BRUTALIST = new BoardPalette(
                Color.web("#FDF6E3"), Color.web("#FFFFFF"), Color.web("#FFFFFF"), Color.web("#111111"), 4,
                Color.web("#111111"), Color.web("#111111"),
                stops("#333333", "#1a1a1a", "#000000"), stops("#FF6B6B", "#FF4D4D", "#D63030"),
                Color.web("#111111"), 3.0, false);

        static final BoardPalette AURORA = new BoardPalette(
                Color.web("#150c2b"), Color.web("#2a1f4a"), Color.web("#1a1338"), Color.web("#5EEAD4"), 2.5,
                Color.web("#6b5a9a"), Color.web("#5EEAD4"),
                stops("#7d8bb0", "#2a3358", "#0d1326"), stops("#fbc0e0", "#F472B6", "#a83a78"),
                Color.web("#10212b"), 1.0, true);

        static final BoardPalette GOBAN = new BoardPalette(
                Color.web("#F3EAD8"), Color.web("#E8C079"), Color.web("#E2B96B"), Color.web("#6b5836"), 4,
                Color.web("#4a3c22"), Color.web("#2B2B2B"),
                stops("#4a4a4a", "#222222", "#000000"), stops("#ffffff", "#f0ead8", "#cfc4a8"),
                Color.web("#3a3326"), 1.0, false);

        static final BoardPalette NEON = new BoardPalette(
                Color.web("#080812"), Color.web("#0a0a16"), Color.web("#080812"), Color.web("#00E5FF"), 2,
                Color.web("#1d6f86"), Color.web("#00E5FF"),
                stops("#aef6ff", "#00E5FF", "#0077aa"), stops("#ffb0d8", "#FF2E97", "#a01060"),
                Color.web("#021018"), 1.0, true);

        static final BoardPalette MATERIAL = new BoardPalette(
                Color.web("#FEF7FF"), Color.web("#F1E9F5"), Color.web("#E8DEF0"), Color.web("#CABFD6"), 3,
                Color.web("#8a7d9c"), Color.web("#5B5BD6"),
                stops("#5a5a66", "#2a2a33", "#1c1b1f"), stops("#FF6A5E", "#BA1A1A", "#7a0e0e"),
                Color.web("#2a2733"), 1.0, false);
    }
}
