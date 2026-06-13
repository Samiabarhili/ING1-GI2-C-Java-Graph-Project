package com.example.cysafecampus.model;

/**
 * Specialized agent responsible for securing an assigned building zone.
 *
 * <p>
 * A security agent starts with a guiding strategy and can react to alerts by
 * staying calm and helping with evacuation.
 * </p>
 *
 * @see Agent
 * @see BuildingElement
 * @see GuideStrategy
 */
public class SecurityAgent extends Agent {

    /** Building element this agent is responsible for. */
    private BuildingElement assignedZone;

    /**
     * Constructs a security agent assigned to a specific zone.
     *
     * @param name agent name
     * @param currentLocation starting location in the building
     * @param assignedZone zone managed by this security agent
     */
    public SecurityAgent(String name, BuildingElement currentLocation, BuildingElement assignedZone) {
        super(name, currentLocation, 1.5, Behavior.RUDE, 1.0);
        this.assignedZone = assignedZone;
        this.setDestination(assignedZone);
        this.setStrategy(new GuideStrategy());
    }

    /**
     * Returns the zone assigned to this security agent.
     *
     * @return assigned building element
     */
    public BuildingElement getAssignedZone() {
        return assignedZone;
    }

    /**
     * Assigns a new zone to this security agent.
     *
     * @param zone new assigned zone
     */
    public void setAssignedZone(BuildingElement zone) {
        this.assignedZone = zone;
    }

    /**
     * Reacts to an alert message.
     *
     * @param alert alert type, for example {@code "FIRE"}
     */
    @Override
    public void update(String alert) {
        if (alert.equals("FIRE")) {
            setState(AgentState.CALM);
            setStrategy(new GuideStrategy());
        }
    }
}
