package com.example.cysafecampus.model;

/**
 * Enum representing the type of event detected by a sensor.
 */
public enum SensorEventType {
    /** A presence was detected in an area */
    PRESENCE_DETECTED,

    /** Smoke was detected — potential fire */
    SMOKE_DETECTED,

    /** Element has reached or exceeded its max capacity */
    OVERCROWDING
}
