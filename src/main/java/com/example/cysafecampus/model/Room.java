package com.example.cysafecampus.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a room in the building (classroom, amphitheater, office, etc.)
 * A room is composed of one or more doors connecting it to passages.
 */
/**
 * Represents a room within a building, such as a classroom, amphitheater or office.
 * <p>
 * A Room is a specialized BuildingElement that adds a type (RoomType) and manages
 * a collection of Door objects that connect the room to other building elements
 * (composition relationship).
 * </p>
 * <p>
 * Instances are constructed with a name, a maximum capacity and a floor number.
 * A RoomType can be provided; when not supplied a sensible default (e.g.
 * {@code RoomType.CLASSROOM}) is used by the backwards-compatible constructor.
 * </p>
 * <p>
 * The room maintains its own list of doors. Doors can be added via {@code addDoor(Door)}
 * and retrieved via {@code getDoors()}. The class does not enforce uniqueness
 * of doors or perform validation on floor or capacity values — such validation
 * should be handled by callers or by the superclass where appropriate.
 * </p>
 * <p>
 * Thread-safety: this class is not synchronized. If a Room instance is accessed
 * concurrently from multiple threads, external synchronization is required when
 * mutating or iterating the doors list.
 * </p>
 *
 * @see com.example.cysafecampus.model.BuildingElement
 * @see com.example.cysafecampus.model.Door
 * @see com.example.cysafecampus.model.RoomType
 * @since 1.0
 */
public class Room extends BuildingElement {

    /** Type of room (classroom, amphitheater, office) */
    private RoomType type;

    /** List of doors connected to this room (composition) */
    private List<Door> doors;

    /**
     * Constructor for Room.
     * @param name the room name (e.g. "Salle 101")
     * @param maxCapacity maximum number of agents
     * @param floor floor number
     * @param type the room type
     */
    public Room(String name, int maxCapacity, int floor, RoomType type) {
        super(name, maxCapacity);
        setFloor(floor);
        this.type = type;
        this.doors = new ArrayList<>();
    }

    /** Backwards-compatible constructor without RoomType. */
    public Room(String name, int maxCapacity, int floor) {
        this(name, maxCapacity, floor, RoomType.CLASSROOM);
    }

    public RoomType getType() { return type; }
    public List<Door> getDoors() { return doors; }

    /**
     * Adds a door to this room.
     * @param door the door to add
     */
    public void addDoor(Door door) {
        doors.add(door);
    }
}