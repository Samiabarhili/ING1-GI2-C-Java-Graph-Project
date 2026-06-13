package com.example.cysafecampus;

import java.util.List;

import com.example.cysafecampus.controller.GraphController;
import com.example.cysafecampus.model.Behavior;
import com.example.cysafecampus.model.BuildingElement;
import com.example.cysafecampus.model.Door;
import com.example.cysafecampus.model.Exit;
import com.example.cysafecampus.model.Graph;
import com.example.cysafecampus.model.Passage;
import com.example.cysafecampus.model.PassageType;
import com.example.cysafecampus.model.PathFinder;
import com.example.cysafecampus.model.Person;
import com.example.cysafecampus.model.Room;
import com.example.cysafecampus.model.SimulationSerializer;
import com.example.cysafecampus.view.LoginView;

import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Main entry point of CY SafeCampus.
 * Launches the login screen (role selection) or CLI mode.
 */
/**
 * Main application entry point for the CY SafeCampus project.
 *
 * <p>
 * This class extends javafx.application.Application and is responsible for
 * starting the JavaFX user interface or running a small command-line
 * demonstration of the core simulation components when invoked with the
 * "cli" argument.
 * </p>
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>start(Stage) — construct the model (Graph), create a GraphController,
 *       and show the LoginView.</li>
 *   <li>main(String[]) — parse command-line arguments and either run the CLI
 *       demo or launch the JavaFX application.</li>
 *   <li>runCLI() — private helper to build a small example graph, run a
 *       pathfinding demo, and test serialization.</li>
 * </ul>
 * </p>
 *
 * <p>Notes:
 * <ul>
 *   <li>start(Stage) is invoked on the JavaFX Application Thread by the runtime.</li>
 *   <li>runCLI() is intended for quick manual testing and examples; avoid
 *       hard-coded filenames in production.</li>
 * </ul>
 * </p>
 *
 * @see javafx.application.Application
 * @see com.example.cysafecampus.Graph
 * @see com.example.cysafecampus.GraphController
 * @see com.example.cysafecampus.views.LoginView
 * @see com.example.cysafecampus.simulation.PathFinder
 * @see com.example.cysafecampus.simulation.SimulationSerializer
 * @since 1.0
 */
 
/**
 * Initialize and display the JavaFX user interface.
 *
 * <p>
 * Constructs the domain model (Graph), creates a GraphController bound to that
 * model and opens the LoginView using the provided Stage.
 * </p>
 *
 * <p>This method is called by the JavaFX runtime on the JavaFX Application Thread.</p>
 *
 * @param stage the primary Stage provided by the JavaFX runtime; must not be null.
 */
 
/**
 * Application entry point.
 *
 * <p>
 * When invoked with a first command-line argument equal to "cli" (case-insensitive)
 * the method runs a headless demonstration via runCLI(). Otherwise it forwards
 * to Application.launch(...) to start the JavaFX lifecycle.
 * </p>
 *
 * @param args command-line arguments; may be null or empty
 */
 
/**
 * Run a small command-line demonstration of the simulation.
 *
 * <p>
 * Builds an example campus graph (rooms, passages, doors, an exit), creates a
 * test agent with a destination, prints the computed shortest path and attempts
 * to save/load the graph using the SimulationSerializer to verify persistence.
 * Output is printed to standard output and errors to standard error.
 * </p>
 *
 * <p>This helper is intended for debugging and manual testing only.</p>
 */
public class Main extends Application {

    @Override
    public void start(Stage stage) {
        Graph graph = new Graph();
        GraphController controller = new GraphController(graph);
        new LoginView(stage, controller).show();
    }

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("cli")) {
            runCLI();
        } else {
            launch(args);
        }
    }

    private static void runCLI() {
        System.out.println("--- CY SafeCampus CLI ---");
        Graph campus = new Graph();
        Room lectureHall = new Room("Amphi A", 200, 1);
        Room classroom   = new Room("Room 101", 30, 1);
        Passage corridor = new Passage("Corridor", 50, 1, 1.0, PassageType.CORRIDOR, 15.0);
        Exit exit = new Exit("Sortie", 100);

        Door d1 = new Door(lectureHall, corridor);
        Door d2 = new Door(classroom, corridor);
        lectureHall.addDoor(d1); classroom.addDoor(d2);
        corridor.addDoor(d1); corridor.addDoor(d2);

        campus.addElement(lectureHall); campus.addElement(classroom);
        campus.addElement(exit); campus.addPassage(corridor);

        Person p = new Person("Test", lectureHall, 1.0, Behavior.POLITE, 0.7);
        p.setDestination(exit);
        campus.addAgent(p);

        List<BuildingElement> path = PathFinder.calculateShortestPath(lectureHall, exit);
        System.out.print("Path: ");
        path.forEach(e -> System.out.print(e.getName() + " → "));
        System.out.println("END");

        try {
            SimulationSerializer.save(campus, "test_save.bin");
            Graph loaded = SimulationSerializer.load("test_save.bin");
            System.out.println("Save/Load OK — agents: " + loaded.getAgents().size());
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
