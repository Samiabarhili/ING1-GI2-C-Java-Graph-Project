package com.example.cysafecampus.view;

import com.example.cysafecampus.controller.GraphController;
import com.example.cysafecampus.model.*;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main JavaFX view — clean dark-themed campus graph.
 * Nodes are color-coded by density, agents shown as stick figures.
 */
public class GraphView {

    private static final int CANVAS_W = 860;
    private static final int CANVAS_H = 520;
    private static final double NODE_R = 32;

    // Dark theme palette
    private static final Color BG          = Color.web("#0f0f1a");
    private static final Color EDGE_COLOR  = Color.web("#5c6bc0");
    private static final Color EDGE_DENSE  = Color.web("#ff9800");
    private static final Color EDGE_FULL   = Color.web("#f44336");
    private static final Color TEXT_LIGHT  = Color.web("#e0e0ff");
    private static final Color TEXT_DIM    = Color.web("#7070a0");
    private static final Color COL_GREEN   = Color.web("#1b5e20");
    private static final Color COL_ORANGE  = Color.web("#bf360c");
    private static final Color COL_RED     = Color.web("#b71c1c");
    private static final Color COL_EXIT    = Color.web("#0d47a1");
    private static final Color COL_PASSAGE = Color.web("#1a237e");
    private static final Color COL_BLOCKED = Color.web("#212121");
    private static final Color STROKE_NORM = Color.web("#3949ab");
    private static final Color STROKE_EXIT = Color.web("#42a5f5");
    private static final Color AGENT_CALM  = Color.web("#40c4ff");
    private static final Color AGENT_PANIC = Color.web("#ff5252");

    private final Stage stage;
    private Canvas canvas;
    private GraphController controller;
    private Label statusLabel;
    private Label tickLabel;
    private Button playPauseBtn;
    private Label selectionLabel;

    /** Context menu displayed when the user right-clicks the graph. */
    private ContextMenu graphContextMenu;

    /** Currently selected graph element for statistics display. */
    private BuildingElement selectedElement;

    /** Currently selected agent for remaining path display. */
    private Agent selectedAgent;

    private final Map<String, Point2D> nodePositions = new HashMap<>();

    public GraphView(Stage stage) { this.stage = stage; }

    public void setController(GraphController controller) {
        this.controller = controller;
        initDefaultPositions();
    }

    // ── Build UI ──────────────────────────────────────────

    public void show() {
        // ── Top bar ───────────────────────────────────────
        statusLabel = new Label("NORMAL");
        styleStatus(false);

        tickLabel = new Label("Tick: 0");
        tickLabel.setStyle("-fx-font-size:12px;-fx-text-fill:#7070a0;");

        Button fireBtn  = styledBtn("🔥 Alarme",  "#c62828", "#e53935");
        Button resetBtn = styledBtn("↺ Reset",    "#37474f", "#546e7a");
        Button saveBtn  = styledBtn("💾 Save",    "#1565c0", "#1976d2");
        Button loadBtn  = styledBtn("📂 Load",    "#1565c0", "#1976d2");

        fireBtn.setOnAction(e  -> controller.triggerFireAlert());
        resetBtn.setOnAction(e -> controller.reset());
        saveBtn.setOnAction(e  -> handleSave());
        loadBtn.setOnAction(e  -> handleLoad());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox topBar = new HBox(10,
            statusLabel, tickLabel, spacer, fireBtn, resetBtn, saveBtn, loadBtn);
        topBar.setPadding(new Insets(8, 14, 8, 14));
        topBar.setStyle("-fx-background-color:#0f0f1a;-fx-border-color:#1a1a3a;-fx-border-width:0 0 1 0;");
        topBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // ── Canvas ────────────────────────────────────────
        canvas = new Canvas(CANVAS_W, CANVAS_H);
        setupContextMenu();
        setupSelectionHandler();
        StackPane center = new StackPane(canvas);
        center.setStyle("-fx-background-color:#0f0f1a;");

        // ── Bottom bar ────────────────────────────────────
        playPauseBtn = styledBtn("▶  Play", "#2e7d32", "#388e3c");
        Button stepBtn = styledBtn("⏭  Step", "#37474f", "#546e7a");

        playPauseBtn.setOnAction(e -> {
            if (controller.isRunning()) {
                controller.pause();
                playPauseBtn.setText("▶  Play");
                styleBtn(playPauseBtn, "#2e7d32", "#388e3c");
            } else {
                controller.play();
                playPauseBtn.setText("⏸  Pause");
                styleBtn(playPauseBtn, "#6a1b9a", "#7b1fa2");
            }
        });
        stepBtn.setOnAction(e -> {
            controller.step();
            tickLabel.setText("Tick: " + controller.getTickCount());
            drawGraphFromModel();
        });

        selectionLabel = new Label("Sélection : aucune");
        selectionLabel.setStyle("-fx-text-fill:#e0e0ff;-fx-font-size:11px;");

        Label speedLbl = new Label("Vitesse");
        speedLbl.setStyle("-fx-text-fill:#7070a0;-fx-font-size:11px;");
        Slider speedSlider = new Slider(50, 2000, 500);
        speedSlider.setPrefWidth(160);
        speedSlider.setStyle("-fx-control-inner-background:#1a1a3a;");
        speedSlider.valueProperty().addListener((o, ov, nv) ->
            controller.setSpeed((int)(2050 - nv.doubleValue())));

        // Legend
        HBox legend = new HBox(6,
            legendDot(COL_GREEN,   "Faible densité"),
            legendDot(COL_ORANGE,  "Densité moyenne"),
            legendDot(COL_RED,     "Forte densité"),
            legendDot(COL_EXIT,    "Sortie"),
            legendDot(EDGE_COLOR,  "Arête sélectionnable")
        );
        legend.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Region sp2 = new Region();
        HBox.setHgrow(sp2, Priority.ALWAYS);

        HBox bottomBar = new HBox(10,
            playPauseBtn, stepBtn, selectionLabel, sp2,
            speedLbl, speedSlider, legend);
            
        bottomBar.setPadding(new Insets(8, 14, 8, 14));
        bottomBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        bottomBar.setStyle("-fx-background-color:#0f0f1a;-fx-border-color:#1a1a3a;-fx-border-width:1 0 0 0;");

        // ── Root ──────────────────────────────────────────
        BorderPane root = new BorderPane();
        root.setTop(topBar);
        root.setCenter(center);
        root.setBottom(bottomBar);
        root.setStyle("-fx-background-color:#0f0f1a;");

        Scene scene = new Scene(root, CANVAS_W, CANVAS_H + 96);
        scene.setFill(Color.web("#0f0f1a"));
        stage.setTitle("CY SafeCampus — Simulation");
        stage.setScene(scene);
        stage.show();

        drawGraphFromModel();
    }

