package com.example.cysafecampus.view;

import com.example.cysafecampus.controller.GraphController;
import com.example.cysafecampus.model.Room;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
/**
 * Login screen — clean white design, role selection cards.
 */
/**
 * LoginView is responsible for rendering the initial role-selection screen of the
 * application and for routing the user to the appropriate view based on their choice.
 *
 * <p>The view presents a branded title area, a subtitle, and two "role cards" that
 * represent the available roles (Administrator and Supervisor). Selecting a card
 * triggers navigation to the corresponding view (AdminView or SupervisorView).
 *
 * <p>This class is tightly coupled to a JavaFX Stage for display and to a
 * GraphController to access domain elements (e.g. rooms) needed when the user
 * chooses the Supervisor role.
 *
 * Dependencies:
 * - GraphController: used to fetch graph elements and rooms.
 * - AdminView, SupervisorView: views instantiated when a role is chosen.
 * - Room: used to identify and pass the selected room to SupervisorView.
 */
 
/**
 * The primary stage used to display the login UI.
 *
 * @implNote Initialized via constructor and used to replace the current scene
 *           when navigating to other views.
 */
 
/**
 * Controller providing access to application graph and business logic needed
 * by the login interactions (room enumeration, navigation to other views).
 */
 
/**
 * Create a new LoginView.
 *
 * @param stage      the JavaFX Stage on which the login scene will be shown.
 *                   The stage is not created by this class and should be managed
 *                   by the application.
 * @param controller the GraphController instance used to query rooms and to
 *                   hand to subsequent views (AdminView / SupervisorView).
 */
 
/**
 * Build and display the login scene on the associated Stage.
 *
 * <p>The scene includes:
 * - a branded title (CY SafeCampus) and subtitle,
 * - a decorative separator,
 * - a prompt to select a role,
 * - interactive role cards for "Administrateur" and "Superviseur".
 *
 * <p>Each role card has hover highlighting and a click handler. Clicking:
 * - the Administrator card: opens AdminView on the same Stage.
 * - the Supervisor card: opens a dialog to pick a room and then opens SupervisorView.
 *
 * <p>This method shows the stage (stage.show()) and sets the scene title to
 * "CY SafeCampus — Connexion".
 */
 
/**
 * Create a clickable role "card" UI element.
 *
 * <p>The returned VBox is styled with a colored background, padding, a fixed
 * preferred width, a hand cursor, and mouse handlers for hover and click.
 *
 * @param title      the role title displayed on the card (e.g. "Administrateur").
 * @param desc       a short descriptive text shown under the title.
 * @param colorDark  the background color used when the card is idle (hex color string).
 * @param colorLight the background color used when the card is hovered (hex color string).
 * @param icon       a short icon string (emoji or text) shown beside the title.
 * @param action     the action to execute when the card is clicked; provided as a Runnable.
 * @return a configured VBox representing the role card. The caller is responsible
 *         for adding it to the scene graph.
 */
 
/**
 * Open the Administrator view.
 *
 * <p>Instantiates and displays an AdminView using the same Stage and the
 * GraphController passed to this LoginView.
 *
 * @implNote Navigation replaces the current scene on the associated Stage.
 */
 
/**
 * Open the Supervisor selection flow.
 *
 * <p>This method opens a modal dialog allowing the user to choose a room from
 * the application's graph. The dialog:
 * - populates a ComboBox with names of Room elements from the controller's graph,
 *   filtering out connector/bi-directional names (those containing "↔"),
 * - selects the first available room by default,
 * - returns the selected room name when the user confirms.
 *
 * <p>When a room is chosen and confirmed, the corresponding Room object is
 * located in the graph and a SupervisorView is instantiated and shown for that room.
 *
 * @implNote If no room is selected or the dialog is cancelled, no navigation occurs.
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
