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

        // Blocked → recompute
        if (next.isBlocked()) {
            agent.setPath(new ArrayList<>());
            agent.setProgress(0.0);
            return;
        }

        if (next instanceof Passage && next.isFull() && agent.getBehavior() == Behavior.POLITE) {
            List<BuildingElement> newPath = PathFinder.calculateFastestPath(
                agent.getCurrentLocation(),
                agent.getDestination(),
                agent.getMaxSpeed()
            );

            if (!newPath.isEmpty()) {
                agent.setPath(newPath);
            }

            agent.setWaitCycles(1);
            return;
        }

        if (next instanceof Passage) {
            Passage passage = (Passage) next;

            // Bottleneck — only agents with low density tolerance wait
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

            // First tick entering passage
            if (agent.getProgress() == 0.0) {
                agent.getCurrentLocation().agentLeaves();
                passage.agentEnters(agent.getMaxSpeed());
                agent.setCurrentLocation(passage);
                agent.setPathIndex(idx + 1);
                idx = idx + 1; // update local idx
            }

            // Advance progress
            double dist = Math.max(0.1, passage.getDistance());
            double step = (agent.getMaxSpeed() * passage.getSpeedFactor()) / dist;
            agent.setProgress(agent.getProgress() + step);

            if (agent.getProgress() >= 1.0) {
                agent.setProgress(0.0);
                // Move to next node after the passage
                int nextIdx = idx + 1;
                if (nextIdx < path.size()) {
                    BuildingElement dest = path.get(nextIdx);
                    passage.agentLeaves();
                    dest.agentEnters(agent.getMaxSpeed());
                    agent.setCurrentLocation(dest);
                    agent.setPathIndex(nextIdx);
                } else {
                    // Passage is the last element — already arrived
                    passage.agentLeaves();
                    agent.setCurrentLocation(passage);
                }
            }

        } else {
            // Room or Exit — instant move
            agent.getCurrentLocation().agentLeaves();
            next.agentEnters(agent.getMaxSpeed());
            agent.setCurrentLocation(next);
            agent.setPathIndex(idx + 1);
            agent.setProgress(0.0);
        }
    }

    protected void onArrival(Agent agent) {
        agent.setPath(new ArrayList<>());
        agent.setDestination(null);
        agent.setProgress(0.0);
    }

    @Override
    public List<BuildingElement> calculatePath() { return new ArrayList<>(); }
}