    // ── Draw ──────────────────────────────────────────────

    public void drawGraphFromModel() {
        if (controller == null || canvas == null) return;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        Graph graph = controller.getGraph();

        // Background
        gc.setFill(BG);
        gc.fillRect(0, 0, CANVAS_W, CANVAS_H);

        // ── Edges / Connections ───────────────────────────
        drawGraphEdges(gc);

        // Draw the selected agent route above the network edges.
        drawSelectedAgentRoute(gc);

        // Nodes — only real rooms and exits are drawn as large nodes.
        // Passages and virtual junctions are drawn as compact density markers below.
        for (BuildingElement el : graph.getElements()) {
            if (el.getName().contains("↔") || el instanceof Passage) continue;

            Point2D pos = getOrCreate(el);
            if (pos == null) continue;

            drawNode(gc, el, pos);
        }

        drawPassageAndJunctionMarkers(gc);

        // Selection details are refreshed after density and movement changes.
        refreshSelectionLabel();

        // ── Agents ────────────────────────────────────────
        for (Agent agent : graph.getAgents()) {
            Point2D pos = agentPos(agent);
            if (pos != null) drawAgent(gc, pos, agent);
        }

        // Tick update
        if (tickLabel != null)
            tickLabel.setText("Tick: " + controller.getTickCount());
    }
    /**
     * Draws every visible graph edge with a color based on passage density.
     * The density is computed from agents currently inside the passage and
     * agents visually moving toward or away from that passage.
     *
     * @param gc canvas graphics context
     */
    private void drawGraphEdges(GraphicsContext gc) {
        Graph graph = controller.getGraph();

        for (Passage passage : graph.getPassages()) {
            Point2D passagePosition = positionForElement(passage);
            if (passagePosition == null) {
                continue;
            }

            for (Door door : passage.getConnectedDoors()) {
                Room room = door.getRoom();
                if (room == null || room.getName().contains("↔")) {
                    continue;
                }

                Point2D roomPosition = positionForElement(room);
                drawDensityEdge(gc, roomPosition, passagePosition, passage);
            }
        }

        for (BuildingElement element : graph.getElements()) {
            if (!(element instanceof Room) || !element.getName().contains("↔")) {
                continue;
            }

            Room junction = (Room) element;
            List<Passage> connectedPassages = connectedPassagesOf(junction);

            if (connectedPassages.size() == 2) {
                Passage first = connectedPassages.get(0);
                Passage second = connectedPassages.get(1);
                Point2D firstPosition = positionForElement(first);
                Point2D secondPosition = positionForElement(second);
                Point2D junctionPosition = positionForElement(junction);

                drawDensityEdge(gc, firstPosition, junctionPosition, first);
                drawDensityEdge(gc, junctionPosition, secondPosition, second);
            }
        }
    }

    /**
     * Draws all passage and virtual junction markers after edges have been drawn.
     * Passages are rendered as compact points so the graph keeps a clear
     * node/edge distinction: rooms are large nodes, passages and junctions are
     * small density markers.
     *
     * @param gc canvas graphics context
     */
    private void drawPassageAndJunctionMarkers(GraphicsContext gc) {
        for (Passage passage : controller.getGraph().getPassages()) {
            drawCompactElementMarker(gc, passage, false);
        }

        for (BuildingElement element : controller.getGraph().getElements()) {
            if (element instanceof Room && element.getName().contains("↔")) {
                drawCompactElementMarker(gc, element, true);
            }
        }
    }

    /**
     * Draws a visual edge using a traffic-light density gradient.
     * Green means low density, orange means medium density and red means the
     * passage is full or over capacity.
     *
     * @param gc canvas graphics context
     * @param from start position
     * @param to end position
     * @param passage passage whose density is displayed by the edge
     */
    private void drawDensityEdge(GraphicsContext gc, Point2D from, Point2D to, Passage passage) {
        if (from == null || to == null || passage == null) {
            return;
        }

        double density = densityRatioWithMovingAgents(passage);
        Color edgeColor = densityColorFromRatio(density);

        gc.setStroke(edgeColor);
        gc.setLineWidth(passage.equals(selectedElement) ? 10.0 : 7.0);
        gc.strokeLine(from.getX(), from.getY(), to.getX(), to.getY());

        // A thin bright overlay keeps very dark green/orange edges visible on the dark theme.
        gc.setStroke(Color.color(1, 1, 1, 0.18));
        gc.setLineWidth(passage.equals(selectedElement) ? 3.0 : 1.5);
        gc.strokeLine(from.getX(), from.getY(), to.getX(), to.getY());

        drawEdgeCapacityLabel(gc, from, to, passage);
    }

