package com.example.cysafecampus.model;

import java.util.ArrayList;
import java.util.List;
import java.io.Serializable;

/**
 * Represents the building as a graph.
 * Central container for all building elements, passages, agents and sensors.
 *
 * Observer pattern split:
 *   - Graph still implements Subject for the legacy Agent notification chain
 *     (triggerAlert → all agents get update())
 *   - Sensors now have their own Subject role toward AdminAgent via SensorObserver
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
