package com.example.cysafecampus.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
/**
 * Abstract class representing a physical sensor attached to a building element.
 * Acts as the Subject in the Sensor → AdminAgent Observer pattern.
 * Subclasses implement the actual detection logic (presence, smoke, etc.)
 */
/**
 * Abstract base class representing a generic sensor attached to a building element.
 *
 * <p>A Sensor encapsulates a unique identifier, the {@code BuildingElement} it
 * monitors and a real-time people count reported by the sensor. Concrete
 * subclasses implement the detection logic in {@link #detect()} and are
 * responsible for creating {@link SensorEvent} instances and broadcasting them
 * to registered observers via {@link #notifyObservers(SensorEvent)}.</p>
 *
 * <h3>Observers and serialization</h3>
 * <p>The class maintains a list of {@code SensorObserver} subscribers that are
 * notified on sensor events. That list is declared {@code transient} because
 * observers are typically UI callbacks or agent references (often lambdas)
 * which are not serializable. Consequently, observers are not persisted and
 * must be re-registered after deserialization (for example, by the containing
 * controller or UI layer).</p>
 *
 * <h3>Usage notes</h3>
 * <ul>
 *   <li>Register observers with {@link #addObserver(SensorObserver)} and remove
 *       them with {@link #removeObserver(SensorObserver)} or {@link #clearObservers()}.</li>
 *   <li>Subclasses should call {@link #notifyObservers(SensorEvent)} whenever
 *       a meaningful detection occurs to inform registered observers.</li>
 *   <li>{@link #getRealTimePeopleCount()} / {@link #setRealTimePeopleCount(int)}
 *       provide a simple mutable counter for real-time occupancy reporting.</li>
 * </ul>
 *
 * @see SensorObserver
 * @see SensorEvent
 * @see BuildingElement
 */
public abstract class Sensor implements Serializable {

    /** Unique identifier for this sensor */
    private String id;

    /** The building element this sensor is monitoring */
    private BuildingElement monitoredElement;

    /** Real-time people count reported by this sensor */
    private int realTimePeopleCount;

    /**
     * List of observers to notify — in practice, only AdminAgent / UI callbacks
     * subscribe. Marked {@code transient} because observers can be lambdas (e.g.
     * the AdminView sensor-event callback) that are not serializable; persisting
     * them is neither needed nor safe. The list is lazily re-created after load.
     */
    private transient List<SensorObserver> observers;

    /**
     * Constructor for Sensor.
     * @param id unique sensor identifier
     * @param monitoredElement the element this sensor watches
     */
    public Sensor(String id, BuildingElement monitoredElement) {
        this.id = id;
        this.monitoredElement = monitoredElement;
        this.realTimePeopleCount = 0;
        this.observers = new ArrayList<>();
    }

    public String getId() { return id; }
    public BuildingElement getMonitoredElement() { return monitoredElement; }
    public int getRealTimePeopleCount() { return realTimePeopleCount; }
    public void setRealTimePeopleCount(int count) { this.realTimePeopleCount = count; }

    /** Registers an observer (AdminAgent) to receive events from this sensor. */
    public void addObserver(SensorObserver observer) {
        if (observers == null) {
            observers = new ArrayList<>();
        }
        observers.add(observer);
    }

    /** Unregisters an observer. */
    public void removeObserver(SensorObserver observer) {
        if (observers != null) {
            observers.remove(observer);
        }
    }

/** Removes all registered observers from this sensor. */
public void clearObservers() {
    if (observers != null) {
        observers.clear();
    }
}
    /**
     * Notifies all registered observers with a sensor event.
     * Called internally by subclasses when detection occurs.
     * @param event the event to broadcast
     */
    protected void notifyObservers(SensorEvent event) {
        if (observers == null) {
            return;
        }
        for (SensorObserver observer : observers) {
            observer.onSensorEvent(event);
        }
    }

    /**
     * Runs the detection logic specific to this sensor type.
     * Must create a SensorEvent and call notifyObservers() when relevant.
     */
    public abstract void detect();

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + id + "] on " + monitoredElement.getName();
    }
}
