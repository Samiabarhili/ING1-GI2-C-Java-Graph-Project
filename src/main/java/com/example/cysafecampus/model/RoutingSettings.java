package com.example.cysafecampus.model;

/**
 * Global routing configuration used by movement strategies.
 *
 * false = shortest path by distance / graph steps.
 * true  = fastest path by estimated travel time.
 */
/**
 * Global, application-wide routing preference holder.
 *
 * <p>This utility class exposes a single binary preference that controls whether
 * routing algorithms should prefer the "fastest" path (for example, minimizing
 * travel time) or not (for example, minimizing distance). The preference is
 * stored as a static {@code volatile} boolean to ensure visibility of updates
 * across threads.</p>
 *
 * <p>Key points:</p>
 * <ul>
 *   <li>The class is final with a private constructor and cannot be instantiated.</li>
 *   <li>Access is provided through the static methods {@code isUseFastestPath()} and
 *       {@code setUseFastestPath(boolean)}.</li>
 *   <li>Thread-safety: the {@code volatile} modifier guarantees that writes to the
 *       preference are visible to other threads immediately. There is no additional
 *       synchronization provided; if callers require compound atomic operations
 *       (read-modify-write), they must coordinate externally.</li>
 *   <li>Changing this setting affects routing behavior globally within the JVM.</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>
 * RoutingSettings.setUseFastestPath(true);
 * boolean preferFast = RoutingSettings.isUseFastestPath();
 * </pre>
 */
public final class RoutingSettings {

    private static volatile boolean useFastestPath = false;

    private RoutingSettings() {
        // Utility class.
    }

    public static boolean isUseFastestPath() {
        return useFastestPath;
    }

    public static void setUseFastestPath(boolean value) {
        useFastestPath = value;
    }
}