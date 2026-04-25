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
│    GomokuFXApp  ──  GomokuMain  ──  GomokuControllerFX       │
│    (entry points)               (bridges model ↔ view)       │
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
| `ttt.controller` | MVC Controller | `GomokuControllerFX`, `GomokuFXApp`, `GomokuMain` |
| `ttt.view` | MVC View | `GomokuLauncherFX`, `GomokuGameView`, `GomokuBoardFX` |

## Design Patterns Used

### 1. **Strategy Pattern**

**Purpose:** Swap AI algorithms at runtime without changing the controller.

**Classes:** 
- `IGameKI<M>` (`ttt.ai`) — interface for all strategies
- `StrategyRandom` — places stones randomly
- `StrategyBlock` — blocks opponent threats
- `StrategyMinimax` — full minimax tree search
- `StrategyAlphaBeta` — minimax with alpha-beta pruning (strongest, depth=4)

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

**Controller** (`ttt.controller`): `GomokuControllerFX`
- Bridges model and view
- Handles user input (mouse clicks → moves)
- Drives AI turns in PC/CC modes
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

**Method:** `GomokuLauncherFX.createStrategy(String code)` (`ttt.view`)

```java
public static IGameKI<Pair<Byte, Byte>> createStrategy(String code) {
    if (code == null) return null;
    switch (code) {
        case "S2": return new StrategyBlock<>();
        case "S3": return new StrategyMinimax<>();
        case "S4": return new StrategyAlphaBeta<>();
        default:   return new StrategyRandom<>();
    }
}
```

**Usage:** Both the launcher GUI and `GomokuFXApp` (CLI args) call this factory:
```bash
java -jar target/Gomokufx-1.0-SNAPSHOT.jar CC S2 S4
#                                              ↓  ↓
#                          createStrategy("S2") and createStrategy("S4")
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
