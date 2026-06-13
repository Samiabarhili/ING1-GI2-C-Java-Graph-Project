package com.example.cysafecampus.model;

/**
 * Enum representing the type of event detected by a sensor.
 */
/**
 * Types of events that can be emitted by sensors in the system.
 *
 * <p>Each enum constant represents a distinct category of sensor-detected incidents:
 * <ul>
 *   <li>{@link #PRESENCE_DETECTED} — a presence or motion was observed in an area.</li>
 *   <li>{@link #SMOKE_DETECTED} — smoke was detected, indicating a potential fire risk.</li>
 *   <li>{@link #OVERCROWDING} — an area has reached or exceeded its safe capacity.</li>
 * </ul>
 *
 * <p>These event types are intended for use in event propagation, alerting logic,
 * and analytics to determine the appropriate response for each situation.
 */
public enum SensorEventType {
    /** A presence was detected in an area */
    PRESENCE_DETECTED,

    /** Smoke was detected — potential fire */
    SMOKE_DETECTED,

    /** Element has reached or exceeded its max capacity */
    OVERCROWDING
}
