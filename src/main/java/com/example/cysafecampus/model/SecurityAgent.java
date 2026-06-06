package com.example.cysafecampus.model;

/**
 * Security agent responsible for physically securing a zone
 * and guiding people during an evacuation.
 * Uses GuideStrategy by default, switches to EvacuateStrategy on alert.
 */
public class SecurityAgent extends Agent {

    /** The building element this agent is responsible for */
    private BuildingElement assignedZone;

    /**
     * Constructor for SecurityAgent.
     * @param name the agent's name
     * @param currentLocation starting location
     * @param assignedZone the zone to manage
     */
    public SecurityAgent(String name, BuildingElement currentLocation, BuildingElement assignedZone) {
        super(name, currentLocation, 1.5, Behavior.RUDE, 1.0);
        this.assignedZone = assignedZone;
        this.setDestination(assignedZone);
        this.setStrategy(new GuideStrategy());
    }

    public BuildingElement getAssignedZone() { return assignedZone; }
    public void setAssignedZone(BuildingElement zone) { this.assignedZone = zone; }

    /**
     * On alert, switches to GuideStrategy to lead people out.
     * @param alert the alert type (e.g. "FIRE")
     */
    @Override
    public void update(String alert) {
        if (alert.equals("FIRE")) {
            setState(AgentState.CALM); // security stays calm
            setStrategy(new GuideStrategy());
        }
    }
}
