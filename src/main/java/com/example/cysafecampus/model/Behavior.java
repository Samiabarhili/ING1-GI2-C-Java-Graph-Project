package com.example.cysafecampus.model;

/**
 * Defines the inherent movement behavior of an agent.
 * Influences how the agent interacts with others in congested areas.
 */
public enum Behavior {
    /** Yields to others, takes longer routes to avoid crowding */
    POLITE,

    /** Follows the crowd without personal initiative */
    FOLLOWER,

    /** Pushes through crowds, may cause local congestion spikes */
    RUDE
}
