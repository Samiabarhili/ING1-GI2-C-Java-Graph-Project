package com.example.cysafecampus.model;

/**
 * Interface for the Subject in the Observer pattern.
 * The Graph implements this interface to notify agents
 * when the building state changes.
 */
public interface Subject {

    /**
     * Adds an observer to the notification list.
     * @param observer the observer to add
     */
    void addObserver(Observer observer);

    /**
     * Removes an observer from the notification list.
     * @param observer the observer to remove
     */
    void removeObserver(Observer observer);

    /**
     * Notifies all registered observers of a state change.
     */
    void notifyObservers();
}
