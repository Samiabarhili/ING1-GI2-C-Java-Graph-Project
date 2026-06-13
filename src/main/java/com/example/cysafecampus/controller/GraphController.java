package com.example.cysafecampus.controller;

import com.example.cysafecampus.model.*;
import java.util.function.Consumer;

import java.util.ArrayList;
import java.util.List;

import javafx.application.Platform;

/**
 * Controller linking the Graph model to the GraphView (MVC pattern).
 * Also owns the SimulationEngine and exposes play/pause/step/speed controls.
 */
public class GraphController {

    private final Graph graph;
    
    private final SimulationEngine engine;

    /** Called when a sensor fires an event — used by AdminView to update the log */
    private Consumer<SensorEvent> sensorEventCallback;

    /**
     * Constructor — builds the default campus graph and wires the simulation engine.
     * @param graph the graph model
     * @param view the main view
     */
    public GraphController(Graph graph) {
        this.graph = graph;
        
        this.engine = new SimulationEngine(graph);

        // On each tick, redraw on the JavaFX thread
        

        initGraph();
    }

    // ── Simulation Controls ───────────────────────────────

    /** Starts the simulation loop. */
    public void play() { engine.play(); }

    /** Pauses the simulation loop. */
    public void pause() { engine.pause(); }

    /** Executes one tick (step mode). */
    public void step() { engine.step(); }

    /** Sets the tick interval in ms (50–2000). */
    public void setSpeed(int intervalMs) { engine.setIntervalMs(intervalMs); }

    public boolean isRunning() { return engine.isRunning(); }
    public long getTickCount() { return engine.getTickCount(); }

    // ── Alert Controls ────────────────────────────────────

    /**
     * Triggers a fire alert: notifies all agents, updates the view.
     */
    public void triggerFireAlert() {
        graph.triggerAlert("FIRE");
        // Redirect all agents toward nearest exit
        for (Agent agent : graph.getAgents()) {
            Exit nearest = findNearestExit(agent.getCurrentLocation());
            if (nearest != null) {
                agent.setDestination(nearest);
                agent.setPath(new java.util.ArrayList<>());
            }
        }
        
    }

    /**
     * Resets all agents to CALM state and clears fire alert.
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
     * Saves the current simulation state to a binary file.
     * @param filePath destination path
     */
    public void saveSimulation(String filePath) {
        try {
            engine.pause();
            SimulationSerializer.save(graph, filePath);
            System.out.println("Simulation saved to: " + filePath);
        } catch (Exception e) {
            System.err.println("Save failed: " + e.getMessage());
        }
    }

    /**
     * Loads a simulation state from a binary file.
     * Note: creates a new graph from file — view must be refreshed after.
     * @param filePath source path
     */
    public void loadSimulation(String filePath) {
        try {
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
            
        } catch (Exception e) {
            System.err.println("Load failed: " + e.getMessage());
        }
    }

    // ── Node Management ───────────────────────────────────

    /**
     * Adds a node to the graph model.
     */
    public void addNode(String name, String type, double x, double y) {
        BuildingElement node;

        if (type.equalsIgnoreCase("Bureau")) {
            node = new Room(name, 30, 1, RoomType.OFFICE);

        } else if (type.equalsIgnoreCase("Amphi")) {
            node = new Room(name, 120, 1, RoomType.AMPHITHEATER);

        } else if (type.equalsIgnoreCase("Salle")) {
            node = new Room(name, 30, 1, RoomType.CLASSROOM);

        } else if (type.equalsIgnoreCase("Sortie")) {
            node = new Exit(name, 100);

        } else {
            node = new Room(name, 30, 1, RoomType.CLASSROOM);
        }

        node.setPosition(x, y);
        graph.addElement(node);
    }

    public void addConnection(String firstName, String secondName) {
        BuildingElement first = findElementByName(firstName);
        BuildingElement second = findElementByName(secondName);

        if (first == null || second == null || first == second) {
            return;
        }

        if (connectionExists(first, second)) {
            return;
        }

        if (first instanceof Passage && second instanceof Passage) {
            connectPassageToPassage((Passage) first, (Passage) second);
            clearAgentRoutes();
            return;
        }

        if (first instanceof Room && second instanceof Passage) {
            addDoor((Room) first, (Passage) second);
            clearAgentRoutes();
            return;
        }

        if (first instanceof Passage && second instanceof Room) {
            addDoor((Room) second, (Passage) first);
            clearAgentRoutes();
        }
    }

    private void addDoor(Room room, Passage passage) {
        Door door = new Door(room, passage);
        room.addDoor(door);
        passage.addDoor(door);
    }

