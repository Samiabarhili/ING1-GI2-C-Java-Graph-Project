# Java Project - GI2 C - Theme 3

> Realized by Abdou Malak, Amaini Maellys, Barhili Samia and Benameur Bayane

## Objective

JavaFX application simulating agent movement in a graph representing a CY Tech building.
The application allows users to visualize and simulate an emergency evacuation with different roles.

---

## Requirements

* **Java 21** or higher
* **Maven 3.8+**

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

---

## Run the project

```bash
# 1. Download the ZIP file and unzip it

# 2. Launch the graphical interface
mvn javafx:run

# 3. (optional) Run in command line mode
mvn package
java -jar target/cysafecampus-1.0.jar cli
```

The first time, Maven downloads JavaFX automatically. This may take around two minutes. After that, the launch is faster.

---

## Project structure

```text
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
    │   ├── Person.java              # Regular occupant
    │   ├── AdminAgent.java          # Administrative evacuation coordinator
    │   ├── SupervisorAgent.java     # Supervisor agent per room
    │   ├── BuildingElement.java     # Abstract building element
    │   ├── Room.java                # Room
    │   ├── Passage.java             # Corridor / staircase / hall
    │   ├── Exit.java                # Exit
    │   ├── Door.java                # Door (room ↔ passage connection)
    │   ├── Graph.java               # Main simulation container
    │   ├── SimulationEngine.java    # Simulation loop (tick)
    │   ├── SimulationSerializer.java# Save / Load (binary serialization)
    │   ├── PathFinder.java          # Dijkstra algorithm (shortest / fastest path)
    │   ├── Sensor.java              # Abstract sensor
    │   ├── PresenceSensor.java      # Presence sensor
    │   ├── SmokeSensor.java         # Smoke sensor
    │   ├── MovementStrategy.java    # Strategy interface
    │   ├── EvacuateStrategy.java    # Calm evacuation behavior
    │   ├── PanicStrategy.java       # Panic evacuation behavior
    │   └── GuideStrategy.java       # Guiding behavior
    └── view/
        ├── LoginView.java           # Role selection screen
        ├── AdminView.java           # Administrator view
        └── SupervisorView.java      # Supervisor view
```

---

## Roles and interfaces

| Role              | Interface                                                             | Access  |
| ----------------- | --------------------------------------------------------------------- | ------- |
| **Administrator** | `AdminView` — full plan, sensors, evacuation orders, agent management | Full    |
| **Supervisor**    | `SupervisorView` — assigned room, occupants and evacuation order      | Limited |

---

## Features

* Tick-by-tick simulation with play / pause / step
* Adjustable simulation speed
* Agents with speed, behavior (`POLITE`, `FOLLOWER`, `RUDE`) and density tolerance
* Path calculation: shortest path using Dijkstra based on distance
* Path calculation: fastest path using Dijkstra based on estimated time and congestion
* Bottleneck handling when a passage is highly congested
* Presence and smoke sensors with real-time alerts
* Node colors based on density, from green to orange to red
* Add, remove and modify nodes, edges and agents during the simulation
* Random node and agent generation with configurable ranges
* Save / load simulation state using a binary `.bin` file
* CLI mode to test the logic without the graphical interface

---

## Design patterns used

* **Strategy** — `MovementStrategy` with evacuation, panic and guide behaviors
* **Observer** — `Graph` notifies agents using `Subject` / `Observer`
* **Observer** — `Sensor` notifies `AdminAgent` using `SensorObserver`
* **MVC** — `GraphController` connects the model and JavaFX views
* **Serialization** — `SimulationSerializer` handles save and load

---
