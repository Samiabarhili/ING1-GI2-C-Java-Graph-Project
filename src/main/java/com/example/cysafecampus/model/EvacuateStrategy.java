package com.example.cysafecampus.model;

import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;

/**
 * Strategy for calm evacuation — moves agent node by node toward destination.
 * Uses shortest path (Dijkstra by distance).
 */
public class EvacuateStrategy implements MovementStrategy, Serializable {

    @Override
    public void execute(Agent agent) {
        if (agent.getDestination() == null) return;
        if (agent.hasArrived()) { onArrival(agent); return; }

        // Compute path if needed
        List<BuildingElement> path = agent.getPath();
        if (path == null || path.isEmpty()) {
            List<BuildingElement> newPath = PathFinder.calculateShortestPath(
                agent.getCurrentLocation(), agent.getDestination());
            if (newPath.isEmpty()) return;
            agent.setPath(newPath);
            path = newPath;
        }

        // Congestion wait
        if (agent.getWaitCycles() > 0) {
            agent.setWaitCycles(agent.getWaitCycles() - 1);
            return;
        }

        int idx = agent.getPathIndex();
        if (idx + 1 >= path.size()) {
            // End of known path — mark arrived
            agent.setCurrentLocation(path.get(path.size() - 1));
            return;
        }

        BuildingElement next = path.get(idx + 1);

        // Strong congestion rule: if a forced relocation placed too many agents
        // in the current node, agents must wait before entering an available edge
        // until the node returns to a normal occupancy level.
        BuildingElement current = agent.getCurrentLocation();
        if (current != null && current.isOvercrowded() && next instanceof Passage) {
            agent.setWaitCycles(2);
            return;
        }

        // Blocked node → recompute path
        if (next.isBlocked()) {
            agent.setPath(new ArrayList<>());
            agent.setProgress(0.0);
            return;
        }

        // If the next element is a passage, apply congestion rules before entering it.
        if (next instanceof Passage) {
            Passage passage = (Passage) next;

            int maxLanes = Math.max(1, passage.getLanes());
            double density = passage.getMaxCapacity() > 0
                ? (double) passage.getCurrentOccupancy() / passage.getMaxCapacity()
                : 0.0;

            if (agent.getBehavior() == Behavior.POLITE && density > agent.getDensityTolerance()) {
                agent.setWaitCycles(2);
                return;
            }

            if (agent.getBehavior() == Behavior.FOLLOWER && passage.getCurrentOccupancy() >= maxLanes) {
                agent.setWaitCycles(1);
                return;
            }

            if (agent.getBehavior() == Behavior.RUDE && passage.getCurrentOccupancy() >= passage.getMaxCapacity()) {
                agent.setWaitCycles(1);
                return;
            }

            if (agent.getBehavior() != Behavior.RUDE && passage.getCurrentOccupancy() >= passage.getMaxCapacity()) {
                agent.setWaitCycles(2);
                return;
            }
        }

        // Move progressively between the current element and the next element.
        // The current location and path index are updated only when progress reaches 1.
        double distance = 10.0;

        if (next instanceof Passage) {
            Passage passage = (Passage) next;
            distance = Math.max(0.1, passage.getDistance());
        }

        double speedFactor = next instanceof Passage
            ? ((Passage) next).getSpeedFactor()
            : 1.0;

        double step = Math.max(0.05, (agent.getMaxSpeed() * speedFactor) / distance);
        double newProgress = agent.getProgress() + step;

        if (newProgress >= 1.0) {
            BuildingElement previous = agent.getCurrentLocation();

            if (previous != null) {
                previous.agentLeaves();
            }

            next.agentEnters(agent.getMaxSpeed());

            agent.setCurrentLocation(next);
            agent.setPathIndex(idx + 1);
            agent.setProgress(0.0);

            if (next.equals(agent.getDestination())) {
                onArrival(agent);
            }

            return;
        }

        agent.setProgress(newProgress);
        return;
    }

    protected void onArrival(Agent agent) {
        agent.setPath(new ArrayList<>());
        agent.setDestination(null);
        agent.setProgress(0.0);
    }

    @Override
    public List<BuildingElement> calculatePath() { return new ArrayList<>(); }
}
