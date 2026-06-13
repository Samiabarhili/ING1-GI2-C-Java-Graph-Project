package com.example.cysafecampus.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the full evacuation plan built by AdminAgent.
 * Aggregates all EvacuationOrders, one per room to evacuate.
 * Calling execute() dispatches each order to the relevant SupervisorAgent.
 */
/**
 * Represents a serializable evacuation plan composed of multiple {@code EvacuationOrder}
 * instances.
 *
 * <p>The plan maintains an ordered, mutable list of evacuation orders. Orders can be
 * added to the plan via {@code addOrder(EvacuationOrder)}, inspected via
 * {@code getOrders()}, and executed via {@code execute()}. In the current
 * implementation {@code execute()} prints a short summary for each order to
 * standard output; in a production system this method would typically dispatch
 * orders to supervisors or other external systems.</p>
 *
 * <p>Note:
 * <ul>
 *   <li>The class is mutable and not thread-safe. Concurrent access should be
 *       synchronized externally if required.</li>
 *   <li>{@code getOrders()} exposes the live list of orders — modifications to
 *       the returned list affect the plan.</li>
 * </ul>
 * </p>
 *
 * @see EvacuationOrder
 * @see java.io.Serializable
 * @since 1.0
 */
public class EvacuationPlan implements Serializable {

    /** All individual evacuation orders composing this plan */
    private List<EvacuationOrder> orders;

    public EvacuationPlan() {
        this.orders = new ArrayList<>();
    }

    /**
     * Adds an order to the plan.
     * @param order the evacuation order to include
     */
    public void addOrder(EvacuationOrder order) {
        orders.add(order);
    }

    public List<EvacuationOrder> getOrders() { return orders; }

    /**
     * Executes all orders in the plan — prints a summary of each.
     * In a full implementation, this would dispatch orders to supervisors.
     */
    public void execute() {
        System.out.println("=== Executing evacuation plan (" + orders.size() + " orders) ===");
        for (EvacuationOrder order : orders) {
            System.out.println("  Evacuating: " + order);
        }
    }

    @Override
    public String toString() {
        return "EvacuationPlan[" + orders.size() + " orders]";
    }
}
