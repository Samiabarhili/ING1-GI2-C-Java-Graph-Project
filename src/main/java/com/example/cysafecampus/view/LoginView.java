package com.example.cysafecampus.view;

import com.example.cysafecampus.controller.GraphController;
import com.example.cysafecampus.model.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

/**
 * Login screen — user picks their role before entering the simulation.
 */
public class LoginView {

    private final Stage stage;
    private final GraphController controller;

    public LoginView(Stage stage, GraphController controller) {
        this.stage = stage;
        this.controller = controller;
    }

    public void show() {
        // ── Title ─────────────────────────────────────────
        Label title = new Label("CY SafeCampus");
        title.setFont(Font.font("Sans", FontWeight.BOLD, 28));
        title.setTextFill(Color.web("#1a237e"));

        Label subtitle = new Label("Système de simulation d'évacuation");
        subtitle.setFont(Font.font("Sans", 13));
        subtitle.setTextFill(Color.web("#546e7a"));

        // ── Role cards ────────────────────────────────────
        VBox roles = new VBox(12,
            roleCard("🛡  Administrateur",
                "Plan complet · Capteurs · Ordres d'évacuation",
                "#1a237e", () -> openAdmin()),
            roleCard("👤  Superviseur",
                "Gestion d'une salle · Réception des ordres",
                "#1b5e20", () -> openSupervisor()),
            roleCard("🔒  Agent de sécurité",
                "Zone assignée · Ouverture des portes",
                "#b71c1c", () -> openSecurity()),
            roleCard("👁  Observateur",
                "Vue globale en lecture seule",
                "#37474f", () -> openObserver())
        );
        roles.setAlignment(Pos.CENTER);

        // ── Root ──────────────────────────────────────────
        VBox root = new VBox(24, title, subtitle, roles);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(50, 60, 50, 60));
        root.setStyle("-fx-background-color: #f5f5f5;");

        Scene scene = new Scene(root, 480, 520);
        stage.setTitle("CY SafeCampus — Connexion");
        stage.setScene(scene);
        stage.show();
    }

    private VBox roleCard(String title, String desc, String color, Runnable action) {
        Label titleLbl = new Label(title);
        titleLbl.setFont(Font.font("Sans", FontWeight.BOLD, 15));
        titleLbl.setTextFill(Color.WHITE);

        Label descLbl = new Label(desc);
        descLbl.setFont(Font.font("Sans", 11));
        descLbl.setTextFill(Color.web("#cccccc"));

        VBox card = new VBox(4, titleLbl, descLbl);
        card.setPadding(new Insets(14, 20, 14, 20));
        card.setStyle(
            "-fx-background-color:" + color + ";" +
            "-fx-background-radius:10;" +
            "-fx-cursor:hand;");
        card.setPrefWidth(360);

        // Hover effect
        card.setOnMouseEntered(e -> card.setStyle(
            "-fx-background-color:derive(" + color + ", 20%);" +
            "-fx-background-radius:10;-fx-cursor:hand;"));
        card.setOnMouseExited(e -> card.setStyle(
            "-fx-background-color:" + color + ";" +
            "-fx-background-radius:10;-fx-cursor:hand;"));
        card.setOnMouseClicked(e -> action.run());

        return card;
    }

    // ── Open views ────────────────────────────────────────

    private void openAdmin() {
        AdminView view = new AdminView(stage, controller);
        view.show();
    }

    private void openSupervisor() {
        // Ask which room this supervisor manages
        Dialog<String> d = new Dialog<>();
        d.setTitle("Superviseur — Choisir votre salle");
        ComboBox<String> roomBox = new ComboBox<>();
        controller.getGraph().getElements().stream()
            .filter(el -> el instanceof Room && !el.getName().contains("↔"))
            .forEach(el -> roomBox.getItems().add(el.getName()));
        roomBox.getSelectionModel().selectFirst();
        GridPane g = new GridPane();
        g.setHgap(10); g.setVgap(10); g.setPadding(new Insets(10));
        g.add(new Label("Votre salle :"), 0, 0);
        g.add(roomBox, 1, 0);
        d.getDialogPane().setContent(g);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        d.setResultConverter(btn -> btn == ButtonType.OK ? roomBox.getValue() : null);
        d.showAndWait().ifPresent(roomName -> {
            BuildingElement room = controller.getGraph().getElements().stream()
                .filter(el -> el.getName().equals(roomName))
                .findFirst().orElse(null);
            if (room instanceof Room) {
                SupervisorView view = new SupervisorView(stage, controller, (Room) room);
                view.show();
            }
        });
    }

    private void openSecurity() {
        // Ask which zone this security agent manages
        Dialog<String> d = new Dialog<>();
        d.setTitle("Sécurité — Choisir votre zone");
        ComboBox<String> zoneBox = new ComboBox<>();
        controller.getGraph().getElements().stream()
            .filter(el -> !el.getName().contains("↔"))
            .forEach(el -> zoneBox.getItems().add(el.getName()));
        zoneBox.getSelectionModel().selectFirst();
        GridPane g = new GridPane();
        g.setHgap(10); g.setVgap(10); g.setPadding(new Insets(10));
        g.add(new Label("Votre zone :"), 0, 0);
        g.add(zoneBox, 1, 0);
        d.getDialogPane().setContent(g);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        d.setResultConverter(btn -> btn == ButtonType.OK ? zoneBox.getValue() : null);
        d.showAndWait().ifPresent(zoneName -> {
            BuildingElement zone = controller.getGraph().getElements().stream()
                .filter(el -> el.getName().equals(zoneName))
                .findFirst().orElse(null);
            if (zone != null) {
                SecurityView view = new SecurityView(stage, controller, zone);
                view.show();
            }
        });
    }

    private void openObserver() {
        ObserverView view = new ObserverView(stage, controller);
        view.show();
    }
}
