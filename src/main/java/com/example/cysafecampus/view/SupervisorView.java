package com.example.cysafecampus.view;

import com.example.cysafecampus.controller.GraphController;
import com.example.cysafecampus.model.*;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.*;

/**
 * Supervisor view — mobile-style notification interface.
 *
 * Evacuation logic (matching the design spec):
 *   - Fire floor  : rooms closest to fire evacuate first (sequential, one at a time).
 *   - Other floors: rooms closest to a staircase/exit evacuate first (sequential
 *                   within the floor, but floors run in parallel with the fire floor).
 *   - This room waits and displays a countdown until its turn; when the previous
 *     room has finished, a "GO" message appears automatically.
 */
public class SupervisorView {

    /** Simulated seconds each room needs to evacuate (adjust for demo). */
    private static final int SECONDS_PER_ROOM = 30;

    private final Stage stage;
    private final GraphController controller;
    private final Room assignedRoom;

    private Label occupancyLbl;
    private Label densityLbl;
    private Label orderBanner;
    private Label orderDetail;
    private Timeline refresh;

    /** True once the fire alert has been received. */
    private boolean alertReceived = false;
    /** True once this room's evacuation turn has started. */
    private boolean myTurnStarted = false;
    /** Evacuation order of this room on its floor (1 = first). */
    private int myOrder = -1;
    /** Total rooms to evacuate on this floor. */
    private int totalOnFloor = -1;
    /** Simulated tick at which this room's turn begins. */
    private long myTurnStartTick = -1;

    public SupervisorView(Stage stage, GraphController controller, Room assignedRoom) {
        this.stage = stage;
        this.controller = controller;
        this.assignedRoom = assignedRoom;
    }

    public void show() {
        // ── Header ────────────────────────────────────────
        Label roomName = new Label(assignedRoom.getName());
        roomName.setFont(Font.font("Sans", FontWeight.BOLD, 22));
        roomName.setTextFill(Color.WHITE);

        Label roleTag = new Label("SUPERVISEUR");
        roleTag.setStyle("-fx-font-size:10px;-fx-text-fill:#a5d6a7;-fx-font-weight:bold;");

        Button backBtn = new Button("←");
        backBtn.setStyle("-fx-background-color:transparent;-fx-text-fill:white;" +
            "-fx-font-size:16px;-fx-cursor:hand;");
        backBtn.setOnAction(e -> goBack());

        VBox roomInfo = new VBox(4, roleTag, roomName);
        HBox header = new HBox(10, backBtn, roomInfo);
        header.setPadding(new Insets(20, 20, 20, 16));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color:#1b5e20;");

        // ── Occupancy card ────────────────────────────────
        occupancyLbl = new Label("0 / " + assignedRoom.getMaxCapacity());
        occupancyLbl.setFont(Font.font("Sans", FontWeight.BOLD, 48));
        occupancyLbl.setTextFill(Color.web("#1b5e20"));

        Label occSubtitle = new Label("occupants dans la salle");
        occSubtitle.setStyle("-fx-font-size:12px;-fx-text-fill:#757575;");

        densityLbl = new Label("● Densité normale");
        densityLbl.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:#2e7d32;");

        VBox occCard = new VBox(6, occupancyLbl, occSubtitle, densityLbl);
        occCard.setPadding(new Insets(20));
        occCard.setAlignment(Pos.CENTER);
        occCard.setStyle("-fx-background-color:white;-fx-border-color:#e0e0e0;" +
            "-fx-border-radius:12;-fx-background-radius:12;");

        // ── Order banner ──────────────────────────────────
        orderBanner = new Label("En attente d'un ordre...");
        orderBanner.setFont(Font.font("Sans", FontWeight.BOLD, 15));
        orderBanner.setWrapText(true);
        orderBanner.setStyle("-fx-text-fill:#757575;");

        orderDetail = new Label("");
        orderDetail.setWrapText(true);
        orderDetail.setStyle("-fx-font-size:12px;-fx-text-fill:#9e9e9e;");

        Label orderIcon = new Label("📋");
        orderIcon.setFont(Font.font(28));

        VBox orderBox = new VBox(8, orderIcon, orderBanner, orderDetail);
        orderBox.setPadding(new Insets(20));
        orderBox.setAlignment(Pos.CENTER);
        orderBox.setStyle("-fx-background-color:#f8f9fa;-fx-border-color:#e8eaed;" +
            "-fx-border-radius:12;-fx-background-radius:12;");


        // ── Layout ────────────────────────────────────────
        VBox content = new VBox(14, occCard, orderBox);
        content.setPadding(new Insets(16));
        content.setBackground(new Background(new BackgroundFill(Color.WHITE,
            CornerRadii.EMPTY, Insets.EMPTY)));

        BorderPane root = new BorderPane();
        root.setTop(header);
        root.setCenter(content);
        root.setBackground(new Background(new BackgroundFill(Color.WHITE,
            CornerRadii.EMPTY, Insets.EMPTY)));

        Scene scene = new Scene(root, 360, 580);
        stage.setTitle("SafeCampus — Superviseur");
        stage.setScene(scene);
        stage.show();

        refresh = new Timeline(new KeyFrame(Duration.millis(400), e -> updateUI()));
        refresh.setCycleCount(Timeline.INDEFINITE);
        refresh.play();
        updateUI();
    }

