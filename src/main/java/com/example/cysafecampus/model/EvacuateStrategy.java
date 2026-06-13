package com.example.cysafecampus.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Strategy for calm evacuation — moves agent node by node toward destination.
 * Uses shortest path (Dijkstra by distance).
 */
/**
 * Movement strategy that drives an Agent toward a chosen destination (typically an exit)
 * while enforcing evacuation-specific rules such as congestion handling, door/corridor
 * occupancy, blocked-node reactions and special-case panic behavior.
 *
 * <p>Responsibilities and high-level behavior:
 * <ul>
 *   <li>Delegates to PanicStrategy if the agent is in a PANICKED state and this strategy
 *       instance is not already a panic strategy.</li>
 *   <li>Requests a path from the PathFinder when the agent has no cached path. The path
 *       computation respects the global RoutingSettings (fastest vs shortest) and the
 *       agent's maximum speed.</li>
 *   <li>If the computed path is empty, the current destination is considered unreachable;
 *       the destination is cleared and the agent will be allowed to attempt a different
 *       exit on the next tick. The agent is not immediately marked TRAPPED here.</li>
 *   <li>Implements "urgent escape" semantics: an agent located on a blocked node must not
 *       wait and gets priority evacuation semantics.</li>
 *   <li>Applies congestion and behavior-specific wait rules before entering passages
 *       (corridors) and doors. Behavior types (POLITE, FOLLOWER, RUDE) influence wait
 *       durations and tolerance to density.</li>
 *   <li>Tracks progressive motion along an edge using a per-agent progress value. Agents
 *       are only anchored to a new node when progress reaches 1.0.</li>
 *   <li>Manages door occupancy: finding the connecting Door for a room↔passage edge,
 *       attempting to enter it (respecting open/closed state and capacity), recording a
 *       successful passage and releasing the previously occupied door when leaving it.</li>
 *   <li>Handles the case when the next node becomes blocked while the agent is en route:
 *       <ul>
 *         <li>If in-transit (0 &lt; progress &lt; 1) and the node behind is safe, the agent
 *             retreats smoothly along the same edge (decreasing progress) rather than
 *             being teleported.</li>
 *         <li>If the agent is trapped between two blocked nodes or cannot retreat, it is
 *             marked TRAPPED and stops advancing until rescued.</li>
 *         <li>If the agent is sitting exactly on a node (progress == 0) and the next node
 *             is blocked, the current planned path is cleared so a new route excluding
 *             blocked nodes will be recomputed on the next tick.</li>
 *       </ul>
 *   </li>
 *   <li>Provides a default calculatePath() that returns an empty list (path computation is
 *       delegated to PathFinder during execution).</li>
 * </ul>
 *
 * <p>Side effects:
 * <ul>
 *   <li>Mutates the Agent (state, current location, path, path index, progress, wait cycles,
 *       current door, destination).</li>
 *   <li>Invokes occupancy updates on Door and BuildingElement objects (agentEnters/agentLeaves,
 *       recordAgentPassage).</li>
 * </ul>
 *
 * <p>Implementation notes:
 * <ul>
 *   <li>The strategy intentionally avoids teleporting agents when edges change state; in-transit
 *       agents retain their interpolated position until they naturally reach a node or retreat.</li>
 *   <li>Strong-congestion handling prevents an agent from repeatedly attempting to enter an
 *       overcrowded node by imposing a short forced delay and recording a marker so the delay
 *       is not reapplied infinitely.</li>
 *   <li>The strategy consults Passage and Door capacity/occupancy and agent-specific tolerance
 *       values to decide whether to wait or enter.</li>
 * </ul>
 *
 * <p>Notes on thread safety:
 * This class mutates shared model objects (Agent, Door, BuildingElement) and is not safe for
 * concurrent use from multiple threads unless external synchronization is provided by the
 * simulation engine.
 *
 * @see Agent
 * @see PathFinder
 * @see RoutingSettings
 * @see Passage
 * @see Door
 */
public class EvacuateStrategy implements MovementStrategy, Serializable {

