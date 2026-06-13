package com.example.cysafecampus.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
/**
 * Abstract class representing an agent moving in the building. Implements
 * Observer (Graph alert chain) and Serializable.
 *
 * Movement model: - path : ordered list of BuildingElements to reach
 * destination - pathIndex : current position in the path - progress : 0.0 → 1.0
 * progress along the current edge (Passage) - waitCycles : congestion wait
 * counter (strong congestion = 2 cycles blocked)
 */
/**
 * Abstract representation of an agent participating in a building/evacuation
 * simulation.
 *
 * <p>
 * An Agent encapsulates the state and behavior required to navigate a graph
 * model of a building: it holds identification and presentation data
 * (id, name), positional information (currentLocation, destination, currentDoor,
 * path, pathIndex, progress), motion characteristics (maxSpeed, movement
 * strategy), behavioural parameters (Behavior, densityTolerance) and runtime
 * state (AgentState, evacuated, waitCycles and a marker used to avoid
 * re-applying a strong congestion delay repeatedly).
 * </p>
 *
 * <p>
 * The class implements Observer to allow agents to react to events published by
 * the environment, and Serializable so agent instances can be persisted or
 * transmitted if needed. Concrete agent types must implement {@link #update(String)}
 * to react to observed events.
 * </p>
 *
 * <h2>Path and transit semantics</h2>
 * <ul>
 *   <li>The {@code path} is a computed sequence of BuildingElement nodes from
 *       the agent's current location to its destination. {@code pathIndex}
 *       points at the current node in that list; {@code getNextInPath()} returns
 *       the subsequent node (or {@code null} if none).</li>
 *   <li>{@code progress} expresses the agent's fractional progress along the
 *       passage between {@code currentLocation} and the next path node:
 *       0.0 = just entered, 1.0 = arrived. The helper {@link #isInTransit()}
 *       returns true when progress is strictly between 0.0 and 1.0 and is the
 *       canonical test used to avoid snapping an agent back to a node while it
 *       is mid-edge.</li>
 *   <li>{@link #hasArrived()} reports whether the agent's current location
 *       equals its destination.</li>
 * </ul>
 *
 * <h3>Movement and strategies</h3>
 * <p>
 * Agents delegate per-tick movement decisions to a {@code MovementStrategy}
 * via {@link #move()}. If the agent is marked evacuated or in the TRAPPED state
 * it will not be moved by the strategy.</p>
 *
 * <h3>Congestion handling</h3>
 * <p>
 * The agent records a {@code strongCongestionDelayLocationName} to prevent
 * repeatedly applying a long congestion delay for the same overcrowded
 * location. Utility methods are provided to query, mark and clear that marker.
 * </p>
 *
 * <h3>Mutators with special behaviour</h3>
 * <ul>
 *   <li>{@link #setCurrentLocation(BuildingElement)} clears the strong
 *       congestion delay marker when the agent actually changes location.</li>
 *   <li>{@link #setWaitCycles(int)} clamps negative inputs to zero.</li>
 *   <li>{@link #setPath(List)} replaces the path and resets path-related
 *       transient state (pathIndex and progress) to their initial values.</li>
 * </ul>
 *
 * <h3>Extensibility</h3>
 * <p>
 * Concrete subclasses must implement the Observer update method and can
 * specialise movement behavior by providing specific MovementStrategy
 * implementations and by overriding lifecycle behaviour if necessary.
 * </p>
 *
 * @see java.io.Serializable
 * @see java.util.Observer
 * @see MovementStrategy
 * @see Behavior
 * @see AgentState
 */
public abstract class Agent implements Observer, Serializable {

    private String id;
    private String name;
    private BuildingElement currentLocation;
    private BuildingElement destination;
    private MovementStrategy strategy;
    private boolean evacuated;
    private Door currentDoor;

    protected double maxSpeed;
    protected Behavior behavior;
    protected AgentState state;
    protected double densityTolerance;

    /**
     * Computed path from currentLocation to destination
     */
    private List<BuildingElement> path;

    /**
     * Index of the element we are currently heading toward in path
     */
    private int pathIndex;

    /**
     * Progress along the current Passage: 0.0 = just entered, 1.0 = arrived at
     * next node. Irrelevant when the agent is sitting in a Room/Exit (not in
     * transit).
     */
    private double progress;

    /**
     * Remaining cycles to wait due to strong congestion
     */
    private int waitCycles;