    // ── Update ────────────────────────────────────────────

    private void updateUI() {
        updateOccupancy();

        if (!controller.getGraph().isEmergencyActive()) {
            return;
        }

        // First time we detect alert: compute evacuation plan
        if (!alertReceived) {
            alertReceived = true;
            computeMyOrder();
        }

        updateEvacStatus();
    }

    private void updateOccupancy() {
        int occ = assignedRoom.getCurrentOccupancy();
        int max = assignedRoom.getMaxCapacity();
        occupancyLbl.setText(occ + " / " + max);
        double ratio = max > 0 ? (double) occ / max : 0;
        if (ratio < 0.4) {
            occupancyLbl.setTextFill(Color.web("#1b5e20"));
            densityLbl.setText("● Densité normale");
            densityLbl.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:#2e7d32;");
        } else if (ratio < 0.8) {
            occupancyLbl.setTextFill(Color.web("#e65100"));
            densityLbl.setText("⚠ Densité élevée");
            densityLbl.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:#e65100;");
        } else {
            occupancyLbl.setTextFill(Color.web("#b71c1c"));
            densityLbl.setText("🚨 Zone saturée !");
            densityLbl.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:#b71c1c;");
        }
    }

    /**
     * Computes the evacuation order for this room using BFS distance.
     *
     * Fire floor  → sorted by distance to fire (ascending: closest first).
     * Other floors → sorted by distance to nearest staircase/exit (ascending).
     *
     * Evacuation is sequential within each floor: room with order 1 starts
     * immediately, room with order N starts after (N-1)*SECONDS_PER_ROOM simulated
     * seconds from the alert tick.
     */
    private void computeMyOrder() {
        Graph graph = controller.getGraph();

        // Identify fire location from the first panicked agent's room
        Room fireRoom = findFireRoom(graph);
        int fireFloor = fireRoom != null ? fireRoom.getFloor() : -1;

        // Collect all rooms on this floor
        List<Room> floorRooms = new ArrayList<>();
        for (BuildingElement el : graph.getElements()) {
            if (el instanceof Room r && !(el instanceof Exit)
                    && r.getFloor() == assignedRoom.getFloor()) {
                floorRooms.add(r);
            }
        }

        // Compute BFS distances
        Map<Room, Integer> distances = new HashMap<>();
        if (assignedRoom.getFloor() == fireFloor && fireRoom != null) {
            // Distance to fire
            Map<BuildingElement, Integer> bfs = bfsFrom(fireRoom, graph);
            for (Room r : floorRooms) {
                distances.put(r, bfs.getOrDefault(r, 999));
            }
        } else {
            // Distance to nearest staircase or exit on this floor
            List<BuildingElement> anchors = new ArrayList<>();
            for (BuildingElement el : graph.getElements()) {
                if ((el instanceof Passage p && p.getType() == PassageType.STAIRCASE)
                        || el instanceof Exit) {
                    anchors.add(el);
                }
            }
            for (Room r : floorRooms) {
                int minDist = 999;
                for (BuildingElement anchor : anchors) {
                    Map<BuildingElement, Integer> bfs = bfsFrom(anchor, graph);
                    int d = bfs.getOrDefault(r, 999);
                    if (d < minDist) minDist = d;
                }
                distances.put(r, minDist);
            }
        }

        // Sort ascending: room with smallest distance evacuates first
        floorRooms.sort(Comparator.comparingInt(r -> distances.getOrDefault(r, 999)));

        for (int i = 0; i < floorRooms.size(); i++) {
            if (floorRooms.get(i).equals(assignedRoom)) {
                myOrder = i + 1;
                totalOnFloor = floorRooms.size();
                // My turn starts after (myOrder-1) rooms have evacuated
                myTurnStartTick = controller.getTickCount()
                    + (long)(myOrder - 1) * SECONDS_PER_ROOM;
                break;
            }
        }
    }

    private void updateEvacStatus() {
        if (myOrder < 0) return;

        long now = controller.getTickCount();

        if (myOrder == 1 || now >= myTurnStartTick) {
            // Our turn
            if (!myTurnStarted) {
                myTurnStarted = true;
                activateGo();
            }
        } else {
            // Still waiting
            long ticksLeft = myTurnStartTick - now;
            long secsLeft = Math.max(0, ticksLeft / 2); // engine default ~500ms/tick ≈ 2 ticks/s
            String prevRoomsInfo = buildPrevRoomsText();
            orderBanner.setText("⏳  ATTENDEZ — ordre " + myOrder + "/" + totalOnFloor);
            orderBanner.setStyle("-fx-text-fill:#0277bd;-fx-font-size:15px;-fx-font-weight:bold;");
            orderDetail.setText(
                "En cours d'évacuation : " + prevRoomsInfo + "\n" +
                "Votre tour estimé : ~" + secsLeft + "s");
            orderDetail.setStyle("-fx-font-size:12px;-fx-text-fill:#424242;");
        }
    }

