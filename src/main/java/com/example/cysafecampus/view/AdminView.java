package com.example.cysafecampus.view;

import com.example.cysafecampus.controller.GraphController;
import com.example.cysafecampus.model.*;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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
import javafx.util.Duration;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin view — full campus map with:
 * - Click on node → statistics panel
 * - Click on agent → highlighted remaining path
 * - Real-time sensor log
 * - Play/Pause/Step controls
 */
public class AdminView {

    private static final int CW = 720, CH = 480;
    private static final double NR = 34;

    private final Stage stage;
    private final GraphController controller;
    private Canvas canvas;
    private Label statusLbl;
    private Button playPauseBtn;
    private TextArea logArea;
    private Timeline uiRefresh;

    private BuildingElement draggedNode = null;
    private double dragOffsetX = 0;
    private double dragOffsetY = 0;
        
    // Selection state
    private BuildingElement selectedNode = null;
    private Agent selectedAgent = null;

    // Stats panel labels
    private Label statNameLbl, statOccLbl, statPassedLbl, statSpeedLbl, statStatusLbl;
    private VBox statsPanel;

    private final Map<String, javafx.geometry.Point2D> pos = new HashMap<>();
    /** true = fastest path (time+congestion), false = shortest path (distance) */
    private boolean useFastestPath = false;

    public AdminView(Stage stage, GraphController controller) {
        this.stage = stage;
        this.controller = controller;
        initPositions();
    }

    public void show() {
        // ── Top bar ───────────────────────────────────────
        Label role = new Label("🛡 Administrateur");
        role.setFont(Font.font("Sans", FontWeight.BOLD, 14));
        role.setTextFill(Color.web("#1a237e"));

        statusLbl = new Label("NORMAL");
        statusLbl.setStyle("-fx-background-color:#2e7d32;-fx-text-fill:white;" +
            "-fx-padding:3 10;-fx-background-radius:5;-fx-font-weight:bold;");

        Button backBtn = btn("← Retour", "#546e7a");
        backBtn.setOnAction(e -> goBack());
        Button saveBtn = btn("💾 Save", "#1565c0");
        Button loadBtn = btn("📂 Load", "#1565c0");
        saveBtn.setOnAction(e -> handleSave());
        loadBtn.setOnAction(e -> handleLoad());

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        HBox top = new HBox(10, backBtn, role, statusLbl, sp, saveBtn, loadBtn);
        top.setPadding(new Insets(8, 12, 8, 12));
        top.setAlignment(Pos.CENTER_LEFT);
        top.setBackground(bg("#f8f9fa"));
        top.setStyle("-fx-border-color:#e8eaed;-fx-border-width:0 0 1 0;");

        // ── Canvas ────────────────────────────────────────
        canvas = new Canvas(CW, CH);
        setupMouseHandlers();
        setupContextMenu();

        StackPane canvasPane = new StackPane(canvas);
        canvasPane.setBackground(bg("#0f172a"));
        canvasPane.setMinSize(CW, CH);
        canvasPane.setPrefSize(CW, CH);
        canvasPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        // ── Right panel ───────────────────────────────────
        VBox rightPanel = buildRightPanel();
        rightPanel.setPrefWidth(250);

        // ── Bottom status ─────────────────────────────────
        Label tickLbl = new Label("Tick: 0");
        tickLbl.setStyle("-fx-font-size:11px;-fx-text-fill:#546e7a;");
        Label agentCountLbl = new Label();
        agentCountLbl.setStyle("-fx-font-size:11px;-fx-text-fill:#546e7a;");
        Label clickHint = new Label("Clic sur nœud = stats · Clic sur agent = trajet");
        clickHint.setStyle("-fx-font-size:10px;-fx-text-fill:#bdbdbd;-fx-font-style:italic;");

        HBox statusBar = new HBox(20, tickLbl, agentCountLbl, clickHint);
        statusBar.setPadding(new Insets(4, 12, 4, 12));
        statusBar.setBackground(bg("#f8f9fa"));
        statusBar.setStyle("-fx-border-color:#e8eaed;-fx-border-width:1 0 0 0;");

        // ── Layout ────────────────────────────────────────
        HBox center = new HBox(canvasPane, rightPanel);
        HBox.setHgrow(canvasPane, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setCenter(center);
        root.setBottom(statusBar);
        root.setBackground(bg("white"));

        Scene scene = new Scene(root, CW + 250, CH + 72);
        scene.setFill(Color.WHITE);
        stage.setTitle("CY SafeCampus — Administration");
        stage.setScene(scene);
        stage.show();

        // Wire sensor callback
        controller.setSensorEventCallback(event -> javafx.application.Platform.runLater(() -> {
            String icon = event.getSeverity() >= 4 ? "🔥" : event.getSeverity() >= 3 ? "⚠" : "ℹ";
            log(icon + " [" + event.getType() + "] " + event.getLocation().getName()
                + " — sévérité " + event.getSeverity());
        }));

        // Auto-refresh
        uiRefresh = new Timeline(new KeyFrame(Duration.millis(150), e -> {
            draw();
            tickLbl.setText("Tick: " + controller.getTickCount());
            agentCountLbl.setText("Agents: " + controller.getGraph().getAgents().size());
            updateStatsPanel();
        }));
        uiRefresh.setCycleCount(Timeline.INDEFINITE);
        uiRefresh.play();

        log("Système démarré");
        draw();
    }

    // ── Right Panel ───────────────────────────────────────

    private VBox buildRightPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(12));
        panel.setBackground(bg("#f8f9fa"));
        panel.setStyle("-fx-border-color:#e8eaed;-fx-border-width:0 0 0 1;");

