package com.example.cysafecampus.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The central command agent of the simulation.
 * Implements SensorObserver: all sensors report to AdminAgent.
 * AdminAgent then decides — based on severity — whether and how
 * to alert SupervisorAgents and build an evacuation plan.
 *
 * Chain: Sensor → SensorEvent → AdminAgent → EvacuationOrder → SupervisorAgent
 */
/**
 * Administrative agent responsible for coordinating building evacuations and
 * supervising subordinate SupervisorAgent instances.
 *
 * <p>This agent observes SensorEvent notifications (implements SensorObserver)
 * and reacts to both localized sensor events and building-wide alerts coming
 * from the Graph. It builds and maintains an EvacuationPlan, dispatches
 * EvacuationOrder instances to supervisors, and can cause building elements
 * (e.g. doors) to change state in response to critical conditions.</p>
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Maintain a collection of managed SupervisorAgent instances.</li>
 *   <li>Construct and update an EvacuationPlan when hazards are detected.</li>
 *   <li>Determine nearest exits using PathFinder and create EvacuationOrder(s).</li>
 *   <li>Notify the appropriate supervisor(s) for a given evacuation order. In
 *       severe situations, notify all supervisors.</li>
 *   <li>Open emergency doors across the building on critical alerts.</li>
 *   <li>Register and deregister agents within the simulation Graph.</li>
 * </ul>
 *
 * <h3>Behavioral rules</h3>
 * <ul>
 *   <li>Sensor event severity 1–2: logged only.</li>
 *   <li>Sensor event severity 3: build an evacuation plan for the affected room.</li>
 *   <li>Sensor event severity 4–5: build plan and immediately open emergency doors
 *       and/or notify all supervisors (building-wide emergency).</li>
 * </ul>
 *
 * <h3>Side effects</h3>
 * <ul>
 *   <li>Modifies the simulation Graph (adds/removes agents and opens doors).</li>
 *   <li>Alters the current EvacuationPlan by adding EvacuationOrder objects.</li>
 *   <li>Invokes PathFinder to compute routes between rooms and exits.</li>
 * </ul>
 *
 * <h3>Usage notes</h3>
 * <ul>
 *   <li>The AdminAgent must be provided with a reference to the Graph so it
 *       can act on building elements and register agents.</li>
 *   <li>findNearestExit and PathFinder usage assumes the graph elements and
 *       connectivity are already populated.</li>
 *   <li>Not synchronized — concurrent access to supervisors, the graph, or the
 *       current plan should be externally coordinated if used from multiple threads.</li>
 * </ul>
 *
 * @see com.example.cysafecampus.model.SupervisorAgent
 * @see com.example.cysafecampus.model.EvacuationPlan
 * @see com.example.cysafecampus.model.EvacuationOrder
 * @see com.example.cysafecampus.model.Graph
 * @see com.example.cysafecampus.model.SensorObserver
 */
public class AdminAgent extends Agent implements SensorObserver {

    /** All supervisor agents managed by this admin */
    private List<SupervisorAgent> supervisors;

    /** The current evacuation plan (rebuilt on each major alert) */
    private EvacuationPlan currentPlan;

    /** Reference to the graph to open doors and access building elements */
    private Graph graph;

    /**
     * Constructor for AdminAgent.
     * @param name admin's name
     * @param currentLocation starting location
     * @param graph the simulation graph (needed to act on elements)
     */
    public AdminAgent(String name, BuildingElement currentLocation, Graph graph) {
        super(name, currentLocation, 2.0, Behavior.POLITE, 1.0);
        this.supervisors = new ArrayList<>();
        this.currentPlan = new EvacuationPlan();
        this.graph = graph;
    }

    // ── Supervisor Management ─────────────────────────────

    /**
     * Adds a supervisor agent to the admin's list.
     * Also registers the supervisor in the graph.
     * @param supervisor the supervisor to manage
     */
    public void addSupervisor(SupervisorAgent supervisor) {
        supervisors.add(supervisor);
        graph.addAgent(supervisor);
    }

    /**
     * Removes a supervisor agent from the admin's list.
     * @param supervisor the supervisor to remove
     */
    public void removeSupervisor(SupervisorAgent supervisor) {
        supervisors.remove(supervisor);
        graph.removeAgent(supervisor);
    }

    /**
     * Adds any kind of agent to the simulation graph.
     * @param agent the agent to add
     */
    public void addAgent(Agent agent) {
        graph.addAgent(agent);
    }

