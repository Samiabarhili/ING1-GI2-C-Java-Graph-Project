package com.example.cysafecampus.model;

/**
 * Represents an exit point of the building.
 *
 * <p>
 * An exit is modeled as a specialized {@link Room} so it can connect to
 * {@link Passage} instances through {@link Door} objects. This keeps exits
 * reachable by {@link PathFinder}, which navigates between rooms and passages.
 * </p>
 *
 * <p>
 * Agents reaching an exit are considered successfully evacuated.
 * </p>
 */
public class Exit extends Room {
    /**
     * Constructs an exit with the specified name and maximum capacity.
     *
     * @param name human-readable identifier for the exit
     * @param maxCapacity maximum number of agents that may occupy the exit
     */
    public Exit(String name, int maxCapacity) {
        super(name, maxCapacity, 0, RoomType.OFFICE);
    }

    /**
     * Checks whether this exit is currently usable.
     *
     * @return {@code true} if the exit is accessible, {@code false} otherwise
     */
    public boolean isUsable() {
        return !isBlocked();
    }
}
