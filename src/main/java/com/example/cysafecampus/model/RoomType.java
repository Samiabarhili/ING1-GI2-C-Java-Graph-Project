package com.example.cysafecampus.model;

/**
 * Enum representing the type of a room in the building.
 */
/**
 * Enumeration of room categories used in the campus model.
 *
 * <p>Provides a concise classification of physical spaces so that components
 * such as scheduling, display, and access control can handle rooms based on
 * their intended use.
 *
 * <p>Enum values:
 * <ul>
 *   <li>{@link #AMPHITHEATER} - Large lecture hall suitable for large audiences and presentations.</li>
 *   <li>{@link #CLASSROOM} - Standard classroom for regular teaching sessions.</li>
 *   <li>{@link #OFFICE} - Staff or faculty office, typically used for administrative or faculty work.</li>
 * </ul>
 */
public enum RoomType {
    /** Large lecture hall */
    AMPHITHEATER,

    /** Standard classroom */
    CLASSROOM,

    /** Staff or faculty office */
    OFFICE
}
