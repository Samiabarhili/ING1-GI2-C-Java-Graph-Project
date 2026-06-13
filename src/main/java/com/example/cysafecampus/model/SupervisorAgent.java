package com.example.cysafecampus.model;

/**
 * Agent responsible for supervising a specific room. Subscribes to that room's
 * events and receives EvacuationOrders from AdminAgent. Guides the crowd
 * (Person agents) in its room toward the exit.
 */
/**
 * SupervisorAgent is an Agent that is assigned responsibility for a single Room
 * and whose primary duties are to guide occupants and execute evacuation orders.
 *
 * <p>Key responsibilities and behaviors:
 * <ul>
 *   <li>Holds a reference to the assigned Room it supervises.</li>
 *   <li>Constructed with a name, a starting BuildingElement location, and the
 *       assigned Room; initializes with a guiding strategy by default.</li>
 *   <li>receiveOrder(EvacuationOrder): if the provided exit route is non-empty,
 *       the agent's destination is set to the exit-route endpoint. For high
 *       urgency orders (urgency >= 4) the agent switches to a panicked state and
 *       uses PanicStrategy; otherwise it stays calm and uses EvacuateStrategy.</li>
 *   <li>guideOccupants(): performs the action of guiding occupants out of the
 *       assigned room (in this implementation it logs the action and invokes
 *       movement); in a fuller simulation it would coordinate Person agents along
 *       an exit route.</li>
 *   <li>update(String): reacts to building alerts; for a "FIRE" alert the
 *       supervisor remains calm and adopts EvacuateStrategy. For other alerts
 *       the agent remains calm.</li>
 * </ul></p>
 *
 * <p>Notes:
 * <ul>
 *   <li>This class relies on the Agent base class for movement, state, and
 *       strategy mechanics.</li>
 *   <li>Thread-safety is not specified; external synchronization is required if
 *       instances are accessed concurrently.</li>
 * </ul></p>
 *
 * @see Agent
 * @see Room
 * @see EvacuationOrder
 * @see EvacuateStrategy
 * @see PanicStrategy
 * @see GuideStrategy
 * @since 1.0
 */
public class SupervisorAgent extends Agent {

    /**
     * The room this supervisor is responsible for
     */
    private Room assignedRoom;

    /**
     * Constructor for SupervisorAgent.
     *
     * @param name supervisor's name
     * @param currentLocation starting location (usually the assigned room)
     * @param assignedRoom the room this agent supervises
     */
    public SupervisorAgent(String name, BuildingElement currentLocation, Room assignedRoom) {
        super(name, currentLocation, 1.2, Behavior.POLITE, 0.8);
        this.assignedRoom = assignedRoom;
        this.setStrategy(new GuideStrategy());
    }

    public Room getAssignedRoom() {
        return assignedRoom;
    }

    /**
     * Receives an evacuation order from AdminAgent and acts on it. Sets the
     * destination to the first step of the exit route and switches to
     * EvacuateStrategy.
     *
     * @param order the order to execute
     */
    public void receiveOrder(EvacuationOrder order) {
        System.out.println(getName() + " received order: " + order);
        if (!order.getExitRoute().isEmpty()) {
            setDestination(order.getExitRoute().get(order.getExitRoute().size() - 1));
        }
        if (order.getUrgencyLevel() >= 4) {
            setState(AgentState.PANICKED);
            setStrategy(new PanicStrategy());
        } else {
            setState(AgentState.CALM);
            setStrategy(new EvacuateStrategy());
        }
    }

    /**
     * Guides occupants out of the assigned room. In a full implementation, this
     * would push Person agents along the exit route.
     */
    public void guideOccupants() {
        System.out.println(getName() + " is guiding occupants out of " + assignedRoom.getName());
        move();
    }

    /**
     * Reacts to building-wide alerts (from the legacy Observer chain on Graph).
     *
     * @param alert the alert type (e.g. "FIRE")
     */
    @Override
    public void update(String alert) {
        if (alert.equals("FIRE")) {
            // Supervisors are trained agents: they stay calm and keep guiding.
            setState(AgentState.CALM);
            setStrategy(new EvacuateStrategy());
        } else {
            setState(AgentState.CALM);
        }
    }
}
