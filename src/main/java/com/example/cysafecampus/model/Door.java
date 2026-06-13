package com.example.cysafecampus.model;

import java.io.Serializable;

/**
 * Represents a corridor segment connecting one room-like node to one passage
 * node. In the user interface this object is displayed as one selectable edge.
 */
/**
 * Represents a doorway / corridor segment that connects a Room-like node to a Passage.
 *
 * <p>This class models a segment that can be open or closed and that enforces a maximum
 * capacity for the number of agents that may occupy the segment at the same time.
 * It also collects simple statistics about agents that complete the segment (count and
 * sum of their speeds) to compute an average speed.</p>
 *
 * Key behavior and invariants:
 * <ul>
 *   <li>Each Door is associated with a {@code Room} and a {@code Passage} (provided at
 *       construction time).</li>
 *   <li>By default a Door is open and has a default maximum capacity of 10 when the
 *       one-arg constructor is used; a custom positive capacity may be provided via the
 *       two-arg constructor. {@link #setMaxCapacity(int)} enforces a strictly positive
 *       capacity and will throw {@link IllegalArgumentException} otherwise.</li>
 *   <li>The occupancy is tracked with {@link #agentEnters()} and {@link #agentLeaves()}.
 *       {@code agentEnters()} returns {@code true} and increments occupancy only if the
 *       door is open and not full; otherwise it returns {@code false}. {@code agentLeaves()}
 *       will decrement occupancy if it is greater than zero.</li>
 *   <li>{@link #isFull()} and {@link #getOccupancyRatio()} provide capacity-related
 *       convenience accessors.</li>
 *   <li>{@link #recordAgentPassage(double)} records that an agent completed traversal:
 *       it increments the total-pass count and adds a non-negative speed contribution
 *       (negative speeds are treated as zero). {@link #getAverageSpeed()} returns the
 *       average speed or {@code 0.0} if no agent has passed yet.</li>
 * </ul>
 *
 * Concurrency:
 * <p>Instances are not synchronized. If multiple threads may access the same Door
 * concurrently (for example to enter/leave or to update statistics), callers must
 * provide external synchronization to ensure correctness of occupancy and statistics.</p>
 *
 * Usage example:
 * <pre>
 *   Door d = new Door(room, passage);      // default capacity 10, initially open
 *   if (d.agentEnters()) {
 *       // agent occupies the segment
 *   }
 *   d.recordAgentPassage(agentSpeed);      // record agent completion and speed
 *   d.agentLeaves();                       // agent leaves the segment
 * </pre>
 *
 * @see Room
 * @see Passage
 * @see java.io.Serializable
 */
public class Door implements Serializable {

    /**
     * Whether the corridor segment is currently open.
     */
    private boolean isOpen;

    /**
     * The room, exit or virtual junction connected to the passage.
     */
    private Room room;

    /**
     * The passage connected to the room-like node.
     */
    private Passage passage;

    /**
     * Maximum number of agents that can be inside this corridor segment.
     */
    private int maxCapacity;

    private int currentOccupancy;

    /**
     * Number of agents that have completed this corridor segment.
     */
    private int totalAgentsPassed;

    /**
     * Sum of speeds of agents that completed this corridor segment.
     */
    private double totalSpeedSum;

    /**
     * Constructor for a corridor segment with a default capacity.
     *
     * @param room the room-like node connected to the passage
     * @param passage the passage connected to the room-like node
     */
    public Door(Room room, Passage passage) {
        this(room, passage, 10);
    }

    /**
     * Constructor for a corridor segment with a custom capacity.
     *
     * @param room the room-like node connected to the passage
     * @param passage the passage connected to the room-like node
     * @param maxCapacity maximum number of agents allowed inside the segment
     */
    public Door(Room room, Passage passage, int maxCapacity) {
        this.room = room;
        this.passage = passage;
        this.isOpen = true;
        this.maxCapacity = Math.max(1, maxCapacity);
        this.totalAgentsPassed = 0;
        this.totalSpeedSum = 0.0;
        this.currentOccupancy = 0;
    }

    /**
     * Returns whether this corridor segment is open.
     *
     * @return true if the segment is open
     */
    public boolean isOpen() {
        return isOpen;
    }

    /**
     * Returns the room-like node connected to this corridor segment.
     *
     * @return connected room-like node
     */
    public Room getRoom() {
        return room;
    }

    /**
     * Returns the passage connected to this corridor segment.
     *
     * @return connected passage
     */
    public Passage getPassage() {
        return passage;
    }

    /**
     * Returns the maximum capacity of this corridor segment.
     *
     * @return maximum capacity
     */
    public int getMaxCapacity() {
        return maxCapacity;
    }

    public int getCurrentOccupancy() {
        return currentOccupancy;
    }

    public boolean isFull() {
        return currentOccupancy >= maxCapacity;
    }

    public double getOccupancyRatio() {
        return maxCapacity > 0 ? (double) currentOccupancy / maxCapacity : 0.0;
    }

    public boolean agentEnters() {
        if (!isOpen || isFull()) {
            return false;
        }

        currentOccupancy++;
        return true;
    }

    public void agentLeaves() {
        if (currentOccupancy > 0) {
            currentOccupancy--;
        }
    }

    /**
     * Returns the number of agents that have completed this segment.
     *
     * @return total agents passed
     */
    public int getTotalAgentsPassed() {
        return totalAgentsPassed;
    }

    /**
     * Returns the average speed of agents that completed this segment.
     *
     * @return average speed, or zero if no agent has passed yet
     */
    public double getAverageSpeed() {
        return totalAgentsPassed > 0 ? totalSpeedSum / totalAgentsPassed : 0.0;
    }

    /**
     * Sets the maximum capacity of this corridor segment.
     *
     * @param maxCapacity maximum capacity, must be greater than zero
     */
    public void setMaxCapacity(int maxCapacity) {
        if (maxCapacity <= 0) {
            throw new IllegalArgumentException("Corridor capacity must be greater than zero.");
        }
        this.maxCapacity = maxCapacity;
    }

    /**
     * Records that an agent completed this corridor segment.
     *
     * @param agentSpeed speed of the agent that passed
     */
    public void recordAgentPassage(double agentSpeed) {
        totalAgentsPassed++;
        totalSpeedSum += Math.max(0.0, agentSpeed);
    }

    /**
     * Opens the corridor segment.
     */
    public void open() {
        this.isOpen = true;
    }

    /**
     * Closes the corridor segment.
     */
    public void close() {
        this.isOpen = false;
    }

    @Override
    public String toString() {
        return "Corridor[" + room.getName() + " <-> " + passage.getName()
                + ", occupancy=" + currentOccupancy + "/" + maxCapacity + "]";
    }
}
