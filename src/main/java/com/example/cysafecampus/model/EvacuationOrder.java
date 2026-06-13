package com.example.cysafecampus.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single evacuation order targeting a specific room.
 * Created by AdminAgent and sent to the SupervisorAgent assigned to that room.
 * Contains the target room and the recommended exit route.
 */
/**
 * Represents an order to evacuate a specific room of the building.
 *
 * <p>An EvacuationOrder bundles three pieces of information:
 * <ul>
 *   <li>{@code targetRoom} – the Room whose occupants should leave;</li>
 *   <li>{@code exitRoute} – an ordered list of BuildingElement instances describing
 *       the recommended path from the target room to an exit (for example:
 *       Room → Corridor → Staircase → Exit); and</li>
 *   <li>{@code urgencyLevel} – an integer severity level from 1 (low) to 5 (immediate)
 *       that reflects how quickly the evacuation should occur.</li>
 * </ul>
 *
 * <p>The class implements {@code Serializable} to allow orders to be transmitted or persisted.
 *
 * <h3>Null and mutability semantics</h3>
 * <ul>
 *   <li>If a {@code null} exitRoute is provided to the constructor, the internal route is initialized
 *       to an empty {@code ArrayList}.</li>
 *   <li>The internal {@code exitRoute} list reference is exposed by {@link #getExitRoute()}.
 *       This class does not defensively copy the list on return; callers that must not mutate
 *       the stored route should create their own defensive copy and treat the returned list as
 *       read-only.</li>
 *   <li>No validation is enforced by this class on the {@code urgencyLevel} parameter. Callers are
 *       expected to provide a value in the range 1..5.</li>
 * </ul>
 *
 * <h3>String representation</h3>
 * <p>The {@link #toString()} implementation produces a concise one-line summary that includes
 * the target room name, urgency level and the number of steps in the route.
 *
 * @see Room
 * @see BuildingElement
 * @serial
 */
public class EvacuationOrder implements Serializable {

    /** The room that must be evacuated */
    private Room targetRoom;

    /**
     * Ordered list of building elements the occupants should follow
     * to reach the exit (e.g. Room → Corridor → Staircase → Exit).
     */
    private List<BuildingElement> exitRoute;

    /**
     * Urgency level from 1 (low) to 5 (immediate).
     * Mirrors the sensor event severity that triggered this order.
     */
    private int urgencyLevel;

    /**
     * Constructor for EvacuationOrder.
     * @param targetRoom the room to evacuate
     * @param exitRoute the recommended path to the exit
     * @param urgencyLevel urgency from 1 to 5
     */
    public EvacuationOrder(Room targetRoom, List<BuildingElement> exitRoute, int urgencyLevel) {
        this.targetRoom = targetRoom;
        this.exitRoute = exitRoute != null ? exitRoute : new ArrayList<>();
        this.urgencyLevel = urgencyLevel;
    }

    public Room getTargetRoom() { return targetRoom; }
    public List<BuildingElement> getExitRoute() { return exitRoute; }
    public int getUrgencyLevel() { return urgencyLevel; }

    @Override
    public String toString() {
        return "EvacuationOrder[room=" + targetRoom.getName()
            + ", urgency=" + urgencyLevel
            + ", route=" + exitRoute.size() + " steps]";
    }
}
