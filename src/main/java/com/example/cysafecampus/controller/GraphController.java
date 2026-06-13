package com.example.cysafecampus.controller;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.example.cysafecampus.model.Agent;
import com.example.cysafecampus.model.Behavior;
import com.example.cysafecampus.model.BlockStatus;
import com.example.cysafecampus.model.BuildingElement;
import com.example.cysafecampus.model.Door;
import com.example.cysafecampus.model.EvacuateStrategy;
import com.example.cysafecampus.model.Exit;
import com.example.cysafecampus.model.Graph;
import com.example.cysafecampus.model.Passage;
import com.example.cysafecampus.model.PassageType;
import com.example.cysafecampus.model.PathFinder;
import com.example.cysafecampus.model.Person;
import com.example.cysafecampus.model.PresenceSensor;
import com.example.cysafecampus.model.Room;
import com.example.cysafecampus.model.RoomType;
import com.example.cysafecampus.model.RoutingSettings;
import com.example.cysafecampus.model.SecurityAgent;
import com.example.cysafecampus.model.Sensor;
import com.example.cysafecampus.model.SensorEvent;
import com.example.cysafecampus.model.SimulationEngine;
import com.example.cysafecampus.model.SimulationSerializer;
import com.example.cysafecampus.model.SmokeSensor;

/**
 * Controller linking the Graph model to the GraphView (MVC pattern). Also owns
 * the SimulationEngine and exposes play/pause/step/speed controls.
 *
 * <p>
 * This class manages:
 * <ul>
 *   <li>Simulation lifecycle (play/pause/step/speed)</li>
 *   <li>Agent routing and behavior during alerts</li>
 *   <li>Creation, modification and removal of nodes and edges</li>
 *   <li>Saving and loading the simulation state</li>
 *   <li>Sensor event callbacks</li>
 * </ul>
 * </p>
 */
public class GraphController {

    private boolean useFastestPath = false;

    private final Graph graph;

    private final SimulationEngine engine;

    /**
     * Called when a sensor fires an event — used by AdminView to update the log.
     */
    private Consumer<SensorEvent> sensorEventCallback;

    /**
     * Constructor — builds the default campus graph and wires the simulation
     * engine.
     *
     * @param graph the graph model used by this controller (must not be null)
     */
    public GraphController(Graph graph) {
        this.graph = graph;

        this.engine = new SimulationEngine(graph);

        // On each tick, redraw on the JavaFX thread
        initGraph();
    }

    // ── Simulation Controls ───────────────────────────────
    /**
     * Starts the simulation loop.
     */
    public void play() {
        engine.play();
    }

    /**
     * Pauses the simulation loop.
     */
    public void pause() {
        engine.pause();
    }

    /**
     * Executes one tick (step mode).
     */
    public void step() {
        engine.step();
    }

    /**
     * Sets the tick interval in milliseconds.
     *
     * @param intervalMs tick interval in milliseconds (recommended 50–2000)
     */
    public void setSpeed(int intervalMs) {
        engine.setIntervalMs(intervalMs);
    }

    /**
     * Returns whether the simulation engine is currently running.
     *
     * @return true if the simulation loop is running
     */
    public boolean isRunning() {
        return engine.isRunning();
    }

    /**
     * Returns the engine tick count since start.
     *
     * @return the current tick count
     */
    public long getTickCount() {
        return engine.getTickCount();
    }

    // ── Alert Controls ────────────────────────────────────
    /**
     * Triggers re-routing for agents affected by a fire.
     *
     * Agents whose current location or remaining path intersects any name in
     * {@code fireZoneNames} will be reassigned a new exit/destination.
     *
     * @param fireZoneNames set of element names representing the fire zone
     */
    public void rerouteAgentsAffectedByFire(Set<String> fireZoneNames) {
        if (fireZoneNames == null || fireZoneNames.isEmpty()) {
            return;
        }

        for (Agent agent : graph.getAgents()) {
            if (agent.isEvacuated()) {
                continue;
            }

            if (!isAgentAffectedByFire(agent, fireZoneNames)) {
                continue;
            }

            Exit nearest = findNearestExit(agent);

            if (nearest != null) {
                agent.setDestination(nearest);
                rerouteAgentAfterFire(agent, nearest, fireZoneNames);
                agent.setWaitCycles(0);
            }
        }
    }

    private boolean isAgentAffectedByFire(Agent agent, Set<String> fireZoneNames) {
        if (agent == null || fireZoneNames == null || fireZoneNames.isEmpty()) {
            return false;
        }

        BuildingElement current = agent.getCurrentLocation();

        if (isInFireZone(current, fireZoneNames)) {
            return true;
        }

        List<BuildingElement> path = agent.getPath();

        if (path == null || path.isEmpty()) {
            return false;
        }

        int startIndex = Math.max(0, Math.min(agent.getPathIndex(), path.size() - 1));

        // Only inspect the remaining path. Nodes already behind the agent should not
        // force a reroute.
        for (int i = startIndex; i < path.size(); i++) {
            BuildingElement element = path.get(i);

            if (isInFireZone(element, fireZoneNames)) {
                return true;
            }
        }

        return false;
    }

    private void rerouteAgentAfterFire(Agent agent, Exit destination, Set<String> fireZoneNames) {
        if (agent == null || destination == null) {
            return;
        }

        List<BuildingElement> oldPath = agent.getPath();
        BuildingElement current = agent.getCurrentLocation();
        double oldProgress = Math.max(0.0, Math.min(1.0, agent.getProgress()));

        if (oldPath != null && !oldPath.isEmpty()) {
            int index = Math.max(0, Math.min(agent.getPathIndex(), oldPath.size() - 1));

            BuildingElement next = index + 1 < oldPath.size()
                    ? oldPath.get(index + 1)
                    : null;

            boolean agentIsMovingBetweenNodes
                    = oldProgress > 0.0
                    && oldProgress < 1.0
                    && current != null
                    && next != null;

            if (agentIsMovingBetweenNodes) {
                // If the agent is already moving toward a safe next node, do not
                // snap it back to its current model location.
                if (!isInFireZone(next, fireZoneNames)) {
                    List<BuildingElement> tail = calculateRouteForAgent(next, destination, agent);

                    if (tail != null && !tail.isEmpty()) {
                        java.util.List<BuildingElement> rebuiltPath = new java.util.ArrayList<>();

                        rebuiltPath.add(current);

                        if (!rebuiltPath.get(rebuiltPath.size() - 1).equals(next)) {
                            rebuiltPath.add(next);
                        }

                        for (int i = 1; i < tail.size(); i++) {
                            BuildingElement element = tail.get(i);

                            if (element != null
                                    && !element.equals(rebuiltPath.get(rebuiltPath.size() - 1))) {
                                rebuiltPath.add(element);
                            }
                        }

                        agent.setPath(rebuiltPath);
                        agent.setPathIndex(0);
                        agent.setProgress(oldProgress);
                        return;
                    }
                }

                // If no safe reroute can be computed while the agent is mid-edge,
                // keep the current movement instead of teleporting it backward.
                agent.setWaitCycles(0);
                return;
            }
        }

        // The agent is not mid-edge, so it can safely be rerouted from its current node.
        List<BuildingElement> newPath = calculateRouteForAgent(current, destination, agent);

        if (newPath == null) {
            newPath = new java.util.ArrayList<>();
        }

        agent.setPath(newPath);
        agent.setPathIndex(0);
        agent.setProgress(0.0);
    }

    private List<BuildingElement> calculateRouteForAgent(
            BuildingElement start,
            BuildingElement destination,
            Agent agent
    ) {
        if (start == null || destination == null || agent == null) {
            return new java.util.ArrayList<>();
        }

        return RoutingSettings.isUseFastestPath()
                ? PathFinder.calculateFastestPath(start, destination, agent.getMaxSpeed())
                : PathFinder.calculateShortestPath(start, destination);
    }

    private boolean isInFireZone(BuildingElement element, Set<String> fireZoneNames) {
        return element != null
                && (element.isBlocked()
                || (element.getName() != null
                && fireZoneNames.contains(element.getName())));
    }

    /**
     * Triggers a fire alert: notifies all agents and assigns exits based on
     * the current routing strategy.
     */
    public void triggerFireAlert() {
        graph.triggerAlert("FIRE");

        if (RoutingSettings.isUseFastestPath()) {
            assignFastestExitsWithPlannedCongestion();
        } else {
            assignShortestExits();
        }
    }

    private void assignShortestExits() {
        for (Agent agent : graph.getAgents()) {
            if (agent.isEvacuated()) {
                continue;
            }

            Exit nearest = findNearestExit(agent);

            if (nearest != null) {
                agent.setDestination(nearest);

                if (!agent.isInTransit()) {
                    agent.setPath(new java.util.ArrayList<>());
                    agent.setPathIndex(0);
                    agent.setProgress(0.0);
                }
            }
        }
    }

