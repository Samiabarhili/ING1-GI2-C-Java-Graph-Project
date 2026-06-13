package com.example.cysafecampus.view;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.StringJoiner;

import com.example.cysafecampus.controller.GraphController;
import com.example.cysafecampus.model.Agent;
import com.example.cysafecampus.model.AgentState;
import com.example.cysafecampus.model.Behavior;
import com.example.cysafecampus.model.BlockStatus;
import com.example.cysafecampus.model.BuildingElement;
import com.example.cysafecampus.model.Door;
import com.example.cysafecampus.model.Exit;
import com.example.cysafecampus.model.Passage;
import com.example.cysafecampus.model.Room;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * AdminView — faithful JavaFX port of the safecampus_evacuation_system.html
 * prototype.
 *
 * Graph model (pure visual, independent of the building model): Node types :
 * ROOM (rectangle, coloured by floor), DOOR (small square, shows rate), HALL
 * (circle, shows capacity), STAIR (triangle, shows rate), EXIT (diamond,
 * green). Edges : plain grey lines — no label on the edge itself. Fire :
 * spreads node-by-node every 1.8 s, colour changes to red/orange.
 *
 * The visual graph is kept in {@code nodes} / {@code edges} lists and painted
 * on a JavaFX Canvas that refreshes at 60 fps via a Timeline.
 */
public class AdminView {

    private static final double FIRE_SPREAD_PROBABILITY = 0.25;

    // ── Visual node type ──────────────────────────────────
    private enum NType {
        ROOM, DOOR, HALL, STAIR, EXIT
    }

    /**
     * One visual node in the graph.
     */
    private static class VNode {

        String id, label;
        NType type;
        int floor;
        double x, y;
        int cap = 20;   // capacity (ROOM / HALL)
        double rate = 2.0;  // flow in pers/s (DOOR / STAIR)

        VNode(String id, NType type, int floor, double x, double y, String label) {
            this.id = id;
            this.type = type;
            this.floor = floor;
            this.x = x;
            this.y = y;
            this.label = label;
        }
    }

    /**
     * One directed edge between two nodes.
     */
    private static class VEdge {

        String id, from, to;

        VEdge(String id, String from, String to) {
            this.id = id;
            this.from = from;
            this.to = to;
        }
    }

    // ── Floor colour palette ──────────────────────────────
    private static final Color[][] FLOOR_FILL = {
        {Color.web("#dbeafe"), Color.web("#2563eb")}, // 0 RDC - blue
        {Color.web("#e0f2fe"), Color.web("#0284c7")}, // 1 1er - sky
        {Color.web("#ccfbf1"), Color.web("#0f766e")}, // 2 2e - teal
        {Color.web("#e0e7ff"), Color.web("#4f46e5")} // 3 3e - indigo
    };
    private static final String[] FLOOR_NAME = {"RDC", "1er", "2e", "3e"};

    // Model floor origins used by GraphController.initGraph().
    // AdminView keeps model coordinates unchanged and only projects them for display.
    private static final double[] MODEL_FLOOR_ORIGIN_X = {220, 460, 700, 940};

    private static final double SINGLE_FLOOR_CENTER_X = 360;
    private static final double ALL_FLOOR_LEFT = 210;
    private static final double ALL_FLOOR_GAP = 360;
    private static final double ALL_FLOOR_TOP_OFFSET = 35;

    // ── Canvas dimensions ─────────────────────────────────
    private static final double CW = 720, CH = 520;

    // ── State ─────────────────────────────────────────────
    private final Stage stage;
    private final GraphController controller;
    private Canvas canvas;
    private double zoom = 1.0;

    private static final double MIN_ZOOM = 0.5;
    private static final double MAX_ZOOM = 2.5;
    private static final double ZOOM_STEP = 0.1;

    private Label zoomLbl;
    private Timeline renderLoop;
    private Timeline fireTimer;

    private final List<VNode> nodes = new ArrayList<>();
    private final List<VEdge> edges = new ArrayList<>();

    private String fireNodeId = null;
    private final Set<String> fireSpread = new HashSet<>();
    private final Map<String, Integer> fireExposure = new HashMap<>();

    private String selectedId = null;
    private String selectedAgentId = null;
    /**
     * Current active tool: null, "addRoom", "addHall", "addStair", "addExit",
     * "addEdge", "fire", "delete".
     */
    private String tool = null;
    private String edgeStart = null;

    /**
     * Drag state
     */
    private String dragId = null;
    private double dragOx = 0, dragOy = 0;
    private boolean wasDragged = false;

    /**
     * Floor filter: -1 = all
     */
    private int currentFloor = -1;

    private int nodeCounter = 0;

    // UI labels
    private Label fireStatusLbl;
    private Label evacCountLbl;
    private VBox infoPanelBox;

    private VBox statsPanel;

    private Label statNameLbl;
    private Label statTypeLbl;
    private Label statFloorLbl;
    private Label statOccupationLbl;
    private Label statPassedLbl;
    private Label statSpeedLbl;
    private Label statLinksLbl;
    private Label statStatusLbl;

    private Button playPauseBtn;
    private TextArea logArea;
    private boolean useFastestPath = false;

    private static class VisualAgent {

        String agentId;
        String fromId;
        String toId;
        String previousId;
        double t;
        double x;
        double y;
    }

    private final Map<String, VisualAgent> visualAgents = new HashMap<>();
    private final Random visualAgentRandom = new Random();
    private long lastAgentFrameNs = 0L;

    private final Map<String, Button> toolBtns = new HashMap<>();
    private final Map<String, Button> floorBtns = new HashMap<>();

    /**
     * Creates the administrator view.
     *
     * @param stage main JavaFX stage used to display the view
     * @param controller shared graph controller containing the simulation model
     */
    public AdminView(Stage stage, GraphController controller) {
        this.stage = stage;
        this.controller = controller;
    }

    /**
     * Displays the administrator interface and starts the render loop.
     *
     * <p>
     * The view is rebuilt from the real graph model before being shown. Sensor
     * events are also connected to the administrator log so that detected
     * hazards remain visible from the interface.
     * </p>
     */
    public void show() {
        reconstructViewFromModel();

        BorderPane root = new BorderPane();
        root.setTop(buildTopBar());
        root.setCenter(buildCenter());

        Scene scene = new Scene(root, CW + 260, CH + 44);
        stage.setTitle("CY SafeCampus — Administration");
        stage.setScene(scene);
        stage.show();

        syncPlayPauseButton();

        // Connect sensor events to the administrator event log.        
        controller.setSensorEventCallback(event -> javafx.application.Platform.runLater(() -> {
            String icon = event.getSeverity() >= 4 ? "🔥"
                    : event.getSeverity() >= 3 ? "⚠"
                    : "ℹ";

            String location = event.getLocation() != null
                    ? event.getLocation().getName()
                    : "?";

            log(icon + " [" + event.getType() + "] " + location
                    + " — sévérité " + event.getSeverity());
        }
        ));
        restoreFireStateFromModel();

        startRenderLoop();
    }

    private void syncPlayPauseButton() {
        if (playPauseBtn == null) {
            return;
        }

        playPauseBtn.setText(controller.isRunning() ? "⏸ Pause" : "▶ Play");
    }

    // ── Initial graph ─────────────────────────────────────
    /**
     * Reconstructs the visual graph (nodes / edges) from the real model in
     * {@code controller.getGraph()}.
     *
     * <p>
     * This is now the single source of truth: every admin action modifies the
     * model through {@link GraphController}, then calls this method to rebuild
     * the visual representation. VNode / VEdge are pure drawing artifacts.</p>
     *
     * <p>
     * Mapping model → view:
     * <ul>
     * <li>Exit → {@link NType#EXIT}</li>
     * <li>Passage(HALL) → {@link NType#HALL}</li>
     * <li>Passage(STAIRCASE) → {@link NType#STAIR}</li>
     * <li>Passage(CORRIDOR) → {@link NType#HALL} (treated as a hall visually so
     * that {@link NType#DOOR} stays dedicated to real model {@link Door}
     * markers)</li>
     * <li>Room → {@link NType#ROOM}</li>
     * <li>Each real {@link Door} → a {@link NType#DOOR} VNode placed at the
     * midpoint of (room, passage). Edges become Room → Door → Passage.</li>
     * <li>Virtual "↔" junction rooms → hidden; their two passage endpoints get
     * a direct visual edge instead.</li>
     * </ul></p>
     *
     * <p>
     * Element names are used as stable visual identifiers, so VNode.id ==
     * VNode.label == BuildingElement.getName().</p>
     */
    private void reconstructViewFromModel() {
        nodes.clear();
        edges.clear();

        if (controller == null || controller.getGraph() == null) {
            nodeCounter = 0;
            selectedId = null;
            return;
        }

        List<BuildingElement> elements = controller.getGraph().getElements();

        // 1. Build visible BuildingElement nodes (skip virtual "↔" junctions). ─
        for (BuildingElement el : elements) {
            String name = el.getName();

            if (name.contains("↔")) {
                continue;
            }

            NType type = mapModelTypeToNType(el);
            int floor = modelFloor(el);

            VNode n = new VNode(name, type, floor, el.getX(), el.getY(), name);

            if (type == NType.ROOM || type == NType.HALL || type == NType.EXIT) {
                n.cap = el.getMaxCapacity();
            }
            if (type == NType.STAIR && el instanceof Passage p) {
                n.rate = p.getSpeedFactor() * 4.0; // rough visual flow indicator
            }

            nodes.add(n);
        }

        // 2. Build Door VNodes for every real model Door (Room/Exit ↔ Passage). ─
        //    The door sits at the midpoint of its (room, passage) segment and
        //    splits the visual edge in two: Room → Door → Passage. This keeps
        //    each visible door tied to a real model Door so future stats and
        //    capacities can be exposed directly from the model.
        //    Doors of virtual "↔" junctions are abstract internal links and
        //    are NOT shown — those are rendered as a single Passage-Passage
        //    edge in step 3.
        for (BuildingElement el : elements) {
            String elName = el.getName();
            if (elName.contains("↔")) {
                continue;
            }

            if (!(el instanceof Room room)) {
                continue; // Exit is also a Room, so this also covers Exit↔Passage
            }

            for (Door door : room.getDoors()) {
                Passage passage = door.getPassage();
                if (passage == null) {
                    continue;
                }

                String doorVId = doorVNodeId(room.getName(), passage.getName());

                // De-duplicate: a Door VNode already exists if the same Door was
                // referenced twice (room side + passage side).
                if (byId(doorVId) != null) {
                    // Edge endpoints may still be missing, ensure they exist.
                    addEIfMissing(room.getName(), doorVId);
                    addEIfMissing(doorVId, passage.getName());
                    continue;
                }

                double dx = (room.getX() + passage.getX()) / 2.0;
                double dy = (room.getY() + passage.getY()) / 2.0;
                int doorFloor = modelFloor(room);

                VNode doorNode = new VNode(
                        doorVId,
                        NType.DOOR,
                        doorFloor,
                        dx,
                        dy,
                        "Porte"
                );
                doorNode.rate = 2.0; // visual indicator only

                nodes.add(doorNode);

                addEIfMissing(room.getName(), doorVId);
                addEIfMissing(doorVId, passage.getName());
            }
        }

        // 3. Direct edges for Passage↔Passage connections (junction collapsed). ─
        for (BuildingElement el : elements) {
            if (!el.getName().contains("↔") || !(el instanceof Room junction)) {
                continue;
            }

            List<Passage> ends = new ArrayList<>();
            for (Door door : junction.getDoors()) {
                if (door.getPassage() != null) {
                    ends.add(door.getPassage());
                }
            }

            if (ends.size() == 2) {
                addEIfMissing(ends.get(0).getName(), ends.get(1).getName());
            }
        }

        // 4. Keep selection only if it still exists. ─────────────────────────
        if (selectedId != null && byId(selectedId) == null) {
            selectedId = null;
        }

        // 5. nodeCounter is only used to generate unique VEdge ids and fallback
        //    names; keep it ahead of the current node count to avoid clashes.
        nodeCounter = Math.max(nodeCounter, nodes.size() + edges.size());

        // 6. Re-anchor existing visual agents to the (possibly new) VNode
        //    coordinates without disturbing their progress along edges.
        resyncVisualAgentsFromModel();
    }

    /**
     * Stable visual id for a Door VNode that represents the real model Door
     * between {@code roomName} and {@code passageName}.
     *
     * <p>
     * The "door::" prefix is used by {@link #deleteNode(String)} to recognise
     * this VNode as a door (not a BuildingElement) and route deletion through
     * {@link GraphController#removeEdge(String, String)}.</p>
     */
    private String doorVNodeId(String roomName, String passageName) {
        return "door::" + roomName + "::" + passageName;
    }

    /**
     * Maps a model element to its visual node type. Note: {@link Exit} extends
     * {@link Room}, so Exit must be tested first. {@link NType#DOOR} is
     * reserved for the visual nodes that represent real model {@link Door}
     * objects (built separately in {@link #reconstructViewFromModel()}). A
     * {@link Passage} of type CORRIDOR is therefore mapped to
     * {@link NType#HALL} to avoid confusing the two.
     */
    private NType mapModelTypeToNType(BuildingElement el) {
        if (el instanceof Exit) {
            return NType.EXIT;
        }
        if (el instanceof Passage p) {
            return switch (p.getType()) {
                case HALL ->
                    NType.HALL;
                case STAIRCASE ->
                    NType.STAIR;
                case CORRIDOR ->
                    NType.HALL;
            };
        }
        return NType.ROOM;
    }

    /**
     * Reads the floor from the model element, defaulting to 0.
     */
    private int modelFloor(BuildingElement el) {
        return el == null ? 0 : Math.max(0, el.getFloor());
    }

