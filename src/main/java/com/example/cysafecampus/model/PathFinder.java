package com.example.cysafecampus.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Utility class responsible for computing navigation routes. Implements
 * Dijkstra's algorithm adapted for multiple criteria.
 */
/**
 * Utility class that provides static path-finding routines for BuildingElement graphs.
 *
 * <p>This class implements a Dijkstra-based search to compute optimal routes between
 * two BuildingElement instances. It offers two public strategies:
 * <ul>
 *   <li>{@link #calculateShortestPath(BuildingElement, BuildingElement)} — chooses paths
 *       based strictly on physical distance.</li>
 *   <li>{@link #calculateFastestPath(BuildingElement, BuildingElement, double)} — chooses
 *       paths based on estimated travel time taking into account agent speed, passage
 *       speed factors, and dynamic congestion.</li>
 * </ul>
 *
 * <p>Algorithm and complexity:
 * <ul>
 *   <li>Implements Dijkstra's algorithm using a PriorityQueue of node records.</li>
 *   <li>Time complexity is O(E log V) in typical adjacency-structure graphs (E edges,
 *       V vertices), dominated by priority queue operations.</li>
 * </ul>
 *
 * <p>Cost models and semantics:
 * <ul>
 *   <li>Shortest-path mode: edge cost = physical distance (units are those returned by
 *       Passage.getDistance()).</li>
 *   <li>Fastest-path mode: time cost = distance / effectiveSpeed, where effectiveSpeed =
 *       agentSpeed * passage.getSpeedFactor() * (1 - congestion). Congestion is computed
 *       as currentOccupancy / maxCapacity and capped below 1.0 to avoid division by zero.</li>
 *   <li>Attractiveness modifiers: after base cost is computed, an attractiveness score
 *       on the target element is applied to slightly reduce or increase the resulting
 *       cost (positive scores reduce cost, negative scores increase it). Scores are
 *       clamped to a reasonable range before application.</li>
 *   <li>Blocked elements (for example due to fire or locked doors) are ignored and not
 *       considered as traversal targets.</li>
 * </ul>
 *
 * <p>Assumptions and requirements:
 * <ul>
 *   <li>Graph connectivity is discovered via {@code Room#getDoors()}, {@code Door#getPassage()},
 *       {@code Passage#getConnectedDoors()}, and {@code Door#getRoom()} semantics; doors must
 *       report their open/closed state.</li>
 *   <li>BuildingElement instances used as nodes must correctly implement {@code equals()}
 *       and {@code hashCode()} because they are stored in maps and the priority queue.</li>
 *   <li>The methods operate on in-memory graph objects and expect any concurrent updates
 *       to the graph to be externally synchronized; the algorithm itself uses only local
 *       data structures and is not synchronized.</li>
 * </ul>
 *
 * <p>Behavior and edge cases:
 * <ul>
 *   <li>If no path exists, an empty list is returned.</li>
 *   <li>If a passage becomes effectively impassable (effective speed &le; 0), that edge
 *       is treated as having infinite cost and is therefore avoided.</li>
 *   <li>Exits are treated as terminal nodes; they do not provide outgoing neighbors.</li>
 * </ul>
 *
 * <p>Note: This class is a focused path-finding helper and intentionally exposes only
 * high-level, static methods. It is suitable for use in routing and evacuation
 * simulations where both distance-optimized and time-optimized routing are required.
 *
 * @see BuildingElement
 * @see Room
 * @see Passage
 * @see Door
 */
public class PathFinder {

    /**
     * Helper class to store a node and its accumulated cost in the
     * PriorityQueue.
     */
    private static class NodeRecord implements Comparable<NodeRecord> {

        BuildingElement element;
        double cost;

        NodeRecord(BuildingElement element, double cost) {
            this.element = element;
            this.cost = cost;
        }

        @Override
        public int compareTo(NodeRecord other) {
            return Double.compare(this.cost, other.cost);
        }
    }

    /**
     * Calculates the shortest path based strictly on physical distance.
     */
    public static List<BuildingElement> calculateShortestPath(BuildingElement start, BuildingElement destination) {
        return findPath(start, destination, 0.0, false);
    }

    /**
     * Calculates the fastest path factoring in distance, agent speed, and edge
     * congestion.
     */
    public static List<BuildingElement> calculateFastestPath(BuildingElement start, BuildingElement destination, double agentSpeed) {
        return findPath(start, destination, agentSpeed, true);
    }

