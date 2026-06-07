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
    private static final double NR = 34;

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
        header.setBackground(new javafx.scene.layout.Background(new javafx.scene.layout.BackgroundFill(Color.web("#f8f9fa"), javafx.scene.layout.CornerRadii.EMPTY, javafx.geometry.Insets.EMPTY)));
        header.setStyle("-fx-border-color:#e8eaed;-fx-border-width:0 0 1 0;");

        // ── Canvas ────────────────────────────────────────
        canvas = new Canvas(CW, CH);

        // Click on node to show stats in title bar
        canvas.setOnMouseClicked(e -> {
            double mx = e.getX(), my = e.getY();
            for (BuildingElement el : controller.getGraph().getElements()) {
                if (el.getName().contains("↔")) continue;
                javafx.geometry.Point2D p = pos.get(el.getName());
                if (p != null && Math.hypot(p.getX()-mx, p.getY()-my) < NR+8) {
                    stage.setTitle("SafeCampus — " + el.getName()
                        + " | Passés: " + el.getTotalAgentsPassed()
                        + " | Vit.moy: " + String.format("%.1f", el.getAverageSpeed()));
                    return;
                }
            }
            stage.setTitle("CY SafeCampus — Observation");
        });

        StackPane center = new StackPane(canvas);
        center.setBackground(new javafx.scene.layout.Background(new javafx.scene.layout.BackgroundFill(javafx.scene.paint.Color.WHITE, javafx.scene.layout.CornerRadii.EMPTY, javafx.geometry.Insets.EMPTY)));

        // ── Legend ────────────────────────────────────────
        HBox legend = new HBox(16,
            legendItem(Color.web("#1b5e20"), "Vide"),
            legendItem(Color.web("#e65100"), "Dense"),
            legendItem(Color.web("#b71c1c"), "Saturé"),
            legendItem(Color.web("#0d47a1"), "Sortie"),
            legendItem(Color.web("#283593"), "Jonction / palier"),            legendItem(Color.web("#1565c0"), "Agent calme"),
            legendItem(Color.web("#f44336"), "Agent paniqué")
        );
        legend.setPadding(new Insets(8, 14, 8, 14));
        legend.setAlignment(Pos.CENTER_LEFT);
        legend.setBackground(new javafx.scene.layout.Background(new javafx.scene.layout.BackgroundFill(Color.web("#f8f9fa"), javafx.scene.layout.CornerRadii.EMPTY, javafx.geometry.Insets.EMPTY)));
        legend.setStyle("-fx-border-color:#e8eaed;-fx-border-width:1 0 0 0;");

        // ── Root ──────────────────────────────────────────
        BorderPane root = new BorderPane();
        root.setTop(header);
        root.setCenter(center);
        root.setBottom(legend);
        root.setBackground(new javafx.scene.layout.Background(new javafx.scene.layout.BackgroundFill(javafx.scene.paint.Color.WHITE, javafx.scene.layout.CornerRadii.EMPTY, javafx.geometry.Insets.EMPTY)));

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

        syncPositionsFromModel();

        gc.clearRect(0, 0, CW, CH);
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, CW, CH);

        drawEdgesFromModel(gc);

        for (BuildingElement el : graph.getElements()) {
            if (el.getName().contains("↔")) continue;

            javafx.geometry.Point2D p = pos.get(el.getName());
            if (p == null) continue;

            drawNode(gc, el, p.getX(), p.getY());
        }

        for (Agent a : graph.getAgents()) {
            javafx.geometry.Point2D p = agentPos(a);
            if (p != null) {
                drawAgent(gc, p.getX(), p.getY(), a);
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


    public void refreshAfterReset() {
        pos.clear();
        initPositions();
        draw();
    }


    private void syncPositionsFromModel() {
        pos.keySet().removeIf(name ->
            controller.getGraph().getElements().stream()
                .noneMatch(el -> el.getName().equals(name))
        );

        for (BuildingElement el : controller.getGraph().getElements()) {
            if (el.getName().contains("↔")) continue;

            if (!pos.containsKey(el.getName()) && (el.getX() != 0.0 || el.getY() != 0.0)) {
                pos.put(el.getName(), new javafx.geometry.Point2D(el.getX(), el.getY()));
            }
        }
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
            System.out.println("Missing observer edge position: " + from + " -> " + to);
            return;
        }

        gc.strokeLine(a.getX(), a.getY(), b.getX(), b.getY());
    }

    private void drawNode(GraphicsContext gc, BuildingElement el, double x, double y) {
        Color fill = nodeColor(el);

        gc.setFill(fill);
        gc.setStroke(Color.web("#9e9e9e"));
        gc.setLineWidth(1.2);

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
            x - occ.length() * 2,
            y + (el instanceof Exit ? 22 : NR + 10)
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

    private void drawAgent(GraphicsContext gc, double x, double y, Agent a) {
        Color c = a.getState() == AgentState.PANICKED
            ? Color.web("#f44336")
            : Color.web("#1565c0");

        gc.setStroke(c);
        gc.setFill(c);
        gc.setLineWidth(1.5);

        gc.fillOval(x - 3, y - 12, 7, 7);
        gc.strokeLine(x, y - 5, x, y + 5);
        gc.strokeLine(x - 4, y - 1, x + 4, y - 1);
        gc.strokeLine(x, y + 5, x - 3, y + 11);
        gc.strokeLine(x, y + 5, x + 3, y + 11);
    }

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
                    base.getY() + (np.getY() - base.getY()) * t
                );
            }
        }

        int h = Math.abs(a.getId().hashCode());

        return new javafx.geometry.Point2D(
            base.getX() + (h % 18) - 9,
            base.getY() + ((h / 18) % 14) - 7
        );
    }

    private Color nodeColor(BuildingElement el) {
        if (el.isBlocked()) return Color.web("#424242");
        if (el instanceof Exit) return Color.web("#0d47a1");
        if (el instanceof Passage) return Color.web("#283593");

        double r = el.getMaxCapacity() > 0
            ? (double) el.getCurrentOccupancy() / el.getMaxCapacity()
            : 0;

        if (r < 0.4) return Color.web("#1b5e20");
        if (r < 0.8) return Color.web("#e65100");
        return Color.web("#b71c1c");
    }

    private void goBack() {
        if (refresh != null) refresh.stop();
        new LoginView(stage, controller).show();
    }

    private void initPositions() {
        put("Réserve", 120, 85);
        put("Bureau 1", 120, 180);
        put("Bureau 2", 120, 275);
        put("Bureau 3", 120, 370);

        put("Palier Esc. 1", 265, 85);
        put("Jonction Nord", 380, 175);
        put("Palier Esc. 2", 445, 255);

        put("LT Serveurs", 540, 105);
        put("Jonction Centrale", 540, 350);

        put("Sortie Ouest", 85, 450);
        put("Amphithéâtre", 245, 450);
        put("Jonction Sud", 430, 450);
        put("Logement", 600, 450);

        put("Sortie Est 1", 700, 230);
        put("Sortie Est 2", 700, 350);
        put("Sortie Est 3", 700, 435);
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