    /**
     * Removes any agent from the simulation graph.
     * @param agent the agent to remove
     */
    public void removeAgent(Agent agent) {
        graph.removeAgent(agent);
    }

    public List<SupervisorAgent> getSupervisors() { return supervisors; }
    public EvacuationPlan getCurrentPlan() { return currentPlan; }

    // ── SensorObserver ────────────────────────────────────

    /**
     * Called by a Sensor when it detects an event.
     * AdminAgent decides what action to take based on severity:
     *   - severity 1-2 : log only
     *   - severity 3   : build evacuation plan for affected room
     *   - severity 4-5 : build plan + immediately notify all supervisors
     *
     * @param event the sensor event to process
     */
    @Override
    public void onSensorEvent(SensorEvent event) {
        System.out.println(getName() + " received sensor event: " + event);

        if (event.getSeverity() >= 3 && event.getLocation() instanceof Room) {
            orderEvacuation((Room) event.getLocation(), event.getSeverity());
        }

        if (event.getSeverity() >= 4) {
            openEmergencyDoors();
        }
    }

    // ── Evacuation Logic ──────────────────────────────────

    /**
     * Builds or updates the evacuation plan for a specific room.
     * Calculates the exit route using PathFinder and creates an EvacuationOrder.
     * @param room the room to evacuate
     * @param severity urgency level (from the triggering sensor event)
     */
    public void orderEvacuation(Room room, int severity) {
        // Find the closest exit from this room using PathFinder
        Exit nearestExit = findNearestExit(room);
        List<BuildingElement> route = new ArrayList<>();

        if (nearestExit != null) {
            route = PathFinder.calculateShortestPath(room, nearestExit);
        }

        EvacuationOrder order = new EvacuationOrder(room, route, severity);
        currentPlan.addOrder(order);

        System.out.println(getName() + " created: " + order);
        notifySupervisors(order, severity);
    }

    /**
     * Sends an evacuation order to the supervisor responsible for the target room.
     * If severity >= 4, all supervisors are notified (building-wide emergency).
     * @param order the order to dispatch
     * @param severity urgency level
     */
    public void notifySupervisors(EvacuationOrder order, int severity) {
        for (SupervisorAgent supervisor : supervisors) {
            if (severity >= 4 || supervisor.getAssignedRoom().equals(order.getTargetRoom())) {
                supervisor.receiveOrder(order);
            }
        }
    }

    /**
     * Opens all emergency doors in the building (on critical alert).
     * Iterates over all passages in the graph and opens their connected doors.
     */
    public void openEmergencyDoors() {
        System.out.println(getName() + " is opening all emergency doors.");
        for (Passage passage : graph.getPassages()) {
            for (Door door : passage.getConnectedDoors()) {
                door.open();
            }
        }
    }

    /**
     * Builds a full evacuation plan for the entire building.
     * Creates one order per room currently occupied.
     * @return the complete evacuation plan
     */
    public EvacuationPlan buildEvacuationPlan() {
        currentPlan = new EvacuationPlan();
        for (BuildingElement element : graph.getElements()) {
            if (element instanceof Room && element.getCurrentOccupancy() > 0) {
                orderEvacuation((Room) element, 3);
            }
        }
        return currentPlan;
    }

    // ── Helpers ───────────────────────────────────────────

    /**
     * Finds the nearest Exit from a given room using PathFinder.
     * @param room the starting room
     * @return the nearest Exit, or null if none found
     */
    private Exit findNearestExit(Room room) {
        Exit nearest = null;
        int shortestPath = Integer.MAX_VALUE;

        for (BuildingElement element : graph.getElements()) {
            if (element instanceof Exit) {
                List<BuildingElement> path = PathFinder.calculateShortestPath(room, element);
                if (!path.isEmpty() && path.size() < shortestPath) {
                    shortestPath = path.size();
                    nearest = (Exit) element;
                }
            }
        }
        return nearest;
    }

    /**
     * AdminAgent reacts to building-wide alerts triggered by Graph.
     * @param alert the alert type (e.g. "FIRE")
     */
    @Override
    public void update(String alert) {
        if (alert.equals("FIRE")) {
            System.out.println(getName() + " detected FIRE alert — initiating full evacuation.");
            buildEvacuationPlan();
            openEmergencyDoors();
        }
    }
}