    private void assignFastestExitsWithPlannedCongestion() {
        java.util.Map<Exit, Integer> plannedExitLoad = new java.util.HashMap<>();

        for (Agent agent : graph.getAgents()) {
            if (agent.isEvacuated()) {
                continue;
            }

            Exit bestExit = findBestExitWithPlannedCongestion(agent, plannedExitLoad);

            if (bestExit != null) {
                agent.setDestination(bestExit);

                if (!agent.isInTransit()) {
                    agent.setPath(new java.util.ArrayList<>());
                    agent.setPathIndex(0);
                    agent.setProgress(0.0);
                }

                plannedExitLoad.put(
                        bestExit,
                        plannedExitLoad.getOrDefault(bestExit, 0) + 1
                );
            }
        }
    }

    private Exit findBestExitWithPlannedCongestion(
            Agent agent,
            java.util.Map<Exit, Integer> plannedExitLoad
    ) {
        if (agent == null || agent.getCurrentLocation() == null) {
            return null;
        }

        Exit bestExit = null;
        double bestScore = Double.MAX_VALUE;

        for (BuildingElement el : graph.getElements()) {
            if (!(el instanceof Exit exit) || exit.isBlocked()) {
                continue;
            }

            List<BuildingElement> path = PathFinder.calculateFastestPath(
                    agent.getCurrentLocation(),
                    exit,
                    agent.getMaxSpeed()
            );

            if (path == null || path.isEmpty()) {
                continue;
            }

            double travelTime = estimateTravelTime(path, agent);

            int alreadyAssigned = plannedExitLoad.getOrDefault(exit, 0);

            // Bigger value = stronger distribution between exits.
            double waitingPenalty = alreadyAssigned * 2.5;

            double score = travelTime + waitingPenalty;

            if (score < bestScore) {
                bestScore = score;
                bestExit = exit;
            }
        }

        return bestExit;
    }

    /**
     * Returns whether the controller is configured to use the fastest-path
     * routing strategy.
     *
     * @return true if fastest path routing is enabled
     */
    public boolean isUseFastestPath() {
        return RoutingSettings.isUseFastestPath();
    }

    /**
     * Sets the routing mode used for evacuation (fastest vs shortest).
     *
     * @param useFastestPath true to use fastest-path routing
     */
    public void setUseFastestPath(boolean useFastestPath) {
        this.useFastestPath = useFastestPath;
        RoutingSettings.setUseFastestPath(useFastestPath);
        clearAgentPathsOnly();
    }

    /**
     * Resets the entire simulation: stops the engine, clears the graph and
     * reinitializes the default campus graph.
     */
    public void reset() {
        pause();

        graph.getElements().clear();
        graph.getPassages().clear();
        graph.getAgents().clear();
        graph.getSensors().clear();
        graph.clearObservers();

        graph.triggerAlert("NORMAL");

        initGraph();
    }

    // ── Save / Load ───────────────────────────────────────
    /**
     * Saves the current simulation state to a binary file. The file name is
     * normalized to end with {@code .bin} if needed; parent directories are
     * created if missing.
     *
     * @param filePath destination path
     * @return the absolute path of the file actually written
     * @throws java.io.IOException if the file cannot be written
     */
    public String saveSimulation(String filePath) throws java.io.IOException {
        engine.pause();

        java.io.File target = new java.io.File(filePath);

        // Ensure a .bin extension.
        if (!target.getName().toLowerCase().endsWith(".bin")) {
            target = new java.io.File(target.getParentFile(), target.getName() + ".bin");
        }

        // Create parent directories if they do not exist yet.
        java.io.File parent = target.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        SimulationSerializer.save(graph, target.getAbsolutePath());
        System.out.println("Simulation saved to: " + target.getAbsolutePath());
        return target.getAbsolutePath();
    }

    /**
     * Loads a simulation state from a binary file. The current graph contents
     * are replaced by the loaded graph.
     *
     * @param filePath source path
     * @throws java.io.IOException if the file cannot be read
     * @throws ClassNotFoundException if the file content is not a valid Graph
     */
    public void loadSimulation(String filePath)
            throws java.io.IOException, ClassNotFoundException {
        engine.pause();
        Graph loaded = SimulationSerializer.load(filePath);
        graph.getElements().clear();
        graph.getPassages().clear();
        graph.getAgents().clear();
        graph.getSensors().clear();
        graph.getElements().addAll(loaded.getElements());
        graph.getPassages().addAll(loaded.getPassages());

        for (BuildingElement el : graph.getElements()) {
            el.setCurrentOccupancy(0);
        }

        for (Agent a : loaded.getAgents()) {
            graph.addAgent(a);
        }

        for (Sensor s : loaded.getSensors()) {
            graph.addSensor(s);
        }
    }

    // ── Node Management ───────────────────────────────────
    /**
     * Adds a node to the graph model.
     *
     * <p>
     * Room-like types are stored as {@link Room} (or {@link Exit}) elements,
     * while Hall / Staircase / Corridor types are stored as {@link Passage}
     * elements and also registered in the passage list so that pathfinding and
     * passage-to-passage connections work as expected.
     * </p>
     *
     * @param name unique element name (used as the stable identifier)
     * @param type French logical type ("Salle", "Bureau", "Amphi", "Sortie",
     *             "Hall", "Escalier", "Couloir")
     * @param x horizontal position used by the views
     * @param y vertical position used by the views
     * @param floor floor index where this element should live (0 = RDC, 1 = 1er, …)
     */
    public void addNode(String name, String type, double x, double y, int floor) {
        BuildingElement node;

        if (type.equalsIgnoreCase("Bureau")) {
            node = new Room(name, 30, floor, RoomType.OFFICE);

        } else if (type.equalsIgnoreCase("Amphi")) {
            node = new Room(name, 120, floor, RoomType.AMPHITHEATER);

        } else if (type.equalsIgnoreCase("Salle")) {
            node = new Room(name, 30, floor, RoomType.CLASSROOM);

        } else if (type.equalsIgnoreCase("Sortie")) {
            Exit exit = new Exit(name, 100);
            exit.setFloor(floor);
            exit.setPosition(x, y);
            graph.addElement(exit);
            return;

        } else if (type.equalsIgnoreCase("Hall")) {
            Passage hall = new Passage(name, 50, floor, 1.0, PassageType.HALL, 12.0);
            hall.setPosition(x, y);
            graph.addElement(hall);
            graph.addPassage(hall);
            return;

        } else if (type.equalsIgnoreCase("Escalier")) {
            Passage stair = new Passage(name, 20, floor, 0.6, PassageType.STAIRCASE, 3.0);
            stair.setPosition(x, y);
            graph.addElement(stair);
            graph.addPassage(stair);
            return;

        } else if (type.equalsIgnoreCase("Couloir")) {
            Passage corridor = new Passage(name, 40, floor, 1.0, PassageType.CORRIDOR, 10.0);
            corridor.setPosition(x, y);
            graph.addElement(corridor);
            graph.addPassage(corridor);
            return;

        } else {
            node = new Room(name, 30, floor, RoomType.CLASSROOM);
        }

        node.setPosition(x, y);
        graph.addElement(node);
    }

    /**
     * Backwards-compatible overload that creates the element on floor 0 (RDC).
     *
     * @param name unique element name
     * @param type element type string
     * @param x horizontal position
     * @param y vertical position
     */
    public void addNode(String name, String type, double x, double y) {
        addNode(name, type, x, y, 0);
    }

    /**
     * Updates the floor of an existing element by name.
     *
     * @param name model element name
     * @param newFloor new floor index (0 = RDC, 1 = 1er, …)
     */
    public void setNodeFloor(String name, int newFloor) {
        BuildingElement element = findElementByName(name);
        if (element == null) {
            return;
        }
        element.setFloor(newFloor);
        clearAgentRoutes();
    }

    /**
     * Connects two existing elements in the model.
     *
     * <p>
     * Accepted cases (any order):
     * <ul>
     * <li>Room ↔ Passage and Exit ↔ Passage → creates a Door.</li>
     * <li>Passage ↔ Passage → creates a virtual "↔" junction Room.</li>
     * </ul>
     * Room ↔ Room and Exit ↔ Exit are not supported.
     * </p>
     *
     * @param firstName name of the first element
     * @param secondName name of the second element
     * @return {@code true} if a connection was created or already existed,
     *         {@code false} if the pair is invalid or unsupported.
     */
    public boolean addConnection(String firstName, String secondName) {
        BuildingElement first = findElementByName(firstName);
        BuildingElement second = findElementByName(secondName);

        if (first == null || second == null || first == second) {
            return false;
        }

        if (!isValidFloorConnection(first, second)) {
            return false;
        }

        if (connectionExists(first, second)) {
            return true;
        }

        // Exit ↔ Exit has no meaning: two final exits are not connected together.
        if (first instanceof Exit && second instanceof Exit) {
            return false;
        }

        // Room ↔ Room, including Room ↔ Exit.
        // We create an intermediate corridor/access passage.
        if (first instanceof Room roomA && second instanceof Room roomB) {
            connectRoomToRoomViaPassage(roomA, roomB);
            clearAgentRoutes();
            return true;
        }

        // Passage ↔ Passage via virtual junction Room.
        if (first instanceof Passage passageA && second instanceof Passage passageB) {
            connectPassageToPassage(passageA, passageB);
            clearAgentRoutes();
            return true;
        }

        // Room / Exit ↔ Passage via real Door.
        if (first instanceof Room room && second instanceof Passage passage) {
            addDoor(room, passage);
            clearAgentRoutes();
            return true;
        }

        if (first instanceof Passage passage && second instanceof Room room) {
            addDoor(room, passage);
            clearAgentRoutes();
            return true;
        }

        return false;
    }