    /**
     * Draws the occupancy/capacity label in the middle of a visual edge.
     *
     * @param gc canvas graphics context
     * @param from start position
     * @param to end position
     * @param passage passage whose capacity label is shown
     */
    private void drawEdgeCapacityLabel(GraphicsContext gc, Point2D from, Point2D to, Passage passage) {
        int visualOccupancy = visualOccupancyOf(passage);
        String capacity = visualOccupancy + "/" + passage.getMaxCapacity();
        double midX = (from.getX() + to.getX()) / 2.0;
        double midY = (from.getY() + to.getY()) / 2.0;

        gc.setFill(Color.color(0, 0, 0, 0.55));
        gc.fillRoundRect(midX + 3, midY - 19, capacity.length() * 7.0 + 8, 16, 6, 6);

        gc.setFill(TEXT_LIGHT);
        gc.setFont(Font.font("Sans", FontWeight.BOLD, 10));
        gc.fillText(capacity, midX + 7, midY - 7);
    }

    /**
     * Draws a compact point for a passage or virtual junction.
     *
     * @param gc canvas graphics context
     * @param element graph element to display
     * @param virtualJunction true when the element is a generated passage-to-passage junction
     */
    private void drawCompactElementMarker(GraphicsContext gc, BuildingElement element, boolean virtualJunction) {
        Point2D position = positionForElement(element);
        if (position == null) {
            return;
        }

        double radius = virtualJunction ? 7.0 : 10.0;
        if (element.equals(selectedElement)) {
            radius += 4.0;
        }

        double density = densityRatioWithMovingAgents(element);
        gc.setFill(densityColorFromRatio(density));
        gc.setStroke(element.equals(selectedElement) ? Color.WHITE : TEXT_LIGHT);
        gc.setLineWidth(element.equals(selectedElement) ? 3.0 : 1.5);
        gc.fillOval(position.getX() - radius, position.getY() - radius, radius * 2.0, radius * 2.0);
        gc.strokeOval(position.getX() - radius, position.getY() - radius, radius * 2.0, radius * 2.0);

        String occupancy = visualOccupancyOf(element) + "/" + element.getMaxCapacity();
        gc.setFill(TEXT_LIGHT);
        gc.setFont(Font.font("Sans", FontWeight.BOLD, 8));
        gc.fillText(occupancy, position.getX() + radius + 3.0, position.getY() - radius - 2.0);

        if (!virtualJunction) {
            String name = element.getName();
            if (name.length() > 12) {
                name = name.substring(0, 11) + "…";
            }
            gc.setFill(TEXT_DIM);
            gc.setFont(Font.font("Sans", 8));
            gc.fillText(name, position.getX() - name.length() * 2.2, position.getY() + radius + 11.0);
        }
    }

    /**
     * Returns the passages connected to a virtual junction room.
     *
     * @param junction virtual junction room
     * @return connected passages
     */
    private List<Passage> connectedPassagesOf(Room junction) {
        List<Passage> connectedPassages = new ArrayList<>();
        for (Door door : junction.getDoors()) {
            if (door.getPassage() != null) {
                connectedPassages.add(door.getPassage());
            }
        }
        return connectedPassages;
    }

    /**
     * Returns a color from green to orange to red according to density.
     *
     * @param ratio density ratio between 0 and 1
     * @return density color
     */
    private Color densityColorFromRatio(double ratio) {
        double clampedRatio = Math.max(0.0, Math.min(1.0, ratio));
        if (clampedRatio >= 1.0) {
            return COL_RED;
        }
        if (clampedRatio >= 0.6) {
            double t = (clampedRatio - 0.6) / 0.4;
            return COL_ORANGE.interpolate(COL_RED, t);
        }
        return COL_GREEN.interpolate(COL_ORANGE, clampedRatio / 0.6);
    }

