package com.example.cysafecampus.model;

import java.io.*;

/**
 * Utility class to save and load the simulation state.
 * Fulfills the Import/Export evaluation criteria.
 */
public class SimulationSerializer {

    /**
     * Saves the current state of the graph (simulation) to a binary file.
     * @param graph The Graph object containing all elements and agents.
     * @param filePath The destination path (e.g., "simulation_save.bin").
     * @throws IOException If an error occurs during file writing.
     */
    public static void save(Graph graph, String filePath) throws IOException {
        // try-with-resources auto-closes the stream
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath))) {
            oos.writeObject(graph);
            System.out.println("Simulation saved to: " + filePath);
        }
    }

    /**
     * Loads a previously saved graph state from a binary file.
     * @param filePath The path of the file to load.
     * @return The restored Graph object.
     * @throws IOException If the file cannot be read.
     * @throws ClassNotFoundException If the file content doesn't match the Graph class.
     */
    public static Graph load(String filePath) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filePath))) {
            Graph graph = (Graph) ois.readObject();
            System.out.println("Simulation loaded from: " + filePath);
            return graph;
        }
    }
}
