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
import java.util.Map;

/**
 * Admin view — full campus map, sensor alerts, evacuation controls, agent management.
 */
public class AdminView {

    private static final int CW = 700, CH = 460;
    private static final double NR = 28;

    private final Stage stage;
    private final GraphController controller;
    private Canvas canvas;
    private Label statusLbl;
    private Button playPauseBtn;
    private TextArea logArea;
    private Timeline uiRefresh;

    private final Map<String, javafx.geometry.Point2D> pos = new HashMap<>();

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
        top.setStyle("-fx-background-color:#e8eaf6;-fx-border-color:#c5cae9;-fx-border-width:0 0 1 0;");

        // ── Canvas (campus map) ───────────────────────────
        canvas = new Canvas(CW, CH);
        setupContextMenu();

        // ── Right panel ───────────────────────────────────
        VBox rightPanel = new VBox(10);
        rightPanel.setPadding(new Insets(10));
        rightPanel.setPrefWidth(240);
        rightPanel.setStyle("-fx-background-color:#f5f5f5;-fx-border-color:#e0e0e0;-fx-border-width:0 0 0 1;");

        // Simulation controls
        Label ctrlTitle = sectionTitle("Simulation");
        playPauseBtn = btn("▶ Play", "#2e7d32");
        Button stepBtn = btn("⏭ Step", "#546e7a");
        Slider speedSlider = new Slider(50, 2000, 500);
        speedSlider.setPrefWidth(200);
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

