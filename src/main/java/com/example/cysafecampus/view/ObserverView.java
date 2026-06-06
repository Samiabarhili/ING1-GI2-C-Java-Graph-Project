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
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.Map;

/**
 * Observer view — read-only simulation view.
 * Shows the full campus graph with moving agents and density colors.
 * No editing controls — pure visualization.
 */
public class ObserverView {

    private static final int CW = 780, CH = 480;
    private static final double NR = 28;

    private final Stage stage;
    private final GraphController controller;
    private Canvas canvas;
    private Label tickLbl;
    private Label agentCountLbl;
    private Label statusLbl;
    private Timeline refresh;

    private final Map<String, javafx.geometry.Point2D> pos = new HashMap<>();

    public ObserverView(Stage stage, GraphController controller) {
        this.stage = stage;
        this.controller = controller;
        initPositions();
    }

    public void show() {
        // ── Header ────────────────────────────────────────
        Label role = new Label("👁 Observateur — Vue globale");
        role.setFont(Font.font("Sans", FontWeight.BOLD, 14));
        role.setTextFill(Color.web("#37474f"));

        statusLbl = new Label("NORMAL");
        statusLbl.setStyle("-fx-background-color:#2e7d32;-fx-text-fill:white;" +
            "-fx-padding:3 10;-fx-background-radius:5;-fx-font-weight:bold;");

        tickLbl = new Label("Tick: 0");
        tickLbl.setStyle("-fx-text-fill:#546e7a;-fx-font-size:11px;");

        agentCountLbl = new Label("Agents: 0");
        agentCountLbl.setStyle("-fx-text-fill:#546e7a;-fx-font-size:11px;");

        Button backBtn = btn("← Retour", "#546e7a");
        backBtn.setOnAction(e -> goBack());

        // Read-only note
        Label readOnly = new Label("🔒 Lecture seule");
        readOnly.setStyle("-fx-text-fill:#9e9e9e;-fx-font-size:11px;-fx-font-style:italic;");

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        HBox header = new HBox(12, backBtn, role, statusLbl, sp,
            tickLbl, agentCountLbl, readOnly);
        header.setPadding(new Insets(8, 14, 8, 14));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color:#eceff1;-fx-border-color:#cfd8dc;-fx-border-width:0 0 1 0;");

        // ── Canvas ────────────────────────────────────────
        canvas = new Canvas(CW, CH);
        StackPane center = new StackPane(canvas);
        center.setStyle("-fx-background-color:white;");

        // ── Legend ────────────────────────────────────────
        HBox legend = new HBox(16,
            legendItem(Color.web("#1b5e20"), "Vide"),
            legendItem(Color.web("#e65100"), "Dense"),
            legendItem(Color.web("#b71c1c"), "Saturé"),
            legendItem(Color.web("#0d47a1"), "Sortie"),
            legendItem(Color.web("#283593"), "Passage"),
            legendItem(Color.web("#1565c0"), "Agent calme"),
            legendItem(Color.web("#f44336"), "Agent paniqué")
        );
        legend.setPadding(new Insets(8, 14, 8, 14));
        legend.setAlignment(Pos.CENTER_LEFT);
        legend.setStyle("-fx-background-color:#eceff1;-fx-border-color:#cfd8dc;-fx-border-width:1 0 0 0;");

        // ── Root ──────────────────────────────────────────
        BorderPane root = new BorderPane();
        root.setTop(header);
        root.setCenter(center);
        root.setBottom(legend);
        root.setStyle("-fx-background-color:white;");

        Scene scene = new Scene(root, CW, CH + 80);
        stage.setTitle("CY SafeCampus — Observation");
        stage.setScene(scene);
        stage.show();

        // Auto-refresh every 150ms
        refresh = new Timeline(new KeyFrame(Duration.millis(150), e -> {
            draw();
            tickLbl.setText("Tick: " + controller.getTickCount());
            agentCountLbl.setText("Agents: " + controller.getGraph().getAgents().size());
            boolean alert = controller.getGraph().getAgents().stream()
                .anyMatch(a -> a.getState() == AgentState.PANICKED);
            if (alert) {
                statusLbl.setText("🔥 ALERTE");
                statusLbl.setStyle("-fx-background-color:#c62828;-fx-text-fill:white;" +
                    "-fx-padding:3 10;-fx-background-radius:5;-fx-font-weight:bold;");
            } else {
                statusLbl.setText("NORMAL");
                statusLbl.setStyle("-fx-background-color:#2e7d32;-fx-text-fill:white;" +
                    "-fx-padding:3 10;-fx-background-radius:5;-fx-font-weight:bold;");
            }
        }));
        refresh.setCycleCount(Timeline.INDEFINITE);
        refresh.play();

        draw();
    }

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
                double density = p.getMaxCapacity() > 0
                    ? (double) p.getCurrentOccupancy() / p.getMaxCapacity() : 0;
                gc.setStroke(density > 0.6 ? Color.web("#ffccbc") : Color.web("#bdbdbd"));
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
            gc.fillRoundRect(x-36, y-15, 72, 30, 10, 10);
            gc.strokeRoundRect(x-36, y-15, 72, 30, 10, 10);
        } else if (el instanceof Passage) {
            double[] xs = {x, x+26, x, x-26};
            double[] ys = {y-16, y, y+16, y};
            gc.fillPolygon(xs, ys, 4);
            gc.strokePolygon(xs, ys, 4);
        } else {
            gc.fillOval(x-NR, y-NR, NR*2, NR*2);
            gc.strokeOval(x-NR, y-NR, NR*2, NR*2);
        }

        String name = el.getName().length() > 10
            ? el.getName().substring(0, 9) + "…" : el.getName();
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Sans", FontWeight.BOLD, 10));
        gc.fillText(name, x - name.length() * 2.8, y + 3);

        String occ = el.getCurrentOccupancy() + "/" + el.getMaxCapacity();
        gc.setFill(Color.web("#eeeeee"));
        gc.setFont(Font.font("Sans", 8));
        gc.fillText(occ, x - occ.length() * 2,
            y + (el instanceof Exit ? 22 : NR + 10));
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

    private void goBack() {
        if (refresh != null) refresh.stop();
        new LoginView(stage, controller).show();
    }

    private void initPositions() {
        put("Réserve", 85, 60);   put("Bureau 1", 85, 155);
        put("Bureau 2", 85, 250); put("Bureau 3", 85, 345);
        put("Escalier 1", 215, 65); put("Couloir Nord", 320, 148);
        put("LT Serveurs", 460, 90); put("Escalier 2", 385, 218);
        put("Hall Central", 460, 305);
        put("Sortie Ouest", 65, 430); put("Amphithéâtre", 205, 430);
        put("Couloir Sud", 375, 430); put("Logement", 530, 430);
        put("Sortie Est 1", 670, 205); put("Sortie Est 2", 670, 305);
        put("Sortie Est 3", 670, 405);
    }

    private void put(String n, double x, double y) {
        pos.putIfAbsent(n, new javafx.geometry.Point2D(x, y));
    }

    private HBox legendItem(Color c, String text) {
        Label dot = new Label("●");
        dot.setTextFill(c);
        dot.setFont(Font.font(14));
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size:10px;-fx-text-fill:#546e7a;");
        HBox h = new HBox(4, dot, lbl);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    private Button btn(String text, String color) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color:" + color + ";-fx-text-fill:white;" +
            "-fx-font-size:11px;-fx-padding:5 10;-fx-background-radius:5;-fx-cursor:hand;");
        return b;
    }
}