        // Simulation controls
        Label simTitle = sectionLbl("Simulation");
        playPauseBtn = btn("▶ Play", "#2e7d32");
        Button stepBtn = btn("⏭ Step", "#546e7a");
        Slider speedSlider = new Slider(50, 2000, 500);
        speedSlider.setPrefWidth(210);
        speedSlider.valueProperty().addListener((o, ov, nv) ->
            controller.setSpeed((int)(2050 - nv.doubleValue())));
        playPauseBtn.setOnAction(e -> {
            if (controller.isRunning()) {
                controller.pause(); playPauseBtn.setText("▶ Play");
            } else {
                controller.play(); playPauseBtn.setText("⏸ Pause");
            }
        });
        stepBtn.setOnAction(e -> { controller.step(); draw(); });
        HBox simBtns = new HBox(8, playPauseBtn, stepBtn);
        javafx.scene.control.ToggleButton pathToggle =
            new javafx.scene.control.ToggleButton("Chemin: Plus court");
        pathToggle.setStyle("-fx-font-size:10px;-fx-padding:3 8;-fx-pref-width:210;");
        pathToggle.setOnAction(e -> {
            useFastestPath = pathToggle.isSelected();
            pathToggle.setText(useFastestPath ? "Chemin: Plus rapide" : "Chemin: Plus court");
            // Recompute all paths with new strategy
            controller.getGraph().getAgents().forEach(a -> a.setPath(new java.util.ArrayList<>()));
        });

        // Alert
        Label alertTitle = sectionLbl("Alertes");
        Button fireBtn = btn("🔥 Déclencher alarme", "#c62828");
        Button resetBtn = btn("↺ Reset", "#37474f");
        fireBtn.setMaxWidth(Double.MAX_VALUE);
        resetBtn.setMaxWidth(Double.MAX_VALUE);
        fireBtn.setOnAction(e -> {
            controller.triggerFireAlert();
            statusLbl.setText("🔥 ALERTE");
            statusLbl.setStyle("-fx-background-color:#c62828;-fx-text-fill:white;" +
                "-fx-padding:3 10;-fx-background-radius:5;-fx-font-weight:bold;");
            log("ALERTE INCENDIE déclenchée");
        });
        resetBtn.setOnAction(e -> {
            controller.reset();
            statusLbl.setText("NORMAL");
            statusLbl.setStyle("-fx-background-color:#2e7d32;-fx-text-fill:white;" +
                "-fx-padding:3 10;-fx-background-radius:5;-fx-font-weight:bold;");
            playPauseBtn.setText("▶ Play");
            selectedAgent = null; selectedNode = null;
            log("Simulation réinitialisée");
        });

        // Agents
        Label agentTitle = sectionLbl("Agents");
        Button addAgentBtn  = btn("+ Ajouter agent",     "#1565c0");
        Button rmAgentBtn   = btn("- Supprimer agent",   "#546e7a");
        Button rndAgentsBtn = btn("⚡ X agents aléat.",  "#6a1b9a");
        addAgentBtn.setMaxWidth(Double.MAX_VALUE);
        rmAgentBtn.setMaxWidth(Double.MAX_VALUE);
        rndAgentsBtn.setMaxWidth(Double.MAX_VALUE);
        Button editAgentBtn = btn("✏ Modifier agent", "#0277bd");
        editAgentBtn.setMaxWidth(Double.MAX_VALUE);
        editAgentBtn.setOnAction(e -> handleEditAgent());
        addAgentBtn.setOnAction(e  -> handleAddAgent());
        rmAgentBtn.setOnAction(e   -> handleRemoveAgent());
        rndAgentsBtn.setOnAction(e -> handleRandomAgents());

        // Stats panel (shown on node/agent click)
        Label statsTitle = sectionLbl("Sélection");
        statNameLbl   = new Label("—");
        statNameLbl.setFont(Font.font("Sans", FontWeight.BOLD, 12));
        statOccLbl    = infoLbl("Occupation: —");
        statPassedLbl = infoLbl("Agents passés: —");
        statSpeedLbl  = infoLbl("Vitesse moy.: —");
        statStatusLbl = infoLbl("Statut: —");
        statsPanel = new VBox(4, statNameLbl, statOccLbl,
            statPassedLbl, statSpeedLbl, statStatusLbl);
        statsPanel.setPadding(new Insets(8));
        statsPanel.setStyle("-fx-background-color:white;-fx-border-color:#e0e0e0;" +
            "-fx-border-radius:6;-fx-background-radius:6;");

        // Log
        Label logTitle = sectionLbl("Journal capteurs");
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefRowCount(5);
        logArea.setStyle("-fx-font-size:10px;-fx-font-family:monospace;");

