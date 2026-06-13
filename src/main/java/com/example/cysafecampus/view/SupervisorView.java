package com.example.cysafecampus.view;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.StringJoiner;

import com.example.cysafecampus.controller.GraphController;
import com.example.cysafecampus.model.Agent;
import com.example.cysafecampus.model.AgentState;
import com.example.cysafecampus.model.BuildingElement;
import com.example.cysafecampus.model.Door;
import com.example.cysafecampus.model.Exit;
import com.example.cysafecampus.model.Graph;
import com.example.cysafecampus.model.Passage;
import com.example.cysafecampus.model.PassageType;
import com.example.cysafecampus.model.Room;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Supervisor view — mobile-style notification interface.
 *
 * Evacuation logic (matching the design spec): - Fire floor : rooms closest to
 * fire evacuate first (sequential, one at a time). - Other floors: rooms
 * closest to a staircase/exit evacuate first (sequential within the floor, but
 * floors run in parallel with the fire floor). - This room waits and displays a
 * countdown until its turn; when the previous room has finished, a "GO" message
 * appears automatically.
 */
public class SupervisorView {

    private Canvas graphCanvas;
    private Label graphHintLbl;
    private Label graphLegendLbl;

    private Label emergencyTitleLbl;
    private Label emergencyDetailLbl;

    /**
     * Simulated seconds each room needs to evacuate (adjust for demo).
     */
    private static final int SECONDS_PER_ROOM = 30;

    private final Stage stage;
    private final GraphController controller;
    private final Room assignedRoom;

    private Label occupancyLbl;
    private Label densityLbl;
    private Label orderBanner;
    private Label orderDetail;
    private Timeline refresh;

    /**
     * True once the fire alert has been received.
     */
    private boolean alertReceived = false;
    /**
     * True once this room's evacuation turn has started.
     */
    private boolean myTurnStarted = false;
    /**
     * Evacuation order of this room on its floor (1 = first).
     */
    private int myOrder = -1;
    /**
     * Total rooms to evacuate on this floor.
     */
    private int totalOnFloor = -1;
    /**
     * Simulated tick at which this room's turn begins.
     */
    private long myTurnStartTick = -1;

    /**
     * Creates the supervisor view for a room supervisor.
     *
     * @param stage main JavaFX stage used to display the view
     * @param controller shared graph controller containing the simulation model
     * @param assignedRoom room assigned to this supervisor
     */
    public SupervisorView(Stage stage, GraphController controller, Room assignedRoom) {
        this.stage = stage;
        this.controller = controller;
        this.assignedRoom = assignedRoom;
    }

    /**
     * Displays the supervisor interface and starts the periodic refresh loop.
     *
     * <p>
     * The view shows the assigned room occupancy, emergency status, evacuation
     * order, trapped people information and a recommended evacuation path.
     * </p>
     */
    public void show() {
        // ── Header ────────────────────────────────────────
        Label roomName = new Label(assignedRoom.getName());
        roomName.setFont(Font.font("Sans", FontWeight.BOLD, 22));
        roomName.setTextFill(Color.WHITE);

        Label roleTag = new Label("SUPERVISEUR");
        roleTag.setStyle("-fx-font-size:10px;-fx-text-fill:#a5d6a7;-fx-font-weight:bold;");

        Button backBtn = new Button("←");
        backBtn.setStyle("-fx-background-color:transparent;-fx-text-fill:white;"
                + "-fx-font-size:16px;-fx-cursor:hand;");
        backBtn.setOnAction(e -> goBack());

        VBox roomInfo = new VBox(4, roleTag, roomName);
        HBox header = new HBox(10, backBtn, roomInfo);
        header.setPadding(new Insets(20, 20, 20, 16));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color:#1b5e20;");

        emergencyTitleLbl = new Label("✅ Aucun incendie signalé");
        emergencyTitleLbl.setFont(Font.font("Sans", FontWeight.BOLD, 15));
        emergencyTitleLbl.setWrapText(true);
        emergencyTitleLbl.setStyle("-fx-text-fill:#166534;");

        emergencyDetailLbl = new Label("Surveillance active du bâtiment.");
        emergencyDetailLbl.setWrapText(true);
        emergencyDetailLbl.setStyle("-fx-font-size:12px;-fx-text-fill:#64748b;");

        VBox emergencyBox = new VBox(6, emergencyTitleLbl, emergencyDetailLbl);
        emergencyBox.setPadding(new Insets(16));
        emergencyBox.setAlignment(Pos.CENTER_LEFT);
        emergencyBox.setStyle(
                "-fx-background-color:#f0fdf4;"
                + "-fx-border-color:#bbf7d0;"
                + "-fx-border-radius:12;"
                + "-fx-background-radius:12;"
        );

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
        occCard.setStyle("-fx-background-color:white;-fx-border-color:#e0e0e0;"
                + "-fx-border-radius:12;-fx-background-radius:12;");

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
        orderBox.setStyle("-fx-background-color:#f8f9fa;-fx-border-color:#e8eaed;"
                + "-fx-border-radius:12;-fx-background-radius:12;");

        // ── Layout ────────────────────────────────────────
        VBox graphCard = buildGraphCard();

        VBox content = new VBox(14, emergencyBox, occCard, orderBox, graphCard);

        content.setPadding(new Insets(16));
        content.setBackground(new Background(new BackgroundFill(Color.WHITE,
                CornerRadii.EMPTY, Insets.EMPTY)));

        BorderPane root = new BorderPane();
        root.setTop(header);
        root.setCenter(content);
        root.setBackground(new Background(new BackgroundFill(Color.WHITE,
                CornerRadii.EMPTY, Insets.EMPTY)));

        Scene scene = new Scene(root, 700, 720);
        stage.setTitle("SafeCampus — Superviseur");
        stage.setScene(scene);
        stage.show();

        refresh = new Timeline(new KeyFrame(Duration.millis(400), e -> updateUI()));
        refresh.setCycleCount(Timeline.INDEFINITE);
        refresh.play();
        updateUI();
    }