    private boolean connectionExists(BuildingElement first, BuildingElement second) {
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

    public void removeNode(String name) {
        BuildingElement toRemove = findElementByName(name);
        if (toRemove == null) return;

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

        // 3. Remove virtual junction nodes related to the removed element.
        for (BuildingElement el : new java.util.ArrayList<>(graph.getElements())) {
            if (el.getName().contains("↔") && el.getName().contains(name)) {
                graph.removeElement(el);
            }
        }

        // 4. Remove the node itself.
        graph.removeElement(toRemove);
    }



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
     * Updates the spatial position of a graph element.
     * This method is used by the view when the user moves a node on screen.
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

    /** Adds random nodes and connects them. */
    public void addRandomNodes(int count) {
        java.util.List<Passage> visiblePassages = graph.getPassages().stream()
            .filter(p -> !p.getName().contains("↔"))
            .toList();

        if (visiblePassages.isEmpty()) {
            return;
        }

        for (int i = 0; i < count; i++) {
            RoomType type = randomRoomType();
            String name = generateRoomName(type);

            double[] position = generateFreePosition();
            double x = position[0];
            double y = position[1];

            int capacity = type == RoomType.AMPHITHEATER ? 120 : 30;
            Room room = new Room(name, capacity, 1, type);
            room.setPosition(x, y);
            graph.addElement(room);

            Passage nearestPassage = findNearestPassageByPosition(x, y);

            if (nearestPassage != null) {
                Door door = new Door(room, nearestPassage);
                room.addDoor(door);
                nearestPassage.addDoor(door);
            }
        }

        clearAgentRoutes();
    }

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

    private double[] generateFreePosition() {
        double x = 0;
        double y = 0;

        for (int attempts = 0; attempts < 500; attempts++) {
            x = 100 + Math.random() * 620;
            y = 80 + Math.random() * 360;

            if (!isTooCloseToExistingNode(x, y)) {
                return new double[]{x, y};
            }
        }

        // If no perfect position is found, gradually offset the node.
        int count = graph.getElements().size();
        x = 120 + (count * 90) % 650;
        y = 90 + ((count * 90) / 650) * 90;

        return new double[]{x, y};
    }

    private boolean isTooCloseToExistingNode(double x, double y) {
        for (BuildingElement el : graph.getElements()) {
            if (el.getName().contains("↔")) continue;

            double ex = el.getX();
            double ey = el.getY();

            // Ignore only elements that truly have no stored position.
            if (ex == 0.0 && ey == 0.0) continue;

            double dx = ex - x;
            double dy = ey - y;

            if (Math.sqrt(dx * dx + dy * dy) < 120) {
                return true;
            }
        }

        return false;
    }

    private Passage findNearestPassageByPosition(double x, double y) {
        Passage nearest = null;
        double bestDistance = Double.MAX_VALUE;

        for (Passage passage : graph.getPassages()) {
            if (passage.getName().contains("↔")) continue;

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

    /** Adds an edge (Door) between a Room and a Passage. */
    public void addEdge(String roomName, String passageName) {
        Room room = null;
        Passage passage = null;
        for (BuildingElement el : graph.getElements()) {
            if (el instanceof Room && el.getName().equals(roomName)) room = (Room) el;
            if (el instanceof Passage && el.getName().equals(passageName)) passage = (Passage) el;
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
     * <p>If the destination element becomes overcrowded, the agent receives
     * a two-cycle delay before being allowed to enter a corridor.</p>
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
     * Removes the edge (Door) between a Room and a Passage.
     * Agents currently in the Passage are relocated to the Room source node,
     * as required by the specification.
     * @param roomName the room side of the edge
     * @param passageName the passage side of the edge
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

    private void clearAgentRoutes() {
        for (Agent agent : graph.getAgents()) {
            agent.setPath(new java.util.ArrayList<>());
            agent.setDestination(null);
            agent.setProgress(0.0);
        }
    }

    // ── Agent Management ──────────────────────────────────

    /** Adds a Person agent at the given location. */
    public void addPersonAgent(String name, String locationName,
                               double maxSpeed, Behavior behavior, double densityTolerance) {
        BuildingElement location = findElementByName(locationName);
        if (location != null) {
            Person person = new Person(name, location, maxSpeed, behavior, densityTolerance);
            person.setStrategy(new EvacuateStrategy());
            graph.addAgent(person);
        }
    }

    /** Removes an agent by name. */
    public void removeAgent(String agentName) {
        graph.getAgents().stream()
            .filter(a -> a.getName().equals(agentName))
            .findFirst()
            .ifPresent(graph::removeAgent);
    }

    /** Updates agent properties. */
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

    /** Adds random agents with speed and tolerance within given ranges. */
    public void addRandomAgents(int count, double minSpeed, double maxSpeed,
                                double minTolerance, double maxTolerance) {
        var elements = graph.getElements();
        if (elements.isEmpty()) return;
        Behavior[] behaviors = Behavior.values();
        for (int i = 0; i < count; i++) {
            String name = "Agent_" + System.currentTimeMillis() + "_" + i;
            BuildingElement loc = elements.get((int) (Math.random() * elements.size()));
            double speed = minSpeed + Math.random() * (maxSpeed - minSpeed);
            double tolerance = minTolerance + Math.random() * (maxTolerance - minTolerance);
            Behavior b = behaviors[(int) (Math.random() * behaviors.length)];
            Person rp = new Person(name, loc, speed, b, tolerance);
            rp.setStrategy(new EvacuateStrategy());
            graph.addAgent(rp);
        }
    }

    // ── Destination management ───────────────────────────

    /**
     * Assigns a random accessible destination to an agent.
     * Called after arrival to keep agents wandering during non-alert simulation.
     * @param agent the agent needing a new destination
     */
    public void assignRandomDestination(Agent agent) {
        var elements = graph.getElements().stream()
            .filter(el -> !el.isBlocked() && !el.getName().contains("↔"))
            .filter(el -> !el.equals(agent.getCurrentLocation()))
            .toList();
        if (!elements.isEmpty()) {
            BuildingElement dest = elements.get((int)(Math.random() * elements.size()));
            agent.setDestination(dest);
            agent.setPath(new java.util.ArrayList<>());
            // Always ensure agent has a strategy to actually move
            if (agent.getStrategy() == null) {
                agent.setStrategy(new EvacuateStrategy());
            }
        }
    }

    // ── Getters ───────────────────────────────────────────

    public Graph getGraph() { return graph; }

    // ── Helpers ───────────────────────────────────────────

    private BuildingElement findElementByName(String name) {
        return graph.getElements().stream()
            .filter(el -> el.getName().equals(name))
            .findFirst().orElse(null);
    }


    private BuildingElement findPreviousOrAdjacentElement(Agent agent, BuildingElement removed) {
        if (agent == null || removed == null) return null;

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
        if (element == null) return null;

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
     */
    private Exit findNearestExit(BuildingElement from) {
        Exit nearest = null;
        int best = Integer.MAX_VALUE;
        for (BuildingElement el : graph.getElements()) {
            if (el instanceof Exit) {
                var path = PathFinder.calculateShortestPath(from, el);
                if (!path.isEmpty() && path.size() < best) {
                    best = path.size();
                    nearest = (Exit) el;
                }
            }
        }
        return nearest;
    }

    /**
     * Registers a callback called on each sensor event.
     * Used by AdminView to display real-time sensor alerts.
     */
    public void setSensorEventCallback(Consumer<SensorEvent> callback) {
        this.sensorEventCallback = callback;
        // Wire AdminAgent as SensorObserver on all sensors
        graph.getSensors().forEach(sensor ->
            sensor.addObserver(event -> {
                if (callback != null) callback.accept(event);
            }));
    }

    /**
     * Initializes the campus graph from the real CY Tech floor plan.
     */
    private void initGraph() {
        // ── Rooms ──────────────────────────────────────────
        Room storage    = new Room("Réserve",       20, 1, RoomType.OFFICE);
        Room office1    = new Room("Bureau 1",      15, 1, RoomType.OFFICE);
        Room office2    = new Room("Bureau 2",      15, 1, RoomType.OFFICE);
        Room office3    = new Room("Bureau 3",      15, 1, RoomType.OFFICE);
        Room serverRoom = new Room("LT Serveurs",   10, 1, RoomType.OFFICE);
        Room amphitheater = new Room("Amphithéâtre", 200, 1, RoomType.AMPHITHEATER);
        Room housing    = new Room("Logement",      10, 1, RoomType.OFFICE);

        // ── Junctions / Passages ───────────────────────────
        // These elements are graph nodes representing intersections/paliers.
        // The visual edges between them represent corridors/passages.
        Passage mainHall      = new Passage("Jonction Centrale", 100, 3, 1.0, PassageType.HALL,      12.0);
        Passage northJunction = new Passage("Jonction Nord",      40, 2, 1.0, PassageType.CORRIDOR,  10.0);
        Passage southJunction = new Passage("Jonction Sud",       40, 2, 1.0, PassageType.CORRIDOR,  10.0);
        Passage staircase1    = new Passage("Palier Esc. 1",      20, 1, 0.6, PassageType.STAIRCASE,  8.0);
        Passage staircase2    = new Passage("Palier Esc. 2",      20, 1, 0.6, PassageType.STAIRCASE,  8.0);

        // ── Exits ──────────────────────────────────────────
        Exit exitEast1 = new Exit("Sortie Est 1", 50);
        Exit exitEast2 = new Exit("Sortie Est 2", 50);
        Exit exitEast3 = new Exit("Sortie Est 3", 50);
        Exit exitWest  = new Exit("Sortie Ouest", 50);

        // ── Positions for graph view ───────────────────────
        storage.setPosition(90, 80);
        office1.setPosition(90, 180);
        office2.setPosition(90, 280);
        office3.setPosition(90, 380);

        staircase1.setPosition(260, 80);
        northJunction.setPosition(330, 230);
        staircase2.setPosition(470, 230);

        serverRoom.setPosition(600, 110);
        mainHall.setPosition(600, 310);

        exitEast1.setPosition(820, 190);
        exitEast2.setPosition(820, 310);
        exitEast3.setPosition(820, 430);

        exitWest.setPosition(90, 520);
        amphitheater.setPosition(300, 520);
        southJunction.setPosition(500, 520);
        housing.setPosition(700, 520);

        // ── Add to graph ───────────────────────────────────
        for (BuildingElement el : new BuildingElement[]{
            storage, office1, office2, office3, serverRoom, amphitheater, housing,
            mainHall, northJunction, southJunction, staircase1, staircase2,
            exitEast1, exitEast2, exitEast3, exitWest
        }) {
            graph.addElement(el);
        }

        graph.addPassage(mainHall);
        graph.addPassage(northJunction);
        graph.addPassage(southJunction);
        graph.addPassage(staircase1);
        graph.addPassage(staircase2);

        // ── Doors / graph connections ──────────────────────
        // North part
        connectRoomToPassage(storage, staircase1, 8);
        connectPassageToPassage(staircase1, northJunction, 8);

        connectRoomToPassage(office1, northJunction, 6);
        connectRoomToPassage(office2, northJunction, 6);

        connectPassageToPassage(northJunction, staircase2, 10);
        connectPassageToPassage(staircase2, mainHall, 10);

        // Central part
        connectRoomToPassage(serverRoom, mainHall, 6);
        connectRoomToPassage(office3, mainHall, 6);

        connectRoomToPassage(exitEast1, mainHall, 20);
        connectRoomToPassage(exitEast2, mainHall, 20);
        connectRoomToPassage(exitEast3, mainHall, 20);

        // South part
        connectRoomToPassage(amphitheater, southJunction, 12);
        connectRoomToPassage(housing, southJunction, 6);
        connectPassageToPassage(southJunction, mainHall, 12);

        // West emergency exit
        connectRoomToPassage(exitWest, southJunction, 20);
        // ── Sensors ────────────────────────────────────────
        PresenceSensor ps1 = new PresenceSensor("PS-Amphi", amphitheater);
        PresenceSensor ps2 = new PresenceSensor("PS-B1", office1);
        SmokeSensor ss1 = new SmokeSensor("SS-LT", serverRoom, 30.0);
        SmokeSensor ss2 = new SmokeSensor("SS-Jonction Centrale", mainHall, 40.0);

        for (var s : new Sensor[]{ps1, ps2, ss1, ss2}) {
            graph.addSensor(s);
        }

        // ── Agents ─────────────────────────────────────────
        Person p1 = new Person("Lucas", office1, 1.0, Behavior.POLITE, 0.6);
        Person p2 = new Person("Samia", office2, 1.2, Behavior.FOLLOWER, 0.8);
        Person p3 = new Person("Theo", amphitheater, 0.9, Behavior.RUDE, 0.4);
        Person p4 = new Person("Malak", office3, 1.1, Behavior.POLITE, 0.7);
        SecurityAgent sec = new SecurityAgent("Agent 01", mainHall, mainHall);

        // Give agents a strategy 
        p1.setStrategy(new EvacuateStrategy());
        p2.setStrategy(new EvacuateStrategy());
        p3.setStrategy(new EvacuateStrategy());
        p4.setStrategy(new EvacuateStrategy());
        sec.setStrategy(new EvacuateStrategy());


        p1.setDestination(null);
        p2.setDestination(null);
        p3.setDestination(null);
        p4.setDestination(null);
        sec.setDestination(null);

        for (Agent a : new Agent[]{p1, p2, p3, p4, sec}) {
            graph.addAgent(a);
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
        String junctionName = a.getName() + "↔" + b.getName();
        Room junction = new Room(junctionName, 20, 1);
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