    // ── Initial graph (LEGACY) ────────────────────────────
    /**
     * @deprecated Legacy hard-coded visual graph. No longer used: the visual
     * graph is now rebuilt from the real model via
     * {@link #reconstructViewFromModel()}. Kept temporarily for reference only;
     * do not call from {@link #show()}.
     */
    @Deprecated
    private void initGraph() {
        nodes.clear();
        edges.clear();
        fireNodeId = null;
        fireSpread.clear();
        selectedId = null;
        tool = null;
        edgeStart = null;
        nodeCounter = 0;

        // ── Ground floor ───────────────────────────────────────────
        addN("h0a", NType.HALL, 0, 280, 155, "Hall RDC", 50, 2);
        addN("r0a", NType.ROOM, 0, 100, 90, "Bureau 101", 20, 2);
        addN("r0b", NType.ROOM, 0, 100, 210, "Amphi 102", 80, 2);
        addN("r0c", NType.ROOM, 0, 460, 90, "LT 103", 30, 2);
        addN("d0a", NType.DOOR, 0, 190, 90, "Porte 1", 10, 2);
        addN("d0b", NType.DOOR, 0, 190, 210, "Porte 2", 10, 3);
        addN("d0c", NType.DOOR, 0, 370, 90, "Porte 3", 10, 2);
        addN("s0a", NType.STAIR, 0, 280, 290, "Escalier A", 20, 4);
        addN("px0", NType.DOOR, 0, 520, 155, "Porte Sortie RDC", 10, 5);
        addN("ex0", NType.EXIT, 0, 590, 155, "Sortie RDC", 50, 5);

        // ── First floor ─────────────────────────────────────
        addN("h1a", NType.HALL, 1, 280, 450, "Hall 1er", 40, 2);
        addN("r1a", NType.ROOM, 1, 100, 400, "Salle 201", 25, 2);
        addN("r1b", NType.ROOM, 1, 100, 500, "Salle 202", 25, 2);
        addN("r1c", NType.ROOM, 1, 460, 450, "Salle 203", 30, 2);
        addN("d1a", NType.DOOR, 1, 190, 400, "Porte 4", 10, 2);
        addN("d1b", NType.DOOR, 1, 190, 500, "Porte 5", 10, 2);
        addN("d1c", NType.DOOR, 1, 370, 450, "Porte 6", 10, 2);
        addN("s1a", NType.STAIR, 1, 440, 290, "Escalier B", 20, 4);
        addN("px1", NType.DOOR, 1, 520, 450, "Porte Sortie 1er", 10, 5);
        addN("ex1", NType.EXIT, 1, 590, 450, "Sortie 1er", 50, 5);
        // ── Ground floor edges ─────────────────────────────────────
        addE("r0a", "d0a");
        addE("d0a", "h0a");
        addE("r0b", "d0b");
        addE("d0b", "h0a");
        addE("h0a", "d0c");
        addE("d0c", "r0c");
        addE("h0a", "s0a");
        addE("h0a", "px0");
        addE("px0", "ex0");

        // ──  First floor edges─────────────────────────────────────
        addE("r1a", "d1a");
        addE("d1a", "h1a");
        addE("r1b", "d1b");
        addE("d1b", "h1a");
        addE("h1a", "d1c");
        addE("d1c", "r1c");
        addE("h1a", "s1a");
        addE("h1a", "px1");
        addE("px1", "ex1");

        // ── Cross-floor ───────────────────────────────────
        addE("s0a", "s1a");
    }

    private void addN(String id, NType type, int floor, double x, double y,
            String label, int cap, double rate) {
        VNode n = new VNode(id, type, floor, x, y, label);
        n.cap = cap;
        n.rate = rate;
        nodes.add(n);
    }

    private void addE(String from, String to) {
        edges.add(new VEdge("e" + (++nodeCounter), from, to));
    }

    private boolean edgeExists(String a, String b) {
        for (VEdge e : edges) {
            if ((e.from.equals(a) && e.to.equals(b))
                    || (e.from.equals(b) && e.to.equals(a))) {
                return true;
            }
        }
        return false;
    }

    private void addEIfMissing(String from, String to) {
        if (from == null || to == null || from.equals(to)) {
            return;
        }

        if (!edgeExists(from, to)) {
            addE(from, to);
        }
    }

    private VNode findDoorForExit(String exitId) {
        for (VEdge e : edges) {
            if (e.from.equals(exitId)) {
                VNode n = byId(e.to);
                if (n != null && n.type == NType.DOOR) {
                    return n;
                }
            }

            if (e.to.equals(exitId)) {
                VNode n = byId(e.from);
                if (n != null && n.type == NType.DOOR) {
                    return n;
                }
            }
        }

        return null;
    }

    private VNode createDoorForExit(VNode exit) {
        double doorX;

        //if exit is far enough from the left edge, place the door to the left. Otherwise, place it to the right to avoid going off the canvas.
        //else place it to the right of the exit.
        if (exit.x >= 80) {
            doorX = exit.x - 45;
        } else {
            doorX = exit.x + 45;
        }

        String did = newId("d");

        VNode door = new VNode(
                did,
                NType.DOOR,
                exit.floor,
                doorX,
                exit.y,
                "Porte " + exit.label
        );

        door.rate = 2;
        nodes.add(door);

// The exit is always connected to its access door.
        addEIfMissing(did, exit.id);

        return door;
    }

    private String newId(String prefix) {
        return prefix + (++nodeCounter);
    }

    /**
     * Generates a unique element name based on a French prefix, checking the
     * real model so the new node's name does not collide with an existing
     * element.
     *
     * @param prefix base label, e.g. "Salle", "Hall", "Escalier", "Sortie"
     * @return a unique name like "Salle 3"
     */
    private String uniqueName(String prefix) {
        int index = 1;
        String name = prefix + " " + index;
        while (controller != null && controller.hasElement(name)) {
            index++;
            name = prefix + " " + index;
        }
        return name;
    }

    /**
     * Automatically connects a newly created room-like element to the nearest
     * passage on the same floor when it is close enough.
     *
     * @param elementName name of the newly created room-like model element
     * @param x x-coordinate of the newly created element
     * @param y y-coordinate of the newly created element
     * @param floor floor of the newly created element
     */
    private void autoConnectRoomToNearestPassage(String elementName, double x, double y, int floor) {
        final double maxDistance = 180.0;

        Passage nearestPassage = null;
        double bestDistance = Double.MAX_VALUE;

        for (BuildingElement element : controller.getGraph().getElements()) {
            if (!(element instanceof Passage passage)) {
                continue;
            }

            if (passage.getFloor() != floor) {
                continue;
            }

            double dx = passage.getX() - x;
            double dy = passage.getY() - y;
            double distance = Math.sqrt(dx * dx + dy * dy);

            if (distance < bestDistance) {
                bestDistance = distance;
                nearestPassage = passage;
            }
        }

        if (nearestPassage == null || bestDistance > maxDistance) {
            log("⚠ Élément créé sans porte : aucun passage proche.");
            return;
        }

        boolean connected = controller.addConnection(elementName, nearestPassage.getName());

        if (connected) {
            log("🚪 Porte automatique créée avec « " + nearestPassage.getName() + " ».");
        } else {
            log("⚠ Porte automatique impossible avec « " + nearestPassage.getName() + " ».");
        }
    }

    // ── Top bar ───────────────────────────────────────────
    private HBox buildTopBar() {
        Button backBtn = sBtn("← Retour", "#546e7a");
        backBtn.setOnAction(e -> goBack());

        Label title = new Label("Administrateur");
        title.setFont(Font.font("Sans", FontWeight.BOLD, 13));
        title.setTextFill(Color.web("#1a237e"));

        Button zoomOutBtn = sBtn("−", "#1565c0");
        Button zoomResetBtn = sBtn("100%", "#1565c0");
        Button zoomInBtn = sBtn("+", "#1565c0");

        zoomLbl = new Label("100%");
        zoomLbl.setStyle("-fx-font-size:11px;-fx-text-fill:#334155;-fx-padding:0 6;");

        zoomOutBtn.setOnAction(e -> setZoom(zoom - ZOOM_STEP));
        zoomResetBtn.setOnAction(e -> setZoom(1.0));
        zoomInBtn.setOnAction(e -> setZoom(zoom + ZOOM_STEP));

        HBox zoomBox = new HBox(4, zoomOutBtn, zoomResetBtn, zoomInBtn, zoomLbl);
        zoomBox.setAlignment(Pos.CENTER);

        Button saveBtn = sBtn("💾 Save", "#1565c0");
        Button loadBtn = sBtn("📂 Load", "#1565c0");
        saveBtn.setOnAction(e -> handleSave());
        loadBtn.setOnAction(e -> handleLoad());

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        HBox bar = new HBox(10, backBtn, title, sp, zoomBox, saveBtn, loadBtn);
        bar.setPadding(new Insets(7, 12, 7, 12));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color:#f8f9fa;-fx-border-color:#e8eaed;-fx-border-width:0 0 1 0;");

        return bar;
    }

    // ── Center : canvas + sidebar ─────────────────────────
    private HBox buildCenter() {
        canvas = new Canvas(CW, CH);
        canvas.setOnMousePressed(this::onMousePressed);
        canvas.setOnMouseDragged(this::onMouseDragged);
        canvas.setOnMouseReleased(this::onMouseReleased);
        canvas.setOnMouseClicked(this::onMouseClicked);
        canvas.setOnScroll(e -> {
            if (e.isControlDown()) {
                if (e.getDeltaY() > 0) {
                    setZoom(zoom + ZOOM_STEP);
                } else {
                    setZoom(zoom - ZOOM_STEP);
                }
                e.consume();
            }
        });

        StackPane canvasWrap = new StackPane(canvas);
        canvasWrap.setBackground(new Background(new BackgroundFill(
                Color.web("#f0f4f8"), CornerRadii.EMPTY, Insets.EMPTY)));

// Let the graph container grow with the window.
        canvasWrap.setMinSize(CW, CH);
        canvasWrap.setPrefSize(CW, CH);
        canvasWrap.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        // Let the canvas take all available space
        canvas.widthProperty().bind(canvasWrap.widthProperty());
        canvas.heightProperty().bind(canvasWrap.heightProperty());

        VBox sidebar = buildSidebar();

        HBox center = new HBox(canvasWrap, sidebar);
        HBox.setHgrow(canvasWrap, Priority.ALWAYS);

        return center;
    }