    @Override
    public void execute(Agent agent) {

        if (agent.getState() == AgentState.PANICKED
                && !(this instanceof PanicStrategy)) {
            new PanicStrategy().execute(agent);
            return;
        }

        if (agent.getDestination() == null) {
            return;
        }
        if (agent.hasArrived()) {
            onArrival(agent);
            return;
        }

        // Compute path if needed
        List<BuildingElement> path = agent.getPath();
        if (path == null || path.isEmpty()) {
            List<BuildingElement> newPath = RoutingSettings.isUseFastestPath()
                    ? PathFinder.calculateFastestPath(
                            agent.getCurrentLocation(),
                            agent.getDestination(),
                            agent.getMaxSpeed()
                    )
                    : PathFinder.calculateShortestPath(
                            agent.getCurrentLocation(),
                            agent.getDestination()
                    );

            if (newPath.isEmpty()) {
                // The current destination is unreachable, but another exit may still be safe.
                // Do not mark the agent as trapped here. The SimulationEngine will search
                // for another reachable exit on the next tick.
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

        BuildingElement current = agent.getCurrentLocation();
        boolean urgentEscape = current != null && current.isBlocked();

// If the agent is currently inside a blocked/fire zone, it must not wait.
        if (urgentEscape) {
            agent.setWaitCycles(0);
        } else if (agent.getWaitCycles() > 0) {
            agent.setWaitCycles(agent.getWaitCycles() - 1);
            return;
        }

        int idx = agent.getPathIndex();
        if (idx + 1 >= path.size()) {
            // End of known path — mark arrived
            agent.setCurrentLocation(path.get(path.size() - 1));
            return;
        }

        BuildingElement next = path.get(idx + 1);

        // Strong congestion rule: if a forced relocation placed too many agents
        // in the current node, the agent loses two simulation cycles before it
        // can enter an available corridor. The marker prevents infinite waiting
        // while the node is still overcrowded.
        if (current != null && !current.isOvercrowded()) {
            agent.clearStrongCongestionDelayMarker();
        }

        if (!urgentEscape
                && current != null
                && current.isOvercrowded()
                && next instanceof Passage
                && !agent.hasCompletedStrongCongestionDelayFor(current)) {
            agent.setWaitCycles(2);
            agent.markStrongCongestionDelayCompletedFor(current);
            return;
        }

        // Blocked node ahead → never snap the agent back to a node centre.
        if (next.isBlocked()) {
            handleBlockedNextNode(agent, current, next);
            return;
        }

        // If the next element is a passage, apply congestion rules before entering it.
        if (!urgentEscape && next instanceof Passage) {
            Passage passage = (Passage) next;

            int maxLanes = Math.max(1, passage.getLanes());
            double density = passage.getMaxCapacity() > 0
                    ? (double) passage.getCurrentOccupancy() / passage.getMaxCapacity()
                    : 0.0;

            if (agent.getBehavior() == Behavior.POLITE && density > agent.getDensityTolerance()) {
                agent.setWaitCycles(2);
                return;
            }

            if (agent.getBehavior() == Behavior.FOLLOWER && passage.getCurrentOccupancy() >= maxLanes) {
                agent.setWaitCycles(1);
                return;
            }

            if (agent.getBehavior() == Behavior.RUDE && passage.getCurrentOccupancy() >= passage.getMaxCapacity()) {
                agent.setWaitCycles(1);
                return;
            }

            if (agent.getBehavior() != Behavior.RUDE && passage.getCurrentOccupancy() >= passage.getMaxCapacity()) {
                agent.setWaitCycles(2);
                return;
            }
        }
        if (!tryEnterCorridorSegment(agent, current, next, urgentEscape, false)) {
            return;
        }

        // Move progressively between the current element and the next element.
        // The current location and path index are updated only when progress reaches 1.
        double distance = 10.0;

        if (next instanceof Passage) {
            Passage passage = (Passage) next;
            distance = Math.max(0.1, passage.getDistance());
        }

        double speedFactor = next instanceof Passage
                ? ((Passage) next).getSpeedFactor()
                : 1.0;

        double step = Math.max(0.05, (agent.getMaxSpeed() * speedFactor) / distance);
        double newProgress = agent.getProgress() + step;

        if (newProgress >= 1.0) {
            BuildingElement previous = agent.getCurrentLocation();

            if (previous != null) {
                previous.agentLeaves();
            }

            next.agentEnters(agent.getMaxSpeed());
            recordCorridorPassage(previous, next, agent.getMaxSpeed());
            releaseCurrentDoor(agent);

            agent.setCurrentLocation(next);
            agent.setPathIndex(idx + 1);
            agent.setProgress(0.0);

            if (next.equals(agent.getDestination())) {
                onArrival(agent);
            }

            return;
        }

        agent.setProgress(newProgress);
        return;
    }

    protected boolean tryEnterCorridorSegment(
            Agent agent,
            BuildingElement current,
            BuildingElement next,
            boolean urgentEscape,
            boolean ignoreBehaviorRules
    ) {
        if (agent.isInTransit()) {
            return true;
        }

        Door corridor = findConnectingDoor(current, next);

        if (corridor == null) {
            return true;
        }

        if (!corridor.isOpen()) {
            agent.setWaitCycles(urgentEscape ? 1 : 2);
            return false;
        }

        double density = corridor.getOccupancyRatio();

        if (!urgentEscape && !ignoreBehaviorRules) {
            if (agent.getBehavior() == Behavior.POLITE
                    && density >= agent.getDensityTolerance()) {
                agent.setWaitCycles(2);
                return false;
            }

            if (agent.getBehavior() == Behavior.FOLLOWER
                    && density >= 0.80) {
                agent.setWaitCycles(1);
                return false;
            }
        }

        if (corridor.isFull()) {
            agent.setWaitCycles(
                    agent.getBehavior() == Behavior.RUDE || urgentEscape ? 1 : 2
            );
            return false;
        }

        boolean entered = corridor.agentEnters();

        if (!entered) {
            agent.setWaitCycles(
                    agent.getBehavior() == Behavior.RUDE || urgentEscape ? 1 : 2
            );
            return false;
        }

        agent.setCurrentDoor(corridor);
        return true;
    }

    protected void releaseCurrentDoor(Agent agent) {
        Door currentDoor = agent.getCurrentDoor();

        if (currentDoor != null) {
            currentDoor.agentLeaves();
            agent.clearCurrentDoor();
        }
    }

    /**
     * Records edge-level statistics for the corridor segment that connects two
     * consecutive path elements. This keeps edge statistics separate from room,
     * exit and junction statistics.
     *
     * @param previous element left by the agent
     * @param next element reached by the agent
     * @param agentSpeed speed of the agent
     */
    protected void recordCorridorPassage(BuildingElement previous, BuildingElement next, double agentSpeed) {
        Door door = findConnectingDoor(previous, next);

        if (door != null) {
            door.recordAgentPassage(agentSpeed);
        }
    }

    /**
     * Finds the door object that represents the corridor segment between two
     * neighboring graph elements.
     *
     * @param first first graph element
     * @param second second graph element
     * @return matching door, or null when the elements are not directly linked
     */
    protected Door findConnectingDoor(BuildingElement first, BuildingElement second) {
        if (first instanceof Room && second instanceof Passage) {
            return findDoor((Room) first, (Passage) second);
        }

        if (first instanceof Passage && second instanceof Room) {
            return findDoor((Room) second, (Passage) first);
        }

        return null;
    }

    /**
     * Finds the door linking a specific room-like node to a specific passage.
     *
     * @param room room-like node
     * @param passage passage node
     * @return matching door, or null if no matching segment exists
     */
    private Door findDoor(Room room, Passage passage) {
        for (Door door : room.getDoors()) {
            if (door.getPassage() == passage) {
                return door;
            }
        }

        return null;
    }

    /**
     * Handles the case where the node the agent is heading toward
     * ({@code next}) has just become blocked (e.g. reached by fire).
     *
     * <p>
     * Design rule "engaged = engaged": an agent already in transit is never
     * teleported and never reverses through a fictional position. The model has
     * no real "position on edge" concept beyond {@code progress}, so the safe,
     * stable behaviour is:</p>
     *
     * <ul>
     * <li>In transit ({@code 0 < progress < 1}) → hold the current interpolated
     * position. We do NOT touch path, pathIndex, progress or currentLocation,
     * so the agent stays exactly where it is drawn. It will only recompute once
     * it has reached a real node.</li>
     * <li>Not in transit ({@code progress == 0}, sitting on a node) → it is on
     * a node centre, so clearing the path is safe and triggers a blocked-free
     * recompute next tick (PathFinder skips blocked nodes).</li>
     * </ul>
     *
     * <p>
     * Note: while in transit toward a node that just burned, the agent stays
     * put until a continuous-retreat behaviour is added. This is intentional —
     * it is the safe stopgap that removes the teleport without inventing edge
     * positions.</p>
     *
     * @param agent the moving agent
     * @param current the node the agent is currently anchored to (kept for a
     * future continuous-retreat implementation)
     * @param next the node ahead that just became blocked (kept for the same
     * reason)
     */
    protected void handleBlockedNextNode(Agent agent, BuildingElement current, BuildingElement next) {
        if (agent.isInTransit()) {
            if (current == null || current.isBlocked()) {
                // Trapped between two blocked nodes (fire ahead and behind):
                // hold the interpolated position and wait for rescue.
                markTrapped(agent);
                return;
            }

            // The node ahead is on fire but the node behind is safe. Retreat
            // smoothly along the SAME edge back to it (progress decreases, no
            // teleport). Once back on the node, the path is dropped so execute()
            // recomputes a fresh route to the destination — even a long detour is
            // preferable to staying stuck in front of the fire.
            double distance = next instanceof Passage
                    ? Math.max(0.1, ((Passage) next).getDistance())
                    : 10.0;
            double retreatStep = Math.max(0.05, agent.getMaxSpeed() / distance);
            double retreated = agent.getProgress() - retreatStep;

            if (retreated > 0.0) {
                agent.setProgress(retreated);
                return;
            }
        }

        // On a real node now (progress == 0): drop the unsafe path. execute()
        // recomputes from here next tick and marks the agent TRAPPED only if no
        // route to the destination exists at all.
        releaseCurrentDoor(agent);
        agent.setPath(new ArrayList<>());
        agent.setProgress(0.0);
    }

    /**
     * Marks an agent as trapped: no safe route is available, so it must wait
     * for rescue. A trapped agent stays visible, keeps its current interpolated
     * position and stops advancing (see {@link Agent#move()}). It is NOT
     * evacuated — it never reached an exit.
     *
     * @param agent the agent with no safe option
     */
    protected void markTrapped(Agent agent) {
        agent.setState(AgentState.TRAPPED);
    }

    protected void onArrival(Agent agent) {
        releaseCurrentDoor(agent);
        agent.setPath(new ArrayList<>());
        agent.setDestination(null);
        agent.setProgress(0.0);
    }

    @Override
    public List<BuildingElement> calculatePath() {
        return new ArrayList<>();
    }
}