    private VBox buildGraphCard() {
        Label title = new Label("Plan d’évacuation");
        title.setFont(Font.font("Sans", FontWeight.BOLD, 14));
        title.setTextFill(Color.web("#1b5e20"));

        graphCanvas = new Canvas(520, 260);

        graphHintLbl = new Label("Salle supervisée : " + assignedRoom.getName());
        graphHintLbl.setWrapText(true);
        graphHintLbl.setStyle("-fx-font-size:12px;-fx-text-fill:#334155;-fx-font-weight:bold;");

        graphLegendLbl = new Label("● Bleu : votre salle   ● Vert : sortie   ● Violet : passage   Ligne bleue : chemin conseillé");
        graphLegendLbl.setWrapText(true);
        graphLegendLbl.setStyle("-fx-font-size:11px;-fx-text-fill:#64748b;");

        VBox box = new VBox(8, title, graphCanvas, graphHintLbl, graphLegendLbl);
        box.setPadding(new Insets(14));
        box.setAlignment(Pos.CENTER_LEFT);
        box.setStyle(
                "-fx-background-color:white;"
                + "-fx-border-color:#e0e0e0;"
                + "-fx-border-radius:12;"
                + "-fx-background-radius:12;"
        );

        return box;
    }

    private List<BuildingElement> computeRecommendedPath() {
        Exit exit = findNearestExit();

        if (exit == null) {
            return List.of();
        }

        Map<BuildingElement, BuildingElement> previous = new HashMap<>();
        Queue<BuildingElement> queue = new LinkedList<>();
        Set<BuildingElement> visited = new HashSet<>();

        queue.add(assignedRoom);
        visited.add(assignedRoom);

        while (!queue.isEmpty()) {
            BuildingElement current = queue.poll();

            if (current == exit) {
                break;
            }

            for (BuildingElement neighbor : neighborsOf(current)) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    previous.put(neighbor, current);
                    queue.add(neighbor);
                }
            }
        }

        if (!visited.contains(exit)) {
            return List.of();
        }

        LinkedList<BuildingElement> path = new LinkedList<>();
        BuildingElement current = exit;

        while (current != null) {
            path.addFirst(current);
            current = previous.get(current);
        }

        return path;
    }

    private List<BuildingElement> neighborsOf(BuildingElement element) {
        List<BuildingElement> neighbors = new ArrayList<>();

        if (element == null || element.isBlocked()) {
            return neighbors;
        }

        if (element instanceof Room room) {
            for (Door door : room.getDoors()) {
                Passage passage = door.getPassage();

                if (passage != null && !passage.isBlocked()) {
                    neighbors.add(passage);
                }
            }
        }

        if (element instanceof Passage passage) {
            for (Door door : passage.getConnectedDoors()) {
                Room room = door.getRoom();

                if (room != null && !room.isBlocked()) {
                    neighbors.add(room);
                }
            }
        }

        return neighbors;
    }

    private void renderGraph() {
        if (graphCanvas == null) {
            return;
        }

        GraphicsContext gc = graphCanvas.getGraphicsContext2D();
        double w = graphCanvas.getWidth();
        double h = graphCanvas.getHeight();

        gc.clearRect(0, 0, w, h);

        List<BuildingElement> path = computeRecommendedPath();

        if (path.isEmpty()) {
            gc.setFill(Color.web("#64748b"));
            gc.setFont(Font.font("Sans", FontWeight.BOLD, 13));
            gc.fillText("Aucun chemin d’évacuation trouvé", 20, h / 2);

            if (graphHintLbl != null) {
                graphHintLbl.setText("Vous êtes ici : " + assignedRoom.getName()
                        + " → aucune sortie accessible");
            }
            return;
        }

        double startX = 45.0;
        double endX = w - 45.0;
        double centerY = h / 2.0;
        double step = path.size() <= 1 ? 0 : (endX - startX) / (path.size() - 1);

        // Draw route line.
        gc.setStroke(Color.web("#2563eb"));
        gc.setLineWidth(4.0);
        gc.strokeLine(startX, centerY, endX, centerY);

        for (int i = 0; i < path.size(); i++) {
            BuildingElement element = path.get(i);
            double x = startX + i * step;

            boolean first = i == 0;
            boolean last = i == path.size() - 1;
            boolean passage = element instanceof Passage;

            if (first) {
                gc.setFill(Color.web("#2563eb")); // current room
            } else if (last) {
                gc.setFill(Color.web("#16a34a")); // exit
            } else if (passage) {
                gc.setFill(Color.web("#7c3aed")); // passage
            } else {
                gc.setFill(Color.web("#60a5fa")); // intermediate room/junction
            }

            double radius = first || last ? 13.0 : 9.0;
            gc.fillOval(x - radius, centerY - radius, radius * 2, radius * 2);

            gc.setFill(Color.web("#111827"));
            gc.setFont(Font.font("Sans", FontWeight.BOLD, first || last ? 11 : 9));

            String label = shortLabel(element.getName(), first || last ? 18 : 12);
            double labelY = (i % 2 == 0) ? centerY - 26 : centerY + 38;

            gc.fillText(label, x - 28, labelY);
        }

        Exit nearestExit = findNearestExit();

        if (nearestExit != null && graphHintLbl != null) {
            graphHintLbl.setText(
                    "Vous êtes ici : " + assignedRoom.getName()
                    + " → Sortie recommandée : " + nearestExit.getName()
            );
        }
    }

    private String shortLabel(String value, int maxLength) {
        if (value == null) {
            return "";
        }

        if (value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    // ── Update ────────────────────────────────────────────
    private void updateUI() {
        updateEmergencyBanner();
        updateOccupancy();
        renderGraph();

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

    private void updateEmergencyBanner() {
        if (emergencyTitleLbl == null || emergencyDetailLbl == null) {
            return;
        }

        boolean emergencyActive = controller.getGraph().isEmergencyActive();
        int evacuated = 0;
        int trapped = 0;
        int inside = 0;

        for (Agent agent : controller.getGraph().getAgents()) {
            if (agent.isEvacuated()) {
                evacuated++;
            } else if (agent.getState() == AgentState.TRAPPED) {
                trapped++;
            } else {
                inside++;
            }
        }

        List<String> blockedZones = blockedZoneNames();

        if (!emergencyActive && blockedZones.isEmpty()) {
            emergencyTitleLbl.setText("✅ Aucun incendie signalé");
            emergencyTitleLbl.setStyle("-fx-text-fill:#166534;-fx-font-size:15px;-fx-font-weight:bold;");
            emergencyDetailLbl.setText("Surveillance active du bâtiment.");
            emergencyDetailLbl.setStyle("-fx-font-size:12px;-fx-text-fill:#64748b;");
            return;
        }

        emergencyTitleLbl.setText("🚨 ALERTE INCENDIE EN COURS");
        emergencyTitleLbl.setStyle("-fx-text-fill:#b91c1c;-fx-font-size:16px;-fx-font-weight:bold;");

        String zonesText = blockedZones.isEmpty()
                ? "Zone exacte non disponible"
                : String.join(", ", blockedZones);

        emergencyDetailLbl.setText(
                "Zones touchées : " + zonesText + "\n"
                + "Évacués : " + evacuated
                + "   |   Bloqués : " + trapped
                + "   |   À l'intérieur : " + inside + "\n"
                + trappedLocationsText()
        );
        emergencyDetailLbl.setStyle("-fx-font-size:12px;-fx-text-fill:#7f1d1d;");
    }

    private List<String> blockedZoneNames() {
        List<String> names = new ArrayList<>();

        for (BuildingElement element : controller.getGraph().getElements()) {
            if (element == null || element.getName() == null) {
                continue;
            }

            if (element.getName().contains("↔")) {
                continue;
            }

            if (element.isBlocked()) {
                names.add(element.getName());
            }
        }

        if (names.size() > 4) {
            return new ArrayList<>(names.subList(0, 4));
        }

        return names;
    }

    private String trappedLocationsText() {
        Map<String, Integer> trappedByLocation = new HashMap<>();

        for (Agent agent : controller.getGraph().getAgents()) {
            if (agent == null || agent.isEvacuated()) {
                continue;
            }

            if (agent.getState() != AgentState.TRAPPED) {
                continue;
            }

            BuildingElement location = agent.getCurrentLocation();

            String locationName = location != null && location.getName() != null
                    ? location.getName()
                    : "position inconnue";

            if (locationName.contains("↔")) {
                locationName = "liaison interne";
            }

            trappedByLocation.put(
                    locationName,
                    trappedByLocation.getOrDefault(locationName, 0) + 1
            );
        }

        if (trappedByLocation.isEmpty()) {
            return "Personnes bloquées : aucune";
        }

        StringJoiner joiner = new StringJoiner(", ");

        trappedByLocation.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> joiner.add(
                entry.getKey() + " (" + entry.getValue() + ")"
        ));

        return "Personnes bloquées : " + joiner;
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
     * Fire floor → sorted by distance to fire (ascending: closest first). Other
     * floors → sorted by distance to nearest staircase/exit (ascending).
     *
     * Evacuation is sequential within each floor: room with order 1 starts
     * immediately, room with order N starts after (N-1)*SECONDS_PER_ROOM
     * simulated seconds from the alert tick.
     */
    private void computeMyOrder() {
        Graph graph = controller.getGraph();

        // Identify fire location from the first panicked agent's room
        Room fireRoom = findFireRoom(graph);
        int fireFloor = fireRoom != null ? fireRoom.getFloor() : -1;

        // Collect all rooms on this floor
        List<Room> floorRooms = new ArrayList<>();
        for (BuildingElement el : graph.getElements()) {
            if (el instanceof Room r
                    && !(el instanceof Exit)
                    && !r.getName().contains("↔")
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
                    if (d < minDist) {
                        minDist = d;
                    }
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
                        + (long) (myOrder - 1) * SECONDS_PER_ROOM;
                break;
            }
        }
    }

    private void updateEvacStatus() {
        if (myOrder < 0) {
            return;
        }

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
                    "En cours d'évacuation : " + prevRoomsInfo + "\n"
                    + "Votre tour estimé : ~" + secsLeft + "s");
            orderDetail.setStyle("-fx-font-size:12px;-fx-text-fill:#424242;");
        }
    }

    private void activateGo() {
        Exit exit = findNearestExit();
        String exitName = exit != null ? exit.getName() : "sortie de votre étage";

        orderBanner.setText("✅  ÉVACUEZ MAINTENANT — ordre " + myOrder + "/" + totalOnFloor);
        orderBanner.setStyle("-fx-text-fill:#2e7d32;-fx-font-size:16px;-fx-font-weight:bold;");
        orderDetail.setText("Guidez les occupants vers : " + exitName
                + "\nVérifiez que la salle est vide avant de partir.");
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
            if (el instanceof Exit ex && !ex.isBlocked()) {
                int d = bfs.getOrDefault(ex, Integer.MAX_VALUE);
                if (d < bestDist) {
                    bestDist = d;
                    best = ex;
                }
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
            if (el instanceof Room r
                    && !(el instanceof Exit)
                    && !r.getName().contains("↔")
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
                        || el instanceof Exit) {
                    anchors.add(el);
                }
            }
            for (Room r : floorRooms) {
                int minDist = 999;
                for (BuildingElement anchor : anchors) {
                    int d = bfsFrom(anchor, graph).getOrDefault(r, 999);
                    if (d < minDist) {
                        minDist = d;
                    }
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
     *
     * @param source starting element
     * @param graph graph to traverse
     * @return map of element → hop distance from source
     */
    private Map<BuildingElement, Integer> bfsFrom(BuildingElement source, Graph graph) {
        Map<BuildingElement, Integer> dist = new HashMap<>();

        if (source == null) {
            return dist;
        }

        Queue<BuildingElement> queue = new LinkedList<>();
        dist.put(source, 0);
        queue.add(source);

        while (!queue.isEmpty()) {
            BuildingElement current = queue.poll();
            int distance = dist.get(current);

            for (BuildingElement neighbor : neighborsOf(current)) {
                if (!dist.containsKey(neighbor)) {
                    dist.put(neighbor, distance + 1);
                    queue.add(neighbor);
                }
            }
        }

        return dist;
    }

    private void goBack() {
        if (refresh != null) {
            refresh.stop();
        }
        new LoginView(stage, controller).show();
    }
}
