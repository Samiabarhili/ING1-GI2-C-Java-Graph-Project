package com.example.cysafecampus.model;

/**
 * Sensor that detects smoke levels in a building element.
 * Triggers a SMOKE_DETECTED event when smoke exceeds the threshold.
 * Severity scales with smoke level relative to threshold.
 */
/**
 * Sensor that monitors the smoke concentration for a specific BuildingElement
 * and notifies registered observers when the measured value meets or exceeds a
 * configured threshold.
 *
 * <p>The sensor maintains:
 * <ul>
 *   <li>{@code smokeLevel} — the most recent measured smoke value (arbitrary units, e.g. ppm).</li>
 *   <li>{@code threshold} — the smoke level above which an alert should be emitted.</li>
 * </ul>
 * The current reading should be updated via {@code setSmokeLevel(double)} (for
 * example once per simulation tick). Calling {@code detect()} compares the
 * current reading with the threshold and, if the threshold is exceeded,
 * creates and dispatches a {@link SensorEvent} of type
 * {@link SensorEventType#SMOKE_DETECTED} to observers.</p>
 *
 * <p>Severity calculation (used when notifying observers):
 * <ul>
 *   <li>Compute ratio = smokeLevel / threshold.</li>
 *   <li>Compute severity = ceil(ratio * 2), then clamp to a maximum of 5.</li>
 *   <li>Examples: ratio 1.0 → severity 2; ratio 2.0 → severity 4; ratio ≥ 3.0 → severity 5 (critical).</li>
 * </ul>
 * </p>
 *
 * <p>Typical usage:
 * <pre>
 * SmokeSensor sensor = new SmokeSensor("id", element, threshold);
 * sensor.setSmokeLevel(currentReading);
 * sensor.detect(); // may notify observers if reading >= threshold
 * </pre>
 * </p>
 *
 * @see Sensor
 * @see SensorEvent
 * @see SensorEventType#SMOKE_DETECTED
 */
public class SmokeSensor extends Sensor {

    /** Current measured smoke level (arbitrary units, e.g. ppm) */
    private double smokeLevel;

    /** Level above which an alert is triggered */
    private double threshold;

    /**
     * Constructor for SmokeSensor.
     * @param id unique sensor identifier
     * @param monitoredElement the element to monitor
     * @param threshold smoke level triggering an alert
     */
    public SmokeSensor(String id, BuildingElement monitoredElement, double threshold) {
        super(id, monitoredElement);
        this.smokeLevel = 0.0;
        this.threshold = threshold;
    }

    public double getSmokeLevel() { return smokeLevel; }
    public double getThreshold() { return threshold; }

    /** Updates the current smoke reading (called each simulation tick). */
    public void setSmokeLevel(double smokeLevel) { this.smokeLevel = smokeLevel; }

    /**
     * Checks if smoke exceeds threshold and notifies observers.
     * Severity is proportional to how far above the threshold the reading is:
     * 1x → severity 2, 2x → severity 4, 3x+ → severity 5 (critical).
     */
    @Override
    public void detect() {
        if (smokeLevel >= threshold) {
            double ratio = smokeLevel / threshold;
            int severity = Math.min(5, (int) Math.ceil(ratio * 2));

            notifyObservers(new SensorEvent(
                SensorEventType.SMOKE_DETECTED,
                severity,
                getMonitoredElement()
            ));
        }
    }
}