    private void addDoor(Room room, Passage passage) {
        Door door = new Door(room, passage);
        room.addDoor(door);
        passage.addDoor(door);
    }

    private boolean isValidFloorConnection(BuildingElement first, BuildingElement second) {
        if (first.getFloor() == second.getFloor()) {
            return true;
        }

        // Cross-floor links are allowed only through staircase passages.
        if (first instanceof Passage p1 && second instanceof Passage p2) {
            return p1.getType() == PassageType.STAIRCASE
                    && p2.getType() == PassageType.STAIRCASE;
        }

        return false;
    }

    private void connectRoomToRoomViaPassage(Room first, Room second) {
        int floor = first.getFloor();

        boolean involvesExit = first instanceof Exit || second instanceof Exit;
        int capacity = involvesExit ? 20 : 10;

        String baseName = involvesExit
                ? accessPassageName(first, second)
                : "Liaison " + first.getName() + "-" + second.getName();

        String passageName = uniqueIntermediatePassageName(baseName);

        double x = (first.getX() + second.getX()) / 2.0;
        double y = (first.getY() + second.getY()) / 2.0;

        double distance = visualDistanceAsModelDistance(first, second);

        Passage link = new Passage(
                passageName,
                capacity,
                floor,
                1.0,
                PassageType.CORRIDOR,
                distance
        );

        link.setPosition(x, y);

        graph.addElement(link);
        graph.addPassage(link);

        connectRoomToPassage(first, link, capacity);
        connectRoomToPassage(second, link, capacity);
    }

    private String accessPassageName(Room first, Room second) {
        Room exitSide = first instanceof Exit ? first : second;
        Room roomSide = first instanceof Exit ? second : first;

        return "Accès " + roomSide.getName() + "-" + exitSide.getName();
    }

    private String uniqueIntermediatePassageName(String baseName) {
        String name = baseName;
        int index = 2;

        while (findElementByName(name) != null) {
            name = baseName + " " + index;
            index++;
        }

        return name;
    }

    private boolean roomToRoomConnectionExists(Room first, Room second) {
        for (Door doorA : first.getDoors()) {
            Passage passageA = doorA.getPassage();

            if (passageA == null) {
                continue;
            }

            for (Door doorB : second.getDoors()) {
                if (doorB.getPassage() == passageA) {
                    return true;
                }
            }
        }

        return false;
    }

    private double visualDistanceAsModelDistance(BuildingElement first, BuildingElement second) {
        double dx = first.getX() - second.getX();
        double dy = first.getY() - second.getY();

        return Math.max(1.0, Math.sqrt(dx * dx + dy * dy) / 40.0);
    }

    private boolean connectionExists(BuildingElement first, BuildingElement second) {
        if (first instanceof Room roomA && second instanceof Room roomB) {
            return roomToRoomConnectionExists(roomA, roomB);
        }
        if (first instanceof Room && second instanceof Passage) {
            Room room = (Room) first;
            Passage passage = (Passage) second;

            return room.getDoors().stream()
                    .anyMatch(d -> d.getPassage().equals(passage));
        }

        if (first instanceof Passage && second instanceof Room) {
            return connectionExists(second, first);
        }

        if (first instanceof Passage && second instanceof Passage) {
            String name1 = first.getName() + "↔" + second.getName();
            String name2 = second.getName() + "↔" + first.getName();

            return graph.getElements().stream()
                    .anyMatch(el -> el.getName().equals(name1) || el.getName().equals(name2));
        }

        return false;
    }

    /**
     * Removes an element (node) by name from the graph.
     *
     * <p>
     * Agents located inside a removed node are relocated according to priorities:
     * previous node in path, next node in path, adjacent nodes, fallback visible node.
     * Connected doors/virtual junctions are also cleaned up.
     * </p>
     *
     * @param name name of the element to remove
     */
    public void removeNode(String name) {
        BuildingElement toRemove = findElementByName(name);
        if (toRemove == null) {
            return;
        }

        BuildingElement adjacent = findAdjacentElement(toRemove);

        // 1. Move agents located inside the removed node.
        for (Agent agent : new java.util.ArrayList<>(graph.getAgents())) {
            if (agent.getCurrentLocation() != null
                    && agent.getCurrentLocation().equals(toRemove)) {

                BuildingElement target = findPreviousOrAdjacentElement(agent, toRemove);

                if (target != null) {
                    moveAgentToElement(agent, target);
                } else {
                    resetAgentRoute(agent);
                }
            } else {
                resetAgentRoute(agent);
            }
        }

        // 2. Remove connected doors/edges.
        if (toRemove instanceof Room) {
            Room room = (Room) toRemove;

            for (Door door : new java.util.ArrayList<>(room.getDoors())) {
                Passage passage = door.getPassage();
                if (passage != null) {
                    passage.getConnectedDoors().remove(door);
                }
            }

            room.getDoors().clear();
        }

        if (toRemove instanceof Passage) {
            Passage passage = (Passage) toRemove;

            for (Door door : new java.util.ArrayList<>(passage.getConnectedDoors())) {
                Room room = door.getRoom();
                if (room != null) {
                    room.getDoors().remove(door);
                }
            }

            passage.getConnectedDoors().clear();
            graph.removePassage(passage);
        }

        // 3. Remove virtual junction nodes that have this element as an exact
        //    endpoint. The junction name format is "A↔B"; we compare the two
        //    endpoints exactly (not by substring) so deleting "Hall 1" never
        //    removes an unrelated junction such as "Hall 10↔Hall 2".
        for (BuildingElement el : new java.util.ArrayList<>(graph.getElements())) {
            String elName = el.getName();
            if (!elName.contains("↔")) {
                continue;
            }
            String[] endpoints = elName.split("↔", 2);
            if (endpoints.length == 2
                    && (endpoints[0].equals(name) || endpoints[1].equals(name))) {
                graph.removeElement(el);
            }
        }

        // 4. Remove the node itself.
        graph.removeElement(toRemove);
    }

    /**
     * Updates an existing node's name and capacity.
     *
     * @param oldName existing element name
     * @param newName new name to assign
     * @param newCapacity new maximum capacity
     */
    public void updateNode(String oldName, String newName, int newCapacity) {
        BuildingElement element = findElementByName(oldName);

        if (element == null) {
            return;
        }

        element.setName(newName);
        element.setMaxCapacity(newCapacity);

        clearAgentRoutes();
    }

    /**
     * Updates the spatial position of a graph element. This method is used by
     * the view when the user moves a node on screen.
     *
     * @param name element name
     * @param x horizontal coordinate
     * @param y vertical coordinate
     */
    public void updateNodePosition(String name, double x, double y) {
        BuildingElement element = findElementByName(name);

        if (element == null) {
            return;
        }

        element.setPosition(x, y);
        clearAgentRoutes();
    }

    /**
     * Generates a unique random room name.
     */
    private String generateRoomName(RoomType type) {
        String prefix;

        if (type == RoomType.AMPHITHEATER) {
            prefix = "Amphi";
        } else if (type == RoomType.OFFICE) {
            prefix = "Bureau";
        } else {
            prefix = "Salle";
        }

        int index = 1;
        String name;

        do {
            name = prefix + " " + index;
            index++;
        } while (findElementByName(name) != null);

        return name;
    }

    private RoomType randomRoomType() {
        double r = Math.random();

        if (r < 0.15) {
            return RoomType.AMPHITHEATER;
        }

        if (r < 0.65) {
            return RoomType.OFFICE;
        }

        return RoomType.CLASSROOM;
    }

    private double[] generateFreePosition(int floor, java.util.List<Passage> floorPassages) {
        for (int attempts = 0; attempts < 500; attempts++) {
            Passage anchor = floorPassages.get((int) (Math.random() * floorPassages.size()));

            double angle = Math.random() * Math.PI * 2.0;
            double radius = 70.0 + Math.random() * 100.0;

            double x = anchor.getX() + Math.cos(angle) * radius;
            double y = anchor.getY() + Math.sin(angle) * radius;

            if (!isTooCloseToExistingNode(x, y, floor)) {
                return new double[]{x, y};
            }
        }

        Passage fallback = floorPassages.get(0);
        int count = graph.getElements().size();

        double x = fallback.getX() + 90 + (count % 3) * 70;
        double y = fallback.getY() + 60 + ((count / 3) % 3) * 60;

        return new double[]{x, y};
    }