    // ── Sidebar ───────────────────────────────────────────
    private VBox buildSidebar() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(12));
        panel.setBackground(new Background(new BackgroundFill(
                Color.web("#f8f9fa"), CornerRadii.EMPTY, Insets.EMPTY
        )));

        // ── Add ───────────────────────────────────────
        panel.getChildren().add(sSection("Ajouter"));

        GridPane addBtns = new GridPane();
        addBtns.setHgap(6);
        addBtns.setVgap(6);
        addBtns.setPadding(new Insets(0, 0, 8, 0));
        addBtns.setMaxWidth(Double.MAX_VALUE);

        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(50);
        c1.setHgrow(Priority.ALWAYS);

        ColumnConstraints c2 = new ColumnConstraints();
        c2.setPercentWidth(50);
        c2.setHgrow(Priority.ALWAYS);

        addBtns.getColumnConstraints().addAll(c1, c2);

        Button bRoom = toolBtn("btn-addRoom", "Ajouter salle", "addRoom");
        Button bHall = toolBtn("btn-addHall", "Ajouter hall", "addHall");
        Button bStair = toolBtn("btn-addStair", "Ajouter escalier", "addStair");
        Button bExit = toolBtn("btn-addExit", "Ajouter sortie", "addExit");
        Button bEdge = toolBtn("btn-addEdge", "Créer lien", "addEdge");
        Button bDel = toolBtn("btn-delete", "Supprimer", "delete");
        Button bRandomNodes = sBtn("Ajouter X nœuds", "#455a64");
        bRandomNodes.setMaxWidth(Double.MAX_VALUE);
        bRandomNodes.setOnAction(e -> handleRandomNodes());

        for (Button b : List.of(bRoom, bHall, bStair, bExit, bEdge, bDel)) {
            b.setMaxWidth(Double.MAX_VALUE);
            b.setMinHeight(30);
            b.setWrapText(true);
            GridPane.setHgrow(b, Priority.ALWAYS);
        }

        addBtns.add(bRoom, 0, 0);
        addBtns.add(bHall, 1, 0);
        addBtns.add(bStair, 0, 1);
        addBtns.add(bExit, 1, 1);
        addBtns.add(bEdge, 0, 2);
        addBtns.add(bDel, 1, 2);

        panel.getChildren().addAll(addBtns, bRandomNodes);

        // ── Simulation ─────────────────────
        panel.getChildren().add(new Separator());
        panel.getChildren().add(sSection("Simulation"));

        playPauseBtn = sBtn("▶ Play", "#2e7d32");
        Button stepBtn = sBtn("⏭ Step", "#546e7a");

        playPauseBtn.setMaxWidth(Double.MAX_VALUE);
        stepBtn.setMaxWidth(Double.MAX_VALUE);

        playPauseBtn.setOnAction(e -> {
            if (controller.isRunning()) {
                controller.pause();
                playPauseBtn.setText("▶ Play");
                log("Simulation en pause");
            } else {
                controller.play();
                playPauseBtn.setText("⏸ Pause");
                log("Simulation lancée");
            }
        });

        stepBtn.setOnAction(e -> {
            controller.step();
            syncAllVisualAgentsWithModel();
            log("Simulation avancée d'un pas");
        });

        HBox simBtns = new HBox(8, playPauseBtn, stepBtn);

        Label speedLbl = new Label("Vitesse :");
        speedLbl.setStyle("-fx-font-size:11px;-fx-text-fill:#64748b;");

        Slider speedSlider = new Slider(50, 2000, 500);
        speedSlider.setMaxWidth(Double.MAX_VALUE);
        speedSlider.valueProperty().addListener((o, ov, nv)
                -> controller.setSpeed((int) (2050 - nv.doubleValue()))
        );

        ToggleButton pathToggle = new ToggleButton("Chemin : plus court");
        pathToggle.setMaxWidth(Double.MAX_VALUE);
        pathToggle.setStyle(defaultBtnStyle());
        pathToggle.setOnAction(e -> {
            useFastestPath = pathToggle.isSelected();
            controller.setUseFastestPath(useFastestPath);
            pathToggle.setText(useFastestPath ? "Chemin : plus rapide" : "Chemin : plus court");
            log(useFastestPath ? "Calcul par chemin le plus rapide activé" : "Calcul par chemin le plus court activé");
        });

        panel.getChildren().addAll(simBtns, speedLbl, speedSlider, pathToggle);

        // ── Alerts ──────────────────────────────────────────
        panel.getChildren().add(new Separator());
        panel.getChildren().add(sSection("Alertes"));

        Button alarmBtn = sBtn("Déclencher alarme", "#c62828");
        Button fireBtn = sBtn("Placer feu sur graphe", "#e24b4a");
        Button resetBtn = sBtn("Réinitialiser feu", "#546e7a");

        alarmBtn.setMaxWidth(Double.MAX_VALUE);
        fireBtn.setMaxWidth(Double.MAX_VALUE);
        resetBtn.setMaxWidth(Double.MAX_VALUE);

        alarmBtn.setOnAction(e -> {
            controller.triggerFireAlert();
            fireStatusLbl.setText("ALERTE INCENDIE");
            fireStatusLbl.setStyle("-fx-font-size:11px;-fx-text-fill:#dc2626;-fx-font-weight:bold;");
            log("ALERTE INCENDIE déclenchée");
        });

        fireBtn.setOnAction(e -> {
            setTool("fire");
            showInfo("Cliquez sur un élément du graphe pour placer le feu");
            log("Mode placement du feu activé");
        });

        resetBtn.setOnAction(e -> {
            resetFire();
            log("Feu réinitialisé");
        });

        fireStatusLbl = new Label("Aucun feu détecté");
        fireStatusLbl.setStyle("-fx-font-size:11px;-fx-text-fill:#64748b;");

        evacCountLbl = new Label("Évacués : 0   |   Bloqués : 0   |   À l'intérieur : 0");
        evacCountLbl.setStyle("-fx-font-size:11px;-fx-text-fill:#334155;");
        evacCountLbl.setWrapText(true);

        panel.getChildren().addAll(alarmBtn, fireBtn, resetBtn, fireStatusLbl, evacCountLbl);

        // ── Agents  ─────────────────────────
        panel.getChildren().add(new Separator());
        panel.getChildren().add(sSection("Agents"));

        Button addAgentBtn = sBtn("Ajouter agent", "#1565c0");
        Button editAgentBtn = sBtn("Modifier agent", "#0277bd");
        Button rmAgentBtn = sBtn("Supprimer agent", "#546e7a");
        Button rndAgentsBtn = sBtn("Agents aléatoires", "#6a1b9a");

        for (Button b : List.of(addAgentBtn, editAgentBtn, rmAgentBtn, rndAgentsBtn)) {
            b.setMaxWidth(Double.MAX_VALUE);
        }

        addAgentBtn.setOnAction(e -> handleAddAgent());
        editAgentBtn.setOnAction(e -> handleEditAgent());
        rmAgentBtn.setOnAction(e -> handleRemoveAgent());
        rndAgentsBtn.setOnAction(e -> handleRandomAgents());

        panel.getChildren().addAll(addAgentBtn, editAgentBtn, rmAgentBtn, rndAgentsBtn);

        // ── Floor ────────────────────────────────────
        panel.getChildren().add(new Separator());
        panel.getChildren().add(sSection("Étage affiché"));

        Button fAll = floorBtn("fl-all", "Tous", -1);
        Button f0 = floorBtn("fl-0", "RDC", 0);
        Button f1 = floorBtn("fl-1", "1er", 1);
        Button f2 = floorBtn("fl-2", "2e", 2);
        Button f3 = floorBtn("fl-3", "3e", 3);

        HBox floorBtnRow = new HBox(4, fAll, f0, f1, f2, f3);
        panel.getChildren().add(floorBtnRow);
        fAll.fire();
        //f0.fire();

        // ── Selection / stats ─────────────────────────
        panel.getChildren().add(new Separator());
        panel.getChildren().add(sSection("Sélection"));

        statNameLbl = new Label("Aucun élément sélectionné");
        statNameLbl.setFont(Font.font("Sans", FontWeight.BOLD, 12));
        statNameLbl.setStyle("-fx-text-fill:#334155;");

        statTypeLbl = statLbl("Type : —");
        statFloorLbl = statLbl("Étage : —");
        statOccupationLbl = statLbl("Occupation : —");
        statPassedLbl = statLbl("Agents passés : —");
        statSpeedLbl = statLbl("Vitesse moy. : —");
        statLinksLbl = statLbl("Connexions : —");
        statStatusLbl = statLbl("Statut : —");

        statsPanel = new VBox(
                4,
                statNameLbl,
                statTypeLbl,
                statFloorLbl,
                statOccupationLbl,
                statPassedLbl,
                statSpeedLbl,
                statLinksLbl,
                statStatusLbl
        );

        statsPanel.setPadding(new Insets(8));
        statsPanel.setStyle(
                "-fx-background-color:white;"
                + "-fx-border-color:#e0e0e0;"
                + "-fx-border-radius:6;"
                + "-fx-background-radius:6;"
        );

        panel.getChildren().add(statsPanel);

        // ── Properties ───────────────────────────────────────
        panel.getChildren().add(new Separator());
        panel.getChildren().add(sSection("Propriétés"));

        infoPanelBox = new VBox(4);
        infoPanelBox.setPadding(new Insets(4, 0, 8, 0));

        Label hint = new Label("Cliquez sur un élément");
        hint.setStyle("-fx-font-size:11px;-fx-text-fill:#94a3b8;");
        infoPanelBox.getChildren().add(hint);

        panel.getChildren().add(infoPanelBox);

        // ── Log ───────────────────────────────────
        panel.getChildren().add(new Separator());
        panel.getChildren().add(sSection("Journal capteurs"));

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefRowCount(6);
        logArea.setWrapText(true);
        logArea.setStyle("-fx-font-size:10px;-fx-font-family:monospace;");

        panel.getChildren().add(logArea);

        // ── Legend ──────────────────────────────────────────
        panel.getChildren().add(new Separator());
        panel.getChildren().add(buildLegend());

        // ── ScrollPane  ───
        ScrollPane scroll = new ScrollPane(panel);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setPannable(true);
        scroll.setStyle("-fx-background:#f8f9fa;-fx-background-color:#f8f9fa;");
        scroll.setPrefWidth(260);

        VBox wrapper = new VBox(scroll);
        wrapper.setPrefWidth(260);
        wrapper.setMinWidth(260);
        wrapper.setStyle("-fx-background-color:#f8f9fa;-fx-border-color:#e8eaed;-fx-border-width:0 0 0 1;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        return wrapper;
    }

    private Label sSection(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size:10px;-fx-font-weight:bold;-fx-text-fill:#94a3b8;"
                + "-fx-padding:8 10 4 10;-fx-text-transform:uppercase;");
        return l;
    }

    private Label statLbl(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size:11px;-fx-text-fill:#64748b;");
        l.setWrapText(true);
        return l;
    }

    private Button toolBtn(String id, String label, String toolName) {
        Button b = new Button(label);
        b.setStyle(defaultBtnStyle());
        b.setOnAction(e -> setTool(toolName));
        toolBtns.put(id, b);
        return b;
    }

    private Button floorBtn(String id, String label, int floor) {
        Button b = new Button(label);
        b.setStyle(defaultBtnStyle());
        b.setOnAction(e -> setFloor(floor));
        floorBtns.put(id, b);
        return b;
    }

    private VBox buildLegend() {
        VBox box = new VBox(5);
        box.setPadding(new Insets(8, 10, 10, 10));
        box.setStyle("-fx-border-color:#e8eaed;-fx-border-width:1 0 0 0;");

        Label title = new Label("Légende");
        title.setStyle("-fx-font-size:10px;-fx-font-weight:bold;-fx-text-fill:#94a3b8;");
        box.getChildren().add(title);

        box.getChildren().addAll(
                legRow(roomIcon(0), "Salle RDC"),
                legRow(roomIcon(1), "Salle 1er étage"),
                legRow(roomIcon(2), "Salle 2e étage"),
                legRow(roomIcon(3), "Salle 3e étage"),
                legRow(doorIcon(), "Porte"),
                legRow(hallIcon(), "Hall"),
                legRow(stairIcon(), "Escalier"),
                legRow(exitIcon(), "Sortie"),
                legRow(fireIcon(), "Feu / propagation"),
                legRow(densityIcon("#fef3c7", "#ca8a04"), "Densité moyenne"),
                legRow(densityIcon("#fed7aa", "#ea580c"), "Densité forte"),
                legRow(densityIcon("#fecaca", "#dc2626"), "Sur-capacité")
        );

        Label note = new Label("Arêtes = liens simples sans étiquette");
        note.setStyle("-fx-font-size:9px;-fx-text-fill:#94a3b8;-fx-padding:4 0 0 0;");
        box.getChildren().add(note);

        return box;
    }

    private HBox legRow(javafx.scene.Node shape, String desc) {
        StackPane iconBox = new StackPane(shape);
        iconBox.setMinWidth(26);
        iconBox.setPrefWidth(26);
        iconBox.setAlignment(Pos.CENTER);

        Label d = new Label(desc);
        d.setStyle("-fx-font-size:10px;-fx-text-fill:#64748b;");

        HBox row = new HBox(8, iconBox, d);
        row.setAlignment(Pos.CENTER_LEFT);

        return row;
    }

    private Shape densityIcon(String fillColor, String strokeColor) {
        Rectangle r = new Rectangle(18, 11);
        r.setArcWidth(4);
        r.setArcHeight(4);
        r.setFill(Color.web(fillColor));
        r.setStroke(Color.web(strokeColor));
        r.setStrokeWidth(1.4);

        return r;
    }

    private Shape roomIcon(int floor) {
        int f = Math.min(floor, FLOOR_FILL.length - 1);

        Rectangle r = new Rectangle(18, 11);
        r.setArcWidth(4);
        r.setArcHeight(4);
        r.setFill(FLOOR_FILL[f][0]);
        r.setStroke(FLOOR_FILL[f][1]);
        r.setStrokeWidth(1.4);

        return r;
    }

    private Shape doorIcon() {
        Rectangle r = new Rectangle(13, 13);
        r.setFill(Color.web("#f8fafc"));
        r.setStroke(Color.web("#6b7280"));
        r.setStrokeWidth(1.4);

        return r;
    }

    private Shape hallIcon() {
        Circle c = new Circle(8);
        c.setFill(Color.web("#ede9fe"));
        c.setStroke(Color.web("#7c3aed"));
        c.setStrokeWidth(1.4);

        return c;
    }

    private Shape stairIcon() {
        Polygon p = new Polygon(
                9.0, 0.0,
                18.0, 16.0,
                0.0, 16.0
        );
        p.setFill(Color.web("#fef9c3"));
        p.setStroke(Color.web("#ca8a04"));
        p.setStrokeWidth(1.4);

        return p;
    }

    private Shape exitIcon() {
        Polygon p = new Polygon(
                10.0, 0.0,
                20.0, 10.0,
                10.0, 20.0,
                0.0, 10.0
        );
        p.setFill(Color.web("#dcfce7"));
        p.setStroke(Color.web("#16a34a"));
        p.setStrokeWidth(1.4);

        return p;
    }

    private Shape fireIcon() {
        Rectangle r = new Rectangle(14, 14);
        r.setFill(Color.web("#ff3333"));
        r.setStroke(Color.web("#aa0000"));
        r.setStrokeWidth(1.4);

        return r;
    }

    private void setZoom(double newZoom) {
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, newZoom));

        if (zoomLbl != null) {
            zoomLbl.setText(Math.round(zoom * 100) + "%");
        }
    }

    private double toGraphX(double screenX) {
        return screenX / zoom;
    }

    private double toGraphY(double screenY) {
        return screenY / zoom;
    }

    // ── Render loop ───────────────────────────────────────
    private void startRenderLoop() {
        renderLoop = new Timeline(new KeyFrame(Duration.millis(16), e -> render()));
        renderLoop.setCycleCount(Timeline.INDEFINITE);
        renderLoop.play();
    }

    // ── Main draw ─────────────────────────────────────────
    private void render() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        double graphW = w / zoom;
        double graphH = h / zoom;

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);

        gc.save();
        gc.scale(zoom, zoom);

        // Draw the grid background
        gc.setStroke(Color.web("#e2e8f0"));
        gc.setLineWidth(0.5);

        for (double x = 0; x < graphW; x += 40) {
            gc.strokeLine(x, 0, x, graphH);
        }

        for (double y = 0; y < graphH; y += 40) {
            gc.strokeLine(0, y, graphW, y);
        }

        List<VNode> visible = visibleNodes();
        Set<String> visIds = new HashSet<>();
        visible.forEach(n -> visIds.add(n.id));

        // Floor panels / title.
        drawFloorBackgrounds(gc, graphH);

        // Links
        gc.setLineWidth(1.5);

        for (VEdge e : edges) {
            VNode a = byId(e.from);
            VNode b = byId(e.to);

            if (a == null || b == null) {
                continue;
            }
            if (!visIds.contains(a.id) || !visIds.contains(b.id)) {
                continue;
            }

            if (a.floor != b.floor) {
                drawStairTransferMarker(gc, a);
                drawStairTransferMarker(gc, b);
                continue;
            }

            gc.setLineDashes(0);
            gc.setStroke(Color.web("#94a3b8"));
            gc.strokeLine(viewX(a), viewY(a), viewX(b), viewY(b));
        }
        drawSelectedAgentPath(gc, visIds);
        // Components
        for (VNode n : visible) {
            drawNode(gc, n);
        }

        // Visual agents
        drawVisualAgents(gc);

        gc.restore();
        updateSelectionStatsPanel();
        updateEvacuationCounts();
    }

    private void drawStairTransferMarker(GraphicsContext gc, VNode node) {
        if (node == null || node.type != NType.STAIR) {
            return;
        }

        double x = viewX(node);
        double y = viewY(node);

        gc.setFill(Color.web("#ffffffdd"));
        gc.fillOval(x - 12, y - 42, 24, 20);

        gc.setStroke(Color.web("#64748b"));
        gc.setLineWidth(1.2);
        gc.strokeOval(x - 12, y - 42, 24, 20);

        gc.setFill(Color.web("#334155"));
        gc.setFont(Font.font("Sans", FontWeight.BOLD, 12));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("↕", x, y - 27);

        gc.setTextAlign(TextAlignment.LEFT);
    }

    /**
     * Refreshes the evacuation tally shown in the side panel. Agents fall into
     * three mutually exclusive buckets: evacuated (reached an exit), trapped
     * (TRAPPED, no safe route, awaiting rescue) and still inside (everyone
     * else).
     */
    private void updateEvacuationCounts() {
        if (evacCountLbl == null) {
            return;
        }

        int evacuated = 0;
        int trapped = 0;
        int inside = 0;

        for (Agent a : controller.getGraph().getAgents()) {
            if (a.isEvacuated()) {
                evacuated++;
            } else if (a.getState() == AgentState.TRAPPED) {
                trapped++;
            } else {
                inside++;
            }
        }

        evacCountLbl.setText(
                "Évacués : " + evacuated
                + "   |   Bloqués : " + trapped
                + "   |   À l'intérieur : " + inside);
    }

    private String fitText(String value, Font font, double maxWidth) {
        if (value == null) {
            return "";
        }

        Text helper = new Text(value);
        helper.setFont(font);

        if (helper.getLayoutBounds().getWidth() <= maxWidth) {
            return value;
        }

        String ellipsis = "…";

        for (int len = value.length() - 1; len > 0; len--) {
            String candidate = value.substring(0, len) + ellipsis;
            helper.setText(candidate);

            if (helper.getLayoutBounds().getWidth() <= maxWidth) {
                return candidate;
            }
        }

        return "";
    }

    private String compactName(String label, String wordToRemove, String prefix) {
        if (label == null || label.isBlank()) {
            return prefix + ".";
        }

        String name = label.trim();

        name = name.replaceFirst("(?i)^" + wordToRemove + "\\s*", "").trim();

        if (name.isEmpty()) {
            return prefix + ".";
        }

        return prefix + "." + name;
    }

    private void drawSelectedAgentPath(GraphicsContext gc, Set<String> visibleNodeIds) {
        if (selectedAgentId == null) {
            return;
        }

        Agent selectedAgent = findAgentById(selectedAgentId);

        if (selectedAgent == null) {
            return;
        }

        List<BuildingElement> path = remainingPathFor(selectedAgent);
        if (path == null || path.isEmpty()) {
            return;
        }

        gc.setStroke(Color.web("#2563eb"));
        gc.setLineWidth(4.0);
        gc.setLineDashes(8, 5);

        VNode previous = null;

        for (BuildingElement element : path) {
            VNode current = visualNodeForModelLocation(element.getName());

            if (current == null) {
                continue;
            }

            if (!visibleNodeIds.contains(current.id)) {
                continue;
            }

            if (previous != null) {
                gc.strokeLine(viewX(previous), viewY(previous), viewX(current), viewY(current));
            }

            previous = current;
        }

        gc.setLineDashes(0);
    }

    private void drawVisualAgents(GraphicsContext gc) {
        updateVisualAgents();

        for (Agent a : controller.getGraph().getAgents()) {
            if (a.isEvacuated()) {
                continue;
            }

            VisualAgent va  = visualAgents.get(a.getId());
            if (va  == null) {
                continue;
            }

            VNode current = byId(va.fromId);
            int floor = current != null ? current.floor : 0;

            if (currentFloor != -1 && floor != currentFloor) {
                continue;
            }

            double ax = viewX(va.x, floor);
            double ay = viewY(va.y, floor);

            if (a.getId().equals(selectedAgentId)) {
                gc.setStroke(Color.web("#2563eb"));
                gc.setLineWidth(3.0);
                gc.strokeOval(ax - 13, ay - 18, 26, 34);
            }

            drawAgentIcon(gc, ax, ay, a);
        }
    }

    private boolean isVirtualJunction(BuildingElement element) {
        return element != null
                && element.getName() != null
                && element.getName().contains("↔");
    }

    private List<BuildingElement> withoutConsecutiveDuplicates(List<BuildingElement> path) {
        List<BuildingElement> cleaned = new ArrayList<>();

        String previousName = null;

        for (BuildingElement element : path) {
            if (element == null || element.getName() == null) {
                continue;
            }

            // Hide internal virtual junctions such as "A↔B".
            if (isVirtualJunction(element)) {
                continue;
            }

            String currentName = element.getName();

            if (previousName == null || !previousName.equalsIgnoreCase(currentName)) {
                cleaned.add(element);
            }

            previousName = currentName;
        }

        return cleaned;
    }

    private void updateVisualAgents() {
        Set<String> aliveIds = new HashSet<>();

        for (Agent agent : controller.getGraph().getAgents()) {
            if (agent.isEvacuated()) {
                continue;
            }

            aliveIds.add(agent.getId());
            visualAgents.computeIfAbsent(agent.getId(), id -> createVisualAgent(agent));
        }

        visualAgents.keySet().removeIf(id -> !aliveIds.contains(id));

        if (!controller.isRunning()) {
            return;
        }

        syncAllVisualAgentsWithModel();
    }

    private void syncAllVisualAgentsWithModel() {
        Set<String> aliveIds = new HashSet<>();

        for (Agent agent : controller.getGraph().getAgents()) {
            if (agent.isEvacuated()) {
                continue;
            }

            aliveIds.add(agent.getId());
            visualAgents.computeIfAbsent(agent.getId(), id -> createVisualAgent(agent));
            syncVisualAgentWithModel(agent);
        }

        visualAgents.keySet().removeIf(id -> !aliveIds.contains(id));
    }

    private void syncVisualAgentWithModel(Agent agent) {
        VisualAgent va  = visualAgents.get(agent.getId());

        if (va  == null) {
            return;
        }

        List<BuildingElement> path = agent.getPath();

        if (path == null || path.isEmpty()) {
            VNode loc = startNodeForAgent(agent);

            if (loc != null) {
                va.fromId = loc.id;
                va.toId = loc.id;
                va.t = 0.0;
                va.x = loc.x + offsetX(agent);
                va.y = loc.y + offsetY(agent);
            }

            return;
        }

        int index = Math.max(0, Math.min(agent.getPathIndex(), path.size() - 1));

        int fromIndex = previousVisiblePathIndex(path, index);
        int toIndex = nextVisiblePathIndex(path, index + 1);

        if (fromIndex < 0) {
            fromIndex = nextVisiblePathIndex(path, index);
        }

        if (fromIndex < 0) {
            return;
        }

        if (toIndex < 0) {
            toIndex = fromIndex;
        }

        VNode fromNode = visualNodeForModelLocation(path.get(fromIndex).getName());
        VNode toNode = visualNodeForModelLocation(path.get(toIndex).getName());

        if (fromNode == null) {
            return;
        }

        if (toNode == null) {
            toNode = fromNode;
        }

        double progress = Math.max(0.0, Math.min(1.0, agent.getProgress()));

        BuildingElement currentElement = path.get(index);
        BuildingElement nextElement = index + 1 < path.size() ? path.get(index + 1) : null;

        // The model path contains hidden virtual junctions A↔B.
        // Visually, we collapse A → A↔B → B into one segment A → B.
        if (isVirtualJunction(currentElement)) {
            progress = 0.5 + progress * 0.5;
        } else if (isVirtualJunction(nextElement)) {
            progress = progress * 0.5;
        }

        if (fromNode.floor != toNode.floor
                && fromNode.type == NType.STAIR
                && toNode.type == NType.STAIR) {

            VNode displayedStair = progress < 0.5 ? fromNode : toNode;

            va.fromId = displayedStair.id;
            va.toId = displayedStair.id;
            va.t = 0.0;
            va.previousId = null;

            va.x = displayedStair.x + offsetX(agent);
            va.y = displayedStair.y + offsetY(agent);
            return;
        }

        va.fromId = fromNode.id;
        va.toId = toNode.id;
        va.t = progress;
        va.previousId = null;

        va.x = fromNode.x + (toNode.x - fromNode.x) * progress + offsetX(agent);
        va.y = fromNode.y + (toNode.y - fromNode.y) * progress + offsetY(agent);
    }

    private int previousVisiblePathIndex(List<BuildingElement> path, int startIndex) {
        for (int i = Math.min(startIndex, path.size() - 1); i >= 0; i--) {
            BuildingElement element = path.get(i);

            if (element == null || isVirtualJunction(element)) {
                continue;
            }

            if (visualNodeForModelLocation(element.getName()) != null) {
                return i;
            }
        }

        return -1;
    }

    private int nextVisiblePathIndex(List<BuildingElement> path, int startIndex) {
        for (int i = Math.max(0, startIndex); i < path.size(); i++) {
            BuildingElement element = path.get(i);

            if (element == null || isVirtualJunction(element)) {
                continue;
            }

            if (visualNodeForModelLocation(element.getName()) != null) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Re-anchors existing visual agents to the current VNode coordinates while
     * preserving their in-flight progress along edges. Called from
     * {@link #reconstructViewFromModel()} (after add/remove/connect/drag/load).
     *
     * <p>
     * If an agent is stationary (toId == fromId), it is snapped to the fromId
     * node's current coordinates plus its personal offset. If an agent is
     * moving (toId != fromId and t in (0,1)), the position is recomputed by
     * interpolating between the now-possibly-moved from and to nodes at the
     * SAME progress value t, so the agent stays at the same proportional
     * position along the (now-moved) edge.</p>
     *
     * <p>
     * If either endpoint VNode no longer exists (e.g. its model element was
     * deleted), the agent is re-anchored to its current model location so it
     * does not vanish to (0,0).</p>
     */
    private void resyncVisualAgentsFromModel() {
        for (Agent a : controller.getGraph().getAgents()) {
            if (a.isEvacuated()) {
                continue;
            }

            VisualAgent va  = visualAgents.get(a.getId());
            if (va  == null) {
                continue; // it will be created lazily on next frame
            }
            VNode from = byId(va.fromId);
            VNode to = byId(va.toId);

            if (from == null && to == null) {
                // Both endpoints are gone — fall back to current model location.
                VNode loc = startNodeForAgent(a);
                if (loc != null) {
                    va.fromId = loc.id;
                    va.toId = loc.id;
                    va.previousId = null;
                    va.t = 0.0;
                    va.x = loc.x + offsetX(a);
                    va.y = loc.y + offsetY(a);
                }
                continue;
            }

            if (from == null) {
                from = to;
            } else if (to == null) {
                to = from;
            }

            va.x = from.x + (to.x - from.x) * va.t + offsetX(a);
            va.y = from.y + (to.y - from.y) * va.t + offsetY(a);
        }
    }

    private VisualAgent createVisualAgent(Agent a) {
        VNode start = startNodeForAgent(a);

        VisualAgent va  = new VisualAgent();
        va.agentId = a.getId();

        if (start == null) {
            va.x = 60;
            va.y = 60;
            return va;
        }

        va.fromId = start.id;
        va.toId = chooseNextNode(start.id, null, false);
        va.previousId = null;
        va.t = 0.0;
        va.x = start.x + offsetX(a);
        va.y = start.y + offsetY(a);

        return va;
    }

    private void advanceVisualAgent(VisualAgent va, Agent a, double dt) {
        VNode from = byId(va.fromId);
        VNode to = byId(va.toId);

        if (from == null) {
            VNode restart = startNodeForAgent(a);

            if (restart == null) {
                return;
            }

            va.fromId = restart.id;
            va.toId = chooseNextNode(restart.id, null, false);
            va.t = 0.0;
            va.x = restart.x;
            va.y = restart.y;
            return;
        }

        if (to == null || from.id.equals(to.id)) {
            va.x = from.x + offsetX(a);
            va.y = from.y + offsetY(a);
            va.toId = chooseNextNode(from.id, va.previousId, isEvacuationMode(a));
            va.t = 0.0;
            return;
        }

        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dist = Math.max(1.0, Math.hypot(dx, dy));

        // Visual speed in pixels/second
        double visualSpeed = Math.max(0.4, a.getMaxSpeed()) * 45.0;

        va.t += (visualSpeed * dt) / dist;

        if (va.t >= 1.0) {
            va.previousId = va.fromId;
            va.fromId = va.toId;

            VNode arrived = byId(va.fromId);

            if (arrived != null && isEvacuationMode(a) && arrived.type == NType.EXIT) {
                va.toId = va.fromId;
                va.t = 0.0;
                va.x = arrived.x + offsetX(a);
                va.y = arrived.y + offsetY(a);
                return;
            }

            va.toId = chooseNextNode(va.fromId, va.previousId, isEvacuationMode(a));
            va.t = 0.0;

            from = byId(va.fromId);
            to = byId(va.toId);
        }

        if (from != null && to != null) {
            va.x = from.x + (to.x - from.x) * va.t + offsetX(a);
            va.y = from.y + (to.y - from.y) * va.t + offsetY(a);
        }
    }

    private boolean isEvacuationMode(Agent a) {
        return fireNodeId != null
                || !fireSpread.isEmpty()
                || a.getState() == AgentState.PANICKED
                || a.getPath() != null && !a.getPath().isEmpty();
    }

    private VNode startNodeForAgent(Agent a) {
        BuildingElement loc = a.getCurrentLocation();

        if (loc != null) {
            VNode exact = visualNodeForModelLocation(loc.getName());

            if (exact != null) {
                return exact;
            }
        }

        List<VNode> candidates = new ArrayList<>();

        for (VNode n : nodes) {
            if (n.type == NType.ROOM || n.type == NType.HALL) {
                candidates.add(n);
            }
        }

        if (candidates.isEmpty()) {
            candidates.addAll(nodes);
        }

        if (candidates.isEmpty()) {
            return null;
        }

        int index = Math.abs(a.getId().hashCode()) % candidates.size();

        return candidates.get(index);
    }

    private VNode visualNodeForModelLocation(String modelName) {
        if (modelName == null) {
            return null;
        }

        if (modelName.contains("↔")) {
            return null;
        }

        String m = normalize(modelName);

        for (VNode n : nodes) {
            if (normalize(n.label).equals(m)) {
                return n;
            }
        }

        // Small matches between the model and your visual graph
        if (m.contains("bureau")) {
            return firstNodeOfType(NType.ROOM, 0);
        }

        if (m.contains("salle")) {
            return firstNodeOfType(NType.ROOM, 1);
        }

        if (m.contains("amphi")) {
            return findNodeLabelContains("Amphi");
        }

        if (m.contains("lt")) {
            return findNodeLabelContains("LT");
        }

        if (m.contains("jonction") || m.contains("hall") || m.contains("palier")) {
            return firstNodeOfType(NType.HALL, -1);
        }

        if (m.contains("esc")) {
            return firstNodeOfType(NType.STAIR, -1);
        }

        if (m.contains("sortie")) {
            return firstNodeOfType(NType.EXIT, -1);
        }

        return null;
    }

    private String normalize(String s) {
        return s.toLowerCase()
                .replace("é", "e")
                .replace("è", "e")
                .replace("ê", "e")
                .replace("à", "a")
                .replace("â", "a")
                .replace("î", "i")
                .replace("ï", "i")
                .replace("ô", "o")
                .replace("ù", "u")
                .replace("ç", "c")
                .trim();
    }

    private VNode firstNodeOfType(NType type, int floor) {
        for (VNode n : nodes) {
            if (n.type == type && (floor < 0 || n.floor == floor)) {
                return n;
            }
        }

        return null;
    }

    private VNode findNodeLabelContains(String text) {
        String needle = normalize(text);

        for (VNode n : nodes) {
            if (normalize(n.label).contains(needle)) {
                return n;
            }
        }

        return null;
    }

    private String chooseNextNode(String currentId, String previousId, boolean evacuationMode) {
        List<String> neighbors = neighborIds(currentId);

        if (neighbors.isEmpty()) {
            return currentId;
        }

        if (evacuationMode) {
            String best = neighbors.get(0);
            double bestDist = Double.MAX_VALUE;

            for (String id : neighbors) {
                VNode n = byId(id);

                if (n == null) {
                    continue;
                }

                double d = distanceToNearestExit(n);

                if (d < bestDist) {
                    bestDist = d;
                    best = id;
                }
            }

            return best;
        }

        List<String> choices = new ArrayList<>();

        for (String id : neighbors) {
            if (previousId == null || !id.equals(previousId)) {
                choices.add(id);
            }
        }

        if (choices.isEmpty()) {
            choices.addAll(neighbors);
        }

        return choices.get(visualAgentRandom.nextInt(choices.size()));
    }

    private List<String> neighborIds(String nodeId) {
        List<String> out = new ArrayList<>();

        for (VEdge e : edges) {
            if (e.from.equals(nodeId)) {
                out.add(e.to);
            } else if (e.to.equals(nodeId)) {
                out.add(e.from);
            }
        }

        return out;
    }

    private double distanceToNearestExit(VNode n) {
        double best = Double.MAX_VALUE;

        for (VNode exit : nodes) {
            if (exit.type == NType.EXIT && exit.floor == n.floor) {
                best = Math.min(best, Math.hypot(exit.x - n.x, exit.y - n.y));
            }
        }

        if (best < Double.MAX_VALUE) {
            return best;
        }

        for (VNode exit : nodes) {
            if (exit.type == NType.EXIT) {
                best = Math.min(best, Math.hypot(exit.x - n.x, exit.y - n.y));
            }
        }

        return best;
    }

    private double offsetX(Agent a) {
        int h = Math.abs(a.getId().hashCode());
        return (h % 15) - 7;
    }

    private double offsetY(Agent a) {
        int h = Math.abs(a.getId().hashCode() / 17);
        return (h % 15) - 7;
    }

    private void drawAgentIcon(GraphicsContext gc, double x, double y, Agent a) {
        boolean trapped = a.getState() == AgentState.TRAPPED;

        Color c;
        if (trapped) {
            c = Color.web("#b45309");
        } else if (a.getState() == AgentState.PANICKED) {
            c = Color.web("#ef4444");
        } else {
            c = Color.web("#2563eb");
        }

        // Halo
        gc.setFill(Color.web("#ffffffcc"));
        gc.fillOval(x - 8, y - 16, 16, 24);

        // Head
        gc.setFill(c);
        gc.fillOval(x - 4, y - 14, 8, 8);

        // Body
        gc.setStroke(c);
        gc.setLineWidth(2);
        gc.strokeLine(x, y - 6, x, y + 5);

        // Arms
        gc.strokeLine(x - 5, y - 1, x + 5, y - 1);

        // Legs
        gc.strokeLine(x, y + 5, x - 4, y + 12);
        gc.strokeLine(x, y + 5, x + 4, y + 12);

        // SOS badge for trapped agents (red disc with "!").
        if (trapped) {
            gc.setFill(Color.web("#dc2626"));
            gc.fillOval(x + 4, y - 24, 12, 12);
            gc.setFill(Color.web("#ffffff"));
            gc.setFont(Font.font("Sans", FontWeight.BOLD, 10));
            gc.fillText("!", x + 8, y - 15);
        }

        // Short name above
        gc.setFill(Color.web("#0f172a"));
        gc.setFont(Font.font("Sans", FontWeight.BOLD, 8));

        String name = a.getName();

        if (name.length() > 8) {
            name = name.substring(0, 7) + "…";
        }

        gc.fillText(name, x, y - 20);
    }

    private void drawNode(GraphicsContext gc, VNode n) {
        double x = viewX(n);
        double y = viewY(n);

        boolean onFire = n.id.equals(fireNodeId);
        boolean spread = fireSpread.contains(n.id);
        boolean sel = n.id.equals(selectedId);

        Color fill, stroke;
        if (onFire) {
            fill = Color.web("#ff3333");
            stroke = Color.web("#aa0000");
        } else if (spread) {
            fill = Color.web("#ff8800");
            stroke = Color.web("#cc5500");
        } else {
            int f = Math.min(n.floor, FLOOR_FILL.length - 1);
            fill = switch (n.type) {
                case DOOR ->
                    Color.web("#f8fafc");
                case HALL ->
                    Color.web("#ede9fe");
                case STAIR ->
                    Color.web("#fef9c3");
                case EXIT ->
                    Color.web("#dcfce7");
                default ->
                    FLOOR_FILL[f][0];
            };
            stroke = switch (n.type) {
                case DOOR ->
                    Color.web("#6b7280");
                case HALL ->
                    Color.web("#7c3aed");
                case STAIR ->
                    Color.web("#ca8a04");
                case EXIT ->
                    Color.web("#16a34a");
                default ->
                    FLOOR_FILL[f][1];
            };
        }

        if (!onFire && !spread) {
            double densityRatio = densityRatioFor(n);
            fill = densityFillColor(densityRatio, fill);
            stroke = densityStrokeColor(densityRatio, stroke);
        }

        gc.setFill(fill);
        gc.setStroke(stroke);
        gc.setLineWidth(sel ? 2.5 : 1.8);

        switch (n.type) {
            case ROOM -> {
                gc.fillRoundRect(x - 34, y - 20, 68, 40, 6, 6);
                gc.strokeRoundRect(x - 34, y - 20, 68, 40, 6, 6);
            }
            case HALL -> {
                gc.fillOval(x - 24, y - 24, 48, 48);
                gc.strokeOval(x - 24, y - 24, 48, 48);
            }
            case STAIR -> {
                double[] xs = {x, x + 22, x - 22};
                double[] ys = {y - 22, y + 16, y + 16};
                gc.fillPolygon(xs, ys, 3);
                gc.strokePolygon(xs, ys, 3);
            }
            case DOOR -> {
                gc.fillRect(x - 9, y - 9, 18, 18);
                gc.strokeRect(x - 9, y - 9, 18, 18);
            }
            case EXIT -> {
                double[] xs = {x, x + 20, x, x - 20};
                double[] ys = {y - 20, y, y + 20, y};
                gc.fillPolygon(xs, ys, 4);
                gc.strokePolygon(xs, ys, 4);
            }
        }

        // selection ring
        if (sel) {
            gc.setStroke(Color.web("#3b82f6"));
            gc.setLineWidth(2);
            gc.setLineDashes(4, 2);
            gc.strokeOval(x - 32, y - 32, 64, 64);
            gc.setLineDashes(0);
        }

        // Labels
        Color txtColor = (onFire || spread) ? Color.WHITE
                : n.type == NType.HALL ? Color.web("#3b0764")
                        : n.type == NType.STAIR ? Color.web("#78350f")
                                : n.type == NType.EXIT ? Color.web("#14532d")
                                        : FLOOR_FILL[Math.min(n.floor, FLOOR_FILL.length - 1)][1];

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);

        if (n.type == NType.DOOR) {
            // Centered flow rate in the door
            gc.setFill(txtColor);
            gc.setFont(Font.font("Sans", FontWeight.BOLD, 9));
            String rateStr = String.format("%.0f", n.rate);
            gc.fillText(rateStr, x, y);

            gc.setFill(Color.web("#94a3b8"));
            gc.setFont(Font.font("Sans", 8));
            gc.fillText("p/s", x, y + 17);

        } else {
            String shortName = n.label.length() > 13
                    ? n.label.substring(0, 12) + "…"
                    : n.label;

            gc.setFont(Font.font("Sans", FontWeight.BOLD, 9));
            gc.setFill(txtColor);

            if (n.type == NType.STAIR) {
                Font stairFont = Font.font("Sans", FontWeight.BOLD, 8);
                String stairName = compactName(n.label, "Escalier", "E");
                stairName = fitText(stairName, stairFont, 32);

                gc.setFont(stairFont);
                gc.setFill(txtColor);
                gc.fillText(stairName, x, y + 8);

                // Flow rate below the triangle
                gc.setFont(Font.font("Sans", 8));
                gc.setFill(Color.web("#78350f"));
                String rs = String.format("%.0f p/s", n.rate);
                gc.fillText(rs, x, y + 30);

            } else if (n.type == NType.HALL) {
                // Name well centered in the hall
                gc.fillText(shortName, x, y - 5);

                // Capacity below the name, without overlap
                gc.setFont(Font.font("Sans", 8));
                gc.setFill(Color.web("#6b7280"));
                gc.fillText("cap: " + n.cap, x, y + 10);

            } else if (n.type == NType.EXIT) {
                Font exitFont = Font.font("Sans", FontWeight.BOLD, 8);
                String exitName = compactName(n.label, "Sortie", "S");
                exitName = fitText(exitName, exitFont, 30);

                gc.setFont(exitFont);
                gc.setFill(txtColor);
                gc.fillText(exitName, x, y);

            } else if (n.type == NType.ROOM) {
                // Short name centered in the room
                gc.fillText(shortName, x, y);

                // Capacity above to the right
                gc.setFont(Font.font("Sans", 8));
                gc.setFill(Color.web("#6b7280"));
                gc.fillText("cap: " + n.cap, x + 34, y - 25);
            }
        }

        // Reset text to default to avoid breaking other drawings
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.BASELINE);

        // 🔥 fire emoji marker
        if (onFire) {
            gc.setFont(Font.font("Sans", 14));
            gc.setFill(Color.BLACK);
            gc.fillText("🔥", x + 30, y - 20);
        }
    }

    // ── Mouse handlers ────────────────────────────────────
    private void onMousePressed(javafx.scene.input.MouseEvent e) {
        if (!e.isPrimaryButtonDown()) {
            return;
        }

        double mx = toGraphX(e.getX());
        double my = toGraphY(e.getY());

        wasDragged = false;

        VNode hit = hitNode(mx, my);

        if (hit != null && tool == null) {
            if (hit.id != null && hit.id.startsWith("door::")) {
                return;
            }

            dragId = hit.id;
            dragOx = mx;
            dragOy = my;
        }
    }

    private void onMouseDragged(javafx.scene.input.MouseEvent e) {
        if (dragId == null) {
            return;
        }

        wasDragged = true;

        double mx = toGraphX(e.getX());
        double my = toGraphY(e.getY());

        VNode n = byId(dragId);

        if (n != null) {
            double viewNodeX = viewX(n) + mx - dragOx;
            double viewNodeY = viewY(n) + my - dragOy;

            double graphW = canvas.getWidth() / zoom;
            double graphH = canvas.getHeight() / zoom;

            viewNodeX = Math.max(30, Math.min(graphW - 30, viewNodeX));
            viewNodeY = Math.max(30, Math.min(graphH - 30, viewNodeY));

            n.x = modelXFromView(viewNodeX, n.floor);
            n.y = modelYFromView(viewNodeY);
        }

        dragOx = mx;
        dragOy = my;
    }

    private void onMouseReleased(javafx.scene.input.MouseEvent e) {
        if (dragId != null && wasDragged) {
            VNode n = byId(dragId);
            if (n != null) {
                // Persist the new position to the real model (names are ids).
                controller.updateNodePosition(n.label, n.x, n.y);
                reconstructViewFromModel();
                updateSelectionStatsPanel();
            }
        }
        dragId = null;
    }

    private void onMouseClicked(javafx.scene.input.MouseEvent e) {
        if (wasDragged) {
            wasDragged = false;
            return;
        }
        double mx = toGraphX(e.getX());
        double my = toGraphY(e.getY());

        VNode hit = hitNode(mx, my);

        Agent hitAgent = tool == null ? hitAgent(mx, my) : null;

        if (hitAgent != null) {
            selectedAgentId = hitAgent.getId();
            selectedId = null;
            showAgentInfo(hitAgent);
            return;
        }

        // ── Tool: fire ────────────────────────────────────
        if ("fire".equals(tool)) {
            if (hit != null) {
                fireNodeId = hit.id;
                fireSpread.clear();
                fireExposure.clear();
                startFire();
                setTool(null);
            }
            return;
        }

        // ── Tool: delete ──────────────────────────────────
        if ("delete".equals(tool)) {
            if (hit != null) {
                deleteNode(hit.id);
                setTool(null);
                clearInfoPanel();
            }
            return;
        }

        // ── Tool: addEdge ─────────────────────────────────
        if ("addEdge".equals(tool)) {
            if (hit != null) {
                if (hit.id != null && hit.id.startsWith("door::")) {
                    log("⚠ Impossible de créer un lien depuis ou vers une porte.");
                    return;
                }
                if (edgeStart == null) {
                    edgeStart = hit.id;
                    showInfo("Cliquez sur le 2e nœud à connecter");
                } else if (!edgeStart.equals(hit.id)) {
                    // Names are the stable model identifiers.
                    String firstName = edgeStart;
                    String secondName = hit.id;

                    boolean ok = controller.addConnection(firstName, secondName);
                    reconstructViewFromModel();

                    if (ok) {
                        log("🔗 Lien créé entre « " + firstName
                                + " » et « " + secondName + " »");
                    } else {
                        log("⚠ Lien impossible entre « " + firstName
                                + " » et « " + secondName + " » "
                                + "(étages incompatibles, sorties entre elles, ou liaison invalide)");
                    }

                    edgeStart = null;
                    setTool(null);
                    updateSelectionStatsPanel();
                }
            }
            return;
        }

        // ── Tool: addRoom / addHall / addStair / addExit ──
        if (hit == null) {
            String activeTool = tool == null ? "" : tool;

            if (currentFloor < 0 && activeTool.startsWith("add")) {
                log("⚠ Choisissez un étage précis avant d'ajouter un élément.");
                return;
            }

            int floorForNew = currentFloor;
            double modelX = currentFloor < 0 ? mx : modelXFromView(mx, floorForNew);
            double modelY = currentFloor < 0 ? my : modelYFromView(my);

            switch (activeTool) {
                case "addRoom" -> {
                    String name = uniqueName("Salle");
                    controller.addNode(name, "Salle", modelX, modelY, floorForNew);
                    autoConnectRoomToNearestPassage(name, modelX, modelY, floorForNew);
                    reconstructViewFromModel();

                    if (controller.hasElement(name)) {
                        selectedId = name;
                        showNodeInfo(name);
                    }

                    setTool(null);
                }
                case "addHall" -> {
                    String name = uniqueName("Hall");
                    controller.addNode(name, "Hall", modelX, modelY, floorForNew);
                    reconstructViewFromModel();
                    if (controller.hasElement(name)) {
                        selectedId = name;
                        showNodeInfo(name);
                    }
                    setTool(null);
                }
                case "addStair" -> {
                    String name = uniqueName("Escalier");
                    controller.addNode(name, "Escalier", modelX, modelY, floorForNew);
                    reconstructViewFromModel();
                    if (controller.hasElement(name)) {
                        selectedId = name;
                        showNodeInfo(name);
                    }
                    setTool(null);
                }
                case "addExit" -> {
                    String name = uniqueName("Sortie");
                    controller.addNode(name, "Sortie", modelX, modelY, floorForNew);
                    autoConnectRoomToNearestPassage(name, modelX, modelY, floorForNew);
                    selectedId = name;
                    reconstructViewFromModel();

                    if (controller.hasElement(name)) {
                        selectedId = name;
                        showNodeInfo(name);
                    }

                    setTool(null);
                }
                default -> {
                    selectedId = null;
                    selectedAgentId = null;
                    clearInfoPanel();
                }
            }
            return;
        }
        selectedAgentId = null;
        selectedId = hit.id;
        showNodeInfo(hit.id);
    }

    // ── Tool / floor management ───────────────────────────
    private void setTool(String t) {
        tool = t;
        edgeStart = null;
        toolBtns.forEach((id, btn) -> btn.setStyle(defaultBtnStyle()));
        String active = switch (t == null ? "" : t) {
            case "addRoom" ->
                "btn-addRoom";
            case "addHall" ->
                "btn-addHall";
            case "addStair" ->
                "btn-addStair";
            case "addExit" ->
                "btn-addExit";
            case "addEdge" ->
                "btn-addEdge";
            case "delete" ->
                "btn-delete";
            case "fire" ->
                "";
            default ->
                "";
        };
        if (!active.isEmpty() && toolBtns.containsKey(active)) {
            toolBtns.get(active).setStyle(activeBtnStyle());
        }
        canvas.setCursor(t != null ? Cursor.CROSSHAIR : Cursor.DEFAULT);
    }

    private void setFloor(int f) {
        currentFloor = f;
        floorBtns.forEach((id, btn) -> btn.setStyle(defaultBtnStyle()));
        String active = switch (f) {
            case -1 ->
                "fl-all";
            case 0 ->
                "fl-0";
            case 1 ->
                "fl-1";
            case 2 ->
                "fl-2";
            case 3 ->
                "fl-3";
            default ->
                "fl-all";
        };
        if (floorBtns.containsKey(active)) {
            floorBtns.get(active).setStyle(activeBtnStyle());
        }
    }

    // ── Fire simulation ───────────────────────────────────
    private void restoreFireStateFromModel() {
        fireNodeId = null;
        fireSpread.clear();
        fireExposure.clear();

        for (VNode node : nodes) {
            if (node == null || node.id == null || node.id.startsWith("door::")) {
                continue;
            }

            BuildingElement element = findModelElementByName(node.label);

            if (element != null && element.isBlocked()) {
                if (fireNodeId == null) {
                    fireNodeId = node.id;
                } else {
                    fireSpread.add(node.id);
                }
            }
        }

        updateFireStatus();

        if (controller.getGraph().isEmergencyActive() && fireNodeId != null) {
            ensureFireTimerRunning();
        }
    }

    private void ensureFireTimerRunning() {
        if (fireTimer != null) {
            return;
        }

        fireTimer = new Timeline(new KeyFrame(Duration.millis(6500), e -> spreadFire()));
        fireTimer.setCycleCount(Timeline.INDEFINITE);
        fireTimer.play();
    }

    private void startFire() {
        if (fireTimer != null) {
            fireTimer.stop();
        }

        fireExposure.clear();

        fireTimer = new Timeline(new KeyFrame(Duration.millis(6500), e -> spreadFire()));
        fireTimer.setCycleCount(Timeline.INDEFINITE);
        fireTimer.play();
        updateFireStatus();
        syncFireWithModel();
        if (fireNodeId != null) {
            controller.reportSmokeAt(fireNodeId);
        }

        controller.triggerFireAlert();
    }

    private void spreadFire() {
        List<String> frontier = new ArrayList<>();

        if (fireNodeId != null) {
            frontier.add(fireNodeId);
        }

        frontier.addAll(fireSpread);

        Set<String> candidates = new LinkedHashSet<>();

        for (String fireZoneId : frontier) {
            for (VEdge edge : edges) {
                if (edge.from.equals(fireZoneId)) {
                    addFireCandidate(fireZoneId, edge.to, candidates);
                }

                if (edge.to.equals(fireZoneId)) {
                    addFireCandidate(fireZoneId, edge.from, candidates);
                }
            }
        }

        Set<String> newlyBurned = new LinkedHashSet<>();

        for (String candidate : candidates) {
            int exposure = fireExposure.getOrDefault(candidate, 0) + 1;

            if (exposure >= fireExposureThreshold(candidate)) {
                newlyBurned.add(candidate);
                fireExposure.remove(candidate);
            } else {
                fireExposure.put(candidate, exposure);
            }
        }

        if (!newlyBurned.isEmpty()) {
            fireSpread.addAll(newlyBurned);
            syncFireWithModel();
            controller.rerouteAgentsAffectedByFire(currentFireZoneNames());
        }

        updateFireStatus();
    }

    private void addFireCandidate(String fromId, String nodeId, Set<String> candidates) {
        if (nodeId == null || nodeId.equals(fireNodeId) || fireSpread.contains(nodeId)) {
            return;
        }

        // Fire crosses visual doors but does not burn the door marker itself.
        if (nodeId.startsWith("door::")) {
            for (VEdge edge : edges) {
                String other = null;

                if (edge.from.equals(nodeId)) {
                    other = edge.to;
                } else if (edge.to.equals(nodeId)) {
                    other = edge.from;
                }

                if (other != null && !other.equals(fromId)) {
                    addFireCandidateThroughStep(fromId, other, candidates);
                }
            }

            return;
        }

        addFireCandidateThroughStep(fromId, nodeId, candidates);
    }

    private void addFireCandidateThroughStep(String fromId, String toId, Set<String> candidates) {
        if (!isValidFireSpreadStep(fromId, toId)) {
            return;
        }

        candidates.add(toId);
    }

    private boolean isValidFireSpreadStep(String fromId, String toId) {
        if (fromId == null || toId == null || toId.startsWith("door::")) {
            return false;
        }

        if (toId.equals(fireNodeId) || fireSpread.contains(toId)) {
            return false;
        }

        VNode from = byId(fromId);
        VNode to = byId(toId);

        if (from == null || to == null || to.type == NType.DOOR) {
            return false;
        }

        // Exits may become blocked, but fire does not continue from an exit.
        if (from.type == NType.EXIT) {
            return false;
        }

        // Fire can move between floors only through connected staircase nodes.
        if (from.floor != to.floor) {
            return from.type == NType.STAIR && to.type == NType.STAIR;
        }

        return true;
    }

    private int fireExposureThreshold(String nodeId) {
        VNode node = byId(nodeId);

        if (node == null) {
            return Integer.MAX_VALUE;
        }

        return switch (node.type) {
            case ROOM ->
                3;
            case HALL ->
                2;
            case STAIR ->
                3;
            case EXIT ->
                2;
            case DOOR ->
                Integer.MAX_VALUE;
        };
    }

    private Set<String> currentFireZoneNames() {
        Set<String> zones = new HashSet<>();

        if (fireNodeId != null) {
            zones.add(fireNodeId);
        }

        zones.addAll(fireSpread);

        return zones;
    }

    private boolean isValidFireSpreadTarget(String nodeId) {
        if (nodeId == null || nodeId.startsWith("door::")) {
            return false;
        }

        VNode node = byId(nodeId);

        return node != null && node.type != NType.DOOR;
    }

    private void markVisualNodeAsBlocked(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return;
        }

        // Do not block visual doors. They are drawing artifacts, not safe zones.
        if (nodeId.startsWith("door::")) {
            return;
        }

        VNode node = byId(nodeId);

        if (node == null || node.type == NType.DOOR) {
            return;
        }

        markModelElementAsBlocked(nodeId);
    }

    private void resetFire() {
        if (fireTimer != null) {
            fireTimer.stop();
            fireTimer = null;
        }
        fireNodeId = null;
        fireSpread.clear();
        fireExposure.clear();
        updateFireStatus();
        controller.reset();
        visualAgents.clear();
        lastAgentFrameNs = 0L;
        // controller.reset() rebuilds the model graph; resync the visual graph.
        reconstructViewFromModel();
        clearInfoPanel();
        updateSelectionStatsPanel();
    }

    private void syncFireWithModel() {
        markVisualNodeAsBlocked(fireNodeId);

        for (String nodeId : fireSpread) {
            markVisualNodeAsBlocked(nodeId);
        }
    }

    private void markModelElementAsBlocked(String elementName) {
        BuildingElement element = findModelElementByName(elementName);

        if (element != null) {
            element.setStatus(BlockStatus.BLOCKED);
        }
    }

    private void updateFireStatus() {
        if (fireStatusLbl == null) {
            return;
        }
        if (fireNodeId == null) {
            fireStatusLbl.setText("Aucun feu détecté");
            fireStatusLbl.setStyle("-fx-font-size:11px;-fx-text-fill:#64748b;");
        } else {
            VNode fn = byId(fireNodeId);
            fireStatusLbl.setText("🔥 " + (fn != null ? fn.label : "?")
                    + " — " + fireSpread.size() + " zone(s)");
            fireStatusLbl.setStyle("-fx-font-size:11px;-fx-text-fill:#dc2626;");
        }
    }

    // ── Node deletion ─────────────────────────────────────
    private void deleteNode(String id) {
        if (id == null || id.isEmpty()) {
            return;
        }

        // Door VNodes are not BuildingElement entries; their id encodes the
        // (room, passage) pair of the real model Door. Remove that Door.
        if (id.startsWith("door::")) {
            String[] parts = id.split("::", 3);
            if (parts.length == 3) {
                controller.removeEdge(parts[1], parts[2]);
                if (id.equals(selectedId)) {
                    selectedId = null;
                }
                reconstructViewFromModel();
                updateSelectionStatsPanel();
            }
            return;
        }

        // Otherwise the id is the exact model name. Only proceed if it really
        // exists, so a stale or empty id can never trigger a cascading deletion.
        if (!controller.hasElement(id)) {
            // The visual node may be a hidden/virtual artifact; just resync.
            reconstructViewFromModel();
            updateSelectionStatsPanel();
            return;
        }

        // Remove exactly one element from the real model (id == element name).
        // The controller also relocates agents and removes related doors /
        // virtual junctions tied to this exact element.
        controller.removeNode(id);

        // Keep the visual-only fire state consistent.
        if (id.equals(fireNodeId)) {
            fireNodeId = null;
            fireSpread.clear();
        }
        fireSpread.remove(id);
        if (id.equals(selectedId)) {
            selectedId = null;
        }

        // Rebuild the visual graph from the updated model.
        reconstructViewFromModel();
        updateSelectionStatsPanel();
    }

    private List<BuildingElement> remainingPathFor(Agent agent) {
        List<BuildingElement> path = agent.getPath();

        if (path == null || path.isEmpty()) {
            return List.of();
        }

        int start = Math.max(0, Math.min(agent.getPathIndex(), path.size() - 1));

        BuildingElement currentLocation = agent.getCurrentLocation();

        if (currentLocation != null && currentLocation.getName() != null) {
            String currentName = currentLocation.getName();

            boolean indexMatchesCurrent = path.get(start) != null
                    && path.get(start).getName() != null
                    && path.get(start).getName().equalsIgnoreCase(currentName);

            if (!indexMatchesCurrent) {
                for (int i = start; i < path.size(); i++) {
                    BuildingElement element = path.get(i);

                    if (element != null
                            && element.getName() != null
                            && element.getName().equalsIgnoreCase(currentName)) {
                        start = i;
                        break;
                    }
                }
            }
        }

        return withoutConsecutiveDuplicates(path.subList(start, path.size()));
    }

    private void showAgentInfo(Agent agent) {
        infoPanelBox.getChildren().clear();

        Label title = new Label("Agent : " + agent.getName());
        title.setStyle("-fx-font-size:11px;-fx-font-weight:bold;-fx-text-fill:#334155;");
        infoPanelBox.getChildren().add(title);

        String location = agent.getCurrentLocation() != null
                ? agent.getCurrentLocation().getName()
                : "—";

        String destination = agent.getDestination() != null
                ? agent.getDestination().getName()
                : "—";

        Label locationLbl = new Label("Position : " + location);
        Label destinationLbl = new Label("Destination : " + destination);
        Label speedLbl = new Label(String.format("Vitesse max : %.2f", agent.getMaxSpeed()));
        Label behaviorLbl = new Label("Comportement : " + agent.getBehavior());
        Label stateLbl = new Label("État : " + agent.getState());
        Label toleranceLbl = new Label(String.format("Tolérance densité : %.2f", agent.getDensityTolerance()));

        for (Label label : List.of(locationLbl, destinationLbl, speedLbl, behaviorLbl, stateLbl, toleranceLbl)) {
            label.setStyle("-fx-font-size:11px;-fx-text-fill:#64748b;");
            label.setWrapText(true);
            infoPanelBox.getChildren().add(label);
        }

        if (agent.getState() == AgentState.TRAPPED) {
            Label trappedLbl = new Label("⚠ Agent bloqué — les secours arrivent");
            trappedLbl.setStyle("-fx-font-size:11px;-fx-font-weight:bold;-fx-text-fill:#b45309;");
            trappedLbl.setWrapText(true);
            infoPanelBox.getChildren().add(trappedLbl);
        }

        Label pathTitle = new Label("Trajet restant :");
        pathTitle.setStyle("-fx-font-size:11px;-fx-font-weight:bold;-fx-text-fill:#334155;");
        infoPanelBox.getChildren().add(pathTitle);

        List<BuildingElement> path = remainingPathFor(agent);
        if (path == null || path.isEmpty()) {
            Label emptyPath = new Label("Aucun trajet calculé pour le moment.");
            emptyPath.setStyle("-fx-font-size:11px;-fx-text-fill:#94a3b8;");
            emptyPath.setWrapText(true);
            infoPanelBox.getChildren().add(emptyPath);
        } else {
            StringJoiner joiner = new StringJoiner(" → ");

            for (BuildingElement element : path) {
                joiner.add(element.getName());
            }

            Label pathLbl = new Label(joiner.toString());
            pathLbl.setWrapText(true);
            pathLbl.setStyle("-fx-font-size:11px;-fx-text-fill:#2563eb;");
            infoPanelBox.getChildren().add(pathLbl);
        }
    }

    // ── Info panel ────────────────────────────────────────
    private void showNodeInfo(String id) {
        VNode n = byId(id);
        if (n == null) {
            clearInfoPanel();
            return;
        }

        infoPanelBox.getChildren().clear();

        // Special case: visual Door nodes (id starts with "door::") represent a
        // real model Door between a room and a passage. They have no editable
        // name/floor/capacity on their own — those belong to the room/passage.
        if (id.startsWith("door::")) {
            String[] parts = id.split("::", 3);
            String roomName = parts.length == 3 ? parts[1] : "?";
            String passageName = parts.length == 3 ? parts[2] : "?";

            Door door = findDoorBetween(roomName, passageName);

            Label typeLbl = new Label("Porte");
            typeLbl.setStyle("-fx-font-size:11px;-fx-font-weight:bold;-fx-text-fill:#334155;");
            infoPanelBox.getChildren().add(typeLbl);

            Label between = new Label("Entre : « " + roomName + " » et « " + passageName + " »");
            between.setStyle("-fx-font-size:11px;-fx-text-fill:#64748b;");
            between.setWrapText(true);
            infoPanelBox.getChildren().add(between);

            if (door != null) {
                Label occupancy = new Label(
                        "Occupation actuelle : "
                        + door.getCurrentOccupancy()
                        + " / "
                        + door.getMaxCapacity()
                );
                occupancy.setStyle("-fx-font-size:11px;-fx-text-fill:#64748b;");
                occupancy.setWrapText(true);
                infoPanelBox.getChildren().add(occupancy);

                infoPanelBox.getChildren().add(intRow("Capacité arête", door.getMaxCapacity(), val -> {
                    controller.updateDoorCapacity(roomName, passageName, val);
                    reconstructViewFromModel();
                    showNodeInfo(id);
                    updateSelectionStatsPanel();
                }));
            } else {
                Label missing = new Label("Porte introuvable dans le modèle.");
                missing.setStyle("-fx-font-size:11px;-fx-text-fill:#b91c1c;");
                missing.setWrapText(true);
                infoPanelBox.getChildren().add(missing);
            }

            Button del = sBtn("✕ Supprimer la porte", "#ef4444");
            del.setOnAction(e -> {
                deleteNode(id);
                clearInfoPanel();
            });
            infoPanelBox.getChildren().add(del);
            return;
        }
        Label typeLbl = new Label(n.type.name() + " — "
                + (n.floor < FLOOR_NAME.length ? FLOOR_NAME[n.floor] : "Étage " + n.floor));
        typeLbl.setStyle("-fx-font-size:11px;-fx-font-weight:bold;-fx-text-fill:#334155;");
        infoPanelBox.getChildren().add(typeLbl);

        // Name — applied to the real model via the controller.
        infoPanelBox.getChildren().add(propRow("Nom", n.label, val -> {
            String newName = val == null ? "" : val.trim();
            if (newName.isEmpty() || newName.equals(n.label)) {
                return;
            }
            if (controller.hasElement(newName)) {
                showErr("Un élément nommé « " + newName + " » existe déjà");
                return;
            }
            BuildingElement el = findModelElementByName(n.label);
            int cap = el != null ? el.getMaxCapacity() : n.cap;
            controller.updateNode(n.label, newName, cap);

            // Preserve selection across the rebuild.
            selectedId = newName;
            reconstructViewFromModel();
            showNodeInfo(newName);
            updateSelectionStatsPanel();
        }));

        // Floor — applied to the real model via the controller.
        ComboBox<String> flCombo = new ComboBox<>();
        for (String fn : FLOOR_NAME) {
            flCombo.getItems().add(fn);
        }
        flCombo.getSelectionModel().select(Math.min(n.floor, FLOOR_NAME.length - 1));
        flCombo.setStyle("-fx-font-size:11px;");
        flCombo.setOnAction(e -> {
            int newFloor = flCombo.getSelectionModel().getSelectedIndex();
            BuildingElement el = findModelElementByName(n.label);
            String currentName = el != null ? el.getName() : n.label;
            controller.setNodeFloor(currentName, newFloor);
            n.floor = newFloor;
            reconstructViewFromModel();
            updateSelectionStatsPanel();
        });
        infoPanelBox.getChildren().add(labeledControl("Étage", flCombo));

        // Capacity — applied to the real model via the controller.
        if (n.type == NType.ROOM || n.type == NType.HALL) {
            infoPanelBox.getChildren().add(intRow("Capacité (pers)", n.cap, val -> {
                BuildingElement el = findModelElementByName(n.label);
                String currentName = el != null ? el.getName() : n.label;
                controller.updateNode(currentName, currentName, val);
                n.cap = val;
                reconstructViewFromModel();
                updateSelectionStatsPanel();
            }));
        }

        BuildingElement modelElement = findModelElementByName(n.label);

        if (modelElement != null && n.type != NType.DOOR) {
            infoPanelBox.getChildren().add(dblRow(
                    "Attractivité",
                    modelElement.getAttractivenessScore(),
                    val -> {
                        controller.updateAttractivenessScore(n.label, val);
                        reconstructViewFromModel();
                        showNodeInfo(n.label);
                        updateSelectionStatsPanel();
                    }
            ));
        }

        // Rate 
        if (n.type == NType.DOOR || n.type == NType.STAIR) {
            infoPanelBox.getChildren().add(dblRow("Débit (pers/s)", n.rate, val -> n.rate = val));
        }

        // Delete
        Button del = sBtn("✕ Supprimer", "#ef4444");
        del.setOnAction(e -> {
            deleteNode(id);
            clearInfoPanel();
        });
        infoPanelBox.getChildren().add(del);
    }

    private HBox propRow(String label, String value, java.util.function.Consumer<String> setter) {
        Label lbl = new Label(label);
        lbl.setMinWidth(80);
        lbl.setStyle("-fx-font-size:11px;-fx-text-fill:#64748b;");
        TextField tf = new TextField(value);
        tf.setPrefWidth(110);
        tf.setStyle("-fx-font-size:11px;");
        tf.setOnAction(e -> setter.accept(tf.getText()));
        tf.focusedProperty().addListener((o, ov, nv) -> {
            if (!nv) {
                setter.accept(tf.getText());

            }
        });
        return new HBox(6, lbl, tf);
    }

    private HBox intRow(String label, int value, java.util.function.Consumer<Integer> setter) {
        Label lbl = new Label(label);
        lbl.setMinWidth(90);
        lbl.setStyle("-fx-font-size:11px;-fx-text-fill:#64748b;");
        TextField tf = new TextField(String.valueOf(value));
        tf.setPrefWidth(70);
        tf.setStyle("-fx-font-size:11px;");
        Runnable apply = () -> {
            try {
                setter.accept(Integer.parseInt(tf.getText().trim()));
            } catch (Exception ignored) {
            }
        };
        tf.setOnAction(e -> apply.run());
        tf.focusedProperty().addListener((o, ov, nv) -> {
            if (!nv) {
                apply.run();

            }
        });
        return new HBox(6, lbl, tf);
    }

    private HBox dblRow(String label, double value, java.util.function.Consumer<Double> setter) {
        Label lbl = new Label(label);
        lbl.setMinWidth(90);
        lbl.setStyle("-fx-font-size:11px;-fx-text-fill:#64748b;");
        TextField tf = new TextField(String.format("%.1f", value));
        tf.setPrefWidth(70);
        tf.setStyle("-fx-font-size:11px;");
        Runnable apply = () -> {
            try {
                setter.accept(Double.parseDouble(tf.getText().trim()));
            } catch (Exception ignored) {
            }
        };
        tf.setOnAction(e -> apply.run());
        tf.focusedProperty().addListener((o, ov, nv) -> {
            if (!nv) {
                apply.run();

            }
        });
        return new HBox(6, lbl, tf);
    }

    private HBox labeledControl(String label, javafx.scene.Node ctrl) {
        Label lbl = new Label(label);
        lbl.setMinWidth(80);
        lbl.setStyle("-fx-font-size:11px;-fx-text-fill:#64748b;");
        return new HBox(6, lbl, ctrl);
    }

    private void clearInfoPanel() {
        infoPanelBox.getChildren().clear();
        Label hint = new Label("Cliquez sur un élément");
        hint.setStyle("-fx-font-size:11px;-fx-text-fill:#94a3b8;");
        infoPanelBox.getChildren().add(hint);
        updateSelectionStatsPanel();
    }

    private void showInfo(String msg) {
        infoPanelBox.getChildren().clear();
        Label l = new Label(msg);
        l.setStyle("-fx-font-size:11px;-fx-text-fill:#94a3b8;");
        l.setWrapText(true);
        infoPanelBox.getChildren().add(l);
    }

    private void handleAddAgent() {
        TextField nameF = new TextField();
        TextField speedF = new TextField("1.0");
        TextField tolF = new TextField("0.7");

        ComboBox<String> locB = nodeCombo();

        ComboBox<Behavior> behB = new ComboBox<>();
        behB.getItems().addAll(Behavior.values());
        behB.getSelectionModel().selectFirst();

        dialog("Ajouter un agent",
                grid("Nom :", nameF,
                        "Position :", locB,
                        "Vitesse :", speedF,
                        "Tolérance :", tolF,
                        "Comportement :", behB),
                () -> {
                    try {
                        String name = nameF.getText().trim();

                        if (name.isEmpty()) {
                            showErr("Le nom de l'agent est obligatoire");
                            return;
                        }

                        controller.addPersonAgent(
                                name,
                                locB.getValue(),
                                Double.parseDouble(speedF.getText().trim()),
                                behB.getValue(),
                                Double.parseDouble(tolF.getText().trim())
                        );

                        log("Agent ajouté : " + name);
                    } catch (Exception ex) {
                        showErr("Valeurs invalides");
                    }
                }
        );
    }

    private void handleEditAgent() {
        ComboBox<String> agB = agentCombo();

        TextField nameF = new TextField();
        TextField speedF = new TextField();
        TextField tolF = new TextField();

        ComboBox<String> locB = nodeCombo();

        ComboBox<Behavior> behB = new ComboBox<>();
        behB.getItems().addAll(Behavior.values());
        behB.getSelectionModel().selectFirst();

        agB.setOnAction(e -> {
            controller.getGraph().getAgents().stream()
                    .filter(a -> a.getName().equals(agB.getValue()))
                    .findFirst()
                    .ifPresent(a -> {
                        nameF.setText(a.getName());
                        speedF.setText(String.valueOf(a.getMaxSpeed()));
                        tolF.setText(String.valueOf(a.getDensityTolerance()));
                        locB.setValue(a.getCurrentLocation().getName());
                        behB.setValue(a.getBehavior());
                    });
        });

        if (!agB.getItems().isEmpty()) {
            agB.getSelectionModel().selectFirst();
            agB.getOnAction().handle(null);
        }

        dialog("Modifier un agent",
                grid("Agent :", agB,
                        "Nouveau nom :", nameF,
                        "Position :", locB,
                        "Vitesse :", speedF,
                        "Tolérance :", tolF,
                        "Comportement :", behB),
                () -> {
                    try {
                        controller.updateAgent(
                                agB.getValue(),
                                nameF.getText().trim(),
                                locB.getValue(),
                                Double.parseDouble(speedF.getText().trim()),
                                behB.getValue(),
                                Double.parseDouble(tolF.getText().trim())
                        );

                        log("Agent modifié : " + nameF.getText().trim());
                    } catch (Exception ex) {
                        showErr("Valeurs invalides");
                    }
                }
        );
    }

    private void handleRemoveAgent() {
        ComboBox<String> agB = agentCombo();

        dialog("Supprimer un agent",
                grid("Agent :", agB),
                () -> {
                    if (agB.getValue() != null) {
                        controller.removeAgent(agB.getValue());
                        log("Agent supprimé : " + agB.getValue());
                    }
                }
        );
    }

    private void handleRandomAgents() {
        TextField countF = new TextField("10");
        TextField minSF = new TextField("0.5");
        TextField maxSF = new TextField("1.5");
        TextField minTF = new TextField("0.3");
        TextField maxTF = new TextField("1.0");

        dialog("Agents aléatoires",
                grid("Nombre :", countF,
                        "Vitesse min :", minSF,
                        "Vitesse max :", maxSF,
                        "Tolérance min :", minTF,
                        "Tolérance max :", maxTF),
                () -> {
                    try {
                        int n = Integer.parseInt(countF.getText().trim());
                        if (n <= 0) {
                            showErr("Le nombre doit être supérieur à 0");
                            return;
                        }

                        if (n > 100) {
                            showErr("Ajoutez au maximum 100 agents à la fois");
                            return;
                        }

                        int totalAfterAdd = controller.getGraph().getAgents().size() + n;

                        if (totalAfterAdd > 300) {
                            showErr("Limite atteinte : maximum 300 agents dans la simulation");
                            return;
                        }
                        controller.addRandomAgents(
                                n,
                                Double.parseDouble(minSF.getText().trim()),
                                Double.parseDouble(maxSF.getText().trim()),
                                Double.parseDouble(minTF.getText().trim()),
                                Double.parseDouble(maxTF.getText().trim())
                        );

                        log(n + " agents aléatoires ajoutés");
                    } catch (Exception ex) {
                        showErr("Valeurs invalides");
                    }
                }
        );
    }

    private void handleRandomNodes() {
        TextField countF = new TextField("5");

        ComboBox<String> floorB = new ComboBox<>();
        for (String floorName : FLOOR_NAME) {
            floorB.getItems().add(floorName);
        }

        int defaultFloor = currentFloor >= 0 ? currentFloor : 0;
        floorB.getSelectionModel().select(Math.min(defaultFloor, FLOOR_NAME.length - 1));

        dialog("Ajouter des nœuds aléatoires",
                grid(
                        "Nombre :", countF,
                        "Étage :", floorB
                ),
                () -> {
                    try {
                        int count = Integer.parseInt(countF.getText().trim());
                        int floor = floorB.getSelectionModel().getSelectedIndex();

                        if (count <= 0) {
                            showErr("Le nombre doit être supérieur à 0");
                            return;
                        }

                        if (count > 50) {
                            showErr("Ajoutez au maximum 50 nœuds à la fois");
                            return;
                        }

                        controller.addRandomNodes(count, floor);
                        reconstructViewFromModel();
                        updateSelectionStatsPanel();

                        log(count + " nœud(s) aléatoire(s) ajouté(s) à l'étage " + floorName(floor));
                    } catch (Exception ex) {
                        showErr("Valeur invalide");
                    }
                }
        );
    }

    private ComboBox<String> nodeCombo() {
        ComboBox<String> b = new ComboBox<>();

        controller.getGraph().getElements().stream()
                .filter(el -> !el.getName().contains("↔"))
                .forEach(el -> b.getItems().add(el.getName()));

        b.getSelectionModel().selectFirst();
        return b;
    }

    private ComboBox<String> agentCombo() {
        ComboBox<String> b = new ComboBox<>();

        controller.getGraph().getAgents()
                .forEach(a -> b.getItems().add(a.getName()));

        b.getSelectionModel().selectFirst();
        return b;
    }

    private void dialog(String title, GridPane content, Runnable onOk) {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle(title);
        d.getDialogPane().setContent(content);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        d.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                onOk.run();
            }
        });
    }

    private GridPane grid(Object... pairs) {
        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(8);
        g.setPadding(new Insets(10));

        for (int i = 0; i < pairs.length; i += 2) {
            g.add(new Label(pairs[i].toString()), 0, i / 2);
            g.add((javafx.scene.Node) pairs[i + 1], 1, i / 2);
        }

        return g;
    }

    private void showErr(String msg) {
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }

    private void log(String msg) {
        if (logArea != null) {
            logArea.appendText(msg + "\n");
            logArea.setScrollTop(Double.MAX_VALUE);
        }
    }

    private void updateSelectionStatsPanel() {
        if (statsPanel == null || statNameLbl == null) {
            return;
        }

        VNode n = byId(selectedId);

        if (n == null) {
            statNameLbl.setText("Aucun élément sélectionné");
            statTypeLbl.setText("Type : —");
            statFloorLbl.setText("Étage : —");
            statOccupationLbl.setText("Occupation : —");
            statPassedLbl.setText("Agents passés : —");
            statSpeedLbl.setText("Vitesse moy. : —");
            statLinksLbl.setText("Connexions : —");
            statStatusLbl.setText("Statut : —");
            return;
        }

        if (n.id != null && n.id.startsWith("door::")) {
            String[] parts = n.id.split("::", 3);
            String roomName = parts.length == 3 ? parts[1] : "?";
            String passageName = parts.length == 3 ? parts[2] : "?";

            Door door = findDoorBetween(roomName, passageName);

            statNameLbl.setText("Porte : " + roomName + " ↔ " + passageName);
            statTypeLbl.setText("Type : Arête / Porte");
            statFloorLbl.setText("Étage : " + floorName(n.floor));
            statLinksLbl.setText("Relie : " + roomName + " ↔ " + passageName);

            if (door != null) {
                statOccupationLbl.setText(
                        "Occupation arête : "
                        + door.getCurrentOccupancy()
                        + " / "
                        + door.getMaxCapacity()
                );
                statPassedLbl.setText("Agents passés : " + door.getTotalAgentsPassed());
                statSpeedLbl.setText(String.format("Vitesse moy. : %.2f", door.getAverageSpeed()));
                statStatusLbl.setText("Statut : porte réelle du modèle");
            } else {
                statOccupationLbl.setText("Occupation : —");
                statPassedLbl.setText("Agents passés : —");
                statSpeedLbl.setText("Vitesse moy. : —");
                statStatusLbl.setText("Statut : porte introuvable dans le modèle");
            }

            return;
        }

        BuildingElement modelElement = findModelElementByName(n.label);

        statNameLbl.setText(n.label);
        statTypeLbl.setText("Type : " + typeName(n.type));
        statFloorLbl.setText("Étage : " + floorName(n.floor));
        statLinksLbl.setText("Connexions : " + connectionCount(n.id));

        if (modelElement != null) {
            int max = modelElement.getMaxCapacity();
            int occ = modelElement.getCurrentOccupancy();

            if (n.type == NType.EXIT) {
                statOccupationLbl.setText("Occupation : sortie");
            } else {
                statOccupationLbl.setText("Occupation : " + occ + " / " + max);
            }

            statPassedLbl.setText("Agents passés : " + modelElement.getTotalAgentsPassed());
            statSpeedLbl.setText(String.format("Vitesse moy. : %.2f", modelElement.getAverageSpeed()));

            double density = max <= 0 ? 0 : (occ * 100.0 / max);

            statStatusLbl.setText(String.format(
                    "Statut : %s · densité %.0f%%",
                    modelElement.getStatus(),
                    density
            ));

        } else {
            if (n.type == NType.ROOM || n.type == NType.HALL) {
                statOccupationLbl.setText("Capacité : " + n.cap + " personnes");
                statSpeedLbl.setText("Débit : —");
            } else if (n.type == NType.DOOR || n.type == NType.STAIR) {
                statOccupationLbl.setText("Capacité : —");
                statSpeedLbl.setText(String.format("Débit : %.1f pers/s", n.rate));
            } else if (n.type == NType.EXIT) {
                statOccupationLbl.setText("Occupation : sortie");
                statSpeedLbl.setText("Débit : —");
            }

            statPassedLbl.setText("Agents passés : —");
            statStatusLbl.setText("Statut : " + visualStatus(n));
        }
    }

    private Door findDoorBetween(String roomName, String passageName) {
        BuildingElement roomElement = findModelElementByName(roomName);
        BuildingElement passageElement = findModelElementByName(passageName);

        if (!(roomElement instanceof Room room) || !(passageElement instanceof Passage passage)) {
            return null;
        }

        for (Door door : room.getDoors()) {
            if (door.getPassage() == passage) {
                return door;
            }
        }

        return null;
    }

    private Agent findAgentById(String agentId) {
        if (agentId == null || controller == null || controller.getGraph() == null) {
            return null;
        }

        for (Agent agent : controller.getGraph().getAgents()) {
            if (agent.getId().equals(agentId)) {
                return agent;
            }
        }

        return null;
    }

    private BuildingElement findModelElementByName(String name) {
        if (name == null || controller == null || controller.getGraph() == null) {
            return null;
        }

        for (BuildingElement el : controller.getGraph().getElements()) {
            if (el.getName().equalsIgnoreCase(name)) {
                return el;
            }
        }

        return null;
    }

    private String typeName(NType type) {
        return switch (type) {
            case ROOM ->
                "Salle";
            case DOOR ->
                "Porte";
            case HALL ->
                "Hall";
            case STAIR ->
                "Escalier";
            case EXIT ->
                "Sortie";
        };
    }

    private String floorName(int floor) {
        if (floor >= 0 && floor < FLOOR_NAME.length) {
            return FLOOR_NAME[floor];
        }

        return "Étage " + floor;
    }

    private double modelFloorOriginX(int floor) {
        if (floor >= 0 && floor < MODEL_FLOOR_ORIGIN_X.length) {
            return MODEL_FLOOR_ORIGIN_X[floor];
        }

        return MODEL_FLOOR_ORIGIN_X[0];
    }

    private double viewX(VNode node) {
        return viewX(node.x, node.floor);
    }

    private double viewY(VNode node) {
        return viewY(node.y, node.floor);
    }

    private double viewX(double modelX, int floor) {
        double localX = modelX - modelFloorOriginX(floor);

        if (currentFloor == -1) {
            return ALL_FLOOR_LEFT + floor * ALL_FLOOR_GAP + localX;
        }

        return SINGLE_FLOOR_CENTER_X + localX;
    }

    private double viewY(double modelY, int floor) {
        if (currentFloor == -1) {
            return modelY + ALL_FLOOR_TOP_OFFSET;
        }

        return modelY;
    }

    private double modelXFromView(double screenGraphX, int floor) {
        if (currentFloor == -1) {
            return modelFloorOriginX(floor)
                    + (screenGraphX - (ALL_FLOOR_LEFT + floor * ALL_FLOOR_GAP));
        }

        return modelFloorOriginX(floor) + (screenGraphX - SINGLE_FLOOR_CENTER_X);
    }

    private double modelYFromView(double screenGraphY) {
        if (currentFloor == -1) {
            return screenGraphY - ALL_FLOOR_TOP_OFFSET;
        }

        return screenGraphY;
    }

    private void drawFloorBackgrounds(GraphicsContext gc, double graphH) {
        if (currentFloor != -1) {
            gc.setFill(Color.web("#64748b"));
            gc.setFont(Font.font("Sans", FontWeight.BOLD, 18));
            gc.fillText(floorName(currentFloor), 20, 28);
            return;
        }

        for (int floor = 0; floor < FLOOR_NAME.length; floor++) {
            double left = ALL_FLOOR_LEFT + floor * ALL_FLOOR_GAP - 185;
            double top = 20;
            double width = 340;
            double height = Math.max(540, graphH - 45);

            gc.setFill(Color.web("#ffffff66"));
            gc.fillRoundRect(left, top, width, height, 18, 18);

            gc.setStroke(Color.web("#cbd5e1"));
            gc.setLineWidth(1.2);
            gc.strokeRoundRect(left, top, width, height, 18, 18);

            gc.setFill(FLOOR_FILL[Math.min(floor, FLOOR_FILL.length - 1)][1]);
            gc.setFont(Font.font("Sans", FontWeight.BOLD, 16));
            gc.fillText(FLOOR_NAME[floor], left + 14, top + 24);
        }
    }

    private int connectionCount(String nodeId) {
        int count = 0;

        for (VEdge e : edges) {
            if (e.from.equals(nodeId) || e.to.equals(nodeId)) {
                count++;
            }
        }

        return count;
    }

    private boolean supportsDensityGradient(VNode node) {
        return node != null
                && (node.type == NType.ROOM
                || node.type == NType.HALL
                || node.type == NType.STAIR);
    }

    private double densityRatioFor(VNode node) {
        if (!supportsDensityGradient(node)) {
            return 0.0;
        }

        BuildingElement element = findModelElementByName(node.label);

        if (element == null || element.getMaxCapacity() <= 0) {
            return 0.0;
        }

        return (double) element.getCurrentOccupancy() / element.getMaxCapacity();
    }

    private Color densityFillColor(double ratio, Color defaultFill) {
        if (ratio < 0.40) {
            return defaultFill;
        }

        if (ratio < 0.75) {
            return Color.web("#fef3c7"); // medium density
        }

        if (ratio <= 1.00) {
            return Color.web("#fed7aa"); // high density
        }

        return Color.web("#fecaca"); // overcrowded
    }

    private Color densityStrokeColor(double ratio, Color defaultStroke) {
        if (ratio < 0.40) {
            return defaultStroke;
        }

        if (ratio < 0.75) {
            return Color.web("#ca8a04");
        }

        if (ratio <= 1.00) {
            return Color.web("#ea580c");
        }

        return Color.web("#dc2626");
    }

    private String visualStatus(VNode n) {
        if (n.id.equals(fireNodeId)) {
            return "Feu départ";
        }

        if (fireSpread.contains(n.id)) {
            return "Touché par la propagation";
        }

        return "Normal";
    }

    // ── Save / Load ───────────────────────────────────────
    private void handleSave() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Sauvegarde (*.bin)", "*.bin"));
        fc.setInitialFileName("simulation.bin");

        // Default to a clear project folder: <project>/saves, created if missing.
        File savesDir = new File("saves");
        if (!savesDir.exists()) {
            savesDir.mkdirs();
        }
        if (savesDir.isDirectory()) {
            fc.setInitialDirectory(savesDir.getAbsoluteFile());
        }

        File f = fc.showSaveDialog(stage);
        if (f == null) {
            return; // user cancelled
        }

        try {
            String savedPath = controller.saveSimulation(f.getAbsolutePath());
            log("💾 Sauvegarde réussie : " + savedPath);
        } catch (Exception ex) {
            log("❌ Échec de la sauvegarde : " + ex.getMessage());
            showErr("Échec de la sauvegarde :\n" + ex.getMessage());
        }
    }

    private void handleLoad() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Sauvegarde (*.bin)", "*.bin"));

        File savesDir = new File("saves");
        if (savesDir.isDirectory()) {
            fc.setInitialDirectory(savesDir.getAbsoluteFile());
        }

        File f = fc.showOpenDialog(stage);
        if (f == null) {
            return; // user cancelled
        }

        try {
            controller.loadSimulation(f.getAbsolutePath());
            // The model changed entirely; rebuild the visual graph from it.
            reconstructViewFromModel();
            clearInfoPanel();
            updateSelectionStatsPanel();
            log("📂 Chargement réussi : " + f.getAbsolutePath());
        } catch (Exception ex) {
            log("❌ Échec du chargement : " + ex.getMessage());
            showErr("Échec du chargement :\n" + ex.getMessage());
        }
    }

    private void goBack() {
        if (renderLoop != null) {
            renderLoop.stop();
        }

        if (fireTimer != null) {
            fireTimer.stop();
            fireTimer = null;
        }

        new LoginView(stage, controller).show();
    }

    // ── Helpers ───────────────────────────────────────────
    private List<VNode> visibleNodes() {
        if (currentFloor == -1) {
            return new ArrayList<>(nodes);
        }
        List<VNode> out = new ArrayList<>();
        for (VNode n : nodes) {
            if (n.floor == currentFloor) {
                out.add(n);
            }
        }
        return out;
    }

    private VNode byId(String id) {
        if (id == null) {
            return null;
        }
        for (VNode n : nodes) {
            if (n.id.equals(id)) {
                return n;
            }
        }
        return null;
    }

    private Agent hitAgent(double mx, double my) {
        for (Agent agent : controller.getGraph().getAgents()) {
            if (agent.isEvacuated()) {
                continue;
            }

            VisualAgent visualAgent = visualAgents.get(agent.getId());

            if (visualAgent == null) {
                continue;
            }

            VNode current = byId(visualAgent.fromId);
            if (current == null) {
                continue;
            }

            if (currentFloor != -1 && current.floor != currentFloor) {
                continue;
            }

            double ax = viewX(visualAgent.x, current.floor);
            double ay = viewY(visualAgent.y, current.floor);

            if (Math.hypot(mx - ax, my - ay) <= 18.0) {
                return agent;
            }
        }

        return null;
    }

    /**
     * Hit-test: returns the topmost node under (mx,my).
     */
    private VNode hitNode(double mx, double my) {
        List<VNode> vis = visibleNodes();
        Collections.reverse(vis); // topmost drawn last
        for (VNode n : vis) {
            double r = switch (n.type) {
                case ROOM ->
                    36;
                case HALL ->
                    28;
                case STAIR ->
                    26;
                case DOOR ->
                    12;
                case EXIT ->
                    24;
            };
            if (Math.hypot(mx - viewX(n), my - viewY(n)) <= r) {
                return n;
            }
        }
        return null;
    }

    // ── Style helpers ─────────────────────────────────────
    private Button sBtn(String text, String color) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color:" + color + ";-fx-text-fill:white;"
                + "-fx-font-size:11px;-fx-padding:4 10;-fx-background-radius:5;-fx-cursor:hand;");
        return b;
    }

    private String defaultBtnStyle() {
        return "-fx-background-color:white;-fx-text-fill:#334155;"
                + "-fx-font-size:11px;-fx-padding:4 8;"
                + "-fx-border-color:#cbd5e1;-fx-border-radius:5;-fx-background-radius:5;"
                + "-fx-cursor:hand;";
    }

    private String activeBtnStyle() {
        return "-fx-background-color:#dbeafe;-fx-text-fill:#1d4ed8;"
                + "-fx-font-size:11px;-fx-padding:4 8;"
                + "-fx-border-color:#3b82f6;-fx-border-radius:5;-fx-background-radius:5;"
                + "-fx-cursor:hand;";
    }

}
