package com.example.cysafecampus.model;

/**
 * Sensor that monitors presence and occupancy in a building element.
 *
 * <p>
 * On each detection cycle, the sensor reads the monitored element occupancy,
 * updates its real-time people count and notifies observers when presence or
 * overcrowding is detected.
 * </p>
 *
 * @see Sensor
 * @see BuildingElement
 * @see SensorEvent
 * @see SensorEventType
 */
public class PresenceSensor extends Sensor {

    /** Whether presence is currently detected. */
    private boolean detectedPresence;

    /**
     * Constructs a presence sensor for a building element.
     *
     * @param id unique sensor identifier
     * @param monitoredElement element monitored by this sensor
     */
    public PresenceSensor(String id, BuildingElement monitoredElement) {
        super(id, monitoredElement);
        this.detectedPresence = false;
    }

    /**
     * Indicates whether presence is currently detected.
     *
     * @return {@code true} if the monitored element is occupied
     */
    public boolean isDetectedPresence() {
        return detectedPresence;
    }

    /**
     * Reads the current occupancy of the monitored element.
     *
     * <p>
     * If the element is occupied, the sensor emits either an overcrowding event
     * or a presence-detected event depending on the element capacity.
     * </p>
     */
    @Override
    public void detect() {
        BuildingElement element = getMonitoredElement();
        int occupancy = element.getCurrentOccupancy();
        setRealTimePeopleCount(occupancy);

        if (occupancy > 0) {
            detectedPresence = true;

            SensorEventType type;
            int severity;

            if (element.isFull()) {
                type = SensorEventType.OVERCROWDING;
                severity = 4;
            } else {
                type = SensorEventType.PRESENCE_DETECTED;
                severity = 1;
            }

            notifyObservers(new SensorEvent(type, severity, element));
        } else {
            detectedPresence = false;
        }
    }
}