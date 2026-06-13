package com.example.cysafecampus.model;

import java.io.Serializable;

/**
 * Abstract class representing a physical space in the building.
 */
/**
 * Abstract base model for an element within a building (for example: rooms, corridors,
 * entrances, stairs). Provides common state and behavior shared by all building
 * elements such as occupancy tracking, capacity, accessibility status, spatial position,
 * simple attractiveness scoring and basic usage statistics.
 *
 * <p>Responsibilities:
 * - Maintain a human-readable name and maximum capacity.
 * - Track the current occupancy and provide helpers to increment/decrement it.
 * - Record aggregate statistics for agents that pass through (count and sum of speeds)
 *   to compute an average speed.
 * - Store a BlockStatus that indicates whether the element is accessible or blocked.
 * - Store a 2D position (x,y) and a floor index for layout and persistence.
 * - Be serializable so instances can be persisted and restored.
 *
 * <p>Behavior notes:
 * - agentEnters(double agentSpeed) increments current occupancy, increments the total
 *   agents passed counter and adds the provided speed to the running speed sum.
 * - agentLeaves() decrements occupancy but will not reduce it below zero.
 * - setCurrentOccupancy(int) is intended for use by serializers and clamps occupancy to
 *   a non-negative value.
 * - getAverageSpeed() returns totalSpeedSum / totalAgentsPassed when at least one agent
 *   has passed, otherwise it returns 0.0.
 * - isFull() is true when current occupancy is greater than or equal to max capacity.
 * - isOvercrowded() is true when occupancy strictly exceeds capacity.
 * - isBlocked() is true when the element's status equals BlockStatus.BLOCKED.
 *
 * <p>Default constructor-initialized values:
 * - currentOccupancy = 0
 * - status = BlockStatus.ACCESSIBLE
 * - attractivenessScore = 0.0
 * - totalAgentsPassed = 0
 * - totalSpeedSum = 0.0
 * - position = (0.0, 0.0)
 * - floor = 0
 *
 * <p>Subclassing:
 * Concrete subclasses should extend this class to provide element-specific attributes
 * and behavior (for instance special capacity rules, event callbacks, or rendering
 * metadata). If subclasses introduce additional serializable fields, consider updating
 * serialVersionUID to preserve compatibility expectations.
 *
 * @implNote This class implements java.io.Serializable; a serialVersionUID is provided
 *           to control the serialized form.
 * @see BlockStatus
 */
public abstract class BuildingElement implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;
    private int maxCapacity;
    private int currentOccupancy;
    private BlockStatus status;
    private double attractivenessScore;
    private double x;
    private double y;

    /** Floor number where this element is located (0 = RDC, 1 = 1er, 2 = 2e). */
    private int floor;

    /** Total agents that have passed through this element (for statistics) */
    private int totalAgentsPassed;

    /** Sum of speeds of all agents that passed (for average speed stat) */
    private double totalSpeedSum;

    public BuildingElement(String name, int maxCapacity) {
        this.name = name;
        this.maxCapacity = maxCapacity;
        this.currentOccupancy = 0;
        this.status = BlockStatus.ACCESSIBLE;
        this.attractivenessScore = 0.0;
        this.totalAgentsPassed = 0;
        this.totalSpeedSum = 0.0;
        this.x = 0.0;
        this.y = 0.0;
        this.floor = 0;
    }

    public String getName() { return name; }
    public int getMaxCapacity() { return maxCapacity; }
    public int getCurrentOccupancy() { return currentOccupancy; }
    public BlockStatus getStatus() { return status; }
    public double getAttractivenessScore() { return attractivenessScore; }
    public int getTotalAgentsPassed() { return totalAgentsPassed; }
    public double getX() { return x; }
    public double getY() { return y; }
    /** Returns the floor index this element belongs to (0 = RDC, 1 = 1er, 2 = 2e). */
    public int getFloor() { return floor; }
    /** Updates the floor index. The value is persisted as part of the model. */
    public void setFloor(int floor) { this.floor = floor; }
     
        
    /** Returns average speed of agents that passed through, or 0 if none. */
    public double getAverageSpeed() {
        return totalAgentsPassed > 0 ? totalSpeedSum / totalAgentsPassed : 0.0;
    }

    public void setName(String name) { this.name = name; }
    public void setMaxCapacity(int maxCapacity) { this.maxCapacity = maxCapacity; }
    public void setAttractivenessScore(double score) { this.attractivenessScore = score; }
    public void setStatus(BlockStatus status) { this.status = status; }
    
    public void setPosition(double x, double y) { this.x = x; this.y = y; }
    /**
     * Sets current occupancy directly (used by serializer).
     */
    public void setCurrentOccupancy(int occupancy) {
        this.currentOccupancy = Math.max(0, occupancy);
    }

    /**
     * Increments occupancy when an agent enters. Also records stats.
     * @param agentSpeed speed of the entering agent
     */
    public void agentEnters(double agentSpeed) {
        currentOccupancy++;
        totalAgentsPassed++;
        totalSpeedSum += agentSpeed;
    }

    /**
     * Decrements occupancy when an agent leaves.
     */
    public void agentLeaves() {
        if (currentOccupancy > 0) currentOccupancy--;
    }

    public boolean isBlocked() { return status == BlockStatus.BLOCKED; }
    public boolean isFull() { return currentOccupancy >= maxCapacity; }

    /** True if occupancy is over capacity (strong congestion). */
    public boolean isOvercrowded() { return currentOccupancy > maxCapacity; }

    @Override
    public String toString() {
        return name + " (" + currentOccupancy + "/" + maxCapacity + ") [" + status + "]";
    }
}