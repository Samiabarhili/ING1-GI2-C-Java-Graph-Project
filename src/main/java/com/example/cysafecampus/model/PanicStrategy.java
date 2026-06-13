package com.example.cysafecampus.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
/**
 * Strategy for panicked agents.
 *
 * Panicked agents move faster and ignore polite congestion waits. They can also
 * choose a less rational safe route: sometimes they follow the shortest visible
 * route instead of the fastest/congestion-aware route.
 *
 * A panicked agent still never enters a blocked/fire zone.
 */
/**
 * PanicStrategy implements an evacuation policy in which agents behave under panic:
 * they attempt to escape faster than normal and may choose suboptimal routes with
 * a behavior-dependent probability.
 *
 * <p>Key behaviors:
 * <ul>
 *   <li>When executed for an agent, the strategy advances the agent along a precomputed path
 *       to its destination or computes a new path when none is available.</li>
 *   <li>Path selection alternates between a "fastest" path and, with some probability,
 *       a "bad" (shortest) route that represents poor decision-making under panic.
 *       The probability of selecting the bad route is determined by
 *       {@link #badRouteProbabilityFor(Agent)} and varies by agent behavior.</li>
 *   <li>Agents move faster than their normal maximum speed by a panic multiplier.
 *       The actual multiplier applied is determined by {@link #panicMultiplierFor(Agent)}
 *       and depends on the agent's behavior (POLITE, FOLLOWER, RUDE, or default).</li>
 *   <li>The strategy handles partial traversal of corridor/passage segments by updating
 *       agent progress and only transferring occupancy when an agent completes traversal.</li>
 *   <li>If the next element on the path is blocked, the strategy invokes the
 *       blocking-handling logic inherited from the evacuation framework (e.g. try/handle
 *       re-routing, waiting or abandoning the destination).</li>
 * </ul>
 *
 * <p>Design notes:
 * <ul>
 *   <li>This class extends {@code EvacuateStrategy} and implements {@code Serializable} so
 *       it can be used in simulations that persist strategy instances.</li>
 *   <li>Two class-level constants provide baseline configuration:
 *       {@link #PANIC_MULTIPLIER} (baseline speed multiplier) and
 *       {@link #BAD_ROUTE_PROBABILITY} (fallback probability for a bad route).</li>
 *   <li>Path computation delegates to the {@code PathFinder} utility:
 *       {@code calculateFastestPath} when attempting to reach quickly, or
 *       {@code calculateShortestPath} when a bad route is chosen.</li>
 *   <li>The strategy interacts with building model primitives such as
 *       {@code Agent}, {@code BuildingElement} and {@code Passage} to manage entry/exit,
 *       occupancy and per-segment travel distance/speed factors.</li>
 * </ul>
 *
 * <p>Behavior-dependent tuning methods:
 * <ul>
 *   <li>{@link #panicMultiplierFor(Agent)} - returns the speed multiplier applied during panic
 *       and encodes behavioral differences (polite, follower, rude, default).</li>
 *   <li>{@link #badRouteProbabilityFor(Agent)} - returns the probability that a panicking
 *       agent will choose a suboptimal (shortest) route instead of the calculated fastest route.</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 *   // Typical use is to attach an instance of this strategy to an agent and call
 *   // its {@code execute(agent)} method on each simulation tick to advance the agent.
 * </pre>
 *
 * @see EvacuateStrategy
 * @see Agent
 * @see PathFinder
 * @see BuildingElement
 * @see Passage
 */
public class PanicStrategy extends EvacuateStrategy implements Serializable {

    private static final double PANIC_MULTIPLIER = 1.5;
    private static final double BAD_ROUTE_PROBABILITY = 0.35;

    @Override
    public void execute(Agent agent) {
        if (agent.getDestination() == null) {
            return;
        }

        if (agent.hasArrived()) {
            onArrival(agent);
            return;
        }

        List<BuildingElement> path = agent.getPath();

        if (path == null || path.isEmpty()) {
            List<BuildingElement> newPath = calculatePanicPath(agent);

            if (newPath == null || newPath.isEmpty()) {
                agent.setDestination(null);

                if (!agent.isInTransit()) {
                    agent.setPath(new ArrayList<>());
                    agent.setPathIndex(0);
                    agent.setProgress(0.0);
                }

                return;
            }

            agent.setPath(newPath);
            agent.setPathIndex(0);
            agent.setProgress(0.0);
            path = newPath;
        }

        agent.setWaitCycles(0);

        int index = agent.getPathIndex();

        if (index + 1 >= path.size()) {
            agent.setCurrentLocation(path.get(path.size() - 1));
            return;
        }

        BuildingElement current = agent.getCurrentLocation();
        BuildingElement next = path.get(index + 1);

        if (next.isBlocked()) {
            handleBlockedNextNode(agent, current, next);
            return;
        }
        boolean urgentEscape = current != null && current.isBlocked();

        if (!tryEnterCorridorSegment(agent, current, next, urgentEscape, true)) {
            return;
        }
        double distance = 10.0;
        double speedFactor = 1.0;

        if (next instanceof Passage passage) {
            distance = Math.max(0.1, passage.getDistance());
            speedFactor = passage.getSpeedFactor();
        }

        double step = Math.max(
                0.05,
                (agent.getMaxSpeed() * panicMultiplierFor(agent) * speedFactor) / distance);

        double newProgress = agent.getProgress() + step;

        if (newProgress >= 1.0) {
            BuildingElement previous = agent.getCurrentLocation();

            if (previous != null) {
                previous.agentLeaves();
            }

            next.agentEnters(agent.getMaxSpeed() * panicMultiplierFor(agent));
            recordCorridorPassage(previous, next, agent.getMaxSpeed() * panicMultiplierFor(agent));
            releaseCurrentDoor(agent);

            agent.setCurrentLocation(next);
            agent.setPathIndex(index + 1);
            agent.setProgress(0.0);

            if (next.equals(agent.getDestination())) {
                onArrival(agent);
            }

            return;
        }

        agent.setProgress(newProgress);
    }

    private List<BuildingElement> calculatePanicPath(Agent agent) {
        boolean badRoute = Math.random() < badRouteProbabilityFor(agent);
        if (badRoute) {
            return PathFinder.calculateShortestPath(
                    agent.getCurrentLocation(),
                    agent.getDestination()
            );
        }

        return PathFinder.calculateFastestPath(
                agent.getCurrentLocation(),
                agent.getDestination(),
                agent.getMaxSpeed() * panicMultiplierFor(agent));
    }

    private double panicMultiplierFor(Agent agent) {
        if (agent.getBehavior() == Behavior.POLITE) {
            return 1.25;
        }

        if (agent.getBehavior() == Behavior.FOLLOWER) {
            return 1.45;
        }

        if (agent.getBehavior() == Behavior.RUDE) {
            return 1.65;
        }

        return 1.5;
    }

    private double badRouteProbabilityFor(Agent agent) {
        if (agent.getBehavior() == Behavior.POLITE) {
            return 0.20;
        }

        if (agent.getBehavior() == Behavior.FOLLOWER) {
            return 0.40;
        }

        if (agent.getBehavior() == Behavior.RUDE) {
            return 0.55;
        }

        return BAD_ROUTE_PROBABILITY;
    }
}
