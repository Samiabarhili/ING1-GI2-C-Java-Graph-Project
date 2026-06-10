# Java Project - GI2 C - Theme 3

> Realized by Abdou Malak, Amaini Maellys, Barhili Samia and Benameur Bayane Lila

## Objective
JavaFX application simulating agent movement in a graph representing the CY Tech campus. Allows visualization and simulation of an emergency evacuation with multiple roles.

-----

## Requirements

- **Java 21** or higher
- **Maven 3.8+**

Check:

```bash
java -version
mvn -version
```

If Maven is not installed:

```bash
sudo apt install maven        # Linux
brew install maven            # Mac
```

-----

## Run the project

```bash
# 1. Clone the repo
download the .zip and unzip it

# 2. Launch the graphical interface
mvn javafx:run

# 3. (optional) Run in command line mode
mvn package
java -jar target/cysafecampus-1.0.jar cli
```

The first time, Maven downloads JavaFX automatically (~2 min). After that, it is instant.

-----

## Project structure

```
cysafecampus/
├── pom.xml                          # Maven configuration + JavaFX dependencies
├── .gitignore
├── README.md
└── src/main/java/com/example/cysafecampus/
    ├── Main.java                    # Entry point
    ├── controller/
    │   └── GraphController.java     # MVC — connects model and views
    ├── model/
    │   ├── Agent.java               # Abstract agent class
    │   ├── Person.java              # Regular occupant (crowd)
    │   ├── AdminAgent.java          # Admin agent (big boss)
    │   ├── SupervisorAgent.java     # Supervisor agent per room
    │   ├── SecurityAgent.java       # Security agent
    │   ├── BuildingElement.java     # Abstract building element
    │   ├── Room.java                # Room
    │   ├── Passage.java             # Corridor / staircase / hall
    │   ├── Exit.java                # Exit
    │   ├── Door.java                # Door (room ↔ corridor connection)
    │   ├── Graph.java               # Main simulation container
    │   ├── SimulationEngine.java    # Simulation loop (tick)
    │   ├── SimulationSerializer.java# Save / Load (binary serialization)
    │   ├── PathFinder.java          # Dijkstra (shortest / fastest)
    │   ├── Sensor.java              # Abstract sensor
    │   ├── PresenceSensor.java      # Presence sensor
    │   ├── SmokeSensor.java         # Smoke sensor
    │   ├── MovementStrategy.java    # Strategy interface (Strategy pattern)
    │   ├── EvacuateStrategy.java    # Calm evacuation
    │   ├── PanicStrategy.java       # Panic evacuation
    │   └── GuideStrategy.java       # Guiding (security agents)
    └── view/
        ├── LoginView.java           # Role selection screen
        ├── AdminView.java           # Administrator view
        ├── SupervisorView.java      # Supervisor view (per room)
        ├── SecurityView.java        # Security agent view
        └── ObserverView.java        # Observer view (read-only)
```

-----

## Roles and interfaces

|Role                 |Interface                                            |Access        |
|---------------------|-----------------------------------------------------|-------------|
|**Administrator**    |Full plan, sensors, evacuation orders, agent management|Full         |
|**Supervisor**       |Own room only, occupants, evacuation order           |Limited      |
|**Security agent**   |Own area, door control                               |Limited      |
|**Observer**         |Global read-only view                                 |Read-only   |

-----

## Features

- Tick-by-tick simulation with play / pause / step
- Adjustable speed
- Agents with speed, behavior (POLITE / FOLLOWER / RUDE) and density tolerance
- Path calculation: shortest path (Dijkstra distance) and fastest path (Dijkstra time + congestion)
- Bottleneck handling (high congestion = 2 wait cycles)
- Presence and smoke sensors with real-time alerts
- Node colors based on density (green → orange → red)
- Add / remove / modify nodes, edges, and agents live
- Random node and agent generation with configurable ranges
- Save / Load simulation state (binary `.bin` file)
- CLI mode to test logic without the interface

-----

## Design patterns used

- **Strategy** — `MovementStrategy` (Evacuate / Panic / Guide)
- **Observer** — `Graph` notifies agents (`Subject` / `Observer`)
- **Observer** — `Sensor` notifies `AdminAgent` (`SensorObserver`)
- **MVC** — `GraphController` connects the model and JavaFX views
- **Serialization** — `SimulationSerializer` for save/load

-----  
