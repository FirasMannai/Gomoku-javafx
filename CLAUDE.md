# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Run the application
mvn clean javafx:run

# Package into a distributable uber-JAR
mvn clean package

# Run with debugger port open (for IDE attach)
mvn clean javafx:run@ide-debug
```

There are no automated tests — `src/test/java` is empty. Manual testing is done by running the app directly.

## Architecture

This is a JavaFX Gomoku (Five-in-a-Row) game with a full MVC structure split across dedicated packages:

### `ttt.model` — True Model (domain data only)

- `IGame<M>` → `IRegularGame<M>` → `ARegularGame<M>` → `Gomoku`: interface hierarchy down to the concrete 15×15 board implementation.
- Board state is `byte[][]` where `0`=empty, `1`=black, `2`=red. Moves are `Pair<Byte, Byte>` (row, col).
- Each move returns a **new cloned game state** — the game is effectively immutable/functional.
- `ARegularGame` exposes public accessors (`getMovesDone`, `setPlayer`, `setMovesDone`, `setBoardPosition`, `setLastPosition`) used by `XMLGameStorage` for save/load.

### `ttt.ai` — AI Strategies

All implement `IGameKI<M>` with `doBestMove()` / `bestMove()`:
- `StrategyRandom` → `StrategyBlock` → `StrategyMinimax(int depth)` → `StrategyAlphaBeta(int depth)` (increasing strength)
- `StrategyRandom` and `StrategyBlock` do **not** use depth — Block only looks 1 move ahead (win or block), then falls back to random.
- `StrategyMinimax` and `StrategyAlphaBeta` both accept a configurable depth (1–8) passed from the launcher slider. Both default to depth 3 if constructed without arguments.

### `ttt.storage` — Persistence

- `XMLGameStorage` — save/load game state via JDOM2

### `ttt.util` — Utilities

- `Debugger` — writes move traces to `gomoku_debug.txt`

### `ttt.network` — Networking

- `GomokuNetwork` — socket wrapper; server constructor blocks until a client connects; moves sent as `"row,col"` strings.
- `GomokuNetworkControl` — turn management and background listener thread (not used directly; wired via `GomokuControllerFX` in PN mode).

### `ttt.controller` — MVC Controller

```
GomokuFXApp              (JavaFX entry point, manages Stage, parses CLI args — incl. an optional trailing D/DEBUG flag)
GomokuMain               (JAR entry point, delegates to GomokuFXApp)
GomokuLauncherController (start-screen controller — reads UI state, refreshes labels, launches game)
GomokuControllerFX       (game controller — bridges model ↔ view, handles undo/hint/save/load, drives AI turns)
```

- `GomokuLauncherController` wires all start-screen toggle/slider events, keeps every dependent label in sync via `refresh()`, and contains `createStrategy(String, int)` — the factory that maps S1–S4 codes to AI instances. It is discarded the moment the game starts. Key helpers: `usesDepth(String code)` returns true only for MINIMAX/ALPHABETA (controls depth card visibility); `buildSummary()` uses `·` / `×` separators; `depthDescriptor()` includes estimated move time (e.g. `"Expert · ~18s"`).
- `GomokuControllerFX` maintains a move history list for undo and uses `StrategyAlphaBeta` for hint computation. In PN mode it holds a `GomokuNetwork` connection, sends moves after each click, and runs a daemon thread to receive opponent moves via `Platform.runLater`. It centralizes per-move bookkeeping in `recordMove()` (move counter, history entry with its per-move time, board marker) across all modes, runs an elapsed-time `Timeline` (`startElapsedTimer`), and installs in-game keyboard shortcuts via `installShortcuts(Scene)` — `R` restart, `H` hint, `Ctrl+Z` undo, `Ctrl+S` save, `Ctrl+L` load, `T` cycle theme, `Esc` quit.
- `GomokuFXApp.startNetworkGame()` establishes the socket on a background thread (so the JavaFX UI stays responsive while the server waits), then calls `GomokuControllerFX.initNetwork()`.

### `ttt.view` — MVC View

```
GomokuLauncherFX   (start screen: mode/strategy/board-size selection, arcade design, theme picker)
GomokuGameView     (BorderPane layout: toolbar, enhanced sidebar, status bar)
GomokuBoardFX      (Canvas: grid/stone rendering, mouse input, win animation, hint + last-move highlights)
ThemeManager       (singleton: 7-theme controller, board palettes, theme picker factory, persistence)
style.css          (token-based multi-theme stylesheet — loaded via absolute path /ttt/view/style.css)
```

- `GomokuLauncherFX` is a pure view — no logic. It exposes toggle groups, the depth slider, and setters (`setPreview`, `setPlayerIcons`, `setDepthEnabled`, etc.) that `GomokuLauncherController` calls on every refresh. All public methods have Javadoc.
- The start screen is arcade-styled; all colors come from the active theme's CSS tokens (Dark theme defaults below):
  - Background: gold radial glow (`-c-glow`) at top over a `-c-bg-top→-c-bg-bottom` gradient
  - Title: `Label("GOMOKU 🎯")` with accent neon dropshadow glow — emoji embedded in the same label, inheriting `.launcher-title` style
  - Theme picker: `🎨 Theme` `ComboBox` in the "01 MATCH SETUP" header (`ThemeManager.createThemeSelector()`)
  - Eyebrow: pulsing gold `Circle` + `FadeTransition` (1.6 s, INDEFINITE)
  - Mode buttons: two `Circle` stone dots (black/red/ai) above text via `ContentDisplay.TOP`
  - Board-size buttons: `Canvas` mini-grid graphic inside a `VBox`
  - VS card: emoji `Label` layered over 58 px styled `Region` stone via `StackPane`
  - Depth card: hidden (`setVisible`/`setManaged`) when PVP mode or when all active AI sides use S1/S2
- `GomokuGameView` sidebar (260 px) holds: a prominent **turn badge** (`setTurn`, tinted on Red's turn via the `:red-turn` pseudo-class), a side-by-side **score card**, a **game-mode card**, and a scrollable **move-history** card — a live elapsed clock in its header plus per-move rows (`addMove`/`clearHistory`) showing the move number, a per-theme color stone, the coordinate, and how long that move took — and Save/Load. The toolbar carries the action buttons + a theme `ComboBox`.
- `GomokuBoardFX` draws all surfaces from the active `ThemeManager.BoardPalette` (board face, grid, star points, both stone gradients, outline, glow) and marks the most recent stone via `setLastMove`. It repaints when the theme changes.
- Board sizes supported: 13×13 (Quick), 15×15 (Standard), 17×17 (Large), 19×19 (Epic).

### Theming (`ttt.view.ThemeManager`)

- Seven themes: `DARK`, `LIGHT`, `BRUTALIST`, `AURORA`, `GOBAN`, `NEON`, `MATERIAL` (a `Theme` enum carrying a display name + CSS class).
- All CSS colors are looked-up tokens (`-c-*`); each theme is one token block (`.theme-dark`, `.theme-light`, …) in `style.css`. `register(root)` swaps the theme class on the scene root; cascading re-resolves every styled node.
- The `Canvas` board (CSS can't reach it) reads a per-theme `BoardPalette` from `boardPalette()` and repaints on `themeProperty()` change.
- Selection persists to `~/.gomokufx_theme` (plain file — no `java.prefs` requirement). `cycle()` advances themes; `T` is bound to it on both screens.
- **To add a theme:** add an `enum` constant, a `.theme-*` token block in `style.css`, and a `BoardPalette` in `ThemeManager`.

### Module system

`src/main/java/module-info.java` declares the Java 11 module. Add `requires` / `opens` directives there when introducing new dependencies or reflection-based libraries.