    private boolean isTooCloseToExistingNode(double x, double y, int floor) {
        for (BuildingElement el : graph.getElements()) {
            if (el.getName().contains("↔")) {
                continue;
            }

            if (el.getFloor() != floor) {
                continue;
            }

            double ex = el.getX();
            double ey = el.getY();

            if (ex == 0.0 && ey == 0.0) {
                continue;
            }

            double dx = ex - x;
            double dy = ey - y;

            if (Math.sqrt(dx * dx + dy * dy) < 85) {
                return true;
            }
        }

        return false;
    }

    private Exit findBestExitForAgent(Agent agent, java.util.Map<Exit, Integer> plannedExitLoad) {
        if (agent == null || agent.getCurrentLocation() == null) {
            return null;
        }

        Exit bestExit = null;
        double bestScore = Double.MAX_VALUE;

        for (BuildingElement el : graph.getElements()) {
            if (!(el instanceof Exit exit) || el.isBlocked()) {
                continue;
            }

            List<BuildingElement> path = RoutingSettings.isUseFastestPath()
                    ? PathFinder.calculateFastestPath(
                            agent.getCurrentLocation(),
                            exit,
                            agent.getMaxSpeed()
                    )
                    : PathFinder.calculateShortestPath(
                            agent.getCurrentLocation(),
                            exit
                    );

            if (path == null || path.isEmpty()) {
                continue;
            }

            double pathScore = RoutingSettings.isUseFastestPath()
                    ? estimateTravelTime(path, agent)
                    : path.size();

            int alreadyAssigned = plannedExitLoad.getOrDefault(exit, 0);

            double loadPenalty = alreadyAssigned * 2.0;

            double score = pathScore + loadPenalty;

            if (score < bestScore) {
                bestScore = score;
                bestExit = exit;
            }
        }

        return bestExit;
    }

    private Passage findNearestPassageByPosition(double x, double y) {
        Passage nearest = null;
        double bestDistance = Double.MAX_VALUE;

        for (Passage passage : graph.getPassages()) {
            if (passage.getName().contains("↔")) {
                continue;
            }

            double dx = passage.getX() - x;
            double dy = passage.getY() - y;
            double distance = Math.sqrt(dx * dx + dy * dy);

            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = passage;
            }
        }

