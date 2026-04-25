# Gomoku FX

A JavaFX implementation of Gomoku (Five-in-a-Row) with multiple AI difficulty levels and a dark-themed GUI.

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

**AI Strategies:**

| Code | Name | Description |
|------|------|-------------|
| `S1` | Random | Places stones randomly |
| `S2` | Block | Blocks opponent threats |
| `S3` | Minimax | Full minimax search |
| `S4` | AlphaBeta | Alpha-Beta pruning (strongest) |

**Examples:**

```bash
java -jar target/Gomokufx-1.0-SNAPSHOT.jar PP
java -jar target/Gomokufx-1.0-SNAPSHOT.jar PC S4
java -jar target/Gomokufx-1.0-SNAPSHOT.jar CC S2 S4

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
ttt.controller/  — MVC Controller: GomokuControllerFX, GomokuFXApp, GomokuMain
ttt.view/        — MVC View: GomokuLauncherFX, GomokuGameView, GomokuBoardFX
```

## Design Patterns

| Pattern | Where |
|---------|-------|
| **Strategy** | `IGameKI` — AI algorithms swapped at runtime without changing the controller |
| **Template Method** | `ARegularGame` defines game lifecycle; `Gomoku` fills in the rules |
| **MVC** | `ttt.model` / `ttt.view` / `ttt.controller` are fully separated |
| **Factory Method** | `GomokuLauncherFX.createStrategy()` maps `S1`–`S4` codes to strategy instances |
| **Prototype** | `ARegularGame.clone()` — each move returns a new cloned state for safe AI search |

See [ARCHITECTURE.md](ARCHITECTURE.md) for full details and flow diagrams.

## Game Features

- 15×15 board with standard Gomoku rules (5 in a row wins)
- Four game modes: Player vs Player, Player vs Computer, Computer vs Computer, Network (SERVER/CLIENT)
- Undo (PC mode) and hint system
- Save/load games to XML
- Debug mode — logs move trace to `gomoku_debug.txt`
- Win highlight animation
