package com.example.cysafecampus.model;

/**
 * Interface for the Subject in the Observer pattern.
 * The Graph implements this interface to notify agents
 * when the building state changes.
 */
/**
 * Represents the Subject role in the Observer design pattern.
 *
 * <p>A Subject maintains a collection of Observer instances and is
 * responsible for registering, deregistering and notifying them when
 * its state changes. Implementations are expected to document their
 * concurrency guarantees; in concurrent contexts, prefer taking a
 * snapshot of the observer list before notifying to avoid
 * ConcurrentModificationExceptions and to minimize locking while
 * invoking observer callbacks.</p>
 *
 * <p>Implementations should also be resilient to observer failures:
 * exceptions thrown by one observer should not prevent notifications
 * from being delivered to others.</p>
 *
 * @see Observer
 */
 
/**
 * Adds an observer to the notification list.
 *
 * <p>Observers registered via this method will receive subsequent
 * notifications from the subject. Implementations may either ignore
 * duplicate registrations or ensure each observer is present only once.
 * Passing a null observer is considered invalid and may result in an
 * IllegalArgumentException or a NullPointerException depending on the
 * implementation.</p>
 *
 * @param observer the observer to add; must not be null
 */
 
/**
 * Removes an observer from the notification list.
 *
 * <p>After successful removal the observer will no longer receive
 * notifications. If the observer was not registered, implementations
 * may silently do nothing. Passing null may result in an exception
 * depending on the implementation.</p>
 *
 * @param observer the observer to remove; must not be null
 */
 
/**
 * Notifies all registered observers of a state change.
 *
 * <p>Implementations should notify every currently registered observer.
 * It is recommended to iterate over a stable snapshot of the observer
 * list so that modifications to the list during notification do not
 * affect the delivery process. Exceptions thrown by individual
 * observers should be caught and handled so as not to prevent other
 * observers from being notified.</p>
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