        return nearest;
    }

    private Passage findNearestPassageByPosition(double x, double y, int floor) {
        Passage nearest = null;
        double bestDistance = Double.MAX_VALUE;

        for (Passage passage : graph.getPassages()) {
            if (passage.getName().contains("↔")) {
                continue;
            }

            if (passage.getFloor() != floor) {
                continue;
            }

            double dx = passage.getX() - x;
            double dy = passage.getY() - y;
            double distance = Math.sqrt(dx * dx + dy * dy);

            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = passage;
            }
        }

        return nearest;
    }

    // ── Edge Management ───────────────────────────────────
    /**
     * Adds an edge (Door) between a Room and a Passage.
     *
     * @param roomName name of the room
     * @param passageName name of the passage
     */
    public void addEdge(String roomName, String passageName) {
        Room room = null;
        Passage passage = null;
        for (BuildingElement el : graph.getElements()) {
            if (el instanceof Room && el.getName().equals(roomName)) {
                room = (Room) el;
            }
            if (el instanceof Passage && el.getName().equals(passageName)) {
                passage = (Passage) el;
            }
        }
        if (room != null && passage != null) {
            Door door = new Door(room, passage);
            room.addDoor(door);
            passage.addDoor(door);
        }
    }

    /**
     * Moves an agent to another building element after a forced relocation.
     *
     * <p>
     * If the destination element becomes overcrowded, the agent receives a
     * two-cycle delay before being allowed to enter a corridor.
     * </p>
     *
     * @param agent the agent to move
     * @param target the destination building element
     */
    private void moveAgentToElement(Agent agent, BuildingElement target) {
        if (agent == null || target == null) {
            return;
        }

        BuildingElement current = agent.getCurrentLocation();

        if (current != null) {
            current.agentLeaves();
        }

        target.agentEnters(agent.getMaxSpeed());
        agent.setCurrentLocation(target);

        agent.setPath(new java.util.ArrayList<>());
        agent.setDestination(null);
        agent.setProgress(0.0);

        // Forced relocation may create overcrowding.
        // The two-cycle delay is applied only when the destination node
        // is actually above its maximum capacity.
        if (target.isOvercrowded()) {
            agent.setWaitCycles(2);
            agent.markStrongCongestionDelayCompletedFor(target);
        } else {
            agent.setWaitCycles(0);
            agent.clearStrongCongestionDelayMarker();
        }
    }

    /**
     * Updates the maximum capacity of a passage used as a visual edge.
     *
     * @param passageName passage name
     * @param newCapacity new maximum number of agents allowed in the passage
     */
    public void updateEdgeCapacity(String passageName, int newCapacity) {
        BuildingElement element = findElementByName(passageName);

        if (!(element instanceof Passage)) {
            return;
        }

        element.setMaxCapacity(Math.max(1, newCapacity));
        clearAgentRoutes();
    }

    /**
     * Updates capacity of a door linking a room and a passage.
     *
     * @param roomName room name
     * @param passageName passage name
     * @param newCapacity new door capacity (> 0)
     */
    public void updateDoorCapacity(String roomName, String passageName, int newCapacity) {
        if (newCapacity <= 0) {
            return;
        }

        BuildingElement roomElement = findElementByName(roomName);
        BuildingElement passageElement = findElementByName(passageName);

        if (!(roomElement instanceof Room room) || !(passageElement instanceof Passage passage)) {
            return;
        }

        for (Door door : room.getDoors()) {
            if (door.getPassage() == passage) {
                door.setMaxCapacity(newCapacity);
                clearAgentRoutes();
                return;
            }
        }
    }

    /**
     * Removes the edge (Door) between a Room and a Passage, or removes a
     * virtual passage-to-passage connection.
     *
     * @param firstName first element name
     * @param secondName second element name
     */
    public void removeEdge(String firstName, String secondName) {
        BuildingElement first = findElementByName(firstName);
        BuildingElement second = findElementByName(secondName);

        if (first == null || second == null) {
            return;
        }

        if (first instanceof Room && second instanceof Passage) {
            removeDoor((Room) first, (Passage) second);
            clearAgentRoutes();
            return;
        }

        if (first instanceof Passage && second instanceof Room) {
            removeDoor((Room) second, (Passage) first);
            clearAgentRoutes();
            return;
        }

        if (first instanceof Passage && second instanceof Passage) {
            removePassageToPassageConnection((Passage) first, (Passage) second);
            clearAgentRoutes();
        }
    }

    private void removeDoor(Room room, Passage passage) {
        // If an agent is currently inside the removed passage/edge,
        // move it back to the source node of this edge.
        for (Agent agent : new java.util.ArrayList<>(graph.getAgents())) {
            if (agent.getCurrentLocation() != null
                    && agent.getCurrentLocation().equals(passage)) {

                moveAgentToElement(agent, room);
            } else {
                resetAgentRoute(agent);
            }
        }

        room.getDoors().removeIf(d -> d.getPassage().equals(passage));
        passage.getConnectedDoors().removeIf(d -> d.getRoom().equals(room));
    }

    private void removePassageToPassageConnection(Passage a, Passage b) {
        String name1 = a.getName() + "↔" + b.getName();
        String name2 = b.getName() + "↔" + a.getName();

        BuildingElement junction = graph.getElements().stream()
                .filter(el -> el.getName().equals(name1) || el.getName().equals(name2))
                .findFirst()
                .orElse(null);

        if (!(junction instanceof Room)) {
            return;
        }

        Room junctionRoom = (Room) junction;

        // If agents are currently inside this virtual edge, move them back
        // to one of the two passage endpoints.
        for (Agent agent : new java.util.ArrayList<>(graph.getAgents())) {
            if (agent.getCurrentLocation() != null
                    && agent.getCurrentLocation().equals(junctionRoom)) {

                BuildingElement target = findPreviousOrAdjacentElement(agent, junctionRoom);

                if (target == null) {
                    target = a;
                }

                moveAgentToElement(agent, target);
            } else {
                resetAgentRoute(agent);
            }
        }

        for (Door door : new java.util.ArrayList<>(junctionRoom.getDoors())) {
            door.getPassage().getConnectedDoors().remove(door);
        }

        junctionRoom.getDoors().clear();
        graph.removeElement(junctionRoom);
    }

    private void clearAgentPathsOnly() {
        for (Agent agent : graph.getAgents()) {
            if (agent.isInTransit()) {
                continue;
            }

            agent.setPath(new java.util.ArrayList<>());
            agent.setPathIndex(0);
            agent.setProgress(0.0);
        }
    }

    private void clearAgentRoutes() {
        for (Agent agent : graph.getAgents()) {
            if (agent.isInTransit()) {
                continue;
            }

            agent.setPath(new java.util.ArrayList<>());
            agent.setDestination(null);
            agent.setProgress(0.0);
        }
    }

    // ── Agent Management ──────────────────────────────────
    /**
     * Adds a Person agent at the given location.
     *
     * @param name agent name
     * @param locationName name of the location element
     * @param maxSpeed maximal walking speed
     * @param behavior behavior enum for movement rules
     * @param densityTolerance tolerance threshold for dense crowds
     */
    public void addPersonAgent(String name, String locationName,
            double maxSpeed, Behavior behavior, double densityTolerance) {
        BuildingElement location = findElementByName(locationName);
        if (location != null) {
            Person person = new Person(name, location, maxSpeed, behavior, densityTolerance);
            person.setStrategy(new EvacuateStrategy());
            graph.addAgent(person);
        }
    }

    /**
     * Removes an agent by name.
     *
     * @param agentName name of the agent to remove
     */
    public void removeAgent(String agentName) {
        graph.getAgents().stream()
                .filter(a -> a.getName().equals(agentName))
                .findFirst()
                .ifPresent(graph::removeAgent);
    }

    /**
     * Updates agent properties.
     *
     * @param oldName current agent name
     * @param newName new name to assign
     * @param locationName new location name
     * @param maxSpeed new max speed
     * @param behavior new behavior
     * @param densityTolerance new density tolerance
     */
    public void updateAgent(String oldName, String newName, String locationName,
            double maxSpeed, Behavior behavior, double densityTolerance) {
        BuildingElement location = findElementByName(locationName);
        graph.getAgents().stream()
                .filter(a -> a.getName().equals(oldName))
                .findFirst()
                .ifPresent(agent -> {
                    agent.setName(newName);

                    if (location != null && agent.getCurrentLocation() != location) {
                        if (agent.getCurrentLocation() != null) {
                            agent.getCurrentLocation().agentLeaves();
                        }
                        location.agentEnters(maxSpeed);
                        agent.setCurrentLocation(location);
                    }

                    agent.setMaxSpeed(maxSpeed);
                    agent.setBehavior(behavior);
                    agent.setDensityTolerance(densityTolerance);
                    agent.setPath(new java.util.ArrayList<>());
                    agent.setDestination(null);
                    agent.setProgress(0.0);
                });
    }

    /**
     * Adds random agents at random valid locations.
     *
     * @param count number of agents to add
     * @param minSpeed minimal speed for randomization
     * @param maxSpeed maximal speed for randomization
     * @param minTolerance minimal density tolerance
     * @param maxTolerance maximal density tolerance
     */
    public void addRandomAgents(
            int count,
            double minSpeed,
            double maxSpeed,
            double minTolerance,
            double maxTolerance
    ) {
        int remainingSlots = Math.max(0, 300 - graph.getAgents().size());
        int safeCount = Math.min(Math.max(0, count), Math.min(100, remainingSlots));
        java.util.List<BuildingElement> possibleLocations = graph.getElements().stream()
                .filter(element -> !element.getName().contains("↔"))
                .filter(element -> !element.isBlocked())
                .filter(element -> !(element instanceof Exit))
                .toList();

        if (possibleLocations.isEmpty()) {
            return;
        }

        double safeMinSpeed = Math.min(minSpeed, maxSpeed);
        double safeMaxSpeed = Math.max(minSpeed, maxSpeed);
        double safeMinTolerance = Math.min(minTolerance, maxTolerance);
        double safeMaxTolerance = Math.max(minTolerance, maxTolerance);

        Behavior[] behaviors = Behavior.values();

        for (int i = 0; i < safeCount; i++) {
            String name = generateAgentName();

            BuildingElement location = possibleLocations.get(
                    (int) (Math.random() * possibleLocations.size())
            );

            double speed = safeMinSpeed + Math.random() * (safeMaxSpeed - safeMinSpeed);
            double tolerance = safeMinTolerance + Math.random() * (safeMaxTolerance - safeMinTolerance);
            Behavior behavior = behaviors[(int) (Math.random() * behaviors.length)];

            Person person = new Person(name, location, speed, behavior, tolerance);
            person.setStrategy(new EvacuateStrategy());
            person.setDestination(null);

            graph.addAgent(person);
        }
    }

    private String generateAgentName() {
        int index = graph.getAgents().size() + 1;

        while (true) {
            String candidateName = "Agent " + index;

            boolean alreadyExists = graph.getAgents().stream()
                    .anyMatch(agent -> agent.getName().equalsIgnoreCase(candidateName));

            if (!alreadyExists) {
                return candidateName;
            }

            index++;
        }
    }

    /**
     * Adds random room-like nodes on the first floor for backward
     * compatibility.
     *
     * @param count number of nodes to add
     */
    public void addRandomNodes(int count) {
        addRandomNodes(count, 1);
    }

    /**
     * Adds random room-like nodes on a specific floor and connects each one to
     * the nearest passage on the same floor.
     *
     * @param count number of nodes to add
     * @param floor floor index where to add them
     */
    public void addRandomNodes(int count, int floor) {
        int safeCount = Math.min(Math.max(0, count), 50);
        int safeFloor = Math.max(0, floor);

        java.util.List<Passage> visiblePassages = graph.getPassages().stream()
                .filter(p -> !p.getName().contains("↔"))
                .filter(p -> p.getFloor() == safeFloor)
                .toList();

        if (visiblePassages.isEmpty()) {
            return;
        }

        for (int i = 0; i < safeCount; i++) {
            RoomType type = randomRoomType();
            String name = generateRoomName(type);

            double[] position = generateFreePosition(safeFloor, visiblePassages);
            double x = position[0];
            double y = position[1];

            int capacity = type == RoomType.AMPHITHEATER ? 120 : 30;

            Room room = new Room(name, capacity, safeFloor, type);
            room.setPosition(x, y);
            graph.addElement(room);

            Passage nearestPassage = findNearestPassageByPosition(x, y, safeFloor);

            if (nearestPassage != null) {
                Door door = new Door(room, nearestPassage);
                room.addDoor(door);
                nearestPassage.addDoor(door);
            }
        }

        clearAgentRoutes();
    }

    // ── Destination management ───────────────────────────
    /**
     * Assigns a random accessible destination to an agent. Called after arrival
     * to keep agents wandering during non-alert simulation.
     *
     * @param agent the agent needing a new destination
     */
    public void assignRandomDestination(Agent agent) {
        var elements = graph.getElements().stream()
                .filter(el -> !el.isBlocked())
                .filter(el -> !el.getName().contains("↔"))
                .filter(el -> !el.equals(agent.getCurrentLocation()))
                .toList();

        if (!elements.isEmpty()) {
            BuildingElement dest = elements.get((int) (Math.random() * elements.size()));
            agent.setDestination(dest);
            agent.setPath(new java.util.ArrayList<>());

            if (agent.getStrategy() == null) {
                agent.setStrategy(new EvacuateStrategy());
            }
        }
    }

    // ── Getters ───────────────────────────────────────────
    /**
     * Returns the underlying graph model managed by this controller.
     *
     * @return the Graph instance
     */
    public Graph getGraph() {
        return graph;
    }

    /**
     * Returns true if an element with the given name already exists in the
     * graph. Exposed so the view can generate unique names and verify
     * additions.
     *
     * @param name candidate element name
     * @return true if a matching element exists
     */
    public boolean hasElement(String name) {
        return findElementByName(name) != null;
    }

    // ── Helpers ───────────────────────────────────────────
    private BuildingElement findElementByName(String name) {
        return graph.getElements().stream()
                .filter(el -> el.getName().equals(name))
                .findFirst().orElse(null);
    }

    private BuildingElement findPreviousOrAdjacentElement(Agent agent, BuildingElement removed) {
        if (agent == null || removed == null) {
            return null;
        }

        List<BuildingElement> path = agent.getPath();
        int index = agent.getPathIndex();

        // First choice: move the agent back to the previous element in its path.
        if (path != null && index > 0 && index - 1 < path.size()) {
            BuildingElement previous = path.get(index - 1);

            if (previous != null && !previous.equals(removed) && graph.getElements().contains(previous)) {
                return previous;
            }
        }

        // Second choice: move the agent to the next valid element in its path.
        if (path != null && index + 1 < path.size()) {
            BuildingElement next = path.get(index + 1);

            if (next != null && !next.equals(removed) && graph.getElements().contains(next)) {
                return next;
            }
        }

        // Third choice: use any adjacent element.
        return findAdjacentElement(removed);
    }

    private BuildingElement findAdjacentElement(BuildingElement element) {
        if (element == null) {
            return null;
        }

        // If the removed node is a room, exit, office or amphitheater,
        // move the agent to one of its connected passages.
        if (element instanceof Room) {
            Room room = (Room) element;

            for (Door door : room.getDoors()) {
                if (door.getPassage() != null) {
                    return door.getPassage();
                }
            }
        }

        // If the removed node is a passage, junction or landing,
        // move the agent to one of its connected rooms.
        if (element instanceof Passage) {
            Passage passage = (Passage) element;

            for (Door door : passage.getConnectedDoors()) {
                if (door.getRoom() != null) {
                    return door.getRoom();
                }
            }
        }

        // Fallback: use the first available visible node.
        return graph.getElements().stream()
                .filter(el -> !el.equals(element))
                .filter(el -> !el.getName().contains("↔"))
                .findFirst()
                .orElse(null);
    }

    private void resetAgentRoute(Agent agent) {
        agent.setPath(new java.util.ArrayList<>());
        agent.setDestination(null);
        agent.setProgress(0.0);
    }

    /**
     * Finds the nearest Exit from a given element using PathFinder.
     *
     * @param agent agent from which to find the nearest exit
     * @return the nearest Exit or null if none reachable
     */
    private Exit findNearestExit(Agent agent) {
        if (agent == null || agent.getCurrentLocation() == null) {
            return null;
        }

        Exit nearest = null;
        double bestScore = Double.MAX_VALUE;

        for (BuildingElement el : graph.getElements()) {
            if (!(el instanceof Exit exit) || el.isBlocked()) {
                continue;
            }

            List<BuildingElement> path = RoutingSettings.isUseFastestPath()
                    ? PathFinder.calculateFastestPath(
                            agent.getCurrentLocation(),
                            exit,
                            agent.getMaxSpeed()
                    )
                    : PathFinder.calculateShortestPath(
                            agent.getCurrentLocation(),
                            exit
                    );

            if (path == null || path.isEmpty()) {
                continue;
            }

            double score = RoutingSettings.isUseFastestPath()
                    ? estimateTravelTime(path, agent)
                    : path.size();

            if (score < bestScore) {
                bestScore = score;
                nearest = exit;
            }
        }

        return nearest;
    }

    /**
     * Reports smoke at the named element by creating or updating a SmokeSensor
     * and triggering its detection.
     *
     * @param elementName name of the element where smoke is detected
     */
    public void reportSmokeAt(String elementName) {
        BuildingElement element = findElementByName(elementName);

        if (element == null) {
            return;
        }

        SmokeSensor smokeSensor = null;

        for (Sensor sensor : graph.getSensors()) {
            if (sensor instanceof SmokeSensor existingSmokeSensor
                    && sensor.getMonitoredElement() == element) {
                smokeSensor = existingSmokeSensor;
                break;
            }
        }

        if (smokeSensor == null) {
            smokeSensor = new SmokeSensor("SS-" + element.getName(), element, 30.0);
            graph.addSensor(smokeSensor);

            if (this.sensorEventCallback != null) {
                smokeSensor.addObserver(event -> this.sensorEventCallback.accept(event));
            }
        }

        smokeSensor.setSmokeLevel(smokeSensor.getThreshold() * 2.0);
        smokeSensor.detect();
    }

    private double distanceBetween(BuildingElement a, BuildingElement b) {
        if (a == null || b == null) {
            return 10.0;
        }

        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();

        return Math.max(1.0, Math.sqrt(dx * dx + dy * dy) / 40.0);
    }

    private double applyAttractivenessCost(double baseCost, BuildingElement element) {
        if (element == null) {
            return baseCost;
        }

        double score = element.getAttractivenessScore();

        if (score == 0.0) {
            return baseCost;
        }

        double clampedScore = Math.max(-10.0, Math.min(10.0, score));

        if (clampedScore > 0.0) {
            return baseCost / (1.0 + clampedScore * 0.15);
        }

        return baseCost * (1.0 + Math.abs(clampedScore) * 0.20);
    }

    private double estimateTravelTime(List<BuildingElement> path, Agent agent) {
        double total = 0.0;

        for (int i = 1; i < path.size(); i++) {
            BuildingElement element = path.get(i);

            double distance = distanceBetween(path.get(i - 1), element);
            double speedFactor = 1.0;
            double congestionFactor = 1.0;

            if (element instanceof Passage passage) {
                distance = Math.max(0.1, passage.getDistance());
                speedFactor = Math.max(0.1, passage.getSpeedFactor());

                congestionFactor = passage.getMaxCapacity() > 0
                        ? 1.0 + ((double) passage.getCurrentOccupancy() / passage.getMaxCapacity())
                        : 1.0;
            }
            double stepCost = (distance / Math.max(0.1, agent.getMaxSpeed() * speedFactor))
                    * congestionFactor;

            total += applyAttractivenessCost(stepCost, element);
        }

        return total;
    }

    /**
     * Updates the attractiveness score of an element and clears agent routes
     * so they will be recalculated.
     *
     * @param elementName element to update
     * @param score new attractiveness score (can be negative)
     */
    public void updateAttractivenessScore(String elementName, double score) {
        BuildingElement element = findElementByName(elementName);

        if (element == null) {
            return;
        }

        element.setAttractivenessScore(score);
        clearAgentRoutes();
    }

    /**
     * Registers a callback called on each sensor event. Used by AdminView to
     * display real-time sensor alerts.
     *
     * @param callback consumer invoked for each SensorEvent
     */
    public void setSensorEventCallback(Consumer<SensorEvent> callback) {
        this.sensorEventCallback = callback;

        graph.getSensors().forEach(sensor -> {
            sensor.clearObservers();
            sensor.addObserver(event -> {
                if (this.sensorEventCallback != null) {
                    this.sensorEventCallback.accept(event);
                }
            });
        });
    }

    /**
     * Initializes the campus graph from the CY Cergy Turin evacuation plan.
     *
     * <p>
     * The basement is intentionally ignored. The graph models only: ground
     * floor, first floor, second floor and third floor.
     * </p>
     */
    private void initGraph() {
        // ── Ground floor rooms ────────────────────────────────
        Room rdcReserve = new Room("RDC Réserve", 20, 0, RoomType.OFFICE);
        Room rdcOfficeNorth = new Room("RDC Bureau Nord", 15, 0, RoomType.OFFICE);
        Room rdcOfficeSouth = new Room("RDC Bureau Sud", 15, 0, RoomType.OFFICE);
        Room rdcServerRoom = new Room("RDC LT Serveurs", 10, 0, RoomType.OFFICE);
        Room rdcAmphitheater = new Room("RDC Amphithéâtre", 200, 0, RoomType.AMPHITHEATER);
        Room rdcHousing = new Room("RDC Logement", 10, 0, RoomType.OFFICE);

        // ── First floor rooms ─────────────────────────────────
        Room f1RoomNorthWest = new Room("1E Salle Nord Ouest", 35, 1, RoomType.CLASSROOM);
        Room f1RoomNorthEast = new Room("1E Salle Nord Est", 35, 1, RoomType.CLASSROOM);
        Room f1RoomSouthWest = new Room("1E Salle Sud Ouest", 35, 1, RoomType.CLASSROOM);
        Room f1RoomSouthEast = new Room("1E Salle Sud Est", 35, 1, RoomType.CLASSROOM);
        Room f1Office = new Room("1E Bureau", 15, 1, RoomType.OFFICE);

        // ── Second floor rooms ───────────────────────────────
        Room f2RoomNorthWest = new Room("2E Salle Nord Ouest", 35, 2, RoomType.CLASSROOM);
        Room f2RoomNorthEast = new Room("2E Salle Nord Est", 35, 2, RoomType.CLASSROOM);
        Room f2RoomSouthWest = new Room("2E Salle Sud Ouest", 35, 2, RoomType.CLASSROOM);
        Room f2RoomSouthEast = new Room("2E Salle Sud Est", 35, 2, RoomType.CLASSROOM);
        Room f2Office = new Room("2E Bureau", 15, 2, RoomType.OFFICE);

        // ── Third floor rooms ─────────────────────────────────
        Room f3OfficeNorth = new Room("3E Bureau Nord", 15, 3, RoomType.OFFICE);
        Room f3OfficeCenter = new Room("3E Bureau Centre", 15, 3, RoomType.OFFICE);
        Room f3OfficeSouth = new Room("3E Bureau Sud", 15, 3, RoomType.OFFICE);
        Room f3MeetingRoom = new Room("3E Salle de Réunion", 30, 3, RoomType.CLASSROOM);
        Room f3BreakRoom = new Room("3E Salle Pause", 20, 3, RoomType.CLASSROOM);
        Room f3Terrace = new Room("3E Terrasse", 25, 3, RoomType.CLASSROOM);

        // ── Ground floor passages ─────────────────────────────
        Passage rdcNorthStair = new Passage("RDC Escalier Nord", 20, 0, 0.6, PassageType.STAIRCASE, 3.0);
        Passage rdcMainStair = new Passage("RDC Escalier Principal", 25, 0, 0.6, PassageType.STAIRCASE, 3.0);
        Passage rdcSouthStair = new Passage("RDC Escalier Sud", 20, 0, 0.6, PassageType.STAIRCASE, 3.0);

        Passage rdcNorthCorridor = new Passage("RDC Couloir Nord", 40, 0, 1.0, PassageType.CORRIDOR, 10.0);
        Passage rdcCentralHall = new Passage("RDC Hall Central", 100, 0, 1.0, PassageType.HALL, 14.0);
        Passage rdcSouthCorridor = new Passage("RDC Couloir Sud", 40, 0, 1.0, PassageType.CORRIDOR, 10.0);

        // ── First floor passages ──────────────────────────────
        Passage f1NorthStair = new Passage("1E Escalier Nord", 20, 1, 0.6, PassageType.STAIRCASE, 3.0);
        Passage f1MainStair = new Passage("1E Escalier Principal", 25, 1, 0.6, PassageType.STAIRCASE, 3.0);
        Passage f1SouthStair = new Passage("1E Escalier Sud", 20, 1, 0.6, PassageType.STAIRCASE, 3.0);

        Passage f1NorthCorridor = new Passage("1E Couloir Nord", 40, 1, 1.0, PassageType.CORRIDOR, 10.0);
        Passage f1CentralHall = new Passage("1E Hall Central", 80, 1, 1.0, PassageType.HALL, 12.0);
        Passage f1SouthCorridor = new Passage("1E Couloir Sud", 40, 1, 1.0, PassageType.CORRIDOR, 10.0);

        // ── Second floor passages ─────────────────────────────
        Passage f2NorthStair = new Passage("2E Escalier Nord", 20, 2, 0.6, PassageType.STAIRCASE, 3.0);
        Passage f2MainStair = new Passage("2E Escalier Principal", 25, 2, 0.6, PassageType.STAIRCASE, 3.0);
        Passage f2SouthStair = new Passage("2E Escalier Sud", 20, 2, 0.6, PassageType.STAIRCASE, 3.0);

        Passage f2NorthCorridor = new Passage("2E Couloir Nord", 40, 2, 1.0, PassageType.CORRIDOR, 10.0);
        Passage f2CentralHall = new Passage("2E Hall Central", 80, 2, 1.0, PassageType.HALL, 12.0);
        Passage f2SouthCorridor = new Passage("2E Couloir Sud", 40, 2, 1.0, PassageType.CORRIDOR, 10.0);

        // ── Third floor passages ──────────────────────────────
        Passage f3NorthStair = new Passage("3E Escalier Nord", 20, 3, 0.6, PassageType.STAIRCASE, 3.0);
        Passage f3MainStair = new Passage("3E Escalier Principal", 25, 3, 0.6, PassageType.STAIRCASE, 3.0);
        Passage f3SouthStair = new Passage("3E Escalier Sud", 20, 3, 0.6, PassageType.STAIRCASE, 3.0);

        Passage f3NorthCorridor = new Passage("3E Couloir Nord", 40, 3, 1.0, PassageType.CORRIDOR, 10.0);
        Passage f3CentralHall = new Passage("3E Hall Central", 80, 3, 1.0, PassageType.HALL, 12.0);
        Passage f3SouthCorridor = new Passage("3E Couloir Sud", 40, 3, 1.0, PassageType.CORRIDOR, 10.0);

        // ── Ground floor exits ────────────────────────────────
        Exit exitNorth = new Exit("Sortie RDC Nord", 60);
        Exit exitCentral = new Exit("Sortie RDC Centrale", 80);
        Exit exitSouth = new Exit("Sortie RDC Sud", 60);
        Exit exitAssembly = new Exit("Sortie Point de Rassemblement", 80);

        exitNorth.setFloor(0);
        exitCentral.setFloor(0);
        exitSouth.setFloor(0);
        exitAssembly.setFloor(0);

        Exit exitF1Emergency = new Exit("Sortie Secours 1E", 50);
        Exit exitF2Emergency = new Exit("Sortie Secours 2E", 50);
        Exit exitF3Emergency = new Exit("Sortie Secours 3E", 50);

        exitF1Emergency.setFloor(1);
        exitF2Emergency.setFloor(2);
        exitF3Emergency.setFloor(3);

        // ── Positions: floors are displayed from left to right ─
        double rdcX = 220;
        double f1X = 460;
        double f2X = 700;
        double f3X = 940;

        // Ground floor positions
        rdcNorthStair.setPosition(rdcX - 70, 110);
        rdcNorthCorridor.setPosition(rdcX, 150);
        rdcCentralHall.setPosition(rdcX, 260);
        rdcSouthCorridor.setPosition(rdcX, 370);
        rdcMainStair.setPosition(rdcX - 70, 260);
        rdcSouthStair.setPosition(rdcX - 70, 430);

        rdcReserve.setPosition(rdcX - 150, 100);
        rdcOfficeNorth.setPosition(rdcX - 150, 180);
        rdcServerRoom.setPosition(rdcX + 120, 180);
        rdcOfficeSouth.setPosition(rdcX - 150, 320);
        rdcAmphitheater.setPosition(rdcX - 80, 520);
        rdcHousing.setPosition(rdcX + 120, 440);

        exitNorth.setPosition(rdcX + 160, 110);
        exitCentral.setPosition(rdcX + 170, 260);
        exitSouth.setPosition(rdcX + 160, 430);
        exitAssembly.setPosition(rdcX - 170, 520);

        // First floor positions
        f1NorthStair.setPosition(f1X - 70, 110);
        f1NorthCorridor.setPosition(f1X, 150);
        f1CentralHall.setPosition(f1X, 260);
        f1SouthCorridor.setPosition(f1X, 370);
        f1MainStair.setPosition(f1X - 70, 260);
        f1SouthStair.setPosition(f1X - 70, 430);

        f1RoomNorthWest.setPosition(f1X - 140, 130);
        f1RoomNorthEast.setPosition(f1X + 140, 130);
        f1Office.setPosition(f1X + 140, 240);
        f1RoomSouthWest.setPosition(f1X - 140, 360);
        f1RoomSouthEast.setPosition(f1X + 140, 360);

        // Second floor positions
        f2NorthStair.setPosition(f2X - 70, 110);
        f2NorthCorridor.setPosition(f2X, 150);
        f2CentralHall.setPosition(f2X, 260);
        f2SouthCorridor.setPosition(f2X, 370);
        f2MainStair.setPosition(f2X - 70, 260);
        f2SouthStair.setPosition(f2X - 70, 430);

        f2RoomNorthWest.setPosition(f2X - 140, 130);
        f2RoomNorthEast.setPosition(f2X + 140, 130);
        f2Office.setPosition(f2X + 140, 240);
        f2RoomSouthWest.setPosition(f2X - 140, 360);
        f2RoomSouthEast.setPosition(f2X + 140, 360);

        // Third floor positions
        f3NorthStair.setPosition(f3X - 70, 110);
        f3NorthCorridor.setPosition(f3X, 150);
        f3CentralHall.setPosition(f3X, 260);
        f3SouthCorridor.setPosition(f3X, 370);
        f3MainStair.setPosition(f3X - 70, 260);
        f3SouthStair.setPosition(f3X - 70, 430);

        f3OfficeNorth.setPosition(f3X + 130, 110);
        f3OfficeCenter.setPosition(f3X + 130, 210);
        f3OfficeSouth.setPosition(f3X + 130, 330);
        f3MeetingRoom.setPosition(f3X + 130, 430);
        f3BreakRoom.setPosition(f3X - 140, 340);
        f3Terrace.setPosition(f3X - 140, 210);

        exitF1Emergency.setPosition(f1X + 170, 260);
        exitF2Emergency.setPosition(f2X + 170, 260);
        exitF3Emergency.setPosition(f3X + 170, 260);

        // ── Add elements to graph ─────────────────────────────
        for (BuildingElement element : new BuildingElement[]{
            rdcReserve, rdcOfficeNorth, rdcOfficeSouth, rdcServerRoom,
            rdcAmphitheater, rdcHousing,
            f1RoomNorthWest, f1RoomNorthEast, f1RoomSouthWest, f1RoomSouthEast, f1Office,
            f2RoomNorthWest, f2RoomNorthEast, f2RoomSouthWest, f2RoomSouthEast, f2Office,
            f3OfficeNorth, f3OfficeCenter, f3OfficeSouth, f3MeetingRoom, f3BreakRoom, f3Terrace,
            rdcNorthStair, rdcMainStair, rdcSouthStair,
            rdcNorthCorridor, rdcCentralHall, rdcSouthCorridor,
            f1NorthStair, f1MainStair, f1SouthStair,
            f1NorthCorridor, f1CentralHall, f1SouthCorridor,
            f2NorthStair, f2MainStair, f2SouthStair,
            f2NorthCorridor, f2CentralHall, f2SouthCorridor,
            f3NorthStair, f3MainStair, f3SouthStair,
            f3NorthCorridor, f3CentralHall, f3SouthCorridor,
            exitNorth, exitCentral, exitSouth, exitAssembly,
            exitF1Emergency, exitF2Emergency, exitF3Emergency
        }) {
            graph.addElement(element);
        }

        for (Passage passage : new Passage[]{
            rdcNorthStair, rdcMainStair, rdcSouthStair,
            rdcNorthCorridor, rdcCentralHall, rdcSouthCorridor,
            f1NorthStair, f1MainStair, f1SouthStair,
            f1NorthCorridor, f1CentralHall, f1SouthCorridor,
            f2NorthStair, f2MainStair, f2SouthStair,
            f2NorthCorridor, f2CentralHall, f2SouthCorridor,
            f3NorthStair, f3MainStair, f3SouthStair,
            f3NorthCorridor, f3CentralHall, f3SouthCorridor
        }) {
            graph.addPassage(passage);
        }

        // ── Ground floor connections ──────────────────────────
        connectPassageToPassage(rdcNorthStair, rdcNorthCorridor, 6);
        connectPassageToPassage(rdcNorthCorridor, rdcCentralHall, 10);
        connectPassageToPassage(rdcCentralHall, rdcSouthCorridor, 12);
        connectPassageToPassage(rdcMainStair, rdcCentralHall, 8);
        connectPassageToPassage(rdcSouthCorridor, rdcSouthStair, 6);

        connectRoomToPassage(rdcReserve, rdcNorthCorridor, 4);
        connectRoomToPassage(rdcOfficeNorth, rdcNorthCorridor, 4);
        connectRoomToPassage(rdcServerRoom, rdcCentralHall, 4);
        connectRoomToPassage(rdcOfficeSouth, rdcSouthCorridor, 4);
        connectRoomToPassage(rdcAmphitheater, rdcSouthCorridor, 12);
        connectRoomToPassage(rdcHousing, rdcSouthCorridor, 4);

        connectRoomToPassage(exitNorth, rdcNorthStair, 20);
        connectRoomToPassage(exitCentral, rdcCentralHall, 30);
        connectRoomToPassage(exitSouth, rdcSouthStair, 20);
        connectRoomToPassage(exitAssembly, rdcSouthCorridor, 25);

        // ── First floor connections ───────────────────────────
        connectPassageToPassage(f1NorthStair, f1NorthCorridor, 6);
        connectPassageToPassage(f1NorthCorridor, f1CentralHall, 10);
        connectPassageToPassage(f1CentralHall, f1SouthCorridor, 12);
        connectPassageToPassage(f1MainStair, f1CentralHall, 8);
        connectPassageToPassage(f1SouthCorridor, f1SouthStair, 6);

        connectRoomToPassage(f1RoomNorthWest, f1NorthCorridor, 6);
        connectRoomToPassage(f1RoomNorthEast, f1NorthCorridor, 6);
        connectRoomToPassage(f1Office, f1CentralHall, 4);
        connectRoomToPassage(f1RoomSouthWest, f1SouthCorridor, 6);
        connectRoomToPassage(f1RoomSouthEast, f1SouthCorridor, 6);

        // ── Second floor connections ──────────────────────────
        connectPassageToPassage(f2NorthStair, f2NorthCorridor, 6);
        connectPassageToPassage(f2NorthCorridor, f2CentralHall, 10);
        connectPassageToPassage(f2CentralHall, f2SouthCorridor, 12);
        connectPassageToPassage(f2MainStair, f2CentralHall, 8);
        connectPassageToPassage(f2SouthCorridor, f2SouthStair, 6);

        connectRoomToPassage(f2RoomNorthWest, f2NorthCorridor, 6);
        connectRoomToPassage(f2RoomNorthEast, f2NorthCorridor, 6);
        connectRoomToPassage(f2Office, f2CentralHall, 4);
        connectRoomToPassage(f2RoomSouthWest, f2SouthCorridor, 6);
        connectRoomToPassage(f2RoomSouthEast, f2SouthCorridor, 6);

        // ── Third floor connections ───────────────────────────
        connectPassageToPassage(f3NorthStair, f3NorthCorridor, 6);
        connectPassageToPassage(f3NorthCorridor, f3CentralHall, 10);
        connectPassageToPassage(f3CentralHall, f3SouthCorridor, 12);
        connectPassageToPassage(f3MainStair, f3CentralHall, 8);
        connectPassageToPassage(f3SouthCorridor, f3SouthStair, 6);

        connectRoomToPassage(f3OfficeNorth, f3NorthCorridor, 4);
        connectRoomToPassage(f3OfficeCenter, f3CentralHall, 4);
        connectRoomToPassage(f3OfficeSouth, f3SouthCorridor, 4);
        connectRoomToPassage(f3MeetingRoom, f3SouthCorridor, 6);
        connectRoomToPassage(f3BreakRoom, f3SouthCorridor, 4);
        connectRoomToPassage(f3Terrace, f3CentralHall, 4);

        connectRoomToPassage(exitF1Emergency, f1CentralHall, 20);
        connectRoomToPassage(exitF2Emergency, f2CentralHall, 20);
        connectRoomToPassage(exitF3Emergency, f3CentralHall, 20);

        // ── Stair connections between floors ──────────────────
        connectPassageToPassage(rdcNorthStair, f1NorthStair, 6);
        connectPassageToPassage(f1NorthStair, f2NorthStair, 6);
        connectPassageToPassage(f2NorthStair, f3NorthStair, 6);

        connectPassageToPassage(rdcMainStair, f1MainStair, 8);
        connectPassageToPassage(f1MainStair, f2MainStair, 8);
        connectPassageToPassage(f2MainStair, f3MainStair, 8);

        connectPassageToPassage(rdcSouthStair, f1SouthStair, 6);
        connectPassageToPassage(f1SouthStair, f2SouthStair, 6);
        connectPassageToPassage(f2SouthStair, f3SouthStair, 6);

        // ── Sensors ───────────────────────────────────────────
        PresenceSensor psAmphi = new PresenceSensor("PS-RDC-Amphi", rdcAmphitheater);
        PresenceSensor psF1 = new PresenceSensor("PS-1E-Hall", f1CentralHall);
        PresenceSensor psF2 = new PresenceSensor("PS-2E-Hall", f2CentralHall);
        SmokeSensor ssServer = new SmokeSensor("SS-RDC-LT-Serveurs", rdcServerRoom, 30.0);
        SmokeSensor ssRdcHall = new SmokeSensor("SS-RDC-Hall-Central", rdcCentralHall, 40.0);
        SmokeSensor ssF3 = new SmokeSensor("SS-3E-Hall-Central", f3CentralHall, 40.0);

        for (Sensor sensor : new Sensor[]{
            psAmphi, psF1, psF2, ssServer, ssRdcHall, ssF3
        }) {
            graph.addSensor(sensor);
        }

        // ── Default agents ────────────────────────────────────
        Person p1 = new Person("Lucas", rdcAmphitheater, 1.0, Behavior.POLITE, 0.6);
        Person p2 = new Person("Samia", f1RoomNorthEast, 1.2, Behavior.FOLLOWER, 0.8);
        Person p3 = new Person("Theo", f2RoomSouthWest, 0.9, Behavior.RUDE, 0.4);
        Person p4 = new Person("Malak", f3MeetingRoom, 1.1, Behavior.POLITE, 0.7);
        Person p5 = new Person("Ines", rdcServerRoom, 1.0, Behavior.FOLLOWER, 0.7);
        SecurityAgent security = new SecurityAgent("Agent Sécurité", rdcCentralHall, rdcCentralHall);

        for (Agent agent : new Agent[]{p1, p2, p3, p4, p5, security}) {
            agent.setStrategy(new EvacuateStrategy());
            agent.setDestination(null);
            graph.addAgent(agent);
        }
    }

    /**
     * Helper: creates one corridor segment between a room-like element and a
     * passage. The capacity belongs to the corridor segment, not to exits.
     *
     * @param roomLike room, exit or virtual junction connected to the passage
     * @param passage passage connected to the room-like element
     * @param corridorCapacity maximum number of agents inside this corridor segment
     */
    private void connectRoomToPassage(BuildingElement roomLike, Passage passage, int corridorCapacity) {
        if (roomLike instanceof Room) {
            Door door = new Door((Room) roomLike, passage, corridorCapacity);
            ((Room) roomLike).addDoor(door);
            passage.addDoor(door);
        }
    }

    /**
     * Backwards-compatible helper using the default corridor capacity.
     *
     * @param roomLike room, exit or virtual junction connected to the passage
     * @param passage passage connected to the room-like element
     */
    private void connectRoomToPassage(BuildingElement roomLike, Passage passage) {
        connectRoomToPassage(roomLike, passage, 10);
    }

    /**
     * Helper: connects two passage nodes through a small virtual junction. This
     * creates two distinct corridor segments: one segment ends at the junction
     * and another segment starts from it.
     *
     * @param a first passage node
     * @param b second passage node
     * @param corridorCapacity capacity used for both corridor segments
     */
    private void connectPassageToPassage(Passage a, Passage b, int corridorCapacity) {
        int junctionFloor = a.getFloor();

        String junctionName = a.getName() + "↔" + b.getName();
        Room junction = new Room(junctionName, 20, junctionFloor);
        junction.setStatus(BlockStatus.ACCESSIBLE);
        graph.addElement(junction);

        Door firstSegment = new Door(junction, a, corridorCapacity);
        Door secondSegment = new Door(junction, b, corridorCapacity);

        junction.addDoor(firstSegment);
        junction.addDoor(secondSegment);
        a.addDoor(firstSegment);
        b.addDoor(secondSegment);
    }

    /**
     * Backwards-compatible helper using the default corridor capacity.
     *
     * @param a first passage node
     * @param b second passage node
     */
    private void connectPassageToPassage(Passage a, Passage b) {
        connectPassageToPassage(a, b, 10);
    }
}