        // Legend
        Label legTitle = sectionLbl("Légende");
        VBox legend = new VBox(3,
            legendRow(Color.web("#1b5e20"), "Vide (< 40%)"),
            legendRow(Color.web("#e65100"), "Dense (40-80%)"),
            legendRow(Color.web("#b71c1c"), "Saturé (> 80%)"),
            legendRow(Color.web("#0d47a1"), "Sortie"),
            legendRow(Color.web("#283593"), "Couloir"),
            legendRow(Color.web("#1565c0"), "Agent calme"),
            legendRow(Color.web("#f44336"), "Agent paniqué")
        );

        panel.getChildren().addAll(
            simTitle, simBtns, new Label("Vitesse:"), speedSlider, pathToggle,
            new Separator(), alertTitle, fireBtn, resetBtn,
            new Separator(), agentTitle, addAgentBtn, editAgentBtn, rmAgentBtn, rndAgentsBtn,
            new Separator(), statsTitle, statsPanel,
            new Separator(), logTitle, logArea,
            new Separator(), legTitle, legend
        );

        ScrollPane scroll = new ScrollPane(panel);
        scroll.setFitToWidth(true);
        scroll.setBackground(bg("#f8f9fa"));
        scroll.setPrefWidth(250);

        VBox wrapper = new VBox(scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return wrapper;
    }

    // ── Draw ──────────────────────────────────────────────

    private void draw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        Graph graph = controller.getGraph();

        gc.clearRect(0, 0, CW, CH);
        gc.setFill(Color.web("#0f172a"));
        gc.fillRect(0, 0, CW, CH);

        // ── Edges ─────────────────────────────────────────
        drawEdgesFromModel(gc);
        // ── Selected agent path highlight ─────────────────
        if (selectedAgent != null && selectedAgent.getPath() != null) {
            List<BuildingElement> path = selectedAgent.getPath();

            gc.setStroke(Color.web("#ff9800"));
            gc.setLineWidth(3.5);
            gc.setLineDashes(8, 4);

            for (int i = selectedAgent.getPathIndex(); i < path.size() - 1; i++) {
                javafx.geometry.Point2D a = pos.get(path.get(i).getName());
                javafx.geometry.Point2D b = pos.get(path.get(i + 1).getName());

                if (a != null && b != null) {
                    gc.strokeLine(a.getX(), a.getY(), b.getX(), b.getY());
                }
            }

            gc.setLineDashes(0);
        }

        // ── Nodes ─────────────────────────────────────────
        for (BuildingElement el : graph.getElements()) {
            if (el.getName().contains("↔")) continue;

            javafx.geometry.Point2D p = pos.get(el.getName());
            if (p == null) continue;

            boolean selected = el.equals(selectedNode);
            drawNode(gc, el, p.getX(), p.getY(), selected);
        }

