package com.example.cysafecampus.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Strategy for security agents guiding people.
 * Moves at normal speed, uses shortest path, waits politely in congestion.
 * Identical to EvacuateStrategy but semantically distinct for security agents.
 */
/**
 * Strategy used by guide/security agents in an evacuation simulation.
 *
 * <p>This strategy indicates that the agent is intended to remain in its
 * assigned zone rather than computing or following an evacuation route.
 * It integrates with the evacuation framework by extending
 * {@code EvacuateStrategy} and is {@link java.io.Serializable} so instances
 * may be serialized when required by the application.
 *
 * <p>Behavior:
 * <ul>
 *   <li>{@code onArrival(Agent)}: Clears any planned path (sets the agent's
 *       path to an empty list) and sets the agent's destination to its
 *       current location so the agent remains at the assigned zone.</li>
 *   <li>{@code calculatePath()}: Returns an empty list because this strategy
 *       does not produce a navigation path.</li>
 * </ul>
 *
 * <p>Use this strategy for actors that act as stationary guides or security
 * personnel who should stay in place after reaching their assigned area.
 *
 * @see EvacuateStrategy
 * @see Agent
 * @see BuildingElement
 * @since 1.0
 */
public class GuideStrategy extends EvacuateStrategy implements java.io.Serializable {

    @Override
    protected void onArrival(Agent agent) {
        // Security agents stay at their assigned zone when they arrive
        agent.setPath(new ArrayList<>());
        agent.setDestination(agent.getCurrentLocation());
    }

    @Override
    public List<BuildingElement> calculatePath() {
        return new ArrayList<>();
    }
}
