package com.example.cysafecampus;

import com.example.cysafecampus.controller.GraphController;
import com.example.cysafecampus.model.*;
import com.example.cysafecampus.view.LoginView;
import javafx.application.Application;
import javafx.stage.Stage;

import java.util.List;

/**
 * Main entry point of CY SafeCampus.
 * Launches the login screen (role selection) or CLI mode.
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
