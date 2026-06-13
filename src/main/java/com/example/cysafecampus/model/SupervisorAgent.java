package com.example.cysafecampus.model;

/**
 * Agent responsible for supervising a specific room.
 * Subscribes to that room's events and receives EvacuationOrders from AdminAgent.
 * Guides the crowd (Person agents) in its room toward the exit.
 */
public class SupervisorAgent extends Agent {

    /** The room this supervisor is responsible for */
    private Room assignedRoom;

    /**
     * Constructor for SupervisorAgent.
     * @param name supervisor's name
     * @param currentLocation starting location (usually the assigned room)
     * @param assignedRoom the room this agent supervises
     */
    public SupervisorAgent(String name, BuildingElement currentLocation, Room assignedRoom) {
        super(name, currentLocation, 1.2, Behavior.POLITE, 0.8);
        this.assignedRoom = assignedRoom;
        this.setStrategy(new GuideStrategy());
    }

    public Room getAssignedRoom() { return assignedRoom; }

    /**
     * Receives an evacuation order from AdminAgent and acts on it.
     * Sets the destination to the first step of the exit route
     * and switches to EvacuateStrategy.
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
     * Guides occupants out of the assigned room.
     * In a full implementation, this would push Person agents along the exit route.
     */
    public void guideOccupants() {
        System.out.println(getName() + " is guiding occupants out of " + assignedRoom.getName());
        move();
    }

    /**
     * Reacts to building-wide alerts (from the legacy Observer chain on Graph).
     * @param alert the alert type (e.g. "FIRE")
     */
    @Override
    public void update(String alert) {
        if (alert.equals("FIRE")) {
            // Supervisors are trained agents: they stay calm and keep guiding.
            setState(AgentState.CALM);
            setStrategy(new EvacuateStrategy());
            setPath(new java.util.ArrayList<>());
            setProgress(0.0);
        } else {
            setState(AgentState.CALM);
        }
    }
}
