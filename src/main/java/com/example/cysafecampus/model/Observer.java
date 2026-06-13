package com.example.cysafecampus.model;
/**
 * Interface for the Observer in the Observer pattern.
 * Agents implement this interface to react to building alerts.
 */
/**
 * Defines a listener for notifications about changes to a building's state.
 *
 * <p>Implementations register with a subject (observable) to receive an update
 * whenever the building state changes. The update method receives a short,
 * textual representation of the new state (for example "FIRE" or "NORMAL") and
 * should perform whatever actions are appropriate for that state (logging,
 * UI updates, alarm handling, etc.).
 *
 * <p>Implementations should validate and handle unexpected or unknown state
 * values. If the subject may notify observers from multiple threads, observers
 * should ensure proper synchronization to remain thread-safe.
 */
public interface Observer {

    /**
     * Called when the building state changes.
     * @param state the new state (e.g. "FIRE", "NORMAL")
     */
    void update(String state);
}
