package com.example.cysafecampus.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Core simulation engine — decoupled from JavaFX. Manages the tick loop and
 * exposes hooks for the controller/view.
 *
 * Each tick: 1. Run all sensors (detect events). 2. Move all agents (call
 * agent.move()). 3. Notify tickListeners so the view can redraw.
 *
 * Speed is expressed as milliseconds between ticks (default 500ms). Step mode
 * executes exactly one tick and pauses.
 */
/**
 * SimulationEngine drives the simulation of agents and sensors on a Graph.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Maintain and advance a discrete, periodic "tick" counter.</li>
 *   <li>Execute sensor updates, move agents according to their state and
 *       routing, and notify registered listeners after each tick.</li>
 *   <li>Provide simple play/pause/step controls and adjustable tick interval.</li>
 * </ul>
 *
 * <p>Tick lifecycle (performed once per tick):
 * <ol>
 *   <li>Increment the internal tick count.</li>
 *   <li>Run all sensors via {@code graph.runSensors()}.</li>
 *   <li>Iterate a snapshot of the graph's agents and for each non-evacuated agent:
 *       <ul>
 *         <li>If no emergency and the agent has no destination, pick a random
 *             non-blocked, non-exit building element as a destination.</li>
 *         <li>Invoke the agent's {@code move()} logic.</li>
 *         <li>If the agent reaches an Exit, mark it evacuated and clear
 *             destination/path/progress.</li>
 *         <li>When an emergency is active and the agent is stationary (not in
 *             transit) re-evaluate/assign the nearest reachable Exit using the
 *             configured routing strategy; mark an agent {@code TRAPPED} when
 *             no exit is currently reachable.</li>
 *       </ul>
 *   </li>
 *   <li>Notify all registered tick listeners by calling each listener with the
 *       current tick count.</li>
 * </ol>
 *
 * <p>Threading and control:
 * <ul>
 *   <li>{@code play()} starts a dedicated daemon thread that runs the tick
 *       loop and sleeps for the configured interval in milliseconds between ticks.
 *       If already running, calling {@code play()} is a no-op.</li>
 *   <li>{@code pause()} stops the tick loop by clearing the running flag and
 *       interrupting the background thread (if present).</li>
 *   <li>{@code step()} pauses the loop (if running) and performs exactly one tick
 *       on the caller thread.</li>
 *   <li>Listeners are invoked on the simulation thread. Consumers that trigger
 *       UI updates should marshal work to the UI thread (for example, via
 *       JavaFX's {@code Platform.runLater}). Listeners should be quick or
 *       offload long-running work to avoid delaying the simulation loop.</li>
 * </ul>
 *
 * <p>Configuration and limits:
 * <ul>
 *   <li>{@code setIntervalMs(int)} clamps the interval to a minimum of 50 ms to
 *       avoid excessively tight loops.</li>
 *   <li>The simulation uses a best-effort routing strategy: when "fastest path"
 *       is enabled, travel-time estimation accounts for passage distance,
 *       passage-specific speed factors, agent maximum speed, and a simple
 *       congestion factor derived from current occupancy and maximum capacity.
 *       When disabled, routing uses shortest-path measured as path length
 *       (number of elements).</li>
 * </ul>
 *
 * <p>Observers:
 * <ul>
 *   <li>Listeners are registered via {@code addTickListener(Consumer<Long>)} and
 *       removed via {@code removeTickListener(Consumer<Long>)}. Each listener
 *       receives the tick count after the simulation has processed sensors and
 *       agent movement for that tick.</li>
 * </ul>
 *
 * <p>Notes and caveats:
 * <ul>
 *   <li>The engine iterates agents over a snapshot to avoid
 *       {@code ConcurrentModificationException} when agents are added/removed
 *       during a tick.</li>
 *   <li>The engine depends on the provided {@code Graph}, {@code Agent},
 *       {@code BuildingElement}, {@code Exit}, {@code Passage}, {@code PathFinder}
 *       and {@code RoutingSettings} APIs. The exact semantics of movement,
 *       routing and sensor behavior are determined by those components.</li>
 *   <li>Basic concurrency control is achieved via the {@code running} flag and
 *       thread interruption; callers should take care when accessing shared
 *       simulation state concurrently (for example, from the UI thread).</li>
 * </ul>
 *
 * @see #play()
 * @see #pause()
 * @see #step()
 * @see #addTickListener(java.util.function.Consumer)
 */
public class SimulationEngine {

    /**
     * The graph being simulated
     */
    private final Graph graph;

    /**
     * True if the simulation is currently running
     */
    private boolean running;

    /**
     * Interval between ticks in milliseconds
     */
    private int intervalMs;

    /**
     * Current tick count since start
     */
    private long tickCount;

    /**
     * Listeners called after each tick — used by the controller to trigger a
     * view redraw without coupling engine to JavaFX.
     */
    private final List<Consumer<Long>> tickListeners;

    /**
     * Background thread running the tick loop
     */
    private Thread simulationThread;

