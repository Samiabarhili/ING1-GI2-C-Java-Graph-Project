package com.example.cysafecampus.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a passage in the building (corridor or staircase).
 * Passages connect building elements and contain physical constraints
 * that affect agent movement.
 */
/**
 * Represents a traversable passage within the building model, such as a corridor or staircase.
 * <p>
 * A Passage is a BuildingElement that connects rooms via Doors and provides additional
 * attributes used by routing and simulation algorithms:
 * <ul>
 *   <li>speedFactor — a multiplier applied to agent movement speed when traversing the passage
 *       (&lt; 1.0 slows agents, &gt; 1.0 speeds them up).</li>
 *   <li>type — the PassageType (e.g. CORRIDOR or STAIRCASE) describing the physical kind of passage.</li>
 *   <li>distance — the physical length used for shortest-path calculations.</li>
 *   <li>connectedDoors — the doors that link this passage to rooms, allowing bidirectional movement.</li>
 *   <li>isOneWay — when true, the passage can only be traversed in a single direction.</li>
 *   <li>lanes — number of parallel lanes (1 = no overtaking, &gt;1 = overtaking possible).</li>
 * </ul>
 * <p>
 * Typical usage: create a Passage with name, capacity, floor, speed factor, type and distance,
 * attach Doors via addDoor(Door) and query its properties during routing or simulation.
 *
 * @see com.example.cysafecampus.model.BuildingElement
 * @see com.example.cysafecampus.model.Door
 * @see com.example.cysafecampus.model.PassageType
 *
 */
public class Passage extends BuildingElement {
    /** Speed multiplier for agents moving through this passage.
* Values below {@code 1.0} make movement slower, values above {@code 1.0}
* make movement faster. */
    private double speedFactor;

    /** Type of passage: CORRIDOR or STAIRCASE */
    private PassageType type;

    /** Physical length/distance of the passage, required for shortest path calculation */
    private double distance;

    /** Doors connecting this passage to rooms (allows bidirectional navigation) */
    private List<Door> connectedDoors;

    /** Defines if the passage can only be traversed in one specific direction */
    private boolean isOneWay = false;

    /** Number of parallel lanes. 1 lane = no overtaking, > 1 = overtaking possible */
    private int lanes = 1;

    /**
     * Constructor for Passage.
     * @param name The passage name
     * @param maxCapacity Maximum number of agents
     * @param floor Floor number
     * @param speedFactor Speed multiplier
     * @param type The PassageType (CORRIDOR or STAIRCASE)
     * @param distance Physical length of the passage
     */
    public Passage(String name, int maxCapacity, int floor,
                   double speedFactor, PassageType type, double distance) {
        super(name, maxCapacity);
        setFloor(floor);
        this.speedFactor = speedFactor;
        this.type = type;
        this.distance = distance;
        this.connectedDoors = new ArrayList<>();
    }

    public double getSpeedFactor() { return speedFactor; }
    public PassageType getType() { return type; }
    public double getDistance() { return distance; }
    public List<Door> getConnectedDoors() { return connectedDoors; }

    public void setSpeedFactor(double speedFactor) { this.speedFactor = speedFactor; }
    public void setDistance(double distance) { this.distance = distance; }

    public boolean isOneWay() { return isOneWay; }
    public void setOneWay(boolean isOneWay) { this.isOneWay = isOneWay; }

    public int getLanes() { return lanes; }
    public void setLanes(int lanes) { this.lanes = lanes; }

    /**
     * Links a door to this passage to allow movement.
     * @param door The door to connect to the passage
     */
    public void addDoor(Door door) {
        this.connectedDoors.add(door);
    }
}
