package com.example.cysafecampus.model;

/**
 * Enum representing the emotional/physical state of an agent.
 * The state influences movement strategy selection.
 */
/**
 * Defines the possible behavioral states of an agent within the simulation.
 *
 * <p>Each state affects how an agent navigates and reacts to hazards:
 * <ul>
 *   <li>{@link #CALM} — the agent is composed and follows instructions, selecting optimal routes.</li>
 *   <li>{@link #PANICKED} — the agent is panicking and may ignore optimal routes or behave unpredictably.</li>
 *   <li>{@link #TRAPPED} — the agent has no safe route remaining (for example, surrounded by fire),
 *       remains visible and does not advance while awaiting rescue; unlike an evacuated agent,
 *       a trapped agent never reached an exit.</li>
 * </ul>
 */
public enum AgentState {
    /** Agent is calm and follows instructions normally */
    CALM,

    /** Agent is panicking — may ignore optimal routes */
    PANICKED,
    /**
     * Agent has no safe route left (surrounded by fire) and is waiting for
     * rescue. A TRAPPED agent stays visible and does NOT advance, but it is
     * distinct from {@code evacuated}: it never reached an exit.
     */
    TRAPPED
}