        // ── Agents ────────────────────────────────────────
        for (Agent a : graph.getAgents()) {
            javafx.geometry.Point2D p = agentPos(a);

            if (p != null) {
                drawAgent(gc, p.getX(), p.getY(), a, a.equals(selectedAgent));
            }
        }
    }
    private void drawEdgesFromModel(GraphicsContext gc) {
        gc.setStroke(Color.web("#455a64"));
        gc.setLineWidth(3.0);

        for (Passage passage : controller.getGraph().getPassages()) {
            for (Door door : passage.getConnectedDoors()) {
                Room room = door.getRoom();

                if (room == null) continue;

                javafx.geometry.Point2D passagePos = getPositionForElement(passage);
                javafx.geometry.Point2D roomPos = getPositionForElement(room);

                if (passagePos == null || roomPos == null) continue;

                gc.strokeLine(
                    passagePos.getX(),
                    passagePos.getY(),
                    roomPos.getX(),
                    roomPos.getY()
                );
            }
        }
    }

    private javafx.geometry.Point2D getPositionForElement(BuildingElement el) {
        if (el.getName().contains("↔")) {
            return getJunctionPosition(el);
        }

        javafx.geometry.Point2D p = pos.get(el.getName());

        if (p != null) {
            return p;
        }

        if (el.getX() != 0.0 || el.getY() != 0.0) {
            p = new javafx.geometry.Point2D(el.getX(), el.getY());
            pos.put(el.getName(), p);
            return p;
        }

        return null;
    }

    private javafx.geometry.Point2D getJunctionPosition(BuildingElement junction) {
        String name = junction.getName();

        if (!name.contains("↔")) {
            return pos.get(name);
        }

        String[] parts = name.split("↔");

        if (parts.length != 2) {
            return null;
        }

        javafx.geometry.Point2D a = pos.get(parts[0]);
        javafx.geometry.Point2D b = pos.get(parts[1]);

        if (a == null || b == null) {
            return null;
        }

        return new javafx.geometry.Point2D(
            (a.getX() + b.getX()) / 2.0,
            (a.getY() + b.getY()) / 2.0
        );
    }

    private void drawDefaultEdges(GraphicsContext gc) {
        gc.setStroke(Color.web("#455a64"));
        gc.setLineWidth(3.0);

        drawEdge(gc, "Réserve", "Palier Esc. 1");
        drawEdge(gc, "Palier Esc. 1", "Jonction Nord");

        drawEdge(gc, "Bureau 1", "Jonction Nord");
        drawEdge(gc, "Bureau 2", "Jonction Nord");

        drawEdge(gc, "Jonction Nord", "Palier Esc. 2");
        drawEdge(gc, "Palier Esc. 2", "Jonction Centrale");

        drawEdge(gc, "LT Serveurs", "Jonction Centrale");
        drawEdge(gc, "Bureau 3", "Jonction Centrale");

        drawEdge(gc, "Jonction Centrale", "Sortie Est 1");
        drawEdge(gc, "Jonction Centrale", "Sortie Est 2");
        drawEdge(gc, "Jonction Centrale", "Sortie Est 3");

        drawEdge(gc, "Amphithéâtre", "Jonction Sud");
        drawEdge(gc, "Logement", "Jonction Sud");
        drawEdge(gc, "Jonction Sud", "Jonction Centrale");
        drawEdge(gc, "Sortie Ouest", "Jonction Sud");
    }

    private void drawEdge(GraphicsContext gc, String from, String to) {
        javafx.geometry.Point2D a = pos.get(from);
        javafx.geometry.Point2D b = pos.get(to);

        if (a == null || b == null) {
            System.out.println("Missing edge position: " + from + " -> " + to);
            return;
        }

        gc.strokeLine(a.getX(), a.getY(), b.getX(), b.getY());
    }

    private void drawNode(GraphicsContext gc, BuildingElement el, double x, double y, boolean selected) {
        Color fill = nodeColor(el);
        Color stroke = selected ? Color.web("#ff9800") : Color.web("#9e9e9e");
        double strokeW = selected ? 3.0 : 1.2;

        gc.setFill(fill);
        gc.setStroke(stroke);
        gc.setLineWidth(strokeW);

        if (el instanceof Exit) {
            gc.fillRoundRect(x - 46, y - 16, 92, 32, 10, 10);
            gc.strokeRoundRect(x - 46, y - 16, 92, 32, 10, 10);
        } else if (el instanceof Passage) {
        double[] xs = {x, x + 42, x, x - 42};
        double[] ys = {y - 26, y, y + 26, y};
        gc.fillPolygon(xs, ys, 4);
        gc.strokePolygon(xs, ys, 4);
        } else {
            gc.fillOval(x - NR, y - NR, NR * 2, NR * 2);
            gc.strokeOval(x - NR, y - NR, NR * 2, NR * 2);
        }

        String name = shortName(el.getName());

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Sans", FontWeight.BOLD, 10));

        if (el instanceof Passage && name.contains(" ")) {
            String[] parts = name.split(" ", 2);
            gc.fillText(parts[0], x - parts[0].length() * 2.8, y - 3);
            gc.fillText(parts[1], x - parts[1].length() * 2.8, y + 10);
        } else {
            gc.fillText(name, x - name.length() * 2.8, y + 3);
        }
        String occ = el.getCurrentOccupancy() + "/" + el.getMaxCapacity();

        gc.setFill(Color.web("#eeeeee"));
        gc.setFont(Font.font("Sans", 8));
        gc.fillText(
            occ,
            x - occ.length() * 2.2,
            y + (el instanceof Exit ? 24 : NR + 11)
        );
    }

    private String shortName(String name) {
        return switch (name) {
            case "Jonction Centrale" -> "J. Centrale";
            case "Jonction Nord" -> "J. Nord";
            case "Jonction Sud" -> "J. Sud";
            case "Palier Esc. 1" -> "P. Esc. 1";
            case "Palier Esc. 2" -> "P. Esc. 2";
            default -> name.length() > 14 ? name.substring(0, 13) + "…" : name;
        };
    }

    private void drawAgent(GraphicsContext gc, double x, double y, Agent a, boolean selected) {
        Color c = a.getState() == AgentState.PANICKED
            ? Color.web("#f44336")
            : Color.web("#1565c0");

        if (selected) {
            gc.setFill(Color.web("#ff980066"));
            gc.fillOval(x - 14, y - 18, 28, 28);
        }

        gc.setStroke(c);
        gc.setFill(c);
        gc.setLineWidth(1.8);

        gc.fillOval(x - 4, y - 13, 8, 8);
        gc.strokeLine(x, y - 5, x, y + 6);
        gc.strokeLine(x - 5, y, x + 5, y);
        gc.strokeLine(x, y + 6, x - 4, y + 13);
        gc.strokeLine(x, y + 6, x + 4, y + 13);
    }

    // ── Mouse handlers ────────────────────────────────────

    private void setupMouseHandlers() {

        canvas.setOnMousePressed(e -> {
            double mx = e.getX();
            double my = e.getY();

            draggedNode = null;

            for (BuildingElement el : controller.getGraph().getElements()) {
                if (el.getName().contains("↔")) continue;

                javafx.geometry.Point2D p = pos.get(el.getName());
                if (p == null) continue;

                double dist = Math.hypot(p.getX() - mx, p.getY() - my);

                if (dist < NR + 8) {
                    draggedNode = el;
                    selectedNode = el;
                    selectedAgent = null;

                    dragOffsetX = mx - p.getX();
                    dragOffsetY = my - p.getY();

                    draw();
                    break;
                }
            }
        });

        canvas.setOnMouseDragged(e -> {
            if (draggedNode == null) return;

            double newX = e.getX() - dragOffsetX;
            double newY = e.getY() - dragOffsetY;

            newX = Math.max(NR, Math.min(CW - NR, newX));
            newY = Math.max(NR, Math.min(CH - NR, newY));

            pos.put(draggedNode.getName(), new javafx.geometry.Point2D(newX, newY));
            draggedNode.setPosition(newX, newY);

            draw();
        });

        canvas.setOnMouseReleased(e -> {
            if (draggedNode != null) {
                draggedNode.setPosition(
                    pos.get(draggedNode.getName()).getX(),
                    pos.get(draggedNode.getName()).getY()
                );
            }

            draggedNode = null;
        });

        canvas.setOnMouseClicked(e -> {
            if (draggedNode != null) return;

            double mx = e.getX();
            double my = e.getY();

            Agent clickedAgent = null;

            for (Agent a : controller.getGraph().getAgents()) {
                javafx.geometry.Point2D p = agentPos(a);

                if (p != null && Math.hypot(p.getX() - mx, p.getY() - my) < 14) {
                    clickedAgent = a;
                    break;
                }
            }

            if (clickedAgent != null) {
                selectedAgent = (selectedAgent == clickedAgent) ? null : clickedAgent;
                selectedNode = null;
                draw();
                return;
            }

            BuildingElement clickedNode = null;

            for (BuildingElement el : controller.getGraph().getElements()) {
                if (el.getName().contains("↔")) continue;

                javafx.geometry.Point2D p = pos.get(el.getName());
                if (p == null) continue;

                double dist = Math.hypot(p.getX() - mx, p.getY() - my);

                if (dist < NR + 8) {
                    clickedNode = el;
                    break;
                }
            }

            selectedNode = (selectedNode == clickedNode) ? null : clickedNode;
            selectedAgent = null;
            draw();
        });
    }

    private void updateStatsPanel() {
        if (selectedNode != null) {
            statNameLbl.setText(selectedNode.getName());
            statOccLbl.setText("Occupation: " + selectedNode.getCurrentOccupancy()
                + " / " + selectedNode.getMaxCapacity());
            statPassedLbl.setText("Agents passés: " + selectedNode.getTotalAgentsPassed());
            statSpeedLbl.setText(String.format("Vitesse moy.: %.2f", selectedNode.getAverageSpeed()));
            statStatusLbl.setText("Statut: " + selectedNode.getStatus());
        } else if (selectedAgent != null) {
            statNameLbl.setText(selectedAgent.getName());
            statOccLbl.setText("État: " + selectedAgent.getState());
            int remaining = selectedAgent.getPath() == null ? 0
                : Math.max(0, selectedAgent.getPath().size() - selectedAgent.getPathIndex() - 1);
            statPassedLbl.setText("Étapes restantes: " + remaining);
            statSpeedLbl.setText(String.format("Vitesse: %.1f", selectedAgent.getMaxSpeed()));
            statStatusLbl.setText("Comportement: " + selectedAgent.getBehavior());
        } else {
            statNameLbl.setText("—");
            statOccLbl.setText("Occupation: —");
            statPassedLbl.setText("Agents passés: —");
            statSpeedLbl.setText("Vitesse moy.: —");
            statStatusLbl.setText("Statut: —");
        }
    }

    // ── Agent position ────────────────────────────────────

    private javafx.geometry.Point2D agentPos(Agent a) {
        if (a.getCurrentLocation() == null) return null;
        javafx.geometry.Point2D base = pos.get(a.getCurrentLocation().getName());
        if (base == null) return null;
        BuildingElement next = a.getNextInPath();
        if (next != null && a.getProgress() > 0) {
            javafx.geometry.Point2D np = pos.get(next.getName());
            if (np != null) {
                double t = a.getProgress();
                return new javafx.geometry.Point2D(
                    base.getX() + (np.getX() - base.getX()) * t,
                    base.getY() + (np.getY() - base.getY()) * t);
            }
        }
        int h = Math.abs(a.getId().hashCode());
        return new javafx.geometry.Point2D(base.getX() + (h % 18) - 9,
            base.getY() + ((h / 18) % 14) - 7);
    }

    private Color nodeColor(BuildingElement el) {
        if (el.isBlocked()) return Color.web("#424242");
        if (el instanceof Exit) return Color.web("#0d47a1");
        if (el instanceof Passage) return Color.web("#283593");
        double r = el.getMaxCapacity() > 0
            ? (double) el.getCurrentOccupancy() / el.getMaxCapacity() : 0;
        if (r < 0.4) return Color.web("#1b5e20");
        if (r < 0.8) return Color.web("#e65100");
        return Color.web("#b71c1c");
    }

    // ── Context menu ──────────────────────────────────────

    private void setupContextMenu() {
        ContextMenu menu = new ContextMenu();
        MenuItem addN = new MenuItem("Ajouter un nœud");
        MenuItem rmN  = new MenuItem("Supprimer un nœud");
        MenuItem editN = new MenuItem("Modifier un nœud");
        MenuItem moveN = new MenuItem("Déplacer un nœud");
        MenuItem addE = new MenuItem("Ajouter une arête");
        MenuItem editE = new MenuItem("Modifier une arête");
        MenuItem rmE  = new MenuItem("Supprimer une arête");
        MenuItem rndN = new MenuItem("X nœuds aléatoires");
        menu.getItems().addAll(addN, editN, rmN, moveN,
            new SeparatorMenuItem(), addE, editE, rmE, new SeparatorMenuItem(), rndN);
        addN.setOnAction(e -> handleAddNode());
        editN.setOnAction(e -> handleEditNode());
        rmN.setOnAction(e  -> handleRemoveNode());
        moveN.setOnAction(e -> handleMoveNode());
        addE.setOnAction(e -> handleAddEdge());
        editE.setOnAction(e -> handleEditEdge());
        rmE.setOnAction(e  -> handleRemoveEdge());
        rndN.setOnAction(e -> handleAddRandomNodes());
        canvas.setOnContextMenuRequested(e ->
            menu.show(canvas, e.getScreenX(), e.getScreenY()));
    }

    // ── Handlers ──────────────────────────────────────────

    private void handleAddNode() {
        double[] freePos = generateFreeNodePosition();

        TextField nameF = new TextField();
        TextField xF = new TextField(String.valueOf((int) freePos[0]));
        TextField yF = new TextField(String.valueOf((int) freePos[1]));
        ComboBox<String> typeB = new ComboBox<>();
        typeB.getItems().addAll("Bureau", "Amphi", "Salle", "Sortie"); typeB.getSelectionModel().selectFirst();
        dialog("Ajouter un nœud", grid("Nom:", nameF, "Type:", typeB, "X:", xF, "Y:", yF), () -> {
            try {
                String n = nameF.getText().trim();
                if (n.isEmpty()) { showErr("Nom vide"); return; }
                double x = Double.parseDouble(xF.getText()), y = Double.parseDouble(yF.getText());
                if (isTooCloseToExistingViewNode(x, y)) {
                    showErr("Impossible de créer ce nœud : il est trop proche d’un autre nœud.");
                    return;
                }
                controller.addNode(n, typeB.getValue(), x, y);
                pos.put(n, new javafx.geometry.Point2D(x, y));
                draw();
            } catch (Exception ex) { showErr("Invalide"); }
        });
    }

    private void handleEditNode() {
        ComboBox<String> nb = nodeCombo();
        TextField nameF = new TextField(), capF = new TextField();
        dialog("Modifier un nœud", grid("Nœud:", nb, "Nouveau nom:", nameF, "Capacité:", capF), () -> {
            try {
                String old = nb.getValue(), newN = nameF.getText().trim();
                if (old == null || newN.isEmpty()) { showErr("Invalide"); return; }
                int cap = Integer.parseInt(capF.getText());
                javafx.geometry.Point2D p = pos.remove(old);
                controller.updateNode(old, newN, cap);
                if (p != null) pos.put(newN, p);
                draw();
            } catch (Exception ex) { showErr("Invalide"); }
        });
    }

    private void handleRemoveNode() {
        ComboBox<String> nb = nodeCombo();
        dialog("Supprimer un nœud", grid("Nœud:", nb), () -> {
            if (nb.getValue() != null) {
                controller.removeNode(nb.getValue());
                pos.remove(nb.getValue());
                selectedNode = null; draw();
            }
        });
    }

    private void handleMoveNode() {
        ComboBox<String> nb = new ComboBox<>();
        pos.keySet().stream().filter(k -> !k.contains("↔")).forEach(nb.getItems()::add);
        nb.getSelectionModel().selectFirst();
        TextField xF = new TextField(), yF = new TextField();
        dialog("Déplacer un nœud", grid("Nœud:", nb, "X:", xF, "Y:", yF), () -> {
            try {
                pos.put(nb.getValue(),
                    new javafx.geometry.Point2D(Double.parseDouble(xF.getText()),
                        Double.parseDouble(yF.getText())));
                draw();
            } catch (Exception ex) { showErr("Coordonnées invalides"); }
        });
    }

    private void handleEditEdge() {
        // Edit edge = modify the passage properties (distance, speedFactor, lanes)
        ComboBox<String> passB = new ComboBox<>();
        controller.getGraph().getPassages().stream()
            .filter(p -> !p.getName().contains("↔"))
            .forEach(p -> passB.getItems().add(p.getName()));
        passB.getSelectionModel().selectFirst();
        TextField distF = new TextField(), speedF = new TextField(), lanesF = new TextField();

        passB.setOnAction(e -> {
            controller.getGraph().getPassages().stream()
                .filter(p -> p.getName().equals(passB.getValue()))
                .findFirst().ifPresent(p -> {
                    distF.setText(String.valueOf(p.getDistance()));
                    speedF.setText(String.valueOf(p.getSpeedFactor()));
                    lanesF.setText(String.valueOf(p.getLanes()));
                });
        });
        if (!passB.getItems().isEmpty()) passB.fireEvent(new javafx.event.ActionEvent());

        dialog("Modifier une arête (passage)",
            grid("Passage:", passB, "Distance:", distF,
                "Facteur vitesse:", speedF, "Couloirs (lanes):", lanesF), () -> {
            try {
                controller.getGraph().getPassages().stream()
                    .filter(p -> p.getName().equals(passB.getValue()))
                    .findFirst().ifPresent(p -> {
                        p.setDistance(Double.parseDouble(distF.getText()));
                        p.setSpeedFactor(Double.parseDouble(speedF.getText()));
                        p.setLanes(Integer.parseInt(lanesF.getText()));
                    });
                log("Arête modifiée: " + passB.getValue());
                draw();
            } catch (Exception ex) { showErr("Valeurs invalides"); }
        });
    }

    private void handleAddEdge() {
        ComboBox<String> spaceB = new ComboBox<>();
        ComboBox<String> passageB = new ComboBox<>();

        controller.getGraph().getElements().forEach(el -> {
            if (el.getName().contains("↔")) return;

            if (el instanceof Passage) {
                passageB.getItems().add(el.getName());
            } else {
                spaceB.getItems().add(el.getName());
            }
        });

        spaceB.getSelectionModel().selectFirst();
        passageB.getSelectionModel().selectFirst();

        dialog("Ajouter une arête",
            grid("Espace / sortie:", spaceB, "Jonction / palier:", passageB), () -> {
                if (spaceB.getValue() == null || passageB.getValue() == null) {
                    showErr("Sélection invalide");
                    return;
                }

                controller.addConnection(spaceB.getValue(), passageB.getValue());
                draw();
            });
    }
    private void handleRemoveEdge() {
        ComboBox<String> edgeB = new ComboBox<>();

        for (Passage passage : controller.getGraph().getPassages()) {
            if (passage.getName().contains("↔")) continue;

            for (Door door : passage.getConnectedDoors()) {
                Room room = door.getRoom();

                if (room == null) continue;
                if (room.getName().contains("↔")) continue;

                String label = room.getName() + " → " + passage.getName();

                if (!edgeB.getItems().contains(label)) {
                    edgeB.getItems().add(label);
                }
            }
        }

        edgeB.getSelectionModel().selectFirst();

        dialog("Supprimer une arête", grid("Arête:", edgeB), () -> {
            if (edgeB.getValue() == null) {
                showErr("Aucune arête sélectionnée");
                return;
            }

            String[] parts = edgeB.getValue().split(" → ");

            if (parts.length != 2) {
                showErr("Arête invalide");
                return;
            }

            controller.removeEdge(parts[0], parts[1]);
            draw();
        });
    }

    private void handleAddRandomNodes() {
        TextField countF = new TextField("5");

        dialog("Nœuds aléatoires", grid("Nombre:", countF), () -> {
            try {
                controller.addRandomNodes(Integer.parseInt(countF.getText().trim()));

                controller.getGraph().getElements().forEach(el -> {
                    if (!el.getName().contains("↔")
                            && !pos.containsKey(el.getName())
                            && (el.getX() != 0.0 || el.getY() != 0.0)) {

                        pos.put(el.getName(),
                            new javafx.geometry.Point2D(el.getX(), el.getY()));
                    }
                });

                draw();
            } catch (Exception ex) {
                showErr("Nombre invalide");
            }
        });
    }

    private void handleEditAgent() {
        ComboBox<String> agB = agentCombo();
        TextField nameF = new TextField(), speedF = new TextField(), tolF = new TextField();
        ComboBox<String> locB = nodeCombo();
        ComboBox<Behavior> behB = new ComboBox<>();
        behB.getItems().addAll(Behavior.values()); behB.getSelectionModel().selectFirst();

        // Pre-fill fields when agent selected
        agB.setOnAction(e -> {
            controller.getGraph().getAgents().stream()
                .filter(a -> a.getName().equals(agB.getValue()))
                .findFirst().ifPresent(a -> {
                    nameF.setText(a.getName());
                    speedF.setText(String.valueOf(a.getMaxSpeed()));
                    tolF.setText(String.valueOf(a.getDensityTolerance()));
                    locB.setValue(a.getCurrentLocation().getName());
                    behB.setValue(a.getBehavior());
                });
        });
        // Trigger pre-fill for first item
        if (!agB.getItems().isEmpty()) agB.fireEvent(
            new javafx.event.ActionEvent());

        dialog("Modifier un agent",
            grid("Agent:", agB, "Nouveau nom:", nameF, "Position:", locB,
                "Vitesse:", speedF, "Tolérance:", tolF, "Comportement:", behB), () -> {
            try {
                controller.updateAgent(agB.getValue(), nameF.getText().trim(),
                    locB.getValue(), Double.parseDouble(speedF.getText()),
                    behB.getValue(), Double.parseDouble(tolF.getText()));
                log("Agent modifié: " + nameF.getText().trim());
                draw();
            } catch (Exception ex) { showErr("Valeurs invalides"); }
        });
    }

    private void handleAddAgent() {
        TextField nameF = new TextField(), speedF = new TextField("1.0"), tolF = new TextField("0.7");
        ComboBox<String> locB = nodeCombo();
        ComboBox<Behavior> behB = new ComboBox<>();
        behB.getItems().addAll(Behavior.values()); behB.getSelectionModel().selectFirst();
        dialog("Ajouter un agent",
            grid("Nom:", nameF, "Position:", locB, "Vitesse:", speedF,
                "Tolérance:", tolF, "Comportement:", behB), () -> {
            try {
                controller.addPersonAgent(nameF.getText().trim(), locB.getValue(),
                    Double.parseDouble(speedF.getText()), behB.getValue(),
                    Double.parseDouble(tolF.getText()));
                log("Agent ajouté: " + nameF.getText().trim());
            } catch (Exception ex) { showErr("Valeurs invalides"); }
        });
    }

    private void handleRemoveAgent() {
        ComboBox<String> agB = agentCombo();
        dialog("Supprimer un agent", grid("Agent:", agB), () -> {
            if (agB.getValue() != null) {
                controller.removeAgent(agB.getValue());
                if (selectedAgent != null && selectedAgent.getName().equals(agB.getValue()))
                    selectedAgent = null;
                log("Agent supprimé: " + agB.getValue());
            }
        });
    }

    private void handleRandomAgents() {
        TextField countF = new TextField("10"), minSF = new TextField("0.5"),
            maxSF = new TextField("1.5"), minTF = new TextField("0.3"), maxTF = new TextField("1.0");
        dialog("Agents aléatoires",
            grid("Nombre:", countF, "Vitesse min:", minSF, "Vitesse max:", maxSF,
                "Tolérance min:", minTF, "Tolérance max:", maxTF), () -> {
            try {
                int n = Integer.parseInt(countF.getText());
                controller.addRandomAgents(n,
                    Double.parseDouble(minSF.getText()), Double.parseDouble(maxSF.getText()),
                    Double.parseDouble(minTF.getText()), Double.parseDouble(maxTF.getText()));
                log(n + " agents aléatoires ajoutés");
            } catch (Exception ex) { showErr("Valeurs invalides"); }
        });
    }

    private void handleSave() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("*.bin", "*.bin"));
        File f = fc.showSaveDialog(stage);
        if (f != null) { controller.saveSimulation(f.getAbsolutePath()); log("Sauvegardé"); }
    }

    private void handleLoad() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("*.bin", "*.bin"));
        File f = fc.showOpenDialog(stage);
        if (f != null) { controller.loadSimulation(f.getAbsolutePath()); log("Chargé"); draw(); }
    }

    private void goBack() {
        if (uiRefresh != null) uiRefresh.stop();
        controller.pause();
        new LoginView(stage, controller).show();
    }

    private double[] generateFreeNodePosition() {
        double x = 0;
        double y = 0;

        for (int attempts = 0; attempts < 300; attempts++) {
            x = 100 + Math.random() * 620;
            y = 80 + Math.random() * 360;

            if (!isTooCloseToExistingViewNode(x, y)) {
                return new double[]{x, y};
            }
        }

        int count = pos.size();
        x = 120 + (count * 90) % 620;
        y = 90 + ((count * 90) / 620) * 90;

        return new double[]{x, y};
    }

    private boolean isTooCloseToExistingViewNode(double x, double y) {
        for (javafx.geometry.Point2D p : pos.values()) {
            double dx = p.getX() - x;
            double dy = p.getY() - y;

            if (Math.sqrt(dx * dx + dy * dy) < 110) {
                return true;
            }
        }

        return false;
    }

    // ── UI helpers ────────────────────────────────────────

    private void log(String msg) {
        if (logArea != null) {
            logArea.appendText(msg + "\n");
            logArea.setScrollTop(Double.MAX_VALUE);
        }
    }

    private void initPositions() {
        put("Réserve", 110, 80);
        put("Bureau 1", 110, 175);
        put("Bureau 2", 110, 270);
        put("Bureau 3", 110, 365);

        put("Palier Esc. 1", 250, 80);
        put("Jonction Nord", 360, 170);
        put("Palier Esc. 2", 420, 245);

        put("LT Serveurs", 510, 100);
        put("Jonction Centrale", 510, 340);

        put("Sortie Ouest", 80, 440);
        put("Amphithéâtre", 230, 440);
        put("Jonction Sud", 410, 440);
        put("Logement", 570, 440);

        put("Sortie Est 1", 660, 220);
        put("Sortie Est 2", 660, 340);
        put("Sortie Est 3", 660, 430);
    }

    private void put(String n, double x, double y) {
        pos.putIfAbsent(n, new javafx.geometry.Point2D(x, y));
    }

    private Button btn(String text, String color) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color:" + color + ";-fx-text-fill:white;" +
            "-fx-font-size:11px;-fx-padding:5 10;-fx-background-radius:5;-fx-cursor:hand;");
        return b;
    }

    private Label sectionLbl(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Sans", FontWeight.BOLD, 12));
        l.setTextFill(Color.web("#1a237e"));
        return l;
    }

    private Label infoLbl(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size:11px;-fx-text-fill:#546e7a;");
        return l;
    }

    private HBox legendRow(Color c, String text) {
        Label dot = new Label("●"); dot.setTextFill(c);
        Label lbl = new Label(text); lbl.setStyle("-fx-font-size:10px;");
        return new HBox(6, dot, lbl);
    }

    private Background bg(String hex) {
        return new Background(new BackgroundFill(Color.web(hex),
            CornerRadii.EMPTY, Insets.EMPTY));
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
        controller.getGraph().getAgents().forEach(a -> b.getItems().add(a.getName()));
        b.getSelectionModel().selectFirst();
        return b;
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
        g.setHgap(10); g.setVgap(8); g.setPadding(new Insets(10));
        for (int i = 0; i < pairs.length; i += 2) {
            g.add(new Label(pairs[i].toString()), 0, i / 2);
            g.add((javafx.scene.Node) pairs[i + 1], 1, i / 2);
        }
        return g;
    }

    private void showErr(String msg) {
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }
}
