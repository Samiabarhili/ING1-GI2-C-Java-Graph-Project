package com.example.cysafecampus.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the building as a graph.
 * Central container for all building elements, passages, agents and sensors.
 *
 * Observer pattern split:
 *   - Graph still implements Subject for the legacy Agent notification chain
 *     (triggerAlert → all agents get update())
 *   - Sensors now have their own Subject role toward AdminAgent via SensorObserver
 */
/**
 * Central model of a building used by the simulation.
 *
 * <p>
 * The Graph holds the static topology (BuildingElement instances and Passage
 * connections) together with dynamic runtime state (Agent and Sensor instances).
 * It also implements a simple subject/observer mechanism to broadcast
 * building-wide alerts (e.g. fire) to interested observers such as agents.
 * </p>
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Maintain collections of building elements and passages that compose the
 *       physical layout.</li>
 *   <li>Manage agents: adding/removing agents and registering them as observers
 *       for alerts. When an agent is added or removed, the agent's current
 *       location is updated accordingly.</li>
 *   <li>Manage sensors and provide a tick method (runSensors) to trigger their
 *       detection logic each simulation cycle.</li>
 *   <li>Detect congested building elements via detectCongestion().</li>
 *   <li>Broadcast alerts to registered observers via triggerAlert() and
 *       notifyObservers() (notifyObservers triggers a FIRE alert by default).</li>
 *   <li>Expose whether an emergency is currently active via isEmergencyActive().</li>
 * </ul>
 *
 * <h3>Threading and concurrency</h3>
 * <p>
 * The agents and observers collections are backed by synchronized lists to
 * reduce the risk of ConcurrentModificationExceptions between the simulation
 * thread (tick/move) and a UI/render thread. However, callers should still be
 * careful: getters expose the live internal lists and additional external
 * synchronization may be required for compound operations.
 * </p>
 *
 * <h3>Notes</h3>
 * <ul>
 *   <li>The class implements Serializable so the model can be serialized if
 *       needed. Transient or custom serialization concerns are not addressed
 *       by this comment.</li>
 *   <li>triggerAlert() sets an internal emergency flag (emergencyActive) to
 *       indicate whether the building is in a non-normal state.</li>
 *   <li>Adding an agent registers it as an observer; removing an agent removes
 *       it from the observer list to avoid "ghost" observers.</li>
 * </ul>
 *
 * @see com.example.cysafecampus.model.Agent
 * @see com.example.cysafecampus.model.Sensor
 * @see com.example.cysafecampus.model.BuildingElement
 * @see com.example.cysafecampus.model.Passage
 */
public class Graph implements Subject, Serializable {

    /** All physical spaces in the building */
    private List<BuildingElement> elements;

    /** All passages (corridors, staircases) */
    private List<Passage> passages;

    /** All agents currently in the building */
    private List<Agent> agents;

    /** All sensors deployed in the building */
    private List<Sensor> sensors;

    /** Observers for building-wide alerts (agents implementing Observer) */
    private List<Observer> observers;

    private boolean emergencyActive = false;

    public Graph() {
        this.elements = new ArrayList<>();
        this.passages = new ArrayList<>();
        // Synchronized to prevent ConcurrentModificationException between
        // the simulation thread (tick/move) and the JavaFX render thread (draw)
        this.agents = java.util.Collections.synchronizedList(new ArrayList<>());
        this.sensors = new ArrayList<>();
        this.observers = java.util.Collections.synchronizedList(new ArrayList<>());
    }

    // ── Building Elements ─────────────────────────────────

    public void addElement(BuildingElement element) { elements.add(element); }
    public void removeElement(BuildingElement element) { elements.remove(element); }
    public List<BuildingElement> getElements() { return elements; }

    // ── Passages ──────────────────────────────────────────

    public void addPassage(Passage passage) { passages.add(passage); }
    public void removePassage(Passage passage) { passages.remove(passage); }
    public List<Passage> getPassages() { return passages; }

    // ── Agents ────────────────────────────────────────────

    /**
     * Adds an agent and registers it as an Observer for building-wide alerts.
     * @param agent the agent to add
     */
    public void addAgent(Agent agent) {
        agents.add(agent);

         if (agent.getCurrentLocation() != null) {
            agent.getCurrentLocation().agentEnters(agent.getMaxSpeed());
        }

        addObserver(agent);
    }

    public void removeAgent(Agent agent) {
        if (agent.getCurrentLocation() != null) {
            agent.getCurrentLocation().agentLeaves();
        }
        agents.remove(agent);
        observers.remove(agent); // ensure no ghost observer remains
    }

    public List<Agent> getAgents() { return agents; }


    public void clearObservers() {
        observers.clear();
    }

    // ── Sensors ───────────────────────────────────────────

    /**
     * Adds a sensor to the graph.
     * @param sensor the sensor to add
     */
    public void addSensor(Sensor sensor) { sensors.add(sensor); }
    public void removeSensor(Sensor sensor) { sensors.remove(sensor); }
    public List<Sensor> getSensors() { return sensors; }

    // ── Observer Pattern (Graph → Agents) ─────────────────

    @Override
    public void addObserver(Observer observer) { observers.add(observer); }

    @Override
    public void removeObserver(Observer observer) { observers.remove(observer); }

    @Override
    public void notifyObservers() { triggerAlert("FIRE"); }

    /**
     * Broadcasts an alert to all registered agent observers.
     * @param state alert type ("FIRE", "NORMAL", etc.)
     */
    public void triggerAlert(String state) {
        this.emergencyActive = !"NORMAL".equalsIgnoreCase(state);

        for (Observer observer : observers) {
            observer.update(state);
        }
    }
    // ── Utilities ─────────────────────────────────────────

    /**
     * Returns all building elements currently at full capacity.
     * @return list of congested elements
     */
    public List<BuildingElement> detectCongestion() {
        List<BuildingElement> congested = new ArrayList<>();
        for (BuildingElement element : elements) {
            if (element.isFull()) {
                congested.add(element);
            }
        }
        return congested;
    }

    /**
     * Runs detect() on all sensors in the building.
     * Should be called each simulation tick.
     */
    public void runSensors() {
        for (Sensor sensor : sensors) {
            sensor.detect();
        }
    }


    public boolean isEmergencyActive() {
        return emergencyActive;
    }
}
