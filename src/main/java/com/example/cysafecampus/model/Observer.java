package com.example.cysafecampus.model;

/**
 * Interface for the Observer in the Observer pattern.
 * Agents implement this interface to react to building alerts.
 */
public interface Observer {

    /**
     * Called when the building state changes.
     * @param state the new state (e.g. "FIRE", "NORMAL")
     */
    void update(String state);
}
