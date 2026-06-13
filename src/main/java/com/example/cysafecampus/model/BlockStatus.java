package com.example.cysafecampus.model;

/**
 * Enum representing the accessibility status of a building element.
 * Replaces the simple boolean isBlocked for finer-grained control.
 */
/**
 * Represents the access state of an element within the campus model.
 *
 * <p>Used by routing, pathfinding and UI components to indicate whether a node,
 * edge or area is available for traversal and under what conditions.</p>
 *
 * <ul>
 *   <li>{@link #ACCESSIBLE} — element is fully accessible (no restrictions).</li>
 *   <li>{@link #BLOCKED} — element is completely blocked and should be treated as impassable
 *       (e.g., fire, collapse, locked access).</li>
 *   <li>{@link #RESTRICTED} — element is accessible but subject to limitations
 *       (e.g., one-way flow, reduced width/capacity, temporary obstruction).</li>
 * </ul>
 *
 * <p>Interpretation guidance:
 * <ul>
 *   <li>{@code ACCESSIBLE}: normal traversal allowed.</li>
 *   <li>{@code BLOCKED}: do not consider for routing or occupancy calculations.</li>
 *   <li>{@code RESTRICTED}: consider with reduced weight/priority or special handling
 *       according to the restriction type.</li>
 * </ul>
 */
public enum BlockStatus {
    /** Element is fully accessible */
    ACCESSIBLE,

    /** Element is completely blocked (fire, collapse, locked) */
    BLOCKED,

    /** Element is accessible but with restrictions (one-way, reduced capacity) */
    RESTRICTED
}