    /**
     * Name of the location where the strong congestion delay has already been
     * applied. This prevents the agent from being blocked forever while the
     * node remains overcrowded.
     */
    private String strongCongestionDelayLocationName;

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
        this.strongCongestionDelayLocationName = null;
        this.evacuated = false;
        this.currentDoor = null;
    }

    // ── Getters ───────────────────────────────────────────
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BuildingElement getCurrentLocation() {
        return currentLocation;
    }

    public BuildingElement getDestination() {
        return destination;
    }

    public MovementStrategy getStrategy() {
        return strategy;
    }

    public AgentState getState() {
        return state;
    }

    public double getMaxSpeed() {
        return maxSpeed;
    }

    public Behavior getBehavior() {
        return behavior;
    }

    public double getDensityTolerance() {
        return densityTolerance;
    }

    public List<BuildingElement> getPath() {
        return path;
    }

    public int getPathIndex() {
        return pathIndex;
    }

    public double getProgress() {
        return progress;
    }

    public int getWaitCycles() {
        return waitCycles;
    }

    public boolean isEvacuated() {
        return evacuated;
    }

    public Door getCurrentDoor() {
        return currentDoor;
    }

    // ── Setters ───────────────────────────────────────────
    public void setName(String name) {
        this.name = name;
    }

    public void setDestination(BuildingElement destination) {
        this.destination = destination;
    }

    public void setStrategy(MovementStrategy strategy) {
        this.strategy = strategy;
    }

    public void setState(AgentState state) {
        this.state = state;
    }

    public void setMaxSpeed(double maxSpeed) {
        this.maxSpeed = maxSpeed;
    }

    public void setBehavior(Behavior behavior) {
        this.behavior = behavior;
    }

    public void setDensityTolerance(double densityTolerance) {
        this.densityTolerance = densityTolerance;
    }

    public void setPath(List<BuildingElement> path) {
        this.path = path;
        this.pathIndex = 0;
        this.progress = 0.0;
    }

    public void setPathIndex(int pathIndex) {
        this.pathIndex = pathIndex;
    }

    public void setProgress(double progress) {
        this.progress = progress;
    }

    public void setEvacuated(boolean evacuated) {
        this.evacuated = evacuated;
    }

    public void setCurrentDoor(Door currentDoor) {
        this.currentDoor = currentDoor;
    }

    public void clearCurrentDoor() {
        this.currentDoor = null;
    }

    /**
     * Updates the current location of the agent and resets the strong
     * congestion delay marker when the agent changes location.
     *
     * @param location the new current location
     */
    public void setCurrentLocation(BuildingElement location) {
        if (this.currentLocation != location) {
            this.strongCongestionDelayLocationName = null;
        }
        this.currentLocation = location;
    }

    /**
     * Sets the number of simulation cycles the agent must wait.
     *
     * @param waitCycles number of cycles to wait, negative values are converted
     * to zero
     */
    public void setWaitCycles(int waitCycles) {
        this.waitCycles = Math.max(0, waitCycles);
    }

    /**
     * Checks whether the strong congestion delay has already been applied for
     * the given location.
     *
     * @param location the overcrowded location
     * @return true if the agent already waited for this location
     */
    public boolean hasCompletedStrongCongestionDelayFor(BuildingElement location) {
        return location != null
                && strongCongestionDelayLocationName != null
                && strongCongestionDelayLocationName.equals(location.getName());
    }

    /**
     * Marks the strong congestion delay as applied for the given location.
     *
     * @param location the overcrowded location
     */
    public void markStrongCongestionDelayCompletedFor(BuildingElement location) {
        strongCongestionDelayLocationName = location != null ? location.getName() : null;
    }

    /**
     * Clears the strong congestion delay marker.
     */
    public void clearStrongCongestionDelayMarker() {
        strongCongestionDelayLocationName = null;
    }

    /**
     * Executes one simulation tick via the current strategy.
     */
    public void move() {
        if (evacuated) {
            return;
        }
        if (state == AgentState.TRAPPED) {
            // Trapped agents stay in place, visible, waiting for rescue.
            return;
        }
        if (strategy != null) {
            strategy.execute(this);
        }
    }

    /**
     * Returns true if the agent is currently engaged on an edge between two
     * nodes (i.e. somewhere strictly between {@code currentLocation} and the
     * next path node).
     *
     * <p>
     * This is the single source of truth for the rule "never reset progress or
     * empty the path while the agent is in transit": any fire / reroute /
     * strategy-change / exit-choice code must check this before snapping the
     * agent back to a node centre.</p>
     *
     * @return true when {@code 0.0 < progress < 1.0}
     */
    public boolean isInTransit() {
        return progress > 0.0 && progress < 1.0;
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
        if (path == null || path.isEmpty()) {
            return null;
        }

        int nextIndex = pathIndex + 1;

        if (nextIndex >= path.size()) {
            return null;
        }

        return path.get(nextIndex);
    }

    @Override
    public abstract void update(String event);

    @Override
    public String toString() {
        return name + " (id=" + id.substring(0, 5) + ") @ " + currentLocation.getName();
    }
}
