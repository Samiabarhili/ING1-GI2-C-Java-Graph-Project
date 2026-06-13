package com.example.cysafecampus.model;

/**
 * Enum representing the type of passage in the building.
 */
/**
 * Types of passages used in the campus/building graph model.
 *
 * <p>This enum classifies the kinds of connections between spaces so routing,
 * visualization, accessibility checks and other logic can treat them appropriately.</p>
 *
 * <ul>
 *   <li>{@link #CORRIDOR} - A horizontal corridor connecting rooms on the same floor.</li>
 *   <li>{@link #STAIRCASE} - A staircase connecting different floors; may affect accessibility and routing cost.</li>
 *   <li>{@link #HALL} - A large open hall connecting multiple passages; typically a junction or open area.</li>
 * </ul>
 *
 * @since 1.0
 */
public enum PassageType {
    /** A horizontal corridor connecting rooms on the same floor */
    CORRIDOR,

    /** A staircase connecting different floors */
    STAIRCASE,

    /** A large open hall connecting multiple passages */
    HALL
}
