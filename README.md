# Gomoku FX

A JavaFX implementation of Gomoku (Five-in-a-Row) with multiple AI difficulty levels and a modern, fully themeable GUI (seven built-in light/dark themes).

## Requirements

- Java 11+
- Maven 3.6+

## Build & Run

```bash
# Run directly with Maven (no JAR needed)
mvn clean javafx:run

# Build executable JAR
mvn clean package

# Run the JAR
java -jar target/Gomokufx-1.0-SNAPSHOT.jar
```

## Command-Line Arguments

You can skip the launcher and start a game directly:

```
java -jar target/Gomokufx-1.0-SNAPSHOT.jar [MODE] [STRAT1] [STRAT2]
```

| Mode | Arguments | Description |
|------|-----------|-------------|
| *(none)* | — | Opens the launcher GUI |
| `PP` | — | Player vs Player |
| `PC` | `S1`–`S4` | Player vs Computer (strategy for the AI) |
| `CC` | `S1`–`S4` `S1`–`S4` | Computer vs Computer |
| `SERVER` | `<port>` | Host a network game — waits for client, plays Black |
| `CLIENT` | `<host> <port>` | Join a network game — connects to server, plays Red |

**Options** (may appear in any position among the arguments):

- **`D`** or **`DEBUG`** — enable the debug move trace (written to `gomoku_debug.txt`). E.g. `PP D`, `PC S4 D`, `CC S2 S4 DEBUG`.
- **`load=<path>`** — resume from a saved XML game (PP/PC/CC modes). On a load error it warns and starts a fresh board. E.g. `PC S4 load=savedgame.xml`.

These combine, e.g. `PC S4 D load=savedgame.xml` loads the save *and* enables debug.

**AI Strategies:**

| Code | Name | Uses depth | Description |
|------|------|------------|-------------|
| `S1` | Random | No | Places stones randomly |
| `S2` | Block | No | Wins immediately or blocks opponent's immediate win, then random |
| `S3` | Minimax | Yes | Full minimax tree search |
| `S4` | AlphaBeta | Yes | Minimax with alpha-beta pruning (strongest) |

The depth slider (1–8) applies only to S3 and S4. S1 and S2 ignore it entirely.

**Examples:**

```bash
java -jar target/Gomokufx-1.0-SNAPSHOT.jar PP
java -jar target/Gomokufx-1.0-SNAPSHOT.jar PC S4
java -jar target/Gomokufx-1.0-SNAPSHOT.jar CC S2 S4
java -jar target/Gomokufx-1.0-SNAPSHOT.jar PP D        # Player vs Player with debug trace
java -jar target/Gomokufx-1.0-SNAPSHOT.jar PC S4 load=savedgame.xml   # resume a saved game vs AlphaBeta

# Network — two machines:
java -jar target/Gomokufx-1.0-SNAPSHOT.jar SERVER 5000          # machine 1
java -jar target/Gomokufx-1.0-SNAPSHOT.jar CLIENT 192.168.1.X 5000  # machine 2

# Network — same machine, two windows:
java -jar target/Gomokufx-1.0-SNAPSHOT.jar SERVER 5000          # terminal 1
java -jar target/Gomokufx-1.0-SNAPSHOT.jar CLIENT localhost 5000 # terminal 2
```

## Project Structure

```
ttt.model/       — True Model: game rules, board state (Gomoku, IGame, ARegularGame, Pair)
ttt.ai/          — AI strategies: IGameKI, StrategyRandom/Block/Minimax/AlphaBeta
ttt.storage/     — Persistence: XMLGameStorage (JDOM2)
ttt.util/        — Utilities: Debugger
ttt.network/     — Networking: GomokuNetwork, GomokuNetworkControl (SERVER/CLIENT CLI modes)
ttt.controller/  — MVC Controller: GomokuFXApp, GomokuMain, GomokuLauncherController, GomokuControllerFX
ttt.view/        — MVC View: GomokuLauncherFX, GomokuGameView, GomokuBoardFX, ThemeManager
```

## Design Patterns

| Pattern | Where |
|---------|-------|
| **Strategy** | `IGameKI` — AI algorithms swapped at runtime without changing the controller |
| **Template Method** | `ARegularGame` defines game lifecycle; `Gomoku` fills in the rules |
| **MVC** | `ttt.model` / `ttt.view` / `ttt.controller` are fully separated |
| **Factory Method** | `GomokuLauncherController.createStrategy()` maps `S1`–`S4` codes to strategy instances |
| **Prototype** | `ARegularGame.clone()` — each move returns a new cloned state for safe AI search |

See [ARCHITECTURE.md](ARCHITECTURE.md) for full details and flow diagrams.

## Game Features

- Configurable board size: 13×13 / 15×15 / 17×17 / 19×19
- Standard Gomoku rules (5 in a row wins)
- Four game modes: Player vs Player, Player vs Computer, Computer vs Computer, Network (SERVER/CLIENT)
- Undo (PC mode) and hint system
- Save/load games to XML
- Debug mode — logs move trace to `gomoku_debug.txt`
- Win highlight animation
- Arcade-style start screen: GOMOKU 🎯 title with neon glow, Canvas mini board previews, emoji player avatars, AI depth slider (shown only when S3/S4 is selected)
- **Enhanced game sidebar:** prominent "to move" turn badge, side-by-side score card, expanded info card (move counter + live elapsed clock + last move + AI/your think times), and a scrollable **move history** list (chess-style coordinates, e.g. `12. 🔴 H8`)
- **Last-move marker** highlighting the most recently placed stone on the board
- Tooltips on every action, with keyboard-shortcut hints

## Theming

The UI ships with **seven switchable themes** — pick one from the 🎨 dropdown in the launcher's Match Setup header or in the in-game toolbar. The choice is saved to `~/.gomokufx_theme` and restored on next launch.

| Theme | Look |
|-------|------|
| **Dark** | Default red/gold arcade over midnight |
| **Light** | Clean light arcade |
| **Neo-Brutalist** | Flat paper, thick black borders, hard offset shadows |
| **Aurora Glass** | Translucent panels over an indigo→teal gradient, mint/pink accents |
| **Zen Goban** | Warm washi paper + wood board, black slate / white shell stones |
| **Neon Grid** | Black background, glowing cyan/magenta grid and stones |
| **Material** | Light Material You surfaces, indigo accent, elevation |

Each theme re-skins the canvas board too (board face, grid, stones, glow). Press **T** anywhere to cycle through all themes.

## Keyboard Shortcuts

**Launcher:** `Enter` start match · `Esc` exit · `T` cycle theme

**In-game:** `R` restart · `H` hint · `Ctrl+Z` undo · `Ctrl+S` save · `Ctrl+L` load · `T` cycle theme · `Esc` quit to launcher
