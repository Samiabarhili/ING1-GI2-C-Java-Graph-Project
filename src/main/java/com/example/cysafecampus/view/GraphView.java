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
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.HashMap;
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
    private static final Color EDGE_COLOR  = Color.web("#2a2a4a");
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
    private static final Color AGENT_CALM  = Color.web("#69f0ae");
    private static final Color AGENT_PANIC = Color.web("#ff5252");

    private final Stage stage;
    private Canvas canvas;
    private GraphController controller;
    private Label statusLabel;
    private Label tickLabel;
    private Button playPauseBtn;

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

        Label speedLbl = new Label("Vitesse");
        speedLbl.setStyle("-fx-text-fill:#7070a0;-fx-font-size:11px;");
        Slider speedSlider = new Slider(50, 2000, 500);
        speedSlider.setPrefWidth(160);
        speedSlider.setStyle("-fx-control-inner-background:#1a1a3a;");
        speedSlider.valueProperty().addListener((o, ov, nv) ->
            controller.setSpeed((int)(2050 - nv.doubleValue())));

        // Legend
        HBox legend = new HBox(6,
            legendDot(COL_GREEN,   "Vide"),
            legendDot(COL_ORANGE,  "Dense"),
            legendDot(COL_RED,     "Saturé"),
            legendDot(COL_EXIT,    "Sortie"),
            legendDot(COL_PASSAGE, "Couloir"));
        legend.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Region sp2 = new Region(); HBox.setHgrow(sp2, Priority.ALWAYS);

        HBox bottomBar = new HBox(10,
            playPauseBtn, stepBtn, sp2,
            speedLbl, speedSlider, sp2, legend);
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

        // ── Edges ─────────────────────────────────────────
        // Draw passage→room connections, skip junction nodes (↔)
        gc.setLineWidth(1.5);
        for (Passage passage : graph.getPassages()) {
            if (passage.getName().contains("↔")) continue;
            Point2D pp = nodePositions.get(passage.getName());
            if (pp == null) continue;

            for (Door door : passage.getConnectedDoors()) {
                Room room = door.getRoom();
                if (room.getName().contains("↔")) continue;
                Point2D rp = nodePositions.get(room.getName());
                if (rp == null) continue;

                // Gradient-like: dimmer lines
                double density = passage.getMaxCapacity() > 0
                    ? (double) passage.getCurrentOccupancy() / passage.getMaxCapacity() : 0;
                gc.setStroke(density > 0.5
                    ? Color.web("#4a2020") : EDGE_COLOR);
                gc.strokeLine(pp.getX(), pp.getY(), rp.getX(), rp.getY());
            }
        }

        // ── Nodes ─────────────────────────────────────────
        for (BuildingElement el : graph.getElements()) {
            if (el.getName().contains("↔")) continue;
            Point2D pos = nodePositions.get(el.getName());
            if (pos == null) continue;
            drawNode(gc, el, pos);
        }

        // ── Agents ────────────────────────────────────────
        for (Agent agent : graph.getAgents()) {
            Point2D pos = agentPos(agent);
            if (pos != null) drawAgent(gc, pos, agent);
        }

        // Tick update
        if (tickLabel != null)
            tickLabel.setText("Tick: " + controller.getTickCount());
    }

    private void drawNode(GraphicsContext gc, BuildingElement el, Point2D pos) {
        double x = pos.getX(), y = pos.getY();
        Color fill   = nodeColor(el);
        Color stroke = (el instanceof Exit) ? STROKE_EXIT : STROKE_NORM;

        gc.setFill(fill);
        gc.setStroke(stroke);
        gc.setLineWidth(1.5);

        if (el instanceof Exit) {
            // Rounded rect
            gc.fillRoundRect(x - 38, y - 16, 76, 32, 12, 12);
            gc.strokeRoundRect(x - 38, y - 16, 76, 32, 12, 12);
        } else if (el instanceof Passage) {
            // Diamond
            double[] xs = {x, x + 30, x, x - 30};
            double[] ys = {y - 18, y, y + 18, y};
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
        Point2D base = nodePositions.get(agent.getCurrentLocation().getName());
        if (base == null) return null;
        BuildingElement next = agent.getNextInPath();
        if (next != null && agent.getProgress() > 0) {
            Point2D np = nodePositions.get(next.getName());
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
        if (el instanceof Exit) return COL_EXIT;
        if (el instanceof Passage) return COL_PASSAGE;
        if (el.getMaxCapacity() == 0) return COL_GREEN;
        double r = (double) el.getCurrentOccupancy() / el.getMaxCapacity();
        if (r < 0.4) return COL_GREEN;
        if (r < 0.8) return COL_ORANGE;
        return COL_RED;
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
        drawGraphFromModel();
    }

    private void styleStatus(boolean alert) {
        String bg = alert ? "#c62828" : "#1b5e20";
        statusLabel.setStyle("-fx-background-color:" + bg + ";-fx-text-fill:white;"
            + "-fx-font-size:13px;-fx-font-weight:bold;"
            + "-fx-padding:4 12;-fx-background-radius:6;");
    }

    // ── Positions ─────────────────────────────────────────

    /** Node positions calibrated to match the CY Tech Rez-de-chaussée floor plan. */
    private void initDefaultPositions() {
        // Left column — offices
        put("Réserve",       90,  70);
        put("Bureau 1",      90, 170);
        put("Bureau 2",      90, 270);
        put("Bureau 3",      90, 360);
        // Top center
        put("Escalier 1",   230,  70);
        put("Couloir Nord", 330, 155);
        put("LT Serveurs",  480, 100);
        put("Escalier 2",   400, 230);
        // Center — hall
        put("Hall Central", 480, 320);
        // Bottom row
        put("Sortie Ouest",  70, 460);
        put("Amphithéâtre", 210, 460);
        put("Couloir Sud",  390, 460);
        put("Logement",     560, 460);
        // Right — exits
        put("Sortie Est 1", 760, 220);
        put("Sortie Est 2", 760, 320);
        put("Sortie Est 3", 760, 420);
    }

    private void put(String name, double x, double y) {
        nodePositions.putIfAbsent(name, new Point2D(x, y));
    }

    private Point2D getOrCreate(BuildingElement el) {
        return nodePositions.computeIfAbsent(el.getName(),
            k -> new Point2D(100 + Math.random() * 660, 60 + Math.random() * 400));
    }

    // ── Context menu ──────────────────────────────────────

    private void setupContextMenu() {
        ContextMenu menu = new ContextMenu();
        MenuItem addNode    = new MenuItem("Ajouter un nœud");
        MenuItem editNode   = new MenuItem("Modifier un nœud");
        MenuItem removeNode = new MenuItem("Supprimer un nœud");
        MenuItem moveNode   = new MenuItem("Déplacer un nœud");
        MenuItem addEdge    = new MenuItem("Ajouter une arête");
        MenuItem removeEdge = new MenuItem("Supprimer une arête");
        MenuItem addRandN   = new MenuItem("Ajouter X nœuds aléatoires");
        MenuItem addAgent   = new MenuItem("Ajouter un agent");
        MenuItem editAgent  = new MenuItem("Modifier un agent");
        MenuItem rmAgent    = new MenuItem("Supprimer un agent");
        MenuItem rndAgents  = new MenuItem("Ajouter X agents aléatoires");

        menu.getItems().addAll(
            addNode, editNode, removeNode, moveNode,
            new SeparatorMenuItem(), addEdge, removeEdge,
            new SeparatorMenuItem(), addRandN,
            new SeparatorMenuItem(), addAgent, editAgent, rmAgent, rndAgents);

        addNode.setOnAction(e    -> handleAddNode());
        editNode.setOnAction(e   -> handleEditNode());
        removeNode.setOnAction(e -> handleRemoveNode());
        moveNode.setOnAction(e   -> handleMoveNode());
        addEdge.setOnAction(e    -> handleAddEdge());
        removeEdge.setOnAction(e -> handleRemoveEdge());
        addRandN.setOnAction(e   -> handleAddRandomNodes());
        addAgent.setOnAction(e   -> handleAddAgent());
        editAgent.setOnAction(e  -> handleEditAgent());
        rmAgent.setOnAction(e    -> handleRemoveAgent());
        rndAgents.setOnAction(e  -> handleAddRandomAgents());

        canvas.setOnContextMenuRequested(e ->
            menu.show(canvas, e.getScreenX(), e.getScreenY()));
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
                nodePositions.put(nodeB.getValue(),
                    new Point2D(Double.parseDouble(xF.getText()),
                                Double.parseDouble(yF.getText())));
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
