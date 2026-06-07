package com.example.cysafecampus.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Abstract class representing an agent moving in the building.
 * Implements Observer (Graph alert chain) and Serializable.
 *
 * Movement model:
 *   - path         : ordered list of BuildingElements to reach destination
 *   - pathIndex    : current position in the path
 *   - progress     : 0.0 → 1.0 progress along the current edge (Passage)
 *   - waitCycles   : congestion wait counter (strong congestion = 2 cycles blocked)
 */
public abstract class Agent implements Observer, Serializable {

    private String id;
    private String name;
    private BuildingElement currentLocation;
    private BuildingElement destination;
    private MovementStrategy strategy;
    private boolean evacuated;

    protected double maxSpeed;
    protected Behavior behavior;
    protected AgentState state;
    protected double densityTolerance;

    /** Computed path from currentLocation to destination */
    private List<BuildingElement> path;

    /** Index of the element we are currently heading toward in path */
    private int pathIndex;

    /**
     * Progress along the current Passage: 0.0 = just entered, 1.0 = arrived at next node.
     * Irrelevant when the agent is sitting in a Room/Exit (not in transit).
     */
    private double progress;

    /** Remaining cycles to wait due to strong congestion */
    private int waitCycles;

    public Agent(String name, BuildingElement currentLocation,
                 double maxSpeed, Behavior behavior, double densityTolerance) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.currentLocation = currentLocation;
        this.maxSpeed = maxSpeed;
        this.behavior = behavior;
        this.densityTolerance = densityTolerance;
        this.state = AgentState.CALM;
        this.path = new ArrayList<>();
        this.pathIndex = 0;
        this.progress = 0.0;
        this.waitCycles = 0;
        this.evacuated = false;
    }

    // ── Getters ───────────────────────────────────────────

    public String getId() { return id; }
    public String getName() { return name; }
    public BuildingElement getCurrentLocation() { return currentLocation; }
    public BuildingElement getDestination() { return destination; }
    public MovementStrategy getStrategy() { return strategy; }
    public AgentState getState() { return state; }
    public double getMaxSpeed() { return maxSpeed; }
    public Behavior getBehavior() { return behavior; }
    public double getDensityTolerance() { return densityTolerance; }
    public List<BuildingElement> getPath() { return path; }
    public int getPathIndex() { return pathIndex; }
    public double getProgress() { return progress; }
    public int getWaitCycles() { return waitCycles; }
    public boolean isEvacuated() { return evacuated; }


    // ── Setters ───────────────────────────────────────────

    public void setName(String name) { this.name = name; }
    public void setCurrentLocation(BuildingElement location) { this.currentLocation = location; }
    public void setDestination(BuildingElement destination) { this.destination = destination; }
    public void setStrategy(MovementStrategy strategy) { this.strategy = strategy; }
    public void setState(AgentState state) { this.state = state; }
    public void setMaxSpeed(double maxSpeed) { this.maxSpeed = maxSpeed; }
    public void setBehavior(Behavior behavior) { this.behavior = behavior; }
    public void setDensityTolerance(double densityTolerance) { this.densityTolerance = densityTolerance; }
    public void setPath(List<BuildingElement> path) { this.path = path; this.pathIndex = 0; this.progress = 0.0; }
    public void setPathIndex(int pathIndex) { this.pathIndex = pathIndex; }
    public void setProgress(double progress) { this.progress = progress; }
    public void setWaitCycles(int waitCycles) { this.waitCycles = waitCycles; }
    public void setEvacuated(boolean evacuated) { this.evacuated = evacuated; }


    /**
     * Executes one simulation tick via the current strategy.
     */
    public void move() {
        if (evacuated) {
            return;
        }
        if (strategy != null) {
            strategy.execute(this);
        }
    }

    /**
     * Returns true if the agent has reached its destination.
     */
    public boolean hasArrived() {
        return destination != null && currentLocation != null
            && currentLocation.equals(destination);
    }

    /**
     * Returns the next BuildingElement in the path, or null if none.
     */
    public BuildingElement getNextInPath() {
        if (path == null || pathIndex + 1 >= path.size()) return null;
        return path.get(pathIndex + 1);
    }

    @Override
    public abstract void update(String event);

    @Override
    public String toString() {
        return name + " (id=" + id.substring(0, 5) + ") @ " + currentLocation.getName();
    }
}
