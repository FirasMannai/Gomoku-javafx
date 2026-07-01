# Architecture & Design Patterns

## High-Level Architecture

Gomoku FX follows a full **MVC (Model-View-Controller)** pattern with dedicated packages for each concern:

```
┌──────────────────────────────────────────────────────────────┐
│                     Model (ttt.model)                        │
│   IGame / IRegularGame / ARegularGame / Gomoku / Pair        │
└──────────────────────────────────────────────────────────────┘
         ▲                    ▲                    ▲
         │                    │                    │
┌────────────────┐  ┌─────────────────┐  ┌────────────────────┐
│   ttt.ai       │  │  ttt.storage    │  │   ttt.util         │
│  IGameKI       │  │  XMLGameStorage │  │   Debugger         │
│  Strategy*     │  └─────────────────┘  └────────────────────┘
└────────────────┘
         ▲
         │ (AI calls, save/load, logging)
         │
┌──────────────────────────────────────────────────────────────┐
│                  Controller (ttt.controller)                 │
│  GomokuFXApp ── GomokuMain ── GomokuLauncherController       │
│  (entry points)               (start screen, AI factory)     │
│                    GomokuControllerFX                        │
│                    (bridges model ↔ view during game)        │
└──────────────────────────────────────────────────────────────┘
         ▲
         │ (update display / user input)
         │
┌──────────────────────────────────────────────────────────────┐
│                      View (ttt.view)                         │
│   GomokuLauncherFX  ──  GomokuGameView  ──  GomokuBoardFX    │
│   (start screen)        (layout/labels)    (canvas/input)    │
│                     ThemeManager + style.css                 │
│                  (7 switchable light/dark themes)            │
└──────────────────────────────────────────────────────────────┘
```

## Package Responsibilities

| Package | Role | Key Classes |
|---|---|---|
| `ttt.model` | True MVC Model — domain data and rules | `Gomoku`, `IGame`, `IRegularGame`, `ARegularGame`, `Pair` |
| `ttt.ai` | AI service layer | `IGameKI`, `StrategyRandom/Block/Minimax/AlphaBeta` |
| `ttt.storage` | Persistence | `XMLGameStorage` |
| `ttt.util` | Utilities | `Debugger` |
| `ttt.network` | Networking (SERVER/CLIENT modes) | `GomokuNetwork`, `GomokuNetworkControl` |
| `ttt.controller` | MVC Controller | `GomokuFXApp`, `GomokuMain`, `GomokuLauncherController`, `GomokuControllerFX` |
| `ttt.view` | MVC View | `GomokuLauncherFX`, `GomokuGameView`, `GomokuBoardFX`, `ThemeManager` |

## Design Patterns Used

### 1. **Strategy Pattern**

**Purpose:** Swap AI algorithms at runtime without changing the controller.

**Classes:** 
- `IGameKI<M>` (`ttt.ai`) — interface for all strategies
- `StrategyRandom` — places stones randomly (no depth)
- `StrategyBlock` — wins immediately or blocks opponent's immediate win, falls back to random (no depth)
- `StrategyMinimax(int depth)` — full minimax tree search; depth configurable at construction time
- `StrategyAlphaBeta(int depth)` — minimax with alpha-beta pruning (strongest); depth configurable at construction time

**Usage in Controller:**
```java
// GomokuControllerFX holds references to strategies
private final IGameKI<Pair<Byte, Byte>> ai1;
private final IGameKI<Pair<Byte, Byte>> ai2;

// Called polymorphically without knowing the concrete strategy
IGame<Pair<Byte, Byte>> nextAI = ai1.doBestMove(currentGame);
```

**Benefit:** Easy to add new AI strategies without modifying controller code.

---

### 2. **Template Method Pattern**

**Purpose:** Define the structure of a board game; let subclasses fill in game-specific rules.

**Classes:**
- `ARegularGame<M>` (`ttt.model`) — abstract base (turn tracking, board storage, game state lifecycle)
- `Gomoku` (`ttt.model`) — concrete implementation (5-in-a-row win logic, board evaluation, move generation)

**Methods defined in `ARegularGame`, overridden in `Gomoku`:**
```java
public abstract List<M> moves();
public abstract IRegularGame<M> doMove(M move);
public abstract boolean wins(byte player);
public abstract int evalState(byte player);
```

**Benefit:** Codebase can support different games by extending `ARegularGame`.

---

### 3. **MVC (Model-View-Controller)**

**Model** (`ttt.model`): `Gomoku` / `IRegularGame<Pair<Byte, Byte>>`
- Pure game state: board, current player, move history
- No UI dependency — testable and reusable
- Immutable per move (each `doMove()` returns a new cloned state)

