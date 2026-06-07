package com.example.cysafecampus.model;

import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.Comparator;

/**
 * Core simulation engine — decoupled from JavaFX.
 * Manages the tick loop and exposes hooks for the controller/view.
 *
 * Each tick:
 *   1. Run all sensors (detect events).
 *   2. Move all agents (call agent.move()).
 *   3. Notify tickListeners so the view can redraw.
 *
 * Speed is expressed as milliseconds between ticks (default 500ms).
 * Step mode executes exactly one tick and pauses.
 */
public class SimulationEngine {

    /** The graph being simulated */
    private final Graph graph;

    /** True if the simulation is currently running */
    private boolean running;

    /** Interval between ticks in milliseconds */
    private int intervalMs;

    /** Current tick count since start */
    private long tickCount;

    /**
     * Listeners called after each tick — used by the controller
     * to trigger a view redraw without coupling engine to JavaFX.
     */
    private final List<Consumer<Long>> tickListeners;

    /** Background thread running the tick loop */
    private Thread simulationThread;

    /**
     * Constructor for SimulationEngine.
     * @param graph the graph to simulate
     */
    public SimulationEngine(Graph graph) {
        this.graph = graph;
        this.running = false;
        this.intervalMs = 500;
        this.tickCount = 0;
        this.tickListeners = new ArrayList<>();
    }

    // ── Control ───────────────────────────────────────────

    /**
     * Starts the simulation loop in a background thread.
     * Does nothing if already running.
     */
    public void play() {
        if (running) return;
        running = true;
        simulationThread = new Thread(() -> {
            while (running) {
                tick();
                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        simulationThread.setDaemon(true);
        simulationThread.start();
    }

    /**
     * Pauses the simulation loop.
     */
    public void pause() {
        running = false;
        if (simulationThread != null) {
            simulationThread.interrupt();
        }
    }

    /**
     * Executes exactly one tick (step-by-step mode).
     * Pauses the loop first if it was running.
     */
    public void step() {
        pause();
        tick();
    }

    /**
     * Sets the simulation speed.
     * @param intervalMs milliseconds between ticks (lower = faster)
     */
    public void setIntervalMs(int intervalMs) {
        this.intervalMs = Math.max(50, intervalMs);
    }

    public int getIntervalMs() { return intervalMs; }
    public boolean isRunning() { return running; }
    public long getTickCount() { return tickCount; }

    // ── Tick ──────────────────────────────────────────────

    /**
     * Executes one simulation tick:
     *   1. Run all sensors.
     *   2. Move all agents.
     *   3. Notify listeners.
     */
    private void tick() {
        tickCount++;

        // 1. Sensors
        graph.runSensors();

        // 2. Agents — iterate on a snapshot to avoid ConcurrentModificationException
        List<Agent> snapshot = new ArrayList<>(graph.getAgents());
        for (Agent agent : snapshot) {
            if (agent.isEvacuated()) {
                continue;
            }

            agent.move();

            if (agent.getCurrentLocation() instanceof Exit) {
                agent.setEvacuated(true);
                agent.setDestination(null);
                agent.setPath(new ArrayList<>());
                agent.setProgress(0.0);
                continue;
            }

            if (agent.getDestination() == null
                    && agent.getCurrentLocation() != null) {

                BuildingElement nearestExit = findNearestExit(agent);

                if (nearestExit != null) {
                    agent.setDestination(nearestExit);
                    agent.setPath(new ArrayList<>());
                    agent.setProgress(0.0);
                }
            }
        }

        // 3. Notify listeners (triggers view redraw via Platform.runLater in controller)
        for (Consumer<Long> listener : tickListeners) {
            listener.accept(tickCount);
        }
    }

    // ── Observers ─────────────────────────────────────────
    private BuildingElement findNearestExit(Agent agent) {
        return graph.getElements().stream()
            .filter(el -> el instanceof Exit)
            .filter(el -> !el.isBlocked())
            .min(Comparator.comparingDouble(exit -> {
                List<BuildingElement> path = PathFinder.calculateFastestPath(
                    agent.getCurrentLocation(),
                    exit,
                    agent.getMaxSpeed()
                );

                if (path == null || path.isEmpty()) {
                    return Double.MAX_VALUE;
                }

                return path.size();
            }))
            .orElse(null);
    }
    /**
     * Registers a tick listener.
     * @param listener called after each tick with the current tick count
     */
    public void addTickListener(Consumer<Long> listener) {
        tickListeners.add(listener);
    }

    /**
     * Removes a tick listener.
     */
    public void removeTickListener(Consumer<Long> listener) {
        tickListeners.remove(listener);
    }
}
