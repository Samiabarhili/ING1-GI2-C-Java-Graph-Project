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

/**
 * Security agent view — mobile-style, minimal.
 * Shows zone status and door controls only.
 * The agent is on the ground, not at a desk.
 */
public class SecurityView {

    private final Stage stage;
    private final GraphController controller;
    private final BuildingElement zone;

    private Label occupancyLbl;
    private Label densityLbl;
    private Label alertBanner;
    private VBox doorControls;
    private Timeline refresh;

    public SecurityView(Stage stage, GraphController controller, BuildingElement zone) {
        this.stage = stage;
        this.controller = controller;
        this.zone = zone;
    }

    public void show() {
        // ── Header ────────────────────────────────────────
        Label zoneName = new Label(zone.getName());
        zoneName.setFont(Font.font("Sans", FontWeight.BOLD, 22));
        zoneName.setTextFill(Color.WHITE);

        Label roleTag = new Label("AGENT DE SÉCURITÉ");
        roleTag.setStyle("-fx-font-size:10px;-fx-text-fill:#ef9a9a;-fx-font-weight:bold;");

        Button backBtn = new Button("←");
        backBtn.setStyle("-fx-background-color:transparent;-fx-text-fill:white;" +
            "-fx-font-size:16px;-fx-cursor:hand;");
        backBtn.setOnAction(e -> goBack());

        VBox zoneInfo = new VBox(4, roleTag, zoneName);
        HBox header = new HBox(10, backBtn, zoneInfo);
        header.setPadding(new Insets(20, 20, 20, 16));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color:#b71c1c;");

        // ── Zone status card ──────────────────────────────
        occupancyLbl = new Label("0 / " + zone.getMaxCapacity());
        occupancyLbl.setFont(Font.font("Sans", FontWeight.BOLD, 48));
        occupancyLbl.setTextFill(Color.web("#b71c1c"));

        densityLbl = new Label("● Zone dégagée");
        densityLbl.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:#2e7d32;");

        VBox statusCard = new VBox(8, occupancyLbl, densityLbl);
        statusCard.setPadding(new Insets(20));
        statusCard.setAlignment(Pos.CENTER);
        statusCard.setStyle("-fx-background-color:white;-fx-border-color:#e0e0e0;" +
            "-fx-border-radius:12;-fx-background-radius:12;");

        // ── Alert banner ──────────────────────────────────
        alertBanner = new Label("Zone sécurisée");
        alertBanner.setFont(Font.font("Sans", FontWeight.BOLD, 14));
        alertBanner.setWrapText(true);
        alertBanner.setStyle("-fx-text-fill:#757575;");

        Label alertIcon = new Label("🛡");
        alertIcon.setFont(Font.font(28));

        VBox alertBox = new VBox(8, alertIcon, alertBanner);
        alertBox.setPadding(new Insets(16));
        alertBox.setAlignment(Pos.CENTER);
        alertBox.setStyle("-fx-background-color:#f5f5f5;-fx-border-color:#e0e0e0;" +
            "-fx-border-radius:12;-fx-background-radius:12;");

        // ── Door controls ─────────────────────────────────
        Label doorTitle = new Label("Contrôle des portes");
        doorTitle.setFont(Font.font("Sans", FontWeight.BOLD, 13));
        doorTitle.setTextFill(Color.web("#424242"));

        doorControls = new VBox(8);

        Button openAllBtn = new Button("🔓  Ouvrir TOUTES les portes d'urgence");
        openAllBtn.setMaxWidth(Double.MAX_VALUE);
        openAllBtn.setStyle("-fx-background-color:#c62828;-fx-text-fill:white;" +
            "-fx-font-size:13px;-fx-padding:12;-fx-background-radius:10;-fx-cursor:hand;");
        openAllBtn.setOnAction(e -> {
            controller.getGraph().getPassages().forEach(p ->
                p.getConnectedDoors().forEach(Door::open));
            alertBanner.setText("✅  Toutes les portes ouvertes");
            alertBanner.setStyle("-fx-text-fill:#2e7d32;");
            alertIcon.setText("🔓");
            buildDoorList();
        });

        buildDoorList();

        // ── Layout ────────────────────────────────────────
        VBox content = new VBox(14,
            statusCard, alertBox,
            new Separator(),
            doorTitle, doorControls, openAllBtn);
        content.setPadding(new Insets(16));
        content.setStyle("-fx-background-color:#fafafa;");

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color:#fafafa;-fx-background:fafafa;");

        BorderPane root = new BorderPane();
        root.setTop(header);
        root.setCenter(scroll);

        Scene scene = new Scene(root, 360, 620);
        stage.setTitle("SafeCampus — Sécurité");
        stage.setScene(scene);
        stage.show();

        refresh = new Timeline(new KeyFrame(Duration.millis(500), e -> updateUI()));
        refresh.setCycleCount(Timeline.INDEFINITE);
        refresh.play();
        updateUI();
    }

    private void buildDoorList() {
        doorControls.getChildren().clear();
        controller.getGraph().getPassages().forEach(p ->
            p.getConnectedDoors().stream()
                .filter(d -> !d.getRoom().getName().contains("↔"))
                .forEach(d -> {
                    String label = d.getRoom().getName() + " ↔ " + p.getName();
                    boolean open = d.isOpen();

                    Button btn = new Button(
                        (open ? "🔓  " : "🔒  ") + label);
                    btn.setMaxWidth(Double.MAX_VALUE);
                    btn.setStyle("-fx-background-color:" + (open ? "white" : "#ffebee") + ";" +
                        "-fx-text-fill:" + (open ? "#2e7d32" : "#c62828") + ";" +
                        "-fx-font-size:12px;-fx-padding:10;-fx-background-radius:8;" +
                        "-fx-border-color:" + (open ? "#a5d6a7" : "#ef9a9a") + ";" +
                        "-fx-border-radius:8;-fx-cursor:hand;");
                    btn.setOnAction(e -> {
                        if (d.isOpen()) d.close(); else d.open();
                        buildDoorList();
                    });
                    doorControls.getChildren().add(btn);
                }));
    }

    private void updateUI() {
        int occ = zone.getCurrentOccupancy();
        int max = zone.getMaxCapacity();
        occupancyLbl.setText(occ + " / " + max);

        double ratio = max > 0 ? (double) occ / max : 0;
        if (ratio < 0.4) {
            occupancyLbl.setTextFill(Color.web("#2e7d32"));
            densityLbl.setText("● Zone dégagée");
            densityLbl.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:#2e7d32;");
        } else if (ratio < 0.8) {
            occupancyLbl.setTextFill(Color.web("#e65100"));
            densityLbl.setText("⚠ Affluence importante");
            densityLbl.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:#e65100;");
        } else {
            occupancyLbl.setTextFill(Color.web("#b71c1c"));
            densityLbl.setText("🚨 Zone saturée — intervenez !");
            densityLbl.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:#b71c1c;");
        }

        // Alert if any panicked agent nearby
        boolean alert = controller.getGraph().getAgents().stream()
            .anyMatch(a -> a.getState() == AgentState.PANICKED);
        if (alert) {
            alertBanner.setText("🔥  ALERTE — Ouvrez les portes d'urgence !");
            alertBanner.setStyle("-fx-text-fill:#c62828;-fx-font-size:14px;-fx-font-weight:bold;");
        }
    }

    private void goBack() {
        if (refresh != null) refresh.stop();
        new LoginView(stage, controller).show();
    }
}