    /**
     * Constructor for SimulationEngine.
     *
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
     * Starts the simulation loop in a background thread. Does nothing if
     * already running.
     */
    public void play() {
        if (running) {
            return;
        }
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
     * Executes exactly one tick (step-by-step mode). Pauses the loop first if
     * it was running.
     */
    public void step() {
        pause();
        tick();
    }

    /**
     * Sets the simulation speed.
     *
     * @param intervalMs milliseconds between ticks (lower = faster)
     */
    public void setIntervalMs(int intervalMs) {
        this.intervalMs = Math.max(50, intervalMs);
    }

    public int getIntervalMs() {
        return intervalMs;
    }

    public boolean isRunning() {
        return running;
    }

    public long getTickCount() {
        return tickCount;
    }

    // ── Tick ──────────────────────────────────────────────
    /**
     * Executes one simulation tick: 1. Run all sensors. 2. Move all agents. 3.
     * Notify listeners.
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

            if (!graph.isEmergencyActive()
                    && agent.getDestination() == null
                    && agent.getCurrentLocation() != null
                    && !agent.isInTransit()
                    && agent.getState() != AgentState.TRAPPED) {

                BuildingElement randomDestination = findRandomDestination(agent);

                if (randomDestination != null) {
                    agent.setDestination(randomDestination);
                    agent.setPath(new ArrayList<>());
                    agent.setProgress(0.0);
                }
            }

            agent.move();

            if (agent.getCurrentLocation() instanceof Exit) {
                agent.setEvacuated(true);
                agent.setDestination(null);
                agent.setPath(new ArrayList<>());
                agent.setProgress(0.0);
                continue;
            }

            if (graph.isEmergencyActive()
                    && agent.getCurrentLocation() != null
                    && !(agent.getCurrentLocation() instanceof Exit)
                    && !agent.isInTransit()) {

                // Re-evaluate the agent's exit when it has none yet, or when it is
                // currently trapped (so it can be freed as soon as a safe route
                // exists). The !isInTransit guard prevents teleporting an agent
                // that is mid-edge.
                boolean needsExit = agent.getDestination() == null
                        || agent.getState() == AgentState.TRAPPED;

                if (needsExit) {
                    BuildingElement reachableExit = findNearestExit(agent);

                    if (reachableExit != null) {
                        if (reachableExit != agent.getDestination()) {
                            agent.setDestination(reachableExit);
                            agent.setPath(new ArrayList<>());
                            agent.setProgress(0.0);
                        }

                        if (agent.getState() == AgentState.TRAPPED) {
                            // A safe route is available again → resume evacuation.
                            agent.setState(AgentState.CALM);
                        }
                    } else {
                        // No reachable exit at all → trapped, awaiting rescue.
                        agent.setState(AgentState.TRAPPED);
                    }
                }
            }
        }

        // 3. Notify listeners (triggers view redraw via Platform.runLater in controller)
        for (Consumer<Long> listener : tickListeners) {
            listener.accept(tickCount);
        }
    }

    private BuildingElement findRandomDestination(Agent agent) {
        List<BuildingElement> candidates = new ArrayList<>();

        for (BuildingElement element : graph.getElements()) {
            if (element == null) {
                continue;
            }

            if (element.isBlocked()) {
                continue;
            }

            if (element.getName() != null && element.getName().contains("↔")) {
                continue;
            }

            if (element.equals(agent.getCurrentLocation())) {
                continue;
            }

            if (element instanceof Exit) {
                continue;
            }

            candidates.add(element);
        }

        if (candidates.isEmpty()) {
            return null;
        }

        int index = (int) (Math.random() * candidates.size());
        return candidates.get(index);
    }

    // ── Observers ─────────────────────────────────────────
    private BuildingElement findNearestExit(Agent agent) {
        BuildingElement best = null;
        double bestScore = Double.MAX_VALUE;

        for (BuildingElement el : graph.getElements()) {
            if (!(el instanceof Exit) || el.isBlocked()) {
                continue;
            }

            List<BuildingElement> path = RoutingSettings.isUseFastestPath()
                    ? PathFinder.calculateFastestPath(
                            agent.getCurrentLocation(),
                            el,
                            agent.getMaxSpeed()
                    )
                    : PathFinder.calculateShortestPath(
                            agent.getCurrentLocation(),
                            el
                    );

            // Skip unreachable exits: only an exit with an actual route counts.
            if (path == null || path.isEmpty()) {
                continue;
            }

            double score = RoutingSettings.isUseFastestPath()
                    ? estimateTravelTime(path, agent)
                    : path.size();

            if (score < bestScore) {
                bestScore = score;
                best = el;
            }
        }

        return best;
    }

    private double estimateTravelTime(List<BuildingElement> path, Agent agent) {
        double total = 0.0;

        for (int i = 1; i < path.size(); i++) {
            BuildingElement element = path.get(i);

            double distance = 10.0;
            double speedFactor = 1.0;
            double congestionFactor = 1.0;

            if (element instanceof Passage passage) {
                distance = Math.max(0.1, passage.getDistance());
                speedFactor = Math.max(0.1, passage.getSpeedFactor());

                congestionFactor = passage.getMaxCapacity() > 0
                        ? 1.0 + ((double) passage.getCurrentOccupancy() / passage.getMaxCapacity())
                        : 1.0;
            }

            total += (distance / Math.max(0.1, agent.getMaxSpeed() * speedFactor))
                    * congestionFactor;
        }

        return total;
    }

    /**
     * Registers a tick listener.
     *
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