        // Alert controls
        Label alertTitle = sectionTitle("Alertes");
        Button fireBtn = btn("🔥 Déclencher alarme", "#c62828");
        Button resetBtn = btn("↺ Reset", "#37474f");
        fireBtn.setStyle(fireBtn.getStyle() + "-fx-pref-width:200;");
        resetBtn.setStyle(resetBtn.getStyle() + "-fx-pref-width:200;");
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
            log("Simulation réinitialisée");
        });

        // Agent management
        Label agentTitle = sectionTitle("Agents");
        Button addAgentBtn  = btn("+ Ajouter agent",    "#1565c0");
        Button rmAgentBtn   = btn("- Supprimer agent",  "#546e7a");
        Button rndAgentsBtn = btn("⚡ X agents aléat.", "#6a1b9a");
        addAgentBtn.setStyle(addAgentBtn.getStyle()   + "-fx-pref-width:200;");
        rmAgentBtn.setStyle(rmAgentBtn.getStyle()     + "-fx-pref-width:200;");
        rndAgentsBtn.setStyle(rndAgentsBtn.getStyle() + "-fx-pref-width:200;");
        addAgentBtn.setOnAction(e  -> handleAddAgent());
        rmAgentBtn.setOnAction(e   -> handleRemoveAgent());
        rndAgentsBtn.setOnAction(e -> handleRandomAgents());

        // Event log
        Label logTitle = sectionTitle("Journal");
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefRowCount(6);
        logArea.setStyle("-fx-font-size:10px;-fx-font-family:monospace;");
        log("Système démarré");

        // Wire sensor event callback — logs real-time alerts
        controller.setSensorEventCallback(event -> {
            javafx.application.Platform.runLater(() -> {
                String severity = "⚠";
                if (event.getSeverity() >= 4) severity = "🔥";
                else if (event.getSeverity() >= 3) severity = "⚠";
                else severity = "ℹ";
                log(severity + " [" + event.getType() + "] "
                    + event.getLocation().getName()
                    + " — sévérité " + event.getSeverity());
            });
        });

        // Legend
        Label legTitle = sectionTitle("Légende");
        VBox legend = new VBox(4,
            legendRow(Color.web("#1b5e20"), "Vide (< 40%)"),
            legendRow(Color.web("#e65100"), "Dense (40-80%)"),
            legendRow(Color.web("#b71c1c"), "Saturé (> 80%)"),
            legendRow(Color.web("#0d47a1"), "Sortie"),
            legendRow(Color.web("#283593"), "Couloir/Passage")
        );

        rightPanel.getChildren().addAll(
            ctrlTitle, simBtns, new Label("Vitesse:"), speedSlider,
            new Separator(), alertTitle, fireBtn, resetBtn,
            new Separator(), agentTitle, addAgentBtn, rmAgentBtn, rndAgentsBtn,
            new Separator(), logTitle, logArea,
            new Separator(), legTitle, legend
        );

        // ── Bottom status bar ─────────────────────────────
        Label tickLbl = new Label("Tick: 0");
        tickLbl.setStyle("-fx-font-size:11px;-fx-text-fill:#546e7a;");
        Label agentCountLbl = new Label();
        agentCountLbl.setStyle("-fx-font-size:11px;-fx-text-fill:#546e7a;");

        HBox statusBar = new HBox(20, tickLbl, agentCountLbl);
        statusBar.setPadding(new Insets(4, 12, 4, 12));
        statusBar.setStyle("-fx-background-color:#e8eaf6;-fx-border-color:#c5cae9;-fx-border-width:1 0 0 0;");

        // ── Layout ────────────────────────────────────────
        HBox center = new HBox(canvas, rightPanel);
        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setCenter(center);
        root.setBottom(statusBar);
        root.setStyle("-fx-background-color:white;");

        Scene scene = new Scene(root, CW + 240, CH + 80);
        stage.setTitle("CY SafeCampus — Administration");
        stage.setScene(scene);
        stage.show();

        // UI auto-refresh every 200ms
        uiRefresh = new Timeline(new KeyFrame(Duration.millis(200), e -> {
            draw();
            tickLbl.setText("Tick: " + controller.getTickCount());
            agentCountLbl.setText("Agents: " + controller.getGraph().getAgents().size());
        }));
        uiRefresh.setCycleCount(Timeline.INDEFINITE);
        uiRefresh.play();

        draw();
    }

    // ── Draw ──────────────────────────────────────────────

    private void draw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        Graph graph = controller.getGraph();

        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, CW, CH);

        // Edges
        gc.setLineWidth(1.5);
        for (Passage p : graph.getPassages()) {
            if (p.getName().contains("↔")) continue;
            javafx.geometry.Point2D pp = pos.get(p.getName());
            if (pp == null) continue;
            for (Door d : p.getConnectedDoors()) {
                if (d.getRoom().getName().contains("↔")) continue;
                javafx.geometry.Point2D rp = pos.get(d.getRoom().getName());
                if (rp == null) continue;
                gc.setStroke(Color.web("#bdbdbd"));
                gc.strokeLine(pp.getX(), pp.getY(), rp.getX(), rp.getY());
            }
        }

        // Nodes
        for (BuildingElement el : graph.getElements()) {
            if (el.getName().contains("↔")) continue;
            javafx.geometry.Point2D p = pos.get(el.getName());
            if (p == null) continue;
            drawNode(gc, el, p.getX(), p.getY());
        }

        // Agents
        for (Agent a : graph.getAgents()) {
            javafx.geometry.Point2D p = agentPos(a);
            if (p != null) drawAgent(gc, p.getX(), p.getY(), a);
        }
    }

    private void drawNode(GraphicsContext gc, BuildingElement el, double x, double y) {
        Color fill = nodeColor(el);
        gc.setFill(fill);
        gc.setStroke(Color.web("#9e9e9e"));
        gc.setLineWidth(1.2);

        if (el instanceof Exit) {
            gc.fillRoundRect(x-34, y-14, 68, 28, 10, 10);
            gc.strokeRoundRect(x-34, y-14, 68, 28, 10, 10);
        } else if (el instanceof Passage) {
            double[] xs = {x, x+24, x, x-24};
            double[] ys = {y-15, y, y+15, y};
            gc.fillPolygon(xs, ys, 4);
            gc.strokePolygon(xs, ys, 4);
        } else {
            gc.fillOval(x-NR, y-NR, NR*2, NR*2);
            gc.strokeOval(x-NR, y-NR, NR*2, NR*2);
        }

        // Label
        String name = el.getName().length() > 10
            ? el.getName().substring(0, 9) + "…" : el.getName();
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Sans", FontWeight.BOLD, 10));
        gc.fillText(name, x - name.length() * 2.8, y + 3);

        // Occupancy
        String occ = el.getCurrentOccupancy() + "/" + el.getMaxCapacity();
        gc.setFill(Color.web("#eeeeee"));
        gc.setFont(Font.font("Sans", 8));
        gc.fillText(occ, x - occ.length() * 2, y + (el instanceof Exit ? 22 : NR + 10));
    }

    private void drawAgent(GraphicsContext gc, double x, double y, Agent a) {
        Color c = a.getState() == AgentState.PANICKED
            ? Color.web("#f44336") : Color.web("#1565c0");
        gc.setStroke(c); gc.setFill(c); gc.setLineWidth(1.5);
        gc.fillOval(x-3, y-12, 7, 7);
        gc.strokeLine(x, y-5, x, y+5);
        gc.strokeLine(x-4, y-1, x+4, y-1);
        gc.strokeLine(x, y+5, x-3, y+11);
        gc.strokeLine(x, y+5, x+3, y+11);
    }

    private javafx.geometry.Point2D agentPos(Agent a) {
        javafx.geometry.Point2D base = pos.get(a.getCurrentLocation().getName());
        if (base == null) return null;
        BuildingElement next = a.getNextInPath();
        if (next != null && a.getProgress() > 0) {
            javafx.geometry.Point2D np = pos.get(next.getName());
            if (np != null) {
                double t = a.getProgress();
                return new javafx.geometry.Point2D(
                    base.getX() + (np.getX()-base.getX())*t,
                    base.getY() + (np.getY()-base.getY())*t);
            }
        }
        int h = Math.abs(a.getId().hashCode());
        return new javafx.geometry.Point2D(base.getX()+(h%18)-9, base.getY()+((h/18)%14)-7);
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

    // ── Helpers ───────────────────────────────────────────

    private void log(String msg) {
        if (logArea != null) {
            logArea.appendText(msg + "\n");
            logArea.setScrollTop(Double.MAX_VALUE);
        }
    }

    private void handleSave() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("*.bin", "*.bin"));
        File f = fc.showSaveDialog(stage);
        if (f != null) { controller.saveSimulation(f.getAbsolutePath()); log("Sauvegardé: " + f.getName()); }
    }

    private void handleLoad() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("*.bin", "*.bin"));
        File f = fc.showOpenDialog(stage);
        if (f != null) { controller.loadSimulation(f.getAbsolutePath()); log("Chargé: " + f.getName()); draw(); }
    }

    private void handleAddAgent() {
        TextField nameF = new TextField(), speedF = new TextField("1.0"), tolF = new TextField("0.7");
        ComboBox<String> locB = nodeCombo();
        ComboBox<Behavior> behB = new ComboBox<>();
        behB.getItems().addAll(Behavior.values()); behB.getSelectionModel().selectFirst();
        dialog("Ajouter un agent",
            grid("Nom:", nameF, "Position:", locB, "Vitesse:", speedF, "Tolérance:", tolF, "Comportement:", behB),
            () -> {
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
            if (agB.getValue() != null) { controller.removeAgent(agB.getValue()); log("Agent supprimé: " + agB.getValue()); }
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

    private void setupContextMenu() {
        ContextMenu menu = new ContextMenu();
        MenuItem addN = new MenuItem("Ajouter un nœud");
        MenuItem rmN  = new MenuItem("Supprimer un nœud");
        MenuItem addE = new MenuItem("Ajouter une arête");
        MenuItem rmE  = new MenuItem("Supprimer une arête");
        menu.getItems().addAll(addN, rmN, new SeparatorMenuItem(), addE, rmE);
        addN.setOnAction(e -> handleAddNode());
        rmN.setOnAction(e  -> handleRemoveNode());
        addE.setOnAction(e -> handleAddEdge());
        rmE.setOnAction(e  -> handleRemoveEdge());
        canvas.setOnContextMenuRequested(e -> menu.show(canvas, e.getScreenX(), e.getScreenY()));
    }

    private void handleAddNode() {
        TextField nameF = new TextField(), xF = new TextField("300"), yF = new TextField("230");
        ComboBox<String> typeB = new ComboBox<>();
        typeB.getItems().addAll("Salle", "Sortie", "Couloir"); typeB.getSelectionModel().selectFirst();
        dialog("Ajouter un nœud", grid("Nom:", nameF, "Type:", typeB, "X:", xF, "Y:", yF), () -> {
            try {
                String n = nameF.getText().trim();
                if (n.isEmpty()) { showErr("Nom vide"); return; }
                double x = Double.parseDouble(xF.getText()), y = Double.parseDouble(yF.getText());
                controller.addNode(n, typeB.getValue(), x, y);
                pos.put(n, new javafx.geometry.Point2D(x, y));
                draw();
            } catch (Exception ex) { showErr("Invalide"); }
        });
    }

    private void handleRemoveNode() {
        ComboBox<String> nb = nodeCombo();
        dialog("Supprimer un nœud", grid("Nœud:", nb), () -> {
            if (nb.getValue() != null) { controller.removeNode(nb.getValue()); pos.remove(nb.getValue()); draw(); }
        });
    }

    private void handleAddEdge() {
        ComboBox<String> rB = new ComboBox<>(), pB = new ComboBox<>();
        controller.getGraph().getElements().forEach(el -> {
            if (el instanceof Room && !el.getName().contains("↔")) rB.getItems().add(el.getName());
            if (el instanceof Passage && !el.getName().contains("↔")) pB.getItems().add(el.getName());
        });
        rB.getSelectionModel().selectFirst(); pB.getSelectionModel().selectFirst();
        dialog("Ajouter une arête", grid("Salle:", rB, "Couloir:", pB), () -> {
            controller.addEdge(rB.getValue(), pB.getValue()); draw();
        });
    }

    private void handleRemoveEdge() {
        ComboBox<String> eB = new ComboBox<>();
        controller.getGraph().getPassages().forEach(p ->
            p.getConnectedDoors().stream().filter(d -> !d.getRoom().getName().contains("↔"))
                .forEach(d -> eB.getItems().add(d.getRoom().getName() + " → " + p.getName())));
        eB.getSelectionModel().selectFirst();
        dialog("Supprimer une arête", grid("Arête:", eB), () -> {
            if (eB.getValue() != null) {
                String[] parts = eB.getValue().split(" → ");
                controller.removeEdge(parts[0], parts[1]); draw();
            }
        });
    }

    private void goBack() {
        if (uiRefresh != null) uiRefresh.stop();
        controller.pause();
        new LoginView(stage, controller).show();
    }

    private void initPositions() {
        put("Réserve", 80, 60); put("Bureau 1", 80, 155); put("Bureau 2", 80, 250);
        put("Bureau 3", 80, 345); put("Escalier 1", 210, 65); put("Couloir Nord", 310, 145);
        put("LT Serveurs", 440, 90); put("Escalier 2", 370, 215); put("Hall Central", 440, 300);
        put("Sortie Ouest", 60, 420); put("Amphithéâtre", 195, 420); put("Couloir Sud", 360, 420);
        put("Logement", 510, 420); put("Sortie Est 1", 640, 200); put("Sortie Est 2", 640, 300);
        put("Sortie Est 3", 640, 400);
    }

    private void put(String n, double x, double y) {
        pos.putIfAbsent(n, new javafx.geometry.Point2D(x, y));
    }

    // ── UI components ─────────────────────────────────────

    private Button btn(String text, String color) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color:" + color + ";-fx-text-fill:white;" +
            "-fx-font-size:11px;-fx-padding:5 10;-fx-background-radius:5;-fx-cursor:hand;");
        return b;
    }

    private Label sectionTitle(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Sans", FontWeight.BOLD, 12));
        l.setTextFill(Color.web("#1a237e"));
        return l;
    }

    private HBox legendRow(Color c, String text) {
        Label dot = new Label("●");
        dot.setTextFill(c);
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size:10px;");
        return new HBox(6, dot, lbl);
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
            g.add(new Label(pairs[i].toString()), 0, i/2);
            g.add((javafx.scene.Node) pairs[i+1], 1, i/2);
        }
        return g;
    }

    private void showErr(String msg) {
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }
}
