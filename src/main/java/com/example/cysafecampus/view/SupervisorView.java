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
 * Supervisor view — mobile-style notification interface.
 * The supervisor is on the ground, not at a desk.
 * Shows only what they need: their room status + the order received.
 */
public class SupervisorView {

    private final Stage stage;
    private final GraphController controller;
    private final Room assignedRoom;

    private Label occupancyLbl;
    private Label densityLbl;
    private Label orderBanner;
    private Label orderDetail;
    private Button guideBtn;
    private Timeline refresh;

    private boolean orderReceived = false;

    public SupervisorView(Stage stage, GraphController controller, Room assignedRoom) {
        this.stage = stage;
        this.controller = controller;
        this.assignedRoom = assignedRoom;
    }

    public void show() {
        // ── Top — room identity ───────────────────────────
        Label roomName = new Label(assignedRoom.getName());
        roomName.setFont(Font.font("Sans", FontWeight.BOLD, 22));
        roomName.setTextFill(Color.WHITE);

        Label roleTag = new Label("SUPERVISEUR");
        roleTag.setStyle("-fx-font-size:10px;-fx-text-fill:#a5d6a7;-fx-font-weight:bold;" +
            "-fx-letter-spacing:2;");

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
        orderBox.setStyle("-fx-background-color:#f5f5f5;-fx-border-color:#e0e0e0;" +
            "-fx-border-radius:12;-fx-background-radius:12;");

        // ── Action button ─────────────────────────────────
        guideBtn = new Button("🚶  Guider les occupants vers la sortie");
        guideBtn.setMaxWidth(Double.MAX_VALUE);
        guideBtn.setStyle("-fx-background-color:#e0e0e0;-fx-text-fill:#9e9e9e;" +
            "-fx-font-size:14px;-fx-padding:14;-fx-background-radius:10;-fx-cursor:hand;");
        guideBtn.setDisable(true);

        guideBtn.setOnAction(e -> {
            controller.getGraph().getAgents().stream()
                .filter(a -> a.getCurrentLocation().equals(assignedRoom))
                .forEach(a -> {
                    a.setStrategy(new EvacuateStrategy());
                    a.setPath(new java.util.ArrayList<>());
                    a.setState(AgentState.CALM);
                });
            guideBtn.setText("✅  Guidage en cours...");
            guideBtn.setStyle("-fx-background-color:#2e7d32;-fx-text-fill:white;" +
                "-fx-font-size:14px;-fx-padding:14;-fx-background-radius:10;");
            guideBtn.setDisable(true);
        });

        Button blockBtn = new Button("🚫  Bloquer la salle");
        blockBtn.setMaxWidth(Double.MAX_VALUE);
        blockBtn.setStyle("-fx-background-color:white;-fx-text-fill:#c62828;" +
            "-fx-font-size:13px;-fx-padding:10;-fx-background-radius:10;" +
            "-fx-border-color:#c62828;-fx-border-radius:10;-fx-cursor:hand;");
        blockBtn.setOnAction(e -> {
            assignedRoom.setStatus(BlockStatus.BLOCKED);
            orderBanner.setText("Salle bloquée");
            orderBanner.setStyle("-fx-text-fill:#c62828;");
        });

        // ── Layout ────────────────────────────────────────
        VBox content = new VBox(14, occCard, orderBox, guideBtn, blockBtn);
        content.setPadding(new Insets(16));
        content.setStyle("-fx-background-color:#fafafa;");

        BorderPane root = new BorderPane();
        root.setTop(header);
        root.setCenter(content);
        root.setStyle("-fx-background-color:#fafafa;");

        // Mobile-like narrow window
        Scene scene = new Scene(root, 360, 580);
        stage.setTitle("SafeCampus — Superviseur");
        stage.setScene(scene);
        stage.show();

        // Auto-refresh + check for evacuation orders
        refresh = new Timeline(new KeyFrame(Duration.millis(400), e -> updateUI()));
        refresh.setCycleCount(Timeline.INDEFINITE);
        refresh.play();
        updateUI();
    }

    private void updateUI() {
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

        // Check if any agent in the room is panicked = fire alert received
        boolean alert = controller.getGraph().getAgents().stream()
            .anyMatch(a -> a.getCurrentLocation().equals(assignedRoom)
                && a.getState() == AgentState.PANICKED);

        if (alert && !orderReceived) {
            orderReceived = true;
            orderBanner.setText("🔥  ÉVACUEZ LA SALLE");
            orderBanner.setStyle("-fx-text-fill:#c62828;-fx-font-size:18px;-fx-font-weight:bold;");
            orderDetail.setText("Guidez les occupants vers la sortie la plus proche. " +
                "Vérifiez que la salle est vide avant de partir.");
            orderDetail.setStyle("-fx-font-size:12px;-fx-text-fill:#424242;");

            // Activate the guide button
            guideBtn.setDisable(false);
            guideBtn.setStyle("-fx-background-color:#c62828;-fx-text-fill:white;" +
                "-fx-font-size:14px;-fx-padding:14;-fx-background-radius:10;-fx-cursor:hand;");
        }
    }

    private void goBack() {
        if (refresh != null) refresh.stop();
        new LoginView(stage, controller).show();
    }
}
