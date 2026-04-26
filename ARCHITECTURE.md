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
| `ttt.view` | MVC View | `GomokuLauncherFX`, `GomokuGameView`, `GomokuBoardFX` |

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

**View** (`ttt.view`): `GomokuGameView` + `GomokuBoardFX` + `GomokuLauncherFX`
- `GomokuLauncherFX` — start screen, mode/strategy selection
- `GomokuGameView` — BorderPane layout, toolbar, sidebar, status labels
- `GomokuBoardFX` — Canvas rendering (grid, stones, hints, animations)
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
   currentGame.doMove()
      ↓
   GomokuGameView.setTurnText() / GomokuBoardFX.redraw()
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

`GomokuLauncherFX` implements an arcade-style start screen (red/gold + midnight theme):

| Element | JavaFX implementation |
|---|---|
| Background | Gold radial glow at top (`rgba(245,166,35,0.12)`) over dark `#0f1419→#0b1016` linear gradient |
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
