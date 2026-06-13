package com.example.cysafecampus.model;

/**
 * Represents an occupant agent in the building simulation.
 *
 * <p>
 * A person is idle by default and waits for an alert or evacuation order before
 * moving. During a fire alert, the person may become panicked depending on its
 * behavior. In all cases, movement remains progressive through an
 * {@link EvacuateStrategy}.
 * </p>
 *
 * @see Agent
 * @see EvacuateStrategy
 */
public class Person extends Agent {

    /**
     * Constructs a person with no default movement strategy.
     *
     * @param name display name or identifier for this person
     * @param currentLocation initial building element where the person is located
     * @param maxSpeed maximum movement speed
     * @param behavior social behavior used during evacuation
     * @param densityTolerance tolerance to crowd density
     */
    public Person(String name, BuildingElement currentLocation,
            double maxSpeed, Behavior behavior, double densityTolerance) {
        super(name, currentLocation, maxSpeed, behavior, densityTolerance);
        this.setStrategy(null);
    }

    /**
     * Reacts to a building-wide alert.
     *
     * <p>
     * On a fire alert, the person may become panicked according to its behavior.
     * On a normal alert, the person returns to a calm state. In both cases, an
     * evacuation strategy is assigned so movement remains progressive.
     * </p>
     *
     * @param alert alert type, such as {@code "FIRE"} or {@code "NORMAL"}
     */
    @Override
    public void update(String alert) {
        if (alert.equals("FIRE")) {
            if (shouldPanic()) {
                setState(AgentState.PANICKED);
            } else {
                setState(AgentState.CALM);
            }

            setStrategy(new EvacuateStrategy());
        } else if (alert.equals("NORMAL")) {
            setState(AgentState.CALM);
            setStrategy(new EvacuateStrategy());
        }
    }

    /**
     * Decides whether this person becomes panicked during an alert.
     *
     * @return {@code true} if the person becomes panicked
     */
    private boolean shouldPanic() {
        double panicProbability;

        if (getBehavior() == Behavior.RUDE) {
            panicProbability = 0.70;
        } else if (getBehavior() == Behavior.FOLLOWER) {
            panicProbability = 0.45;
        } else {
            panicProbability = 0.25;
        }

        return Math.random() < panicProbability;
    }
}