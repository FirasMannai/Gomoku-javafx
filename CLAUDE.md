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
- `StrategyRandom` → `StrategyBlock` → `StrategyMinimax` → `StrategyAlphaBeta` (increasing strength; AlphaBeta uses depth=4)

### `ttt.storage` — Persistence

- `XMLGameStorage` — save/load game state via JDOM2

### `ttt.util` — Utilities

- `Debugger` — writes move traces to `gomoku_debug.txt`

### `ttt.network` — Networking

- `GomokuNetwork` — socket wrapper; server constructor blocks until a client connects; moves sent as `"row,col"` strings.
- `GomokuNetworkControl` — turn management and background listener thread (not used directly; wired via `GomokuControllerFX` in PN mode).

### `ttt.controller` — MVC Controller

```
GomokuFXApp          (JavaFX entry point, manages Stage, parses CLI args)
GomokuMain           (JAR entry point, delegates to GomokuFXApp)
GomokuControllerFX   (game controller — bridges model ↔ view, handles undo/hint/save/load, drives AI turns)
```

- `GomokuControllerFX` maintains a move history list for undo and uses `StrategyAlphaBeta` for hint computation. In PN mode it holds a `GomokuNetwork` connection, sends moves after each click, and runs a daemon thread to receive opponent moves via `Platform.runLater`.
- `GomokuFXApp.startNetworkGame()` establishes the socket on a background thread (so the JavaFX UI stays responsive while the server waits), then calls `GomokuControllerFX.initNetwork()`.

### `ttt.view` — MVC View

```
GomokuLauncherFX   (start screen: mode/strategy selection)
GomokuGameView     (BorderPane layout: toolbar, sidebar, status bar)
GomokuBoardFX      (Canvas: grid/stone rendering, mouse input, win animation, hint highlights)
style.css          (dark theme — loaded via absolute path /ttt/view/style.css)
```

- `GomokuLauncherFX.createStrategy()` is the public factory that maps strategy codes (S1–S4) to strategy instances.

### Module system

`src/main/java/module-info.java` declares the Java 11 module. Add `requires` / `opens` directives there when introducing new dependencies or reflection-based libraries.
