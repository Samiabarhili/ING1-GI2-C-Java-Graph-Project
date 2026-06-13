package com.example.cysafecampus.model;

import java.util.List;

/**
 * Interface defining the movement strategy for an agent.
 * Different strategies implement different behaviors
 * (evacuate, panic, guide, etc.)
 */
/**
 * Strategy interface that defines how an Agent moves within the building model.
 *
 * Implementations encapsulate both the logic to compute a path through the
 * building (calculatePath) and the procedure to advance or relocate an Agent
 * according to that path (execute). Different algorithms (e.g. shortest path,
 * heuristic-driven, random, evacuation-focused) can be provided by implementing
 * this interface without modifying the Agent or environment code.
 *
 * Implementations should document any assumptions about the graph of
 * BuildingElement instances (connectivity, weights, dynamic updates) and
 * indicate whether they are thread-safe or maintain internal state.
 *
 * @see Agent
 * @see BuildingElement
 */
public interface MovementStrategy {

    /**
     * Executes the movement strategy for the given agent.
     * @param agent the agent to move
     */
    void execute(Agent agent);

    /**
     * Calculates the best path for the agent to follow.
     * @return list of building elements representing the path
     */
    List<BuildingElement> calculatePath();
}