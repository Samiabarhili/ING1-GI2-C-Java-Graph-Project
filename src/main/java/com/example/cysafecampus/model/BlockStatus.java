package com.example.cysafecampus.model;

/**
 * Enum representing the accessibility status of a building element.
 * Replaces the simple boolean isBlocked for finer-grained control.
 */
public enum BlockStatus {
    /** Element is fully accessible */
    ACCESSIBLE,

    /** Element is completely blocked (fire, collapse, locked) */
    BLOCKED,

    /** Element is accessible but with restrictions (one-way, reduced capacity) */
    RESTRICTED
}
