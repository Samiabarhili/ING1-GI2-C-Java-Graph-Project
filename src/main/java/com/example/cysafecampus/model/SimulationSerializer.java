package com.example.cysafecampus.model;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Utility class to save and load the simulation state.
 * Fulfills the Import/Export evaluation criteria.
 */
/**
 * Utility class responsible for persisting and restoring a Graph instance using Java's
 * built-in object serialization mechanism.
 *
 * <p>This class provides two convenience operations:
 * <ul>
 *   <li>save(Graph, String) — serialize a Graph to a binary file</li>
 *   <li>load(String) — read a Graph instance back from a binary file</li>
 * </ul>
 *
 * <p>Important notes:
 * <ul>
 *   <li>The Graph instance and any objects it references must implement {@code Serializable}
 *       (directly or transitively) for serialization and deserialization to succeed.</li>
 *   <li>Deserializing data from untrusted sources is inherently unsafe and may lead to
 *       security vulnerabilities. Only load files from trusted locations.</li>
 *   <li>These methods use try-with-resources to ensure streams are closed, but they are not
 *       synchronized; callers should handle concurrent access to the same file or Graph instance.</li>
 * </ul>
 */
 
/**
 * Saves the provided Graph to the specified file path using Java object serialization.
 **/
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
