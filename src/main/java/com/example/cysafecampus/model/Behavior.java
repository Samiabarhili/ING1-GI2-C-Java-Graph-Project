package com.example.cysafecampus.model;

/**
 * Defines the inherent movement behavior of an agent.
 * Influences how the agent interacts with others in congested areas.
 */
/**
 * Defines high-level behavioral profiles for agents in the simulation.
 *
 * <p>Each constant models a distinct navigation style and reaction to nearby
 * agents:</p>
 * <ul>
 *   <li>{@link #POLITE} — yields to others and prefers longer or less crowded routes;</li>
 *   <li>{@link #FOLLOWER} — aligns with nearby agents and follows crowd flows;</li>
 *   <li>{@link #RUDE} — aggressively pushes through crowds and may cause local congestion.</li>
 * </ul>
 *
 * <p>These behaviors can be used to tune pathfinding, collision avoidance, and
 * decision-making heuristics for simulated pedestrians or autonomous actors.</p>
 */
public enum Behavior {
    /** Yields to others, takes longer routes to avoid crowding */
    POLITE,

    /** Follows the crowd without personal initiative */
    FOLLOWER,

    /** Pushes through crowds, may cause local congestion spikes */
    RUDE
}
