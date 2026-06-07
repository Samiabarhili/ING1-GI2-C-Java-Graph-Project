package com.example.cysafecampus.model;

import java.io.Serializable;

/**
 * Abstract class representing a physical space in the building.
 */
public abstract class BuildingElement implements Serializable {

    private String name;
    private int maxCapacity;
    private int currentOccupancy;
    private BlockStatus status;
    private double attractivenessScore;
    private double x;
    private double y;

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
    }

    public String getName() { return name; }
    public int getMaxCapacity() { return maxCapacity; }
    public int getCurrentOccupancy() { return currentOccupancy; }
    public BlockStatus getStatus() { return status; }
    public double getAttractivenessScore() { return attractivenessScore; }
    public int getTotalAgentsPassed() { return totalAgentsPassed; }
    public double getX() { return x; }
    public double getY() { return y; }
     
        
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