    /**
     * Computes density using both stored occupancy and agents currently moving
     * visually on the element. This makes edge colors update as soon as agents
     * start entering an edge, instead of only after they arrive at the passage node.
     *
     * @param element graph element to inspect
     * @return density ratio clamped between 0 and 1
     */
    private double densityRatioWithMovingAgents(BuildingElement element) {
        if (element == null || element.getMaxCapacity() <= 0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, (double) visualOccupancyOf(element) / element.getMaxCapacity()));
    }

    /**
     * Computes the visual occupancy of an element, including agents in transit.
     *
     * @param element graph element to inspect
     * @return number of agents visually occupying or moving through the element
     */
    private int visualOccupancyOf(BuildingElement element) {
        if (element == null || controller == null || controller.getGraph() == null) {
            return 0;
        }

        int count = 0;
        for (Agent agent : controller.getGraph().getAgents()) {
            BuildingElement current = agent.getCurrentLocation();
            BuildingElement next = agent.getNextInPath();

            if (element.equals(current)) {
                count++;
                continue;
            }

            if (agent.getProgress() > 0.0 && element.equals(next)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns a continuous color from green to orange to red according to density.
     *
     * @param element element whose density must be rendered
     * @return density color
     */
    private Color densityColor(BuildingElement element) {
        return densityColorFromRatio(densityRatioWithMovingAgents(element));
    }

    /**
     * Computes an element density safely, clamped between 0 and 1.
     *
     * @param element element to inspect
     * @return occupancy divided by maximum capacity
     */
    private double densityRatio(BuildingElement element) {
        return densityRatioWithMovingAgents(element);
    }

    /**
     * Highlights the remaining route of the selected agent.
     * @param gc canvas graphics context
     */
    private void drawSelectedAgentRoute(GraphicsContext gc) {
        if (selectedAgent == null) return;

        List<BuildingElement> path = selectedAgent.getPath();
        int index = selectedAgent.getPathIndex();
        if (path == null || path.size() < 2 || index >= path.size() - 1) return;

        gc.setStroke(Color.web("#ffff00"));
        gc.setLineWidth(2.5);
        gc.setLineDashes(8, 6);

        for (int i = Math.max(0, index); i < path.size() - 1; i++) {
            Point2D from = positionForElement(path.get(i));
            Point2D to = positionForElement(path.get(i + 1));
            if (from != null && to != null) {
                gc.strokeLine(from.getX(), from.getY(), to.getX(), to.getY());
            }
        }

        gc.setLineDashes(null);
    }


    private void drawNode(GraphicsContext gc, BuildingElement el, Point2D pos) {
        double x = pos.getX(), y = pos.getY();
        Color fill   = nodeColor(el);
        Color stroke = (el instanceof Exit) ? STROKE_EXIT : STROKE_NORM;

        gc.setFill(fill);
        gc.setStroke(stroke);
        gc.setLineWidth(3);

        if (el.equals(selectedElement)) {
            gc.setLineWidth(6);
        }

        if (el instanceof Exit) {
            // Rounded rect
            gc.fillRoundRect(x - 38, y - 16, 76, 32, 12, 12);
            gc.strokeRoundRect(x - 38, y - 16, 76, 32, 12, 12);
        } else if (el instanceof Passage) {
            // Diamond
            double[] xs = {x, x + 42, x, x - 42};
            double[] ys = {y - 26, y, y + 26, y};
            gc.fillPolygon(xs, ys, 4);
            gc.strokePolygon(xs, ys, 4);
        } else {
            // Circle for rooms
            gc.fillOval(x - NODE_R, y - NODE_R, NODE_R * 2, NODE_R * 2);
            gc.strokeOval(x - NODE_R, y - NODE_R, NODE_R * 2, NODE_R * 2);
        }

        // Name label — truncate long names
        String name = el.getName();
        if (name.length() > 11) name = name.substring(0, 10) + "…";
        gc.setFill(TEXT_LIGHT);
        gc.setFont(Font.font("Sans", FontWeight.BOLD, 11));
        gc.fillText(name, x - name.length() * 3.2, y + 3);

        // Occupancy label below
        String occ = el.getCurrentOccupancy() + "/" + el.getMaxCapacity();
        gc.setFill(TEXT_DIM);
        gc.setFont(Font.font("Sans", 9));
        gc.fillText(occ, x - occ.length() * 2.5, y + (el instanceof Exit ? 26 : NODE_R + 11));
    }

    private void drawAgent(GraphicsContext gc, Point2D pos, Agent agent) {
        double x = pos.getX(), y = pos.getY();
        Color c = agent.getState() == AgentState.PANICKED ? AGENT_PANIC : AGENT_CALM;
        gc.setStroke(c);
        gc.setFill(c);
        gc.setLineWidth(1.8);
        gc.fillOval(x - 4, y - 13, 9, 9);   // head
        gc.strokeLine(x, y - 4, x, y + 6);  // body
        gc.strokeLine(x - 5, y, x + 5, y);  // arms
        gc.strokeLine(x, y + 6, x - 4, y + 13); // left leg
        gc.strokeLine(x, y + 6, x + 4, y + 13); // right leg
    }

    private Point2D agentPos(Agent agent) {
        Point2D base = positionForElement(agent.getCurrentLocation());
        if (base == null) return null;
        BuildingElement next = agent.getNextInPath();
        if (next != null && agent.getProgress() > 0) {
            Point2D np = positionForElement(next);
            if (np != null) {
                double t = agent.getProgress();
                return new Point2D(
                    base.getX() + (np.getX() - base.getX()) * t,
                    base.getY() + (np.getY() - base.getY()) * t);
            }
        }
        // Jitter so agents in same room don't perfectly overlap
        int h = Math.abs(agent.getId().hashCode());
        return new Point2D(base.getX() + (h % 20) - 10, base.getY() + ((h / 20) % 16) - 8);
    }

    private Color nodeColor(BuildingElement el) {
        if (el.isBlocked()) return COL_BLOCKED;
        if (el instanceof Exit && densityRatio(el) < 0.6) return COL_EXIT;
        if (el instanceof Passage && densityRatio(el) < 0.6) return COL_PASSAGE;
        return densityColor(el);
    }

    // ── Status helpers ────────────────────────────────────

    public void showAlert() {
        statusLabel.setText("🔥 ALERTE");
        styleStatus(true);
        drawGraphFromModel();
    }

    public void showNormal() {
        statusLabel.setText("NORMAL");
        styleStatus(false);
        playPauseBtn.setText("▶  Play");
        styleBtn(playPauseBtn, "#2e7d32", "#388e3c");

        nodePositions.clear();
        initDefaultPositions();

        drawGraphFromModel();
    }

    private void styleStatus(boolean alert) {
        String bg = alert ? "#c62828" : "#1b5e20";
        statusLabel.setStyle("-fx-background-color:" + bg + ";-fx-text-fill:white;"
            + "-fx-font-size:13px;-fx-font-weight:bold;"
            + "-fx-padding:4 12;-fx-background-radius:6;");
    }

    // ── Positions ─────────────────────────────────────────

    /** Node positions calibrated to match the CY Tech ground-floor plan. */
    private void initDefaultPositions() {
        put("Réserve", 90, 80);
        put("Bureau 1", 90, 180);
        put("Bureau 2", 90, 280);
        put("Bureau 3", 90, 380);

        put("Palier Esc. 1", 260, 80);
        put("Jonction Nord", 330, 230);
        put("Palier Esc. 2", 470, 230);

        put("LT Serveurs", 600, 110);
        put("Jonction Centrale", 600, 310);

        put("Sortie Est 1", 820, 190);
        put("Sortie Est 2", 820, 310);
        put("Sortie Est 3", 820, 430);

        put("Sortie Ouest", 90, 500);
        put("Amphithéâtre", 300, 500);
        put("Jonction Sud", 500, 500);
        put("Logement", 700, 500);
    }
    private void put(String name, double x, double y) {
        nodePositions.putIfAbsent(name, new Point2D(x, y));
    }

    private Point2D getOrCreate(BuildingElement el) {
        Point2D existing = nodePositions.get(el.getName());
        if (existing != null) {
            return existing;
        }

        if (el.getX() != 0.0 || el.getY() != 0.0) {
            Point2D fromModel = new Point2D(el.getX(), el.getY());
            nodePositions.put(el.getName(), fromModel);
            return fromModel;
        }

        Point2D random = new Point2D(100 + Math.random() * 660, 60 + Math.random() * 400);
        nodePositions.put(el.getName(), random);
        el.setPosition(random.getX(), random.getY());
        return random;
    }


    /**
     * Returns the visual position of any graph element, including virtual
     * junction rooms created to connect two passages.
     * @param element graph element to locate
     * @return canvas position, or null when the element cannot be located
     */
    private Point2D positionForElement(BuildingElement element) {
        if (element == null) return null;

        if (element instanceof Room && element.getName().contains("↔")) {
            Room junction = (Room) element;
            List<Passage> connectedPassages = new ArrayList<>();

            for (Door door : junction.getDoors()) {
                if (door.getPassage() != null) {
                    connectedPassages.add(door.getPassage());
                }
            }

            if (connectedPassages.size() >= 2) {
                Point2D first = getOrCreate(connectedPassages.get(0));
                Point2D second = getOrCreate(connectedPassages.get(1));

                if (first != null && second != null) {
                    return new Point2D(
                        (first.getX() + second.getX()) / 2.0,
                        (first.getY() + second.getY()) / 2.0);
                }
            }
        }

        return getOrCreate(element);
    }


    // ── Selection handling ────────────────────────────────

    /**
     * Handles canvas primary clicks to select an agent, junction, edge or node.
     * The handler is registered on mouse pressed instead of mouse clicked so the
     * context menu can be hidden immediately when the user clicks elsewhere.
     */
    private void setupSelectionHandler() {
        canvas.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            if (graphContextMenu != null && graphContextMenu.isShowing()) {
                graphContextMenu.hide();
            }

            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }

            Point2D click = new Point2D(event.getX(), event.getY());

            selectedAgent = findAgentAt(click);
            selectedElement = selectedAgent == null ? findJunctionAt(click) : null;
            if (selectedAgent == null && selectedElement == null) {
                selectedElement = findPassageMarkerAt(click);
            }
            if (selectedAgent == null && selectedElement == null) {
                selectedElement = findPassageEdgeAt(click);
            }
            if (selectedAgent == null && selectedElement == null) {
                selectedElement = findElementAt(click);
            }

            refreshSelectionLabel();
            drawGraphFromModel();
            event.consume();
        });
    }

    /**
     * Finds an agent close to the clicked position.
     * @param click clicked canvas position
     * @return selected agent or null
     */
    private Agent findAgentAt(Point2D click) {
        for (Agent agent : controller.getGraph().getAgents()) {
            Point2D position = agentPos(agent);
            if (position != null && position.distance(click) <= 16) {
                return agent;
            }
        }
        return null;
    }


    /**
     * Finds a virtual junction marker close to the clicked position.
     *
     * @param click clicked canvas position
     * @return selected junction room or null
     */
    private BuildingElement findJunctionAt(Point2D click) {
        for (BuildingElement element : controller.getGraph().getElements()) {
            if (!(element instanceof Room) || !element.getName().contains("↔")) continue;

            Point2D position = positionForElement(element);
            if (position != null && position.distance(click) <= 14.0) {
                return element;
            }
        }
        return null;
    }

    /**
     * Finds a compact passage marker close to the clicked position.
     *
     * @param click clicked canvas position
     * @return selected passage or null
     */
    private Passage findPassageMarkerAt(Point2D click) {
        for (Passage passage : controller.getGraph().getPassages()) {
            Point2D position = positionForElement(passage);
            if (position != null && position.distance(click) <= 18.0) {
                return passage;
            }
        }
        return null;
    }

    /**
     * Finds a passage represented by a clicked visual edge.
     * @param click clicked canvas position
     * @return selected passage or null
     */
    private Passage findPassageEdgeAt(Point2D click) {
        Graph graph = controller.getGraph();

        for (Passage passage : graph.getPassages()) {
            Point2D passagePosition = positionForElement(passage);
            if (passagePosition == null) continue;

            for (Door door : passage.getConnectedDoors()) {
                Room room = door.getRoom();
                if (room == null || room.getName().contains("↔")) continue;

                Point2D roomPosition = positionForElement(room);
                if (distanceToSegment(click, roomPosition, passagePosition) <= 18.0) {
                    return passage;
                }
            }
        }

        for (BuildingElement element : graph.getElements()) {
            if (!(element instanceof Room) || !element.getName().contains("↔")) continue;

            Room junction = (Room) element;
            List<Passage> connectedPassages = new ArrayList<>();
            for (Door door : junction.getDoors()) {
                if (door.getPassage() != null) connectedPassages.add(door.getPassage());
            }

            if (connectedPassages.size() == 2) {
                Passage first = connectedPassages.get(0);
                Passage second = connectedPassages.get(1);
                Point2D firstPosition = positionForElement(first);
                Point2D secondPosition = positionForElement(second);
                if (distanceToSegment(click, firstPosition, secondPosition) <= 18.0) {
                    return first;
                }
            }
        }

        return null;
    }

    /**
     * Computes the distance from a point to a segment.
     * @param point tested point
     * @param start segment start
     * @param end segment end
     * @return shortest distance to the segment
     */
    private double distanceToSegment(Point2D point, Point2D start, Point2D end) {
        if (point == null || start == null || end == null) return Double.MAX_VALUE;

        double dx = end.getX() - start.getX();
        double dy = end.getY() - start.getY();
        double lengthSquared = dx * dx + dy * dy;

        if (lengthSquared == 0.0) return point.distance(start);

        double t = ((point.getX() - start.getX()) * dx + (point.getY() - start.getY()) * dy)
            / lengthSquared;
        t = Math.max(0.0, Math.min(1.0, t));

        Point2D projection = new Point2D(start.getX() + t * dx, start.getY() + t * dy);
        return point.distance(projection);
    }

    /**
     * Finds a visible element close to the clicked position.
     * @param click clicked canvas position
     * @return selected element or null
     */
    private BuildingElement findElementAt(Point2D click) {
        for (BuildingElement element : controller.getGraph().getElements()) {
            if (element.getName().contains("↔")) continue;
            Point2D position = getOrCreate(element);
            if (position != null && position.distance(click) <= 45) {
                return element;
            }
        }
        return null;
    }

    /** Refreshes the selection statistics text shown in the bottom bar. */
    private void refreshSelectionLabel() {
        if (selectionLabel == null) return;

        if (selectedAgent != null) {
            int remaining = 0;
            List<BuildingElement> path = selectedAgent.getPath();
            if (path != null) {
                remaining = Math.max(0, path.size() - selectedAgent.getPathIndex() - 1);
            }
            selectionLabel.setText(
                "Agent: " + selectedAgent.getName()
                + " | état=" + selectedAgent.getState()
                + " | vitesse=" + String.format("%.2f", selectedAgent.getMaxSpeed())
                + " | étapes restantes=" + remaining);
            return;
        }

        if (selectedElement != null) {
            int visualOccupancy = visualOccupancyOf(selectedElement);
            double density = selectedElement.getMaxCapacity() > 0
                ? (double) visualOccupancy / selectedElement.getMaxCapacity()
                : 0.0;
            String elementType = selectedElement instanceof Passage
                ? "Arête"
                : selectedElement.getName().contains("↔") ? "Jonction" : "Nœud";
            selectionLabel.setText(
                elementType + ": " + selectedElement.getName()
                + " | agents visibles=" + visualOccupancy
                + " | capacité maximale=" + selectedElement.getMaxCapacity()
                + " | occupation=" + visualOccupancy + "/" + selectedElement.getMaxCapacity()
                + " | agents passés=" + selectedElement.getTotalAgentsPassed()
                + " | vitesse moyenne=" + String.format("%.2f", selectedElement.getAverageSpeed())
                + " | densité=" + String.format("%.0f%%", density * 100));
            return;
        }

        selectionLabel.setText("Sélection : aucune");
    }

    // ── Context menu ──────────────────────────────────────

    private void setupContextMenu() {
        graphContextMenu = new ContextMenu();
        graphContextMenu.setAutoHide(true);
        ContextMenu menu = graphContextMenu;
        MenuItem addNode    = new MenuItem("Ajouter un nœud");
        MenuItem editNode   = new MenuItem("Modifier un nœud");
        MenuItem removeNode = new MenuItem("Supprimer un nœud");
        MenuItem moveNode   = new MenuItem("Déplacer un nœud");
        MenuItem addEdge    = new MenuItem("Ajouter une arête");
        MenuItem editEdgeCapacity = new MenuItem("Modifier capacité arête");
        MenuItem removeEdge = new MenuItem("Supprimer une arête");
        MenuItem addRandN   = new MenuItem("Ajouter X nœuds aléatoires");
        MenuItem addAgent   = new MenuItem("Ajouter un agent");
        MenuItem editAgent  = new MenuItem("Modifier un agent");
        MenuItem rmAgent    = new MenuItem("Supprimer un agent");
        MenuItem rndAgents  = new MenuItem("Ajouter X agents aléatoires");

        menu.getItems().addAll(
            addNode, editNode, removeNode, moveNode,
            new SeparatorMenuItem(), addEdge, editEdgeCapacity, removeEdge,
            new SeparatorMenuItem(), addRandN,
            new SeparatorMenuItem(), addAgent, editAgent, rmAgent, rndAgents);

        addNode.setOnAction(e    -> handleAddNode());
        editNode.setOnAction(e   -> handleEditNode());
        removeNode.setOnAction(e -> handleRemoveNode());
        moveNode.setOnAction(e   -> handleMoveNode());
        addEdge.setOnAction(e    -> handleAddEdge());
        editEdgeCapacity.setOnAction(e -> handleEditEdgeCapacity());
        removeEdge.setOnAction(e -> handleRemoveEdge());
        addRandN.setOnAction(e   -> handleAddRandomNodes());
        addAgent.setOnAction(e   -> handleAddAgent());
        editAgent.setOnAction(e  -> handleEditAgent());
        rmAgent.setOnAction(e    -> handleRemoveAgent());
        rndAgents.setOnAction(e  -> handleAddRandomAgents());

        canvas.setOnContextMenuRequested(e -> {
            if (graphContextMenu.isShowing()) {
                graphContextMenu.hide();
            }
            graphContextMenu.show(canvas, e.getScreenX(), e.getScreenY());
            e.consume();
        });
    }

    // ── Save / Load ───────────────────────────────────────

    private void handleSave() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Sauvegarder");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Simulation (*.bin)", "*.bin"));
        File f = fc.showSaveDialog(stage);
        if (f != null) controller.saveSimulation(f.getAbsolutePath());
    }

    private void handleLoad() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Charger");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Simulation (*.bin)", "*.bin"));
        File f = fc.showOpenDialog(stage);
        if (f != null) {
            controller.loadSimulation(f.getAbsolutePath());
            initDefaultPositions();
            drawGraphFromModel();
        }
    }

    // ── Node dialogs ──────────────────────────────────────

    private void handleAddNode() {
        TextField nameF = new TextField(), xF = new TextField("300"), yF = new TextField("250");
        ComboBox<String> typeB = new ComboBox<>();
        typeB.getItems().addAll("Salle", "Sortie", "Couloir");
        typeB.getSelectionModel().selectFirst();
        GridPane g = grid("Nom:", nameF, "Type:", typeB, "X:", xF, "Y:", yF);
        dialog("Ajouter un nœud", g, () -> {
            String name = nameF.getText().trim();
            if (name.isEmpty()) { showErr("Nom vide"); return; }
            try {
                double x = Double.parseDouble(xF.getText());
                double y = Double.parseDouble(yF.getText());
                controller.addNode(name, typeB.getValue(), x, y);
                nodePositions.put(name, new Point2D(x, y));
                drawGraphFromModel();
            } catch (Exception ex) { showErr("Coordonnées invalides"); }
        });
    }

    private void handleEditNode() {
        ComboBox<String> nodeB = nodeBox();
        TextField nameF = new TextField(), capF = new TextField();
        GridPane g = grid("Nœud:", nodeB, "Nouveau nom:", nameF, "Capacité:", capF);
        dialog("Modifier un nœud", g, () -> {
            String old = nodeB.getValue(), newN = nameF.getText().trim();
            if (old == null || newN.isEmpty()) { showErr("Invalide"); return; }
            try {
                int cap = Integer.parseInt(capF.getText().trim());
                Point2D pos = nodePositions.remove(old);
                controller.updateNode(old, newN, cap);
                if (pos != null) nodePositions.put(newN, pos);
                drawGraphFromModel();
            } catch (Exception ex) { showErr("Capacité invalide"); }
        });
    }

    private void handleRemoveNode() {
        ComboBox<String> nodeB = nodeBox();
        dialog("Supprimer un nœud", grid("Nœud:", nodeB), () -> {
            if (nodeB.getValue() != null) {
                controller.removeNode(nodeB.getValue());
                nodePositions.remove(nodeB.getValue());
                drawGraphFromModel();
            }
        });
    }

    private void handleMoveNode() {
        ComboBox<String> nodeB = new ComboBox<>();
        nodePositions.keySet().stream()
            .filter(k -> !k.contains("↔"))
            .forEach(nodeB.getItems()::add);
        nodeB.getSelectionModel().selectFirst();
        TextField xF = new TextField(), yF = new TextField();
        dialog("Déplacer un nœud", grid("Nœud:", nodeB, "X:", xF, "Y:", yF), () -> {
            try {
                double x = Double.parseDouble(xF.getText());
                double y = Double.parseDouble(yF.getText());
                nodePositions.put(nodeB.getValue(), new Point2D(x, y));
                controller.updateNodePosition(nodeB.getValue(), x, y);
                drawGraphFromModel();
            } catch (Exception ex) { showErr("Coordonnées invalides"); }
        });
    }

    private void handleAddEdge() {
        ComboBox<String> roomB = new ComboBox<>(), passB = new ComboBox<>();
        controller.getGraph().getElements().forEach(el -> {
            if (el instanceof Room && !el.getName().contains("↔"))
                roomB.getItems().add(el.getName());
            if (el instanceof Passage && !el.getName().contains("↔"))
                passB.getItems().add(el.getName());
        });
        roomB.getSelectionModel().selectFirst();
        passB.getSelectionModel().selectFirst();
        dialog("Ajouter une arête", grid("Salle:", roomB, "Couloir:", passB), () -> {
            controller.addEdge(roomB.getValue(), passB.getValue());
            drawGraphFromModel();
        });
    }


    private void handleEditEdgeCapacity() {
        ComboBox<String> edgeB = new ComboBox<>();
        controller.getGraph().getPassages().stream()
            .filter(p -> !p.getName().contains("↔"))
            .forEach(p -> edgeB.getItems().add(p.getName()));
        edgeB.getSelectionModel().selectFirst();

        TextField capacityF = new TextField("10");
        dialog("Modifier capacité arête", grid("Arête:", edgeB, "Capacité max:", capacityF), () -> {
            try {
                if (edgeB.getValue() == null) {
                    showErr("Arête invalide");
                    return;
                }

                int capacity = Integer.parseInt(capacityF.getText().trim());
                controller.updateEdgeCapacity(edgeB.getValue(), capacity);
                drawGraphFromModel();
            } catch (Exception ex) {
                showErr("Capacité invalide");
            }
        });
    }

    private void handleRemoveEdge() {
        ComboBox<String> edgeB = new ComboBox<>();
        controller.getGraph().getPassages().forEach(p ->
            p.getConnectedDoors().stream()
                .filter(d -> !d.getRoom().getName().contains("↔"))
                .forEach(d -> edgeB.getItems().add(
                    d.getRoom().getName() + " → " + p.getName())));
        edgeB.getSelectionModel().selectFirst();
        dialog("Supprimer une arête", grid("Arête:", edgeB), () -> {
            if (edgeB.getValue() != null) {
                String[] p = edgeB.getValue().split(" → ");
                controller.removeEdge(p[0], p[1]);
                drawGraphFromModel();
            }
        });
    }

    private void handleAddRandomNodes() {
        TextField countF = new TextField();
        dialog("Nœuds aléatoires", grid("Nombre:", countF), () -> {
            try {
                controller.addRandomNodes(Integer.parseInt(countF.getText().trim()));
                controller.getGraph().getElements().forEach(el -> {
                    if (!nodePositions.containsKey(el.getName()))
                        nodePositions.put(el.getName(),
                            new Point2D(80 + Math.random() * 700, 50 + Math.random() * 420));
                });
                drawGraphFromModel();
            } catch (Exception ex) { showErr("Nombre invalide"); }
        });
    }

    // ── Agent dialogs ─────────────────────────────────────

    private void handleAddAgent() {
        TextField nameF = new TextField(), speedF = new TextField("1.0"), tolF = new TextField("0.7");
        ComboBox<String> locB = nodeBox();
        ComboBox<Behavior> behB = new ComboBox<>();
        behB.getItems().addAll(Behavior.values()); behB.getSelectionModel().selectFirst();
        dialog("Ajouter un agent",
            grid("Nom:", nameF, "Position:", locB, "Vitesse:", speedF,
                 "Tolérance:", tolF, "Comportement:", behB), () -> {
            try {
                controller.addPersonAgent(nameF.getText().trim(), locB.getValue(),
                    Double.parseDouble(speedF.getText()), behB.getValue(),
                    Double.parseDouble(tolF.getText()));
                drawGraphFromModel();
            } catch (Exception ex) { showErr("Valeurs invalides"); }
        });
    }

    private void handleEditAgent() {
        ComboBox<String> agB = agentBox();
        TextField nameF = new TextField(), speedF = new TextField(), tolF = new TextField();
        ComboBox<String> locB = nodeBox();
        ComboBox<Behavior> behB = new ComboBox<>();
        behB.getItems().addAll(Behavior.values()); behB.getSelectionModel().selectFirst();
        dialog("Modifier un agent",
            grid("Agent:", agB, "Nom:", nameF, "Position:", locB,
                 "Vitesse:", speedF, "Tolérance:", tolF, "Comportement:", behB), () -> {
            try {
                controller.updateAgent(agB.getValue(), nameF.getText().trim(),
                    locB.getValue(), Double.parseDouble(speedF.getText()),
                    behB.getValue(), Double.parseDouble(tolF.getText()));
                drawGraphFromModel();
            } catch (Exception ex) { showErr("Valeurs invalides"); }
        });
    }

    private void handleRemoveAgent() {
        ComboBox<String> agB = agentBox();
        dialog("Supprimer un agent", grid("Agent:", agB), () -> {
            if (agB.getValue() != null) {
                controller.removeAgent(agB.getValue());
                drawGraphFromModel();
            }
        });
    }

    private void handleAddRandomAgents() {
        TextField countF = new TextField(), minSF = new TextField("0.5"),
            maxSF = new TextField("1.5"), minTF = new TextField("0.3"), maxTF = new TextField("1.0");
        dialog("Agents aléatoires",
            grid("Nombre:", countF, "Vitesse min:", minSF, "Vitesse max:", maxSF,
                 "Tolérance min:", minTF, "Tolérance max:", maxTF), () -> {
            try {
                controller.addRandomAgents(Integer.parseInt(countF.getText()),
                    Double.parseDouble(minSF.getText()), Double.parseDouble(maxSF.getText()),
                    Double.parseDouble(minTF.getText()), Double.parseDouble(maxTF.getText()));
                drawGraphFromModel();
            } catch (Exception ex) { showErr("Valeurs invalides"); }
        });
    }

    // ── UI Helpers ────────────────────────────────────────

    private Button styledBtn(String text, String bg, String hover) {
        Button b = new Button(text);
        styleBtn(b, bg, hover);
        return b;
    }

    private void styleBtn(Button b, String bg, String hover) {
        b.setStyle("-fx-background-color:" + bg + ";-fx-text-fill:white;"
            + "-fx-font-size:12px;-fx-padding:5 12;-fx-background-radius:6;"
            + "-fx-cursor:hand;");
        b.setOnMouseEntered(e -> b.setStyle(b.getStyle()
            .replace(bg, hover)));
        b.setOnMouseExited(e -> styleBtn(b, bg, hover));
    }

    private Label legendDot(Color c, String label) {
        Label l = new Label("● " + label);
        String hex = String.format("#%02x%02x%02x",
            (int)(c.getRed()*255), (int)(c.getGreen()*255), (int)(c.getBlue()*255));
        l.setStyle("-fx-text-fill:" + hex + ";-fx-font-size:11px;");
        return l;
    }

    private void dialog(String title, GridPane content, Runnable onOk) {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle(title);
        d.getDialogPane().setContent(content);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        d.showAndWait().ifPresent(btn -> { if (btn == ButtonType.OK) onOk.run(); });
    }

    private GridPane grid(Object... pairs) {
        GridPane g = new GridPane();
        g.setHgap(10); g.setVgap(8);
        g.setPadding(new Insets(10));
        for (int i = 0; i < pairs.length; i += 2) {
            g.add(new Label(pairs[i].toString()), 0, i / 2);
            g.add((javafx.scene.Node) pairs[i + 1], 1, i / 2);
        }
        return g;
    }

    private ComboBox<String> nodeBox() {
        ComboBox<String> b = new ComboBox<>();
        controller.getGraph().getElements().stream()
            .filter(el -> !el.getName().contains("↔"))
            .forEach(el -> b.getItems().add(el.getName()));
        b.getSelectionModel().selectFirst();
        return b;
    }

    private ComboBox<String> agentBox() {
        ComboBox<String> b = new ComboBox<>();
        controller.getGraph().getAgents()
            .forEach(a -> b.getItems().add(a.getName()));
        b.getSelectionModel().selectFirst();
        return b;
    }

    private void showErr(String msg) {
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }
}