    /**
     * Core Dijkstra algorithm implementation.
     *
     * @param start The starting building element.
     * @param destination The target building element.
     * @param agentSpeed The max speed of the agent.
     * @param useTimeAndCongestion Flag: true to use time/congestion costs,
     * false for physical distance.
     * @return A list of {@link BuildingElement} representing the optimal path,
     * or an empty list if no path is found.
     */
    private static List<BuildingElement> findPath(BuildingElement start, BuildingElement destination, double agentSpeed, boolean useTimeAndCongestion) {
        PriorityQueue<NodeRecord> queue = new PriorityQueue<>();
        Map<BuildingElement, Double> minCosts = new HashMap<>();
        Map<BuildingElement, BuildingElement> previousNodes = new HashMap<>();

        queue.add(new NodeRecord(start, 0.0));
        minCosts.put(start, 0.0);

        while (!queue.isEmpty()) {
            NodeRecord currentRecord = queue.poll();
            BuildingElement currentElement = currentRecord.element;

            // Destination reached
            if (currentElement.equals(destination)) {
                return reconstructPath(previousNodes, destination);
            }

            // For each accessible neighbor
            for (BuildingElement neighbor : getNeighbors(currentElement)) {
                // Skip blocked elements (fire, locked, etc.)
                if (neighbor.isBlocked()) {
                    continue;
                }

                double edgeCost = calculateCost(currentElement, neighbor, agentSpeed, useTimeAndCongestion);
                double newTotalCost = currentRecord.cost + edgeCost;

                if (newTotalCost < minCosts.getOrDefault(neighbor, Double.MAX_VALUE)) {
                    minCosts.put(neighbor, newTotalCost);
                    previousNodes.put(neighbor, currentElement);
                    queue.add(new NodeRecord(neighbor, newTotalCost));
                }
            }
        }

        // No path found
        return new ArrayList<>();
    }

    /**
     * Retrieves all accessible neighboring elements (Room <-> Passage via open
     * Doors).
     */
    private static List<BuildingElement> getNeighbors(BuildingElement current) {
        List<BuildingElement> neighbors = new ArrayList<>();

        if (current instanceof Room) {
            // Room and Exit (subclasses of BuildingElement) share the same door model
            for (Door door : ((Room) current).getDoors()) {
                if (door.isOpen()) {
                    neighbors.add(door.getPassage());
                }
            }
        } else if (current instanceof Passage) {
            for (Door door : ((Passage) current).getConnectedDoors()) {
                if (door.isOpen()) {
                    // The door's room may be a regular Room or a junction Room
                    neighbors.add(door.getRoom());
                }
            }
        }
        // Exit is treated as a terminal node — no outgoing neighbors needed
        return neighbors;
    }

    /**
     * Calculates the cost to move from current to neighbor.
     */
    private static double calculateCost(BuildingElement current, BuildingElement neighbor, double agentSpeed, boolean useTimeAndCongestion) {
        double distance = 1.0; // Base cost to enter a room
        double speedFactor = 1.0;
        double congestion = 0.0;

        if (neighbor instanceof Passage) {
            Passage p = (Passage) neighbor;
            distance = p.getDistance();

            if (useTimeAndCongestion) {
                speedFactor = p.getSpeedFactor();
                // Congestion ratio: C = occupancy / capacity
                congestion = (double) p.getCurrentOccupancy() / p.getMaxCapacity();
                if (congestion >= 1.0) {
                    congestion = 0.99; // Cap at 0.99 to avoid division by zero

                }
            }
        }

        if (!useTimeAndCongestion) {
            return distance; // Shortest path: distance only
        }

// Fastest path: time = distance / (speed * speedFactor * (1 - congestion))
        double effectiveSpeed = agentSpeed * speedFactor * (1.0 - congestion);

        if (effectiveSpeed <= 0) {
            return Double.MAX_VALUE; // Passage effectively impassable
        }

        double timeCost = distance / effectiveSpeed;

        return applyAttractivenessCost(timeCost, neighbor);
    }

    private static double applyAttractivenessCost(double baseCost, BuildingElement element) {
        if (element == null) {
            return baseCost;
        }

        double score = element.getAttractivenessScore();

        if (score == 0.0) {
            return baseCost;
        }

        double clampedScore = Math.max(-5.0, Math.min(5.0, score));

        if (clampedScore > 0.0) {
            return baseCost / (1.0 + clampedScore * 0.10);
        }

        return baseCost * (1.0 + Math.abs(clampedScore) * 0.15);
    }

    /**
     * Backtracks from the destination to the start to build the path list.
     */
    private static List<BuildingElement> reconstructPath(Map<BuildingElement, BuildingElement> previousNodes, BuildingElement target) {
        List<BuildingElement> path = new ArrayList<>();
        BuildingElement current = target;
        while (current != null) {
            path.add(0, current); // Prepend to reconstruct path from start to end
            current = previousNodes.get(current);
        }
        return path;
    }
}