    private void activateGo() {
        Exit exit = findNearestExit();
        String exitName = exit != null ? exit.getName() : "sortie de votre étage";

        orderBanner.setText("✅  ÉVACUEZ MAINTENANT — ordre " + myOrder + "/" + totalOnFloor);
        orderBanner.setStyle("-fx-text-fill:#2e7d32;-fx-font-size:16px;-fx-font-weight:bold;");
        orderDetail.setText("Guidez les occupants vers : " + exitName +
            "\nVérifiez que la salle est vide avant de partir.");
        orderDetail.setStyle("-fx-font-size:12px;-fx-text-fill:#424242;");
    }

    // ── Helpers ───────────────────────────────────────────

    private Room findFireRoom(Graph graph) {
        for (Agent a : graph.getAgents()) {
            if (a.getState() == AgentState.PANICKED
                    && a.getCurrentLocation() instanceof Room r
                    && !(a.getCurrentLocation() instanceof Exit)) {
                return r;
            }
        }
        return null;
    }

    private Exit findNearestExit() {
        Map<BuildingElement, Integer> bfs = bfsFrom(assignedRoom, controller.getGraph());
        Exit best = null;
        int bestDist = Integer.MAX_VALUE;
        for (BuildingElement el : controller.getGraph().getElements()) {
            if (el instanceof Exit ex) {
                int d = bfs.getOrDefault(ex, Integer.MAX_VALUE);
                if (d < bestDist) { bestDist = d; best = ex; }
            }
        }
        return best;
    }

    private String buildPrevRoomsText() {
        Graph graph = controller.getGraph();
        Room fireRoom = findFireRoom(graph);
        int fireFloor = fireRoom != null ? fireRoom.getFloor() : -1;

        List<Room> floorRooms = new ArrayList<>();
        for (BuildingElement el : graph.getElements()) {
            if (el instanceof Room r && !(el instanceof Exit)
                    && r.getFloor() == assignedRoom.getFloor()) {
                floorRooms.add(r);
            }
        }

        Map<Room, Integer> distances = new HashMap<>();
        if (assignedRoom.getFloor() == fireFloor && fireRoom != null) {
            Map<BuildingElement, Integer> bfs = bfsFrom(fireRoom, graph);
            floorRooms.forEach(r -> distances.put(r, bfs.getOrDefault(r, 999)));
        } else {
            List<BuildingElement> anchors = new ArrayList<>();
            for (BuildingElement el : graph.getElements()) {
                if ((el instanceof Passage p && p.getType() == PassageType.STAIRCASE)
                        || el instanceof Exit) anchors.add(el);
            }
            for (Room r : floorRooms) {
                int minDist = 999;
                for (BuildingElement anchor : anchors) {
                    int d = bfsFrom(anchor, graph).getOrDefault(r, 999);
                    if (d < minDist) minDist = d;
                }
                distances.put(r, minDist);
            }
        }

        floorRooms.sort(Comparator.comparingInt(r -> distances.getOrDefault(r, 999)));

        StringJoiner sj = new StringJoiner(", ");
        for (int i = 0; i < myOrder - 1 && i < floorRooms.size(); i++) {
            sj.add(floorRooms.get(i).getName());
        }
        return sj.toString().isEmpty() ? "—" : sj.toString();
    }

    /**
     * BFS over the graph model (Room ↔ Passage via Door).
     * @param source starting element
     * @param graph  graph to traverse
     * @return map of element → hop distance from source
     */
    private Map<BuildingElement, Integer> bfsFrom(BuildingElement source, Graph graph) {
        Map<BuildingElement, Integer> dist = new HashMap<>();
        Queue<BuildingElement> queue = new LinkedList<>();
        dist.put(source, 0);
        queue.add(source);
        while (!queue.isEmpty()) {
            BuildingElement cur = queue.poll();
            int d = dist.get(cur);
            // Find neighbors
            if (cur instanceof Room room) {
                for (Door door : room.getDoors()) {
                    Passage p = door.getPassage();
                    if (!dist.containsKey(p)) { dist.put(p, d + 1); queue.add(p); }
                    // Also traverse junction room if passage connects to another room
                    for (Door pd : p.getConnectedDoors()) {
                        Room nb = pd.getRoom();
                        if (nb != null && !dist.containsKey(nb)) { dist.put(nb, d + 2); queue.add(nb); }
                    }
                }
            } else if (cur instanceof Passage passage) {
                for (Door door : passage.getConnectedDoors()) {
                    Room nb = door.getRoom();
                    if (nb != null && !dist.containsKey(nb)) { dist.put(nb, d + 1); queue.add(nb); }
                }
            }
        }
        return dist;
    }

    private void goBack() {
        if (refresh != null) refresh.stop();
        new LoginView(stage, controller).show();
    }
}
