package com.example.cysafecampus.view;

import com.example.cysafecampus.controller.GraphController;
import com.example.cysafecampus.model.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.paint.CycleMethod;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

/**
 * Login screen — clean white design, role selection cards.
 */
public class LoginView {

    private final Stage stage;
    private final GraphController controller;

    public LoginView(Stage stage, GraphController controller) {
        this.stage = stage;
        this.controller = controller;
    }

    public void show() {
        // ── Logo / Title ──────────────────────────────────
        Label cyLabel = new Label("CY");
        cyLabel.setFont(Font.font("Sans", FontWeight.BOLD, 36));
        cyLabel.setTextFill(Color.web("#1a237e"));

        Label safeLabel = new Label("SafeCampus");
        safeLabel.setFont(Font.font("Sans", FontWeight.BOLD, 36));
        safeLabel.setTextFill(Color.web("#c62828"));

        HBox titleBox = new HBox(6, cyLabel, safeLabel);
        titleBox.setAlignment(Pos.CENTER);

        Label subtitle = new Label("Système de simulation d'évacuation");
        subtitle.setFont(Font.font("Sans", 13));
        subtitle.setTextFill(Color.web("#78909c"));

        // ── Separator ─────────────────────────────────────
        Rectangle sep = new Rectangle(60, 3);
        sep.setFill(Color.web("#c62828"));
        sep.setArcWidth(3); sep.setArcHeight(3);

        VBox titleSection = new VBox(10, titleBox, sep, subtitle);
        titleSection.setAlignment(Pos.CENTER);

        // ── Role label ────────────────────────────────────
        Label chooseLabel = new Label("Sélectionnez votre rôle");
        chooseLabel.setFont(Font.font("Sans", FontWeight.BOLD, 12));
        chooseLabel.setTextFill(Color.web("#90a4ae"));

        // ── Role cards ────────────────────────────────────
        VBox cards = new VBox(10,
            roleCard("Administrateur",
                "Plan complet  ·  Capteurs  ·  Ordres d'évacuation",
                "#1a237e", "#283593", "🛡", this::openAdmin),
            roleCard("Superviseur",
                "Gestion d'une salle  ·  Réception des ordres",
                "#1b5e20", "#2e7d32", "👤", this::openSupervisor)
        );
        cards.setAlignment(Pos.CENTER);

        // ── Root ──────────────────────────────────────────
        VBox root = new VBox(24, titleSection, chooseLabel, cards);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(50, 50, 50, 50));
        root.setBackground(new Background(new BackgroundFill(
            Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY)));

        Scene scene = new Scene(root, 460, 520);
        scene.setFill(Color.WHITE);
        stage.setTitle("CY SafeCampus — Connexion");
        stage.setScene(scene);
        stage.show();
    }

    private VBox roleCard(String title, String desc, String colorDark,
                          String colorLight, String icon, Runnable action) {
        Label iconLbl = new Label(icon);
        iconLbl.setFont(Font.font(20));

        Label titleLbl = new Label(title);
        titleLbl.setFont(Font.font("Sans", FontWeight.BOLD, 15));
        titleLbl.setTextFill(Color.WHITE);

        HBox titleRow = new HBox(10, iconLbl, titleLbl);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label descLbl = new Label(desc);
        descLbl.setFont(Font.font("Sans", 11));
        descLbl.setTextFill(Color.web("#dddddd"));

        VBox card = new VBox(6, titleRow, descLbl);
        card.setPadding(new Insets(16, 20, 16, 20));
        card.setPrefWidth(380);
        card.setMaxWidth(380);
        card.setBackground(new Background(new BackgroundFill(
            Color.web(colorDark), new CornerRadii(10), Insets.EMPTY)));
        card.setCursor(javafx.scene.Cursor.HAND);

        card.setOnMouseEntered(e -> card.setBackground(new Background(
            new BackgroundFill(Color.web(colorLight), new CornerRadii(10), Insets.EMPTY))));
        card.setOnMouseExited(e -> card.setBackground(new Background(
            new BackgroundFill(Color.web(colorDark), new CornerRadii(10), Insets.EMPTY))));
        card.setOnMouseClicked(e -> action.run());

        return card;
    }

    private void openAdmin() {
        new AdminView(stage, controller).show();
    }

    private void openSupervisor() {
        Dialog<String> d = new Dialog<>();
        d.setTitle("Choisir votre salle");
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
        d.showAndWait().ifPresent(name -> {
            controller.getGraph().getElements().stream()
                .filter(el -> el.getName().equals(name) && el instanceof Room)
                .findFirst()
                .ifPresent(el -> new SupervisorView(stage, controller, (Room) el).show());
        });
    }

} 
