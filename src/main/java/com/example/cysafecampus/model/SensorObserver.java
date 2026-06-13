package com.example.cysafecampus.model;

/**
 * Observer interface for the Sensor → AdminAgent notification chain.
 * Only AdminAgent implements this interface: sensors notify the admin,
 * and the admin then decides what to do with supervisors.
 */
/**
 * Listener interface for receiving notifications about sensor-detected events.
 *
 * <p>Implementations of this interface can be registered with sensor sources to
 * be notified whenever a {@link SensorEvent} occurs. The single callback
 * {@link #onSensorEvent(SensorEvent)} receives an event object that describes
 * the event's type, severity and location.</p>
 *
 * <p>Implementations should be designed to return quickly because callbacks may
 * be invoked on I/O or sensor threads; offload any CPU-intensive or blocking
 * work to a separate thread or executor. Implementations should also consider
 * thread-safety if observers can be registered/unregistered or invoked from
 * multiple threads.</p>
 *
 * @see SensorEvent
 */
public interface SensorObserver {

    /**
     * Called by a Sensor when an event is detected.
     * @param event the sensor event containing type, severity and location
     */
    void onSensorEvent(SensorEvent event);
}