**View** (`ttt.view`): `GomokuLauncherFX` + `GomokuGameView` + `GomokuBoardFX` + `ThemeManager`
- `GomokuLauncherFX` — start screen, mode/strategy selection, theme picker
- `GomokuGameView` — BorderPane layout, toolbar, enhanced sidebar (turn badge, score, game mode, move history), status labels
- `GomokuBoardFX` — Canvas rendering (grid, stones, hints, last-move marker, animations); reads the active theme's `BoardPalette`
- `ThemeManager` — application-wide theme controller (see [Theming](#theming))
- Never calls game logic directly

**Controller** (`ttt.controller`): two controllers, different lifecycles

`GomokuLauncherController` (active until Start is clicked):
- Wires all start-screen toggle/slider events
- Calls `view.setXxx(...)` on every change to keep labels in sync
- Contains `createStrategy(String, int)` — the AI factory used by both the GUI and CLI paths
- Translates UI state into `StartSettings` and hands off to `GomokuFXApp`

`GomokuControllerFX` (active during a match):
- Bridges model and view
- Handles user input (mouse clicks → moves)
- Drives AI turns in PC/CC modes on a background thread
- Manages undo history
- Computes hints via `StrategyAlphaBeta`
- Saves/loads games via `XMLGameStorage`
- In PN (network) mode: sends moves via `GomokuNetwork`, receives opponent moves on a daemon thread using `Platform.runLater`

**Local game flow:**
```
User clicks board → GomokuBoardFX.handleMouseClick()
      ↓
   GomokuControllerFX.onBoardClicked()
      ↓
   currentGame.doMove()  →  recordMove() (counter, history, last-move marker)
      ↓
   GomokuGameView.setTurn() / GomokuBoardFX.redraw()
```

**Network game flow (PN mode):**
```
GomokuFXApp detects SERVER/CLIENT arg
      ↓
   GomokuControllerFX created with mode="PN"
      ↓
   Background thread: GomokuNetwork connects (blocks until peer joins)
      ↓
   initNetwork() called → daemon listener thread starts
      ↓
   Local click → onBoardClicked() → doMove() + net.sendMove()
   Opponent move → net.receiveMove() → Platform.runLater → applyNetworkMove()
```

---

### 4. **Factory Method Pattern**

**Purpose:** Create AI strategy instances from string codes without exposing concrete classes.

**Method:** `GomokuLauncherController.createStrategy(String code, int depth)` (`ttt.controller`)

```java
public static IGameKI<Pair<Byte, Byte>> createStrategy(String code, int depth) {
    if (code == null) return null;
    switch (code.toUpperCase()) {
        case "BLOCK":     case "S2": return new StrategyBlock<>();
        case "MINIMAX":   case "S3": return new StrategyMinimax<>(depth);
        case "ALPHABETA": case "S4": return new StrategyAlphaBeta<>(depth);
        default:                     return new StrategyRandom<>();
    }
}
```

**Usage:** Both the launcher GUI and `GomokuFXApp` (CLI args) call this factory:
```bash
java -jar target/Gomokufx-1.0-SNAPSHOT.jar CC S2 S4
#                                              ↓  ↓
#                          createStrategy("S2", 4) and createStrategy("S4", 4)
```

---

### 5. **Prototype Pattern (Immutable Game State)**

**Purpose:** Clone game state before making moves, so AI can safely explore move trees.

**Classes:** `ARegularGame.clone()` (`ttt.model`)

```java
public IRegularGame<Pair<Byte, Byte>> doMove(Pair<Byte, Byte> move) {
    Gomoku clone = (Gomoku) this.clone();
    clone.setAtPosition(move.first, move.second);
    return clone;
}
```

**Why:** Minimax and AlphaBeta explore thousands of hypothetical branches. Cloning ensures no side effects between branches.

---

## Key Design Principles

1. **Separation of Concerns** — `ttt.model` has zero UI dependencies; each package has one clear role
2. **Immutable Game State** — Each move creates a new state; safe for AI search
3. **Polymorphism** — Strategies implement `IGameKI`; controller is agnostic to algorithm
4. **Extensibility** — New AI strategies only require implementing `IGameKI` and adding a `createStrategy` case
5. **Access Control** — `ARegularGame` protected fields exposed via public accessors (`getMovesDone`, `setPlayer`, etc.) rather than direct field access across packages

---

## Start Screen Design

`GomokuLauncherFX` implements an arcade-style start screen. All colors come from the active theme's CSS tokens (see [Theming](#theming)); the values below are the **Dark** theme defaults:

| Element | JavaFX implementation |
|---|---|
| Background | Gold radial glow at top (`-c-glow`) over a dark `-c-bg-top→-c-bg-bottom` linear gradient |
| Theme picker | `🎨 Theme` `ComboBox` in the "01 MATCH SETUP" header, built by `ThemeManager.createThemeSelector()` |
| GOMOKU title | `Label("GOMOKU 🎯")` with red neon glow dropshadow — emoji embedded in the same label, inheriting the `.launcher-title` style |
| Animated pulse dot on eyebrow | `Circle` (gold `#f5a623`) + `FadeTransition` (1.6 s, loops) |
| Mode buttons with stone dots | Two `Circle` nodes (black/red/ai colour) displayed above the label via `ContentDisplay.TOP` |
| Board-size mini-grid previews | `Canvas` drawn per button inside a `VBox` graphic |
| Emoji avatar in VS card | `Label` (emoji) layered over a `Region` (styled circle) inside a `StackPane` |
| Depth card hidden when unused | `setVisible(false)` + `setManaged(false)`; hidden in PVP and when all active AI sides use S1/S2 |
| Depth card shown only for S3/S4 | `GomokuLauncherController.usesDepth(code)` returns true only for MINIMAX and ALPHABETA |
| Dynamic avatars (human vs AI) | `GomokuLauncherController.refresh()` calls `view.setPlayerIcons()` |
| Depth descriptor with time hint | e.g. `"Balanced · ~0.8s"`, `"Expert · ~18s"` — shown in the depth card header |
| Footer summary separators | Uses `·` and `×` (e.g. `PVA · 15×15 · ALPHABETA·D4`) |
| Configurable board sizes | 13×13 / 15×15 / 17×17 / 19×19, passed into `new Gomoku(size, size)` |

---

## Theming

The UI supports **seven switchable themes**: Dark, Light, Neo-Brutalist, Aurora Glass, Zen Goban, Neon Grid, and Material. The whole system is driven by `ttt.view.ThemeManager` (a singleton) plus a token-based `style.css`.

### How it works

1. **CSS tokens.** Every color in `style.css` is a JavaFX *looked-up color* token (e.g. `-c-accent`, `-c-card`, `-c-text`). Each theme defines one complete token block keyed by a class (`.theme-dark`, `.theme-light`, `.theme-brutalist`, …). The shared rules reference only tokens, so the look is fully data-driven.
2. **Class swap.** `ThemeManager.register(root)` puts the active theme's class on the scene root and updates it whenever the theme changes. Because looked-up colors cascade from the root, every styled descendant re-resolves its colors automatically — no per-node restyling.
3. **Canvas palette.** CSS cannot reach the `GomokuBoardFX` `Canvas`, so each theme also has a `ThemeManager.BoardPalette` (board face, grid, star points, both players' stone gradients, outline, glow flag). The board reads `ThemeManager.boardPalette()` and repaints on theme change.
4. **Persistence.** The selected theme is written to `~/.gomokufx_theme` and reloaded on startup (plain file — no extra module dependency).

### User entry points

- **Theme picker** — `ComboBox<Theme>` from `ThemeManager.createThemeSelector()`, shown in both the launcher header and the in-game toolbar; both stay in sync via the shared `themeProperty()`.
- **Keyboard** — `T` calls `ThemeManager.cycle()` from either screen.

### Why this design

| Goal | Mechanism |
|---|---|
| Add a new theme with minimal code | One CSS token block + one `BoardPalette` + one `enum` constant |
| Keep all controls consistent across screens | A single factory (`createThemeSelector`) builds the picker for both views |
| No UI logic in the model/controller | `ThemeManager` lives in `ttt.view`; controllers only call `register()` / `cycle()` |

### In-game UX additions

`GomokuGameView` / `GomokuControllerFX` also add a richer in-game layer on top of the base MVC flow:

- **Turn badge** — prominent "X to move" indicator (tinted on Red's turn via a `:red-turn` CSS pseudo-class), reused to show the result on game over.
- **Move history** — a scrollable `ListView` fed by `GomokuControllerFX.recordMove()`, which centralizes the move counter, the history entry (with its per-move time), and the board marker for every mode (human, AI, CC auto-play, network).
- **Elapsed clock** — a 1-second `Timeline` (`startElapsedTimer`) that resets on restart and stops on game over / quit.
- **Keyboard shortcuts** — `installShortcuts(Scene)`: `R` restart, `H` hint, `Ctrl+Z` undo, `Ctrl+S` save, `Ctrl+L` load, `T` cycle theme, `Esc` quit.
