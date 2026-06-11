package com.example.cysafecampus.view;

import com.example.cysafecampus.controller.GraphController;
import com.example.cysafecampus.model.*;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
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
import javafx.geometry.VPos;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.Text;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;

import java.io.File;
import java.util.*;

/**
 * AdminView — faithful JavaFX port of the safecampus_evacuation_system.html prototype.
 *
 * Graph model (pure visual, independent of the building model):
 *   Node types : ROOM (rectangle, coloured by floor), DOOR (small square, shows rate),
 *                HALL (circle, shows capacity), STAIR (triangle, shows rate),
 *                EXIT (diamond, green).
 *   Edges      : plain grey lines — no label on the edge itself.
 *   Fire       : spreads node-by-node every 1.8 s, colour changes to red/orange.
 *
 * The visual graph is kept in {@code nodes} / {@code edges} lists and painted on a
 * JavaFX Canvas that refreshes at 60 fps via a Timeline.
 */
public class AdminView {

    // ── Visual node type ──────────────────────────────────

    private enum NType { ROOM, DOOR, HALL, STAIR, EXIT }

    /** One visual node in the graph. */
    private static class VNode {
        String id, label;
        NType  type;
        int    floor;
        double x, y;
        int    cap  = 20;   // capacity (ROOM / HALL)
        double rate = 2.0;  // flow in pers/s (DOOR / STAIR)

        VNode(String id, NType type, int floor, double x, double y, String label) {
            this.id = id; this.type = type; this.floor = floor;
            this.x = x;   this.y = y;       this.label = label;
        }
    }

    /** One directed edge between two nodes. */
    private static class VEdge {
        String id, from, to;
        VEdge(String id, String from, String to) {
            this.id = id; this.from = from; this.to = to;
        }
    }

    // ── Floor colour palette ──────────────────────────────

    private static final Color[][] FLOOR_FILL   = {
        {Color.web("#dbeafe"), Color.web("#3b82f6")},  // 0 RDC  fill / stroke
        {Color.web("#d1fae5"), Color.web("#10b981")},  // 1 1er
        {Color.web("#fde8d8"), Color.web("#f97316")},  // 2 2e
    };
    private static final String[] FLOOR_NAME = {"RDC", "1er", "2e"};

    // ── Canvas dimensions ─────────────────────────────────
    private static final double CW = 720, CH = 520;
    
    // ── State ─────────────────────────────────────────────

    private final Stage stage;
    private final GraphController controller;
    private Canvas canvas;
    private double zoom = 1.0;

    private static final double MIN_ZOOM = 0.5;
    private static final double MAX_ZOOM = 2.5;
    private static final double ZOOM_STEP = 0.1;

    private Label zoomLbl;
    private Timeline renderLoop;
    private Timeline fireTimer;

    private final List<VNode>  nodes = new ArrayList<>();
    private final List<VEdge>  edges = new ArrayList<>();

    private String fireNodeId = null;
    private final Set<String> fireSpread = new HashSet<>();

    private String selectedId = null;
    /** Current active tool: null, "addRoom", "addHall", "addStair", "addExit",
     *  "addEdge", "fire", "delete". */
    private String tool = null;
    private String edgeStart = null;

    /** Drag state */
    private String dragId  = null;
    private double dragOx  = 0, dragOy = 0;
    private boolean wasDragged = false;

    /** Floor filter: -1 = all */
    private int currentFloor = -1;

    private int nodeCounter = 0;

    // UI labels
    private Label fireStatusLbl;
    private VBox  infoPanelBox;

    private VBox statsPanel;

    private Label statNameLbl;
    private Label statTypeLbl;
    private Label statFloorLbl;
    private Label statOccupationLbl;
    private Label statPassedLbl;
    private Label statSpeedLbl;
    private Label statLinksLbl;
    private Label statStatusLbl;

    private Button playPauseBtn;
    private TextArea logArea;
    private boolean useFastestPath = false;
    private final Map<String, Button> toolBtns = new HashMap<>();
    private final Map<String, Button> floorBtns = new HashMap<>();

    // ── Constructor ───────────────────────────────────────

    public AdminView(Stage stage, GraphController controller) {
        this.stage      = stage;
        this.controller = controller;
    }

    // ── Public entry point ────────────────────────────────

    public void show() {
        initGraph();

        BorderPane root = new BorderPane();
        root.setTop(buildTopBar());
        root.setCenter(buildCenter());

        Scene scene = new Scene(root, CW + 260, CH + 44);
        stage.setTitle("CY SafeCampus — Administration");
        stage.setScene(scene);
        stage.show();

        // Journal capteurs comme dans le ZIP
        controller.setSensorEventCallback(event -> javafx.application.Platform.runLater(() -> {
            String icon = event.getSeverity() >= 4 ? "🔥"
                        : event.getSeverity() >= 3 ? "⚠"
                        : "ℹ";

            String location = event.getLocation() != null
                ? event.getLocation().getName()
                : "?";

            log(icon + " [" + event.getType() + "] " + location
                + " — sévérité " + event.getSeverity());
        }));

        startRenderLoop();
    }

    // ── Initial graph ─────────────────────────────────────

    private void initGraph() {
        nodes.clear(); edges.clear();
        fireNodeId = null; fireSpread.clear();
        selectedId = null; tool = null; edgeStart = null;
        nodeCounter = 0;

        // ── RDC ───────────────────────────────────────────
        addN("h0a", NType.HALL,  0, 280, 155, "Hall RDC",   50,  2);
        addN("r0a", NType.ROOM,  0, 100,  90, "Bureau 101", 20,  2);
        addN("r0b", NType.ROOM,  0, 100, 210, "Amphi 102",  80,  2);
        addN("r0c", NType.ROOM,  0, 460,  90, "LT 103",     30,  2);
        addN("d0a", NType.DOOR,  0, 190,  90, "Porte 1",    10,  2);
        addN("d0b", NType.DOOR,  0, 190, 210, "Porte 2",    10,  3);
        addN("d0c", NType.DOOR,  0, 370,  90, "Porte 3",    10,  2);
        addN("s0a", NType.STAIR, 0, 280, 290, "Escalier A",       20, 4);
        addN("px0", NType.DOOR,  0, 520, 155, "Porte Sortie RDC", 10, 5);
        addN("ex0", NType.EXIT,  0, 590, 155, "Sortie RDC",       50, 5);

        // ── 1er étage ─────────────────────────────────────
        addN("h1a", NType.HALL,  1, 280, 450, "Hall 1er",   40,  2);
        addN("r1a", NType.ROOM,  1, 100, 400, "Salle 201",  25,  2);
        addN("r1b", NType.ROOM,  1, 100, 500, "Salle 202",  25,  2);
        addN("r1c", NType.ROOM,  1, 460, 450, "Salle 203",  30,  2);
        addN("d1a", NType.DOOR,  1, 190, 400, "Porte 4",    10,  2);
        addN("d1b", NType.DOOR,  1, 190, 500, "Porte 5",    10,  2);
        addN("d1c", NType.DOOR,  1, 370, 450, "Porte 6",    10,  2);
        addN("s1a", NType.STAIR, 1, 440, 290, "Escalier B",       20, 4);
        addN("px1", NType.DOOR,  1, 520, 450, "Porte Sortie 1er", 10, 5);
        addN("ex1", NType.EXIT,  1, 590, 450, "Sortie 1er",       50, 5);        
        // ── Edges RDC ─────────────────────────────────────
        addE("r0a","d0a"); addE("d0a","h0a");
        addE("r0b","d0b"); addE("d0b","h0a");
        addE("h0a","d0c"); addE("d0c","r0c");
        addE("h0a","s0a"); addE("h0a","px0"); addE("px0","ex0");

        // ── Edges 1er ─────────────────────────────────────
        addE("r1a","d1a"); addE("d1a","h1a");
        addE("r1b","d1b"); addE("d1b","h1a");
        addE("h1a","d1c"); addE("d1c","r1c");
        addE("h1a","s1a"); addE("h1a","px1"); addE("px1","ex1");

        // ── Cross-floor ───────────────────────────────────
        addE("s0a","s1a");
    }

    private void addN(String id, NType type, int floor, double x, double y,
                      String label, int cap, double rate) {
        VNode n = new VNode(id, type, floor, x, y, label);
        n.cap = cap; n.rate = rate;
        nodes.add(n);
    }

    private void addE(String from, String to) {
        edges.add(new VEdge("e" + (++nodeCounter), from, to));
    }


    private boolean edgeExists(String a, String b) {
        for (VEdge e : edges) {
            if ((e.from.equals(a) && e.to.equals(b)) ||
                (e.from.equals(b) && e.to.equals(a))) {
                return true;
            }
        }
        return false;
    }

    private void addEIfMissing(String from, String to) {
        if (from == null || to == null || from.equals(to)) return;

        if (!edgeExists(from, to)) {
            addE(from, to);
        }
    }

    private VNode findDoorForExit(String exitId) {
        for (VEdge e : edges) {
            if (e.from.equals(exitId)) {
                VNode n = byId(e.to);
                if (n != null && n.type == NType.DOOR) {
                    return n;
                }
            }

            if (e.to.equals(exitId)) {
                VNode n = byId(e.from);
                if (n != null && n.type == NType.DOOR) {
                    return n;
                }
            }
        }

        return null;
    }

    private VNode createDoorForExit(VNode exit) {
        double doorX;

        // Si la sortie est assez loin du bord gauche, on met la porte à gauche.
        // Sinon, on la met à droite pour éviter qu'elle sorte du canvas.
        if (exit.x >= 80) {
            doorX = exit.x - 45;
        } else {
            doorX = exit.x + 45;
        }

        String did = newId("d");

        VNode door = new VNode(
            did,
            NType.DOOR,
            exit.floor,
            doorX,
            exit.y,
            "Porte " + exit.label
        );

        door.rate = 2;
        nodes.add(door);

        // La sortie est forcément reliée à sa porte
        addEIfMissing(did, exit.id);

        return door;
    }

    

    private String newId(String prefix) {
        return prefix + (++nodeCounter);
    }

    // ── Top bar ───────────────────────────────────────────

    private HBox buildTopBar() {
        Button backBtn = sBtn("← Retour", "#546e7a");
        backBtn.setOnAction(e -> goBack());

        Label title = new Label("Administrateur");
        title.setFont(Font.font("Sans", FontWeight.BOLD, 13));
        title.setTextFill(Color.web("#1a237e"));

        Button zoomOutBtn = sBtn("−", "#1565c0");
        Button zoomResetBtn = sBtn("100%", "#1565c0");
        Button zoomInBtn = sBtn("+", "#1565c0");

        zoomLbl = new Label("100%");
        zoomLbl.setStyle("-fx-font-size:11px;-fx-text-fill:#334155;-fx-padding:0 6;");

        zoomOutBtn.setOnAction(e -> setZoom(zoom - ZOOM_STEP));
        zoomResetBtn.setOnAction(e -> setZoom(1.0));
        zoomInBtn.setOnAction(e -> setZoom(zoom + ZOOM_STEP));

        HBox zoomBox = new HBox(4, zoomOutBtn, zoomResetBtn, zoomInBtn, zoomLbl);
        zoomBox.setAlignment(Pos.CENTER);

        Button saveBtn = sBtn("💾 Save", "#1565c0");
        Button loadBtn = sBtn("📂 Load", "#1565c0");
        saveBtn.setOnAction(e -> handleSave());
        loadBtn.setOnAction(e -> handleLoad());

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        HBox bar = new HBox(10, backBtn, title, sp, zoomBox, saveBtn, loadBtn);
        bar.setPadding(new Insets(7, 12, 7, 12));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color:#f8f9fa;-fx-border-color:#e8eaed;-fx-border-width:0 0 1 0;");

        return bar;
    }

    // ── Center : canvas + sidebar ─────────────────────────

    private HBox buildCenter() {
        canvas = new Canvas(CW, CH);
        canvas.setOnMousePressed(this::onMousePressed);
        canvas.setOnMouseDragged(this::onMouseDragged);
        canvas.setOnMouseReleased(this::onMouseReleased);
        canvas.setOnMouseClicked(this::onMouseClicked);
        canvas.setOnScroll(e -> {
        if (e.isControlDown()) {
            if (e.getDeltaY() > 0) {
                setZoom(zoom + ZOOM_STEP);
            } else {
                setZoom(zoom - ZOOM_STEP);
            }
            e.consume();
        }
    });

        StackPane canvasWrap = new StackPane(canvas);
        canvasWrap.setBackground(new Background(new BackgroundFill(
            Color.web("#f0f4f8"), CornerRadii.EMPTY, Insets.EMPTY)));

        // Le conteneur du graphe peut grandir avec la fenêtre
        canvasWrap.setMinSize(CW, CH);
        canvasWrap.setPrefSize(CW, CH);
        canvasWrap.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        // Le canvas prend toute la place disponible
        canvas.widthProperty().bind(canvasWrap.widthProperty());
        canvas.heightProperty().bind(canvasWrap.heightProperty());

        VBox sidebar = buildSidebar();

        HBox center = new HBox(canvasWrap, sidebar);
        HBox.setHgrow(canvasWrap, Priority.ALWAYS);

        return center;
    }

    // ── Sidebar ───────────────────────────────────────────

    private VBox buildSidebar() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(12));
        panel.setBackground(new Background(new BackgroundFill(
            Color.web("#f8f9fa"), CornerRadii.EMPTY, Insets.EMPTY
        )));

        // ── Ajouter ───────────────────────────────────────
        panel.getChildren().add(sSection("Ajouter"));

        GridPane addBtns = new GridPane();
        addBtns.setHgap(6);
        addBtns.setVgap(6);
        addBtns.setPadding(new Insets(0, 0, 8, 0));
        addBtns.setMaxWidth(Double.MAX_VALUE);

        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(50);
        c1.setHgrow(Priority.ALWAYS);

        ColumnConstraints c2 = new ColumnConstraints();
        c2.setPercentWidth(50);
        c2.setHgrow(Priority.ALWAYS);

        addBtns.getColumnConstraints().addAll(c1, c2);

        Button bRoom  = toolBtn("btn-addRoom",  "Ajouter salle",     "addRoom");
        Button bHall  = toolBtn("btn-addHall",  "Ajouter hall",      "addHall");
        Button bStair = toolBtn("btn-addStair", "Ajouter escalier",  "addStair");
        Button bExit  = toolBtn("btn-addExit",  "Ajouter sortie",    "addExit");
        Button bEdge  = toolBtn("btn-addEdge",  "Créer lien",        "addEdge");
        Button bDel   = toolBtn("btn-delete",   "Supprimer",         "delete");

        for (Button b : List.of(bRoom, bHall, bStair, bExit, bEdge, bDel)) {
            b.setMaxWidth(Double.MAX_VALUE);
            b.setMinHeight(30);
            b.setWrapText(true);
            GridPane.setHgrow(b, Priority.ALWAYS);
        }

        addBtns.add(bRoom,  0, 0);
        addBtns.add(bHall,  1, 0);
        addBtns.add(bStair, 0, 1);
        addBtns.add(bExit,  1, 1);
        addBtns.add(bEdge,  0, 2);
        addBtns.add(bDel,   1, 2);

        panel.getChildren().add(addBtns);

        // ── Simulation comme dans le ZIP ─────────────────────
        panel.getChildren().add(new Separator());
        panel.getChildren().add(sSection("Simulation"));

        playPauseBtn = sBtn("▶ Play", "#2e7d32");
        Button stepBtn = sBtn("⏭ Step", "#546e7a");

        playPauseBtn.setMaxWidth(Double.MAX_VALUE);
        stepBtn.setMaxWidth(Double.MAX_VALUE);

        playPauseBtn.setOnAction(e -> {
            if (controller.isRunning()) {
                controller.pause();
                playPauseBtn.setText("▶ Play");
                log("Simulation en pause");
            } else {
                controller.play();
                playPauseBtn.setText("⏸ Pause");
                log("Simulation lancée");
            }
        });

        stepBtn.setOnAction(e -> {
            controller.step();
            log("Simulation avancée d'un pas");
        });

        HBox simBtns = new HBox(8, playPauseBtn, stepBtn);

        Label speedLbl = new Label("Vitesse :");
        speedLbl.setStyle("-fx-font-size:11px;-fx-text-fill:#64748b;");

        Slider speedSlider = new Slider(50, 2000, 500);
        speedSlider.setMaxWidth(Double.MAX_VALUE);
        speedSlider.valueProperty().addListener((o, ov, nv) ->
            controller.setSpeed((int)(2050 - nv.doubleValue()))
        );

        ToggleButton pathToggle = new ToggleButton("Chemin : plus court");
        pathToggle.setMaxWidth(Double.MAX_VALUE);
        pathToggle.setStyle(defaultBtnStyle());
        pathToggle.setOnAction(e -> {
            useFastestPath = pathToggle.isSelected();
            pathToggle.setText(useFastestPath ? "Chemin : plus rapide" : "Chemin : plus court");
            log(useFastestPath ? "Calcul par chemin le plus rapide" : "Calcul par chemin le plus court");
        });

        panel.getChildren().addAll(simBtns, speedLbl, speedSlider, pathToggle);

        // ── Alertes ──────────────────────────────────────────
        panel.getChildren().add(new Separator());
        panel.getChildren().add(sSection("Alertes"));

        Button alarmBtn = sBtn("Déclencher alarme", "#c62828");
        Button fireBtn  = sBtn("Placer feu sur graphe", "#e24b4a");
        Button resetBtn = sBtn("Réinitialiser feu", "#546e7a");

        alarmBtn.setMaxWidth(Double.MAX_VALUE);
        fireBtn.setMaxWidth(Double.MAX_VALUE);
        resetBtn.setMaxWidth(Double.MAX_VALUE);

        alarmBtn.setOnAction(e -> {
            controller.triggerFireAlert();
            fireStatusLbl.setText("ALERTE INCENDIE");
            fireStatusLbl.setStyle("-fx-font-size:11px;-fx-text-fill:#dc2626;-fx-font-weight:bold;");
            log("ALERTE INCENDIE déclenchée");
        });

        fireBtn.setOnAction(e -> {
            setTool("fire");
            showInfo("Cliquez sur un élément du graphe pour placer le feu");
            log("Mode placement du feu activé");
        });

        resetBtn.setOnAction(e -> {
            resetFire();
            log("Feu réinitialisé");
        });

        fireStatusLbl = new Label("Aucun feu détecté");
        fireStatusLbl.setStyle("-fx-font-size:11px;-fx-text-fill:#64748b;");

        panel.getChildren().addAll(alarmBtn, fireBtn, resetBtn, fireStatusLbl);

        // ── Agents comme dans le ZIP ─────────────────────────
        panel.getChildren().add(new Separator());
        panel.getChildren().add(sSection("Agents"));

        Button addAgentBtn  = sBtn("Ajouter agent", "#1565c0");
        Button editAgentBtn = sBtn("Modifier agent", "#0277bd");
        Button rmAgentBtn   = sBtn("Supprimer agent", "#546e7a");
        Button rndAgentsBtn = sBtn("Agents aléatoires", "#6a1b9a");

        for (Button b : List.of(addAgentBtn, editAgentBtn, rmAgentBtn, rndAgentsBtn)) {
            b.setMaxWidth(Double.MAX_VALUE);
        }

        addAgentBtn.setOnAction(e -> handleAddAgent());
        editAgentBtn.setOnAction(e -> handleEditAgent());
        rmAgentBtn.setOnAction(e -> handleRemoveAgent());
        rndAgentsBtn.setOnAction(e -> handleRandomAgents());

        panel.getChildren().addAll(addAgentBtn, editAgentBtn, rmAgentBtn, rndAgentsBtn);

        // ── Étage affiché ────────────────────────────────────
        panel.getChildren().add(new Separator());
        panel.getChildren().add(sSection("Étage affiché"));

        Button fAll = floorBtn("fl-all", "Tous", -1);
        Button f0   = floorBtn("fl-0",   "RDC",   0);
        Button f1   = floorBtn("fl-1",   "1er",   1);
        Button f2   = floorBtn("fl-2",   "2e",    2);

        HBox floorBtnRow = new HBox(4, fAll, f0, f1, f2);
        panel.getChildren().add(floorBtnRow);
        fAll.fire();


        // ── Sélection / statistiques ─────────────────────────
        panel.getChildren().add(new Separator());
        panel.getChildren().add(sSection("Sélection"));

        statNameLbl = new Label("Aucun élément sélectionné");
        statNameLbl.setFont(Font.font("Sans", FontWeight.BOLD, 12));
        statNameLbl.setStyle("-fx-text-fill:#334155;");

        statTypeLbl       = statLbl("Type : —");
        statFloorLbl      = statLbl("Étage : —");
        statOccupationLbl = statLbl("Occupation : —");
        statPassedLbl     = statLbl("Agents passés : —");
        statSpeedLbl      = statLbl("Vitesse moy. : —");
        statLinksLbl      = statLbl("Connexions : —");
        statStatusLbl     = statLbl("Statut : —");

        statsPanel = new VBox(
            4,
            statNameLbl,
            statTypeLbl,
            statFloorLbl,
            statOccupationLbl,
            statPassedLbl,
            statSpeedLbl,
            statLinksLbl,
            statStatusLbl
        );

        statsPanel.setPadding(new Insets(8));
        statsPanel.setStyle(
            "-fx-background-color:white;" +
            "-fx-border-color:#e0e0e0;" +
            "-fx-border-radius:6;" +
            "-fx-background-radius:6;"
        );

        panel.getChildren().add(statsPanel);





        // ── Propriétés ───────────────────────────────────────
        panel.getChildren().add(new Separator());
        panel.getChildren().add(sSection("Propriétés"));

        infoPanelBox = new VBox(4);
        infoPanelBox.setPadding(new Insets(4, 0, 8, 0));

        Label hint = new Label("Cliquez sur un élément");
        hint.setStyle("-fx-font-size:11px;-fx-text-fill:#94a3b8;");
        infoPanelBox.getChildren().add(hint);

        panel.getChildren().add(infoPanelBox);

        // ── Journal capteurs ─────────────────────────────────
        panel.getChildren().add(new Separator());
        panel.getChildren().add(sSection("Journal capteurs"));

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefRowCount(6);
        logArea.setWrapText(true);
        logArea.setStyle("-fx-font-size:10px;-fx-font-family:monospace;");

        panel.getChildren().add(logArea);

        // ── Légende ──────────────────────────────────────────
        panel.getChildren().add(new Separator());
        panel.getChildren().add(buildLegend());

        // ── ScrollPane : le curseur pour monter / descendre ───
        ScrollPane scroll = new ScrollPane(panel);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setPannable(true);
        scroll.setStyle("-fx-background:#f8f9fa;-fx-background-color:#f8f9fa;");
        scroll.setPrefWidth(260);

        VBox wrapper = new VBox(scroll);
        wrapper.setPrefWidth(260);
        wrapper.setMinWidth(260);
        wrapper.setStyle("-fx-background-color:#f8f9fa;-fx-border-color:#e8eaed;-fx-border-width:0 0 0 1;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        return wrapper;
    }

    private Label sSection(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size:10px;-fx-font-weight:bold;-fx-text-fill:#94a3b8;" +
                   "-fx-padding:8 10 4 10;-fx-text-transform:uppercase;");
        return l;
    }


    private Label statLbl(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size:11px;-fx-text-fill:#64748b;");
        l.setWrapText(true);
        return l;
    }

    private Button toolBtn(String id, String label, String toolName) {
        Button b = new Button(label);
        b.setStyle(defaultBtnStyle());
        b.setOnAction(e -> setTool(toolName));
        toolBtns.put(id, b);
        return b;
    }

    private Button floorBtn(String id, String label, int floor) {
        Button b = new Button(label);
        b.setStyle(defaultBtnStyle());
        b.setOnAction(e -> setFloor(floor));
        floorBtns.put(id, b);
        return b;
    }

    private VBox buildLegend() {
        VBox box = new VBox(5);
        box.setPadding(new Insets(8, 10, 10, 10));
        box.setStyle("-fx-border-color:#e8eaed;-fx-border-width:1 0 0 0;");

        Label title = new Label("Légende");
        title.setStyle("-fx-font-size:10px;-fx-font-weight:bold;-fx-text-fill:#94a3b8;");
        box.getChildren().add(title);

        box.getChildren().addAll(
            legRow(roomIcon(0),  "Salle RDC"),
            legRow(roomIcon(1),  "Salle 1er étage"),
            legRow(roomIcon(2),  "Salle 2e étage"),
            legRow(doorIcon(),   "Porte"),
            legRow(hallIcon(),   "Hall"),
            legRow(stairIcon(),  "Escalier"),
            legRow(exitIcon(),   "Sortie"),
            legRow(fireIcon(),   "Feu / propagation")
        );

        Label note = new Label("Arêtes = liens simples sans étiquette");
        note.setStyle("-fx-font-size:9px;-fx-text-fill:#94a3b8;-fx-padding:4 0 0 0;");
        box.getChildren().add(note);

        return box;
    }


    private HBox legRow(javafx.scene.Node shape, String desc) {
        StackPane iconBox = new StackPane(shape);
        iconBox.setMinWidth(26);
        iconBox.setPrefWidth(26);
        iconBox.setAlignment(Pos.CENTER);

        Label d = new Label(desc);
        d.setStyle("-fx-font-size:10px;-fx-text-fill:#64748b;");

        HBox row = new HBox(8, iconBox, d);
        row.setAlignment(Pos.CENTER_LEFT);

        return row;
    }


    private Shape roomIcon(int floor) {
        int f = Math.min(floor, FLOOR_FILL.length - 1);

        Rectangle r = new Rectangle(18, 11);
        r.setArcWidth(4);
        r.setArcHeight(4);
        r.setFill(FLOOR_FILL[f][0]);
        r.setStroke(FLOOR_FILL[f][1]);
        r.setStrokeWidth(1.4);

        return r;
    }

    private Shape doorIcon() {
        Rectangle r = new Rectangle(13, 13);
        r.setFill(Color.web("#f8fafc"));
        r.setStroke(Color.web("#6b7280"));
        r.setStrokeWidth(1.4);

        return r;
    }

    private Shape hallIcon() {
        Circle c = new Circle(8);
        c.setFill(Color.web("#ede9fe"));
        c.setStroke(Color.web("#7c3aed"));
        c.setStrokeWidth(1.4);

        return c;
    }

    private Shape stairIcon() {
        Polygon p = new Polygon(
            9.0, 0.0,
            18.0, 16.0,
            0.0, 16.0
        );
        p.setFill(Color.web("#fef9c3"));
        p.setStroke(Color.web("#ca8a04"));
        p.setStrokeWidth(1.4);

        return p;
    }

    private Shape exitIcon() {
        Polygon p = new Polygon(
            10.0, 0.0,
            20.0, 10.0,
            10.0, 20.0,
            0.0, 10.0
        );
        p.setFill(Color.web("#dcfce7"));
        p.setStroke(Color.web("#16a34a"));
        p.setStrokeWidth(1.4);

        return p;
    }

    private Shape fireIcon() {
        Rectangle r = new Rectangle(14, 14);
        r.setFill(Color.web("#ff3333"));
        r.setStroke(Color.web("#aa0000"));
        r.setStrokeWidth(1.4);

        return r;
    }

    private void setZoom(double newZoom) {
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, newZoom));

        if (zoomLbl != null) {
            zoomLbl.setText(Math.round(zoom * 100) + "%");
        }
    }

    private double toGraphX(double screenX) {
        return screenX / zoom;
    }

    private double toGraphY(double screenY) {
        return screenY / zoom;
    }


    // ── Render loop ───────────────────────────────────────

    private void startRenderLoop() {
        renderLoop = new Timeline(new KeyFrame(Duration.millis(16), e -> render()));
        renderLoop.setCycleCount(Timeline.INDEFINITE);
        renderLoop.play();
    }

    // ── Main draw ─────────────────────────────────────────

    private void render() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        double graphW = w / zoom;
        double graphH = h / zoom;

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);

        gc.save();
        gc.scale(zoom, zoom);

        // Fond quadrillé
        gc.setStroke(Color.web("#e2e8f0"));
        gc.setLineWidth(0.5);

        for (double x = 0; x < graphW; x += 40) {
            gc.strokeLine(x, 0, x, graphH);
        }

        for (double y = 0; y < graphH; y += 40) {
            gc.strokeLine(0, y, graphW, y);
        }

        List<VNode> visible = visibleNodes();
        Set<String> visIds = new HashSet<>();
        visible.forEach(n -> visIds.add(n.id));

        // Séparation des étages
        if (currentFloor == -1) {
            gc.setStroke(Color.web("#cbd5e1"));
            gc.setLineWidth(1);
            gc.setLineDashes(6, 4);
            gc.strokeLine(0, 330, graphW, 330);
            gc.setLineDashes(0);

            gc.setFill(Color.web("#94a3b8"));
            gc.setFont(Font.font("Sans", 10));
            gc.fillText("RDC", 8, 320);
            gc.fillText("1er étage", 8, 345);
        }

        // Liens
        gc.setStroke(Color.web("#94a3b8"));
        gc.setLineWidth(1.5);

        for (VEdge e : edges) {
            VNode a = byId(e.from);
            VNode b = byId(e.to);

            if (a == null || b == null) continue;
            if (!visIds.contains(a.id) || !visIds.contains(b.id)) continue;

            gc.strokeLine(a.x, a.y, b.x, b.y);
        }

        // Composants
        for (VNode n : visible) {
            drawNode(gc, n);
        }

        gc.restore();
        updateSelectionStatsPanel();
    }

    private String fitText(String value, Font font, double maxWidth) {
        if (value == null) return "";

        Text helper = new Text(value);
        helper.setFont(font);

        if (helper.getLayoutBounds().getWidth() <= maxWidth) {
            return value;
        }

        String ellipsis = "…";

        for (int len = value.length() - 1; len > 0; len--) {
            String candidate = value.substring(0, len) + ellipsis;
            helper.setText(candidate);

            if (helper.getLayoutBounds().getWidth() <= maxWidth) {
                return candidate;
            }
        }

        return "";
    }


    private String compactName(String label, String wordToRemove, String prefix) {
        if (label == null || label.isBlank()) {
            return prefix + ".";
        }

        String name = label.trim();

        // Enlève le mot "Sortie" ou "Escalier" au début, sans tenir compte des majuscules
        name = name.replaceFirst("(?i)^" + wordToRemove + "\\s*", "").trim();

        if (name.isEmpty()) {
            return prefix + ".";
        }

        return prefix + "." + name;
    }


    private void drawNode(GraphicsContext gc, VNode n) {
        boolean onFire  = n.id.equals(fireNodeId);
        boolean spread  = fireSpread.contains(n.id);
        boolean sel     = n.id.equals(selectedId);

        Color fill, stroke;
        if (onFire) {
            fill   = Color.web("#ff3333");
            stroke = Color.web("#aa0000");
        } else if (spread) {
            fill   = Color.web("#ff8800");
            stroke = Color.web("#cc5500");
        } else {
            int f = Math.min(n.floor, FLOOR_FILL.length - 1);
            fill   = switch (n.type) {
                case DOOR  -> Color.web("#f8fafc");
                case HALL  -> Color.web("#ede9fe");
                case STAIR -> Color.web("#fef9c3");
                case EXIT  -> Color.web("#dcfce7");
                default    -> FLOOR_FILL[f][0];
            };
            stroke = switch (n.type) {
                case DOOR  -> Color.web("#6b7280");
                case HALL  -> Color.web("#7c3aed");
                case STAIR -> Color.web("#ca8a04");
                case EXIT  -> Color.web("#16a34a");
                default    -> FLOOR_FILL[f][1];
            };
        }

        gc.setFill(fill);
        gc.setStroke(stroke);
        gc.setLineWidth(sel ? 2.5 : 1.8);

        switch (n.type) {
            case ROOM  -> { gc.fillRoundRect(n.x-34, n.y-20, 68, 40, 6, 6);
                            gc.strokeRoundRect(n.x-34, n.y-20, 68, 40, 6, 6); }
            case HALL  -> { gc.fillOval(n.x-24, n.y-24, 48, 48);
                            gc.strokeOval(n.x-24, n.y-24, 48, 48); }
            case STAIR -> { double[] xs = {n.x, n.x+22, n.x-22};
                            double[] ys = {n.y-22, n.y+16, n.y+16};
                            gc.fillPolygon(xs, ys, 3);
                            gc.strokePolygon(xs, ys, 3); }
            case DOOR  -> { gc.fillRect(n.x-9, n.y-9, 18, 18);
                            gc.strokeRect(n.x-9, n.y-9, 18, 18); }
            case EXIT  -> { double[] xs = {n.x, n.x+20, n.x, n.x-20};
                            double[] ys = {n.y-20, n.y, n.y+20, n.y};
                            gc.fillPolygon(xs, ys, 4);
                            gc.strokePolygon(xs, ys, 4); }
        }

        // selection ring
        if (sel) {
            gc.setStroke(Color.web("#3b82f6"));
            gc.setLineWidth(2);
            gc.setLineDashes(4, 2);
            gc.strokeOval(n.x - 32, n.y - 32, 64, 64);
            gc.setLineDashes(0);
        }

        // Labels
        Color txtColor = (onFire || spread) ? Color.WHITE
                    : n.type == NType.HALL  ? Color.web("#3b0764")
                    : n.type == NType.STAIR ? Color.web("#78350f")
                    : n.type == NType.EXIT  ? Color.web("#14532d")
                    : FLOOR_FILL[Math.min(n.floor, FLOOR_FILL.length - 1)][1];

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);

        if (n.type == NType.DOOR) {
            // Débit centré dans la porte
            gc.setFill(txtColor);
            gc.setFont(Font.font("Sans", FontWeight.BOLD, 9));
            String rateStr = String.format("%.0f", n.rate);
            gc.fillText(rateStr, n.x, n.y);

            gc.setFill(Color.web("#94a3b8"));
            gc.setFont(Font.font("Sans", 8));
            gc.fillText("p/s", n.x, n.y + 17);

        } else {
            String shortName = n.label.length() > 13
                ? n.label.substring(0, 12) + "…"
                : n.label;

            gc.setFont(Font.font("Sans", FontWeight.BOLD, 9));
            gc.setFill(txtColor);

            if (n.type == NType.STAIR) {
                Font stairFont = Font.font("Sans", FontWeight.BOLD, 8);
                String stairName = compactName(n.label, "Escalier", "E");
                stairName = fitText(stairName, stairFont, 32);

                gc.setFont(stairFont);
                gc.setFill(txtColor);
                gc.fillText(stairName, n.x, n.y + 8);

                // Débit en dessous du triangle
                gc.setFont(Font.font("Sans", 8));
                gc.setFill(Color.web("#78350f"));
                String rs = String.format("%.0f p/s", n.rate);
                gc.fillText(rs, n.x, n.y + 30);

            } else if (n.type == NType.HALL) {
                // Nom bien centré dans le hall
                gc.fillText(shortName, n.x, n.y - 5);

                // Capacité sous le nom, sans chevauchement
                gc.setFont(Font.font("Sans", 8));
                gc.setFill(Color.web("#6b7280"));
                gc.fillText("cap: " + n.cap, n.x, n.y + 10);

              } else if (n.type == NType.EXIT) {
                Font exitFont = Font.font("Sans", FontWeight.BOLD, 8);
                String exitName = compactName(n.label, "Sortie", "S");
                exitName = fitText(exitName, exitFont, 30);

                gc.setFont(exitFont);
                gc.setFill(txtColor);
                gc.fillText(exitName, n.x, n.y);

            } else if (n.type == NType.ROOM) {
                // Nom centré dans la salle
                gc.fillText(shortName, n.x, n.y);

                // Capacité au-dessus à droite
                gc.setFont(Font.font("Sans", 8));
                gc.setFill(Color.web("#6b7280"));
                gc.fillText("cap: " + n.cap, n.x + 34, n.y - 25);
            }
        }

        // Remet le texte par défaut pour éviter de casser les autres dessins
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.BASELINE);

        // 🔥 fire emoji marker
        if (onFire) {
            gc.setFont(Font.font("Sans", 14));
            gc.setFill(Color.BLACK);
            gc.fillText("🔥", n.x + 30, n.y - 20);
        }
    }

    // ── Mouse handlers ────────────────────────────────────

    private void onMousePressed(javafx.scene.input.MouseEvent e) {
        if (!e.isPrimaryButtonDown()) return;

        double mx = toGraphX(e.getX());
        double my = toGraphY(e.getY());

        wasDragged = false;

        VNode hit = hitNode(mx, my);

        if (hit != null && tool == null) {
            dragId = hit.id;
            dragOx = mx;
            dragOy = my;
        }
    }

    private void onMouseDragged(javafx.scene.input.MouseEvent e) {
        if (dragId == null) return;

        wasDragged = true;

        double mx = toGraphX(e.getX());
        double my = toGraphY(e.getY());

        VNode n = byId(dragId);

        if (n != null) {
            n.x += mx - dragOx;
            n.y += my - dragOy;

            double graphW = canvas.getWidth() / zoom;
            double graphH = canvas.getHeight() / zoom;

            n.x = Math.max(30, Math.min(graphW - 30, n.x));
            n.y = Math.max(30, Math.min(graphH - 30, n.y));
        }

        dragOx = mx;
        dragOy = my;
    }

    private void onMouseReleased(javafx.scene.input.MouseEvent e) {
        dragId = null;
    }

    private void onMouseClicked(javafx.scene.input.MouseEvent e) {
        if (wasDragged) { wasDragged = false; return; }
        double mx = toGraphX(e.getX());
        double my = toGraphY(e.getY());

        VNode hit = hitNode(mx, my);

        // ── Tool: fire ────────────────────────────────────
        if ("fire".equals(tool)) {
            if (hit != null) {
                fireNodeId = hit.id; fireSpread.clear();
                startFire(); setTool(null);
            }
            return;
        }

        // ── Tool: delete ──────────────────────────────────
        if ("delete".equals(tool)) {
            if (hit != null) {
                deleteNode(hit.id); setTool(null);
                clearInfoPanel();
            }
            return;
        }

        // ── Tool: addEdge ─────────────────────────────────
        if ("addEdge".equals(tool)) {
            if (hit != null) {
                if (edgeStart == null) {
                    edgeStart = hit.id;
                    showInfo("Cliquez sur le 2e nœud à connecter");
                } else if (!edgeStart.equals(hit.id)) {
                    VNode a = byId(edgeStart), b = hit;
                    if (a.type == NType.HALL && b.type == NType.HALL) {
                        // auto-insert a door between two halls
                        String did = newId("d");
                        VNode door = new VNode(did, NType.DOOR, a.floor,
                            (a.x + b.x) / 2, (a.y + b.y) / 2, "Porte");
                        door.rate = 2;
                        nodes.add(door);
                        addE(edgeStart, did); addE(did, hit.id);
                    } else if (a.type == NType.EXIT || b.type == NType.EXIT) {
                        VNode exit = a.type == NType.EXIT ? a : b;
                        VNode other = a.type == NType.EXIT ? b : a;

                        VNode door = findDoorForExit(exit.id);

                        if (door == null) {
                            door = createDoorForExit(exit);
                        }

                        // Au lieu de relier directement à la sortie,
                        // on relie l'autre composant à la porte de la sortie.
                        addEIfMissing(other.id, door.id);
                        addEIfMissing(door.id, exit.id);

                    } else {
                        addE(edgeStart, hit.id);
                    }
                    edgeStart = null; setTool(null);
                }
            }
            return;
        }

        // ── Tool: addRoom / addHall / addStair / addExit ──
        if (hit == null) {
            switch (tool == null ? "" : tool) {
                case "addRoom" -> {
                    String rid = newId("r"), did = newId("d");
                    VNode room = new VNode(rid, NType.ROOM,
                        currentFloor < 0 ? 0 : currentFloor, mx, my, "Salle");
                    room.cap = 20;
                    nodes.add(room);
                    VNode door = new VNode(did, NType.DOOR,
                        currentFloor < 0 ? 0 : currentFloor, mx + 50, my, "Porte");
                    door.rate = 2;
                    nodes.add(door);
                    addE(rid, did);
                    selectedId = rid; setTool(null); showNodeInfo(rid);
                }
                case "addHall" -> {
                    String hid = newId("h");
                    VNode hall = new VNode(hid, NType.HALL,
                        currentFloor < 0 ? 0 : currentFloor, mx, my, "Hall");
                    hall.cap = 40;
                    nodes.add(hall); setTool(null);
                }
                case "addStair" -> {
                    String sid = newId("s");
                    VNode stair = new VNode(sid, NType.STAIR,
                        currentFloor < 0 ? 0 : currentFloor, mx, my, "Escalier");
                    stair.rate = 4;
                    nodes.add(stair); setTool(null);
                }
                case "addExit" -> {
                    int floor = currentFloor < 0 ? 0 : currentFloor;

                    String xid = newId("x");

                    VNode exit = new VNode(
                        xid,
                        NType.EXIT,
                        floor,
                        mx,
                        my,
                        "Sortie"
                    );

                    nodes.add(exit);

                    // Création automatique de la porte attachée à la sortie
                    createDoorForExit(exit);

                    selectedId = xid;
                    setTool(null);
                    showNodeInfo(xid);
                }
                default -> {
                    selectedId = null; clearInfoPanel();
                }
            }
            return;
        }

        // ── No tool: select node ──────────────────────────
        selectedId = hit.id;
        showNodeInfo(hit.id);
    }

    // ── Tool / floor management ───────────────────────────

    private void setTool(String t) {
        tool = t; edgeStart = null;
        toolBtns.forEach((id, btn) -> btn.setStyle(defaultBtnStyle()));
        String active = switch (t == null ? "" : t) {
            case "addRoom"  -> "btn-addRoom";
            case "addHall"  -> "btn-addHall";
            case "addStair" -> "btn-addStair";
            case "addExit"  -> "btn-addExit";
            case "addEdge"  -> "btn-addEdge";
            case "delete"   -> "btn-delete";
            case "fire"     -> "";
            default         -> "";
        };
        if (!active.isEmpty() && toolBtns.containsKey(active))
            toolBtns.get(active).setStyle(activeBtnStyle());
        canvas.setCursor(t != null ? Cursor.CROSSHAIR : Cursor.DEFAULT);
    }

    private void setFloor(int f) {
        currentFloor = f;
        floorBtns.forEach((id, btn) -> btn.setStyle(defaultBtnStyle()));
        String active = switch (f) {
            case -1 -> "fl-all";
            case  0 -> "fl-0";
            case  1 -> "fl-1";
            case  2 -> "fl-2";
            default -> "fl-all";
        };
        if (floorBtns.containsKey(active))
            floorBtns.get(active).setStyle(activeBtnStyle());
    }

    // ── Fire simulation ───────────────────────────────────

    private void startFire() {
        if (fireTimer != null) fireTimer.stop();
        fireTimer = new Timeline(new KeyFrame(Duration.millis(1800), e -> spreadFire()));
        fireTimer.setCycleCount(Timeline.INDEFINITE);
        fireTimer.play();
        updateFireStatus();
        // Sync with Java model
        controller.triggerFireAlert();
    }

    private void spreadFire() {
        List<String> frontier = new ArrayList<>();
        if (fireNodeId != null) frontier.add(fireNodeId);
        frontier.addAll(fireSpread);
        Set<String> candidates = new LinkedHashSet<>();
        for (String fid : frontier) {
            for (VEdge e : edges) {
                if (e.from.equals(fid) && !fireSpread.contains(e.to) && !e.to.equals(fireNodeId))
                    candidates.add(e.to);
                if (e.to.equals(fid) && !fireSpread.contains(e.from) && !e.from.equals(fireNodeId))
                    candidates.add(e.from);
            }
        }
        if (!candidates.isEmpty()) {
            String[] arr = candidates.toArray(new String[0]);
            String picked = arr[(int)(Math.random() * arr.length)];
            fireSpread.add(picked);
        }
        updateFireStatus();
    }

    private void resetFire() {
        if (fireTimer != null) { fireTimer.stop(); fireTimer = null; }
        fireNodeId = null; fireSpread.clear();
        updateFireStatus();
        controller.reset();
    }

    private void updateFireStatus() {
        if (fireNodeId == null) {
            fireStatusLbl.setText("Aucun feu détecté");
            fireStatusLbl.setStyle("-fx-font-size:11px;-fx-text-fill:#64748b;");
        } else {
            VNode fn = byId(fireNodeId);
            fireStatusLbl.setText("🔥 " + (fn != null ? fn.label : "?")
                + " — " + fireSpread.size() + " zone(s)");
            fireStatusLbl.setStyle("-fx-font-size:11px;-fx-text-fill:#dc2626;");
        }
    }

    // ── Node deletion ─────────────────────────────────────

    private void deleteNode(String id) {
        nodes.removeIf(n -> n.id.equals(id));
        edges.removeIf(e -> e.from.equals(id) || e.to.equals(id));
        if (id.equals(fireNodeId)) { fireNodeId = null; fireSpread.clear(); }
        fireSpread.remove(id);
        if (id.equals(selectedId)) selectedId = null;
    }

    // ── Info panel ────────────────────────────────────────

    private void showNodeInfo(String id) {
        VNode n = byId(id);
        if (n == null) { clearInfoPanel(); return; }

        infoPanelBox.getChildren().clear();

        Label typeLbl = new Label(n.type.name() + " — " +
            (n.floor < FLOOR_NAME.length ? FLOOR_NAME[n.floor] : "Étage " + n.floor));
        typeLbl.setStyle("-fx-font-size:11px;-fx-font-weight:bold;-fx-text-fill:#334155;");
        infoPanelBox.getChildren().add(typeLbl);

        // Name
        infoPanelBox.getChildren().add(propRow("Nom", n.label, val -> {
            n.label = val; }));

        // Floor
        ComboBox<String> flCombo = new ComboBox<>();
        for (String fn : FLOOR_NAME) flCombo.getItems().add(fn);
        flCombo.getSelectionModel().select(Math.min(n.floor, FLOOR_NAME.length-1));
        flCombo.setStyle("-fx-font-size:11px;");
        flCombo.setOnAction(e -> n.floor = flCombo.getSelectionModel().getSelectedIndex());
        infoPanelBox.getChildren().add(labeledControl("Étage", flCombo));

        // Capacity
        if (n.type == NType.ROOM || n.type == NType.HALL) {
            infoPanelBox.getChildren().add(intRow("Capacité (pers)", n.cap, val -> n.cap = val));
        }
        // Rate
        if (n.type == NType.DOOR || n.type == NType.STAIR) {
            infoPanelBox.getChildren().add(dblRow("Débit (pers/s)", n.rate, val -> n.rate = val));
        }

        // Delete
        Button del = sBtn("✕ Supprimer", "#ef4444");
        del.setOnAction(e -> { deleteNode(id); clearInfoPanel(); });
        infoPanelBox.getChildren().add(del);
    }

    private HBox propRow(String label, String value, java.util.function.Consumer<String> setter) {
        Label lbl = new Label(label); lbl.setMinWidth(80);
        lbl.setStyle("-fx-font-size:11px;-fx-text-fill:#64748b;");
        TextField tf = new TextField(value); tf.setPrefWidth(110);
        tf.setStyle("-fx-font-size:11px;");
        tf.setOnAction(e -> setter.accept(tf.getText()));
        tf.focusedProperty().addListener((o, ov, nv) -> { if (!nv) setter.accept(tf.getText()); });
        return new HBox(6, lbl, tf);
    }

    private HBox intRow(String label, int value, java.util.function.Consumer<Integer> setter) {
        Label lbl = new Label(label); lbl.setMinWidth(90);
        lbl.setStyle("-fx-font-size:11px;-fx-text-fill:#64748b;");
        TextField tf = new TextField(String.valueOf(value)); tf.setPrefWidth(70);
        tf.setStyle("-fx-font-size:11px;");
        Runnable apply = () -> {
            try { setter.accept(Integer.parseInt(tf.getText().trim())); } catch (Exception ignored) {}
        };
        tf.setOnAction(e -> apply.run());
        tf.focusedProperty().addListener((o, ov, nv) -> { if (!nv) apply.run(); });
        return new HBox(6, lbl, tf);
    }

    private HBox dblRow(String label, double value, java.util.function.Consumer<Double> setter) {
        Label lbl = new Label(label); lbl.setMinWidth(90);
        lbl.setStyle("-fx-font-size:11px;-fx-text-fill:#64748b;");
        TextField tf = new TextField(String.format("%.1f", value)); tf.setPrefWidth(70);
        tf.setStyle("-fx-font-size:11px;");
        Runnable apply = () -> {
            try { setter.accept(Double.parseDouble(tf.getText().trim())); } catch (Exception ignored) {}
        };
        tf.setOnAction(e -> apply.run());
        tf.focusedProperty().addListener((o, ov, nv) -> { if (!nv) apply.run(); });
        return new HBox(6, lbl, tf);
    }

    private HBox labeledControl(String label, javafx.scene.Node ctrl) {
        Label lbl = new Label(label); lbl.setMinWidth(80);
        lbl.setStyle("-fx-font-size:11px;-fx-text-fill:#64748b;");
        return new HBox(6, lbl, ctrl);
    }

    private void clearInfoPanel() {
        infoPanelBox.getChildren().clear();
        Label hint = new Label("Cliquez sur un élément");
        hint.setStyle("-fx-font-size:11px;-fx-text-fill:#94a3b8;");
        infoPanelBox.getChildren().add(hint);
        updateSelectionStatsPanel();
    }

    private void showInfo(String msg) {
        infoPanelBox.getChildren().clear();
        Label l = new Label(msg);
        l.setStyle("-fx-font-size:11px;-fx-text-fill:#94a3b8;");
        l.setWrapText(true);
        infoPanelBox.getChildren().add(l);
    }


    private void handleAddAgent() {
        TextField nameF = new TextField();
        TextField speedF = new TextField("1.0");
        TextField tolF = new TextField("0.7");

        ComboBox<String> locB = nodeCombo();

        ComboBox<Behavior> behB = new ComboBox<>();
        behB.getItems().addAll(Behavior.values());
        behB.getSelectionModel().selectFirst();

        dialog("Ajouter un agent",
            grid("Nom :", nameF,
                "Position :", locB,
                "Vitesse :", speedF,
                "Tolérance :", tolF,
                "Comportement :", behB),
            () -> {
                try {
                    String name = nameF.getText().trim();

                    if (name.isEmpty()) {
                        showErr("Le nom de l'agent est obligatoire");
                        return;
                    }

                    controller.addPersonAgent(
                        name,
                        locB.getValue(),
                        Double.parseDouble(speedF.getText().trim()),
                        behB.getValue(),
                        Double.parseDouble(tolF.getText().trim())
                    );

                    log("Agent ajouté : " + name);
                } catch (Exception ex) {
                    showErr("Valeurs invalides");
                }
            }
        );
    }

    private void handleEditAgent() {
        ComboBox<String> agB = agentCombo();

        TextField nameF = new TextField();
        TextField speedF = new TextField();
        TextField tolF = new TextField();

        ComboBox<String> locB = nodeCombo();

        ComboBox<Behavior> behB = new ComboBox<>();
        behB.getItems().addAll(Behavior.values());
        behB.getSelectionModel().selectFirst();

        agB.setOnAction(e -> {
            controller.getGraph().getAgents().stream()
                .filter(a -> a.getName().equals(agB.getValue()))
                .findFirst()
                .ifPresent(a -> {
                    nameF.setText(a.getName());
                    speedF.setText(String.valueOf(a.getMaxSpeed()));
                    tolF.setText(String.valueOf(a.getDensityTolerance()));
                    locB.setValue(a.getCurrentLocation().getName());
                    behB.setValue(a.getBehavior());
                });
        });

        if (!agB.getItems().isEmpty()) {
            agB.getSelectionModel().selectFirst();
            agB.getOnAction().handle(null);
        }

        dialog("Modifier un agent",
            grid("Agent :", agB,
                "Nouveau nom :", nameF,
                "Position :", locB,
                "Vitesse :", speedF,
                "Tolérance :", tolF,
                "Comportement :", behB),
            () -> {
                try {
                    controller.updateAgent(
                        agB.getValue(),
                        nameF.getText().trim(),
                        locB.getValue(),
                        Double.parseDouble(speedF.getText().trim()),
                        behB.getValue(),
                        Double.parseDouble(tolF.getText().trim())
                    );

                    log("Agent modifié : " + nameF.getText().trim());
                } catch (Exception ex) {
                    showErr("Valeurs invalides");
                }
            }
        );
    }

    private void handleRemoveAgent() {
        ComboBox<String> agB = agentCombo();

        dialog("Supprimer un agent",
            grid("Agent :", agB),
            () -> {
                if (agB.getValue() != null) {
                    controller.removeAgent(agB.getValue());
                    log("Agent supprimé : " + agB.getValue());
                }
            }
        );
    }

    private void handleRandomAgents() {
        TextField countF = new TextField("10");
        TextField minSF = new TextField("0.5");
        TextField maxSF = new TextField("1.5");
        TextField minTF = new TextField("0.3");
        TextField maxTF = new TextField("1.0");

        dialog("Agents aléatoires",
            grid("Nombre :", countF,
                "Vitesse min :", minSF,
                "Vitesse max :", maxSF,
                "Tolérance min :", minTF,
                "Tolérance max :", maxTF),
            () -> {
                try {
                    int n = Integer.parseInt(countF.getText().trim());

                    controller.addRandomAgents(
                        n,
                        Double.parseDouble(minSF.getText().trim()),
                        Double.parseDouble(maxSF.getText().trim()),
                        Double.parseDouble(minTF.getText().trim()),
                        Double.parseDouble(maxTF.getText().trim())
                    );

                    log(n + " agents aléatoires ajoutés");
                } catch (Exception ex) {
                    showErr("Valeurs invalides");
                }
            }
        );
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

        controller.getGraph().getAgents()
            .forEach(a -> b.getItems().add(a.getName()));

        b.getSelectionModel().selectFirst();
        return b;
    }

    private void dialog(String title, GridPane content, Runnable onOk) {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle(title);
        d.getDialogPane().setContent(content);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        d.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                onOk.run();
            }
        });
    }

    private GridPane grid(Object... pairs) {
        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(8);
        g.setPadding(new Insets(10));

        for (int i = 0; i < pairs.length; i += 2) {
            g.add(new Label(pairs[i].toString()), 0, i / 2);
            g.add((javafx.scene.Node) pairs[i + 1], 1, i / 2);
        }

        return g;
    }

    private void showErr(String msg) {
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }

    private void log(String msg) {
        if (logArea != null) {
            logArea.appendText(msg + "\n");
            logArea.setScrollTop(Double.MAX_VALUE);
        }
    }


    private void updateSelectionStatsPanel() {
        if (statsPanel == null || statNameLbl == null) return;

        VNode n = byId(selectedId);

        if (n == null) {
            statNameLbl.setText("Aucun élément sélectionné");
            statTypeLbl.setText("Type : —");
            statFloorLbl.setText("Étage : —");
            statOccupationLbl.setText("Occupation : —");
            statPassedLbl.setText("Agents passés : —");
            statSpeedLbl.setText("Vitesse moy. : —");
            statLinksLbl.setText("Connexions : —");
            statStatusLbl.setText("Statut : —");
            return;
        }

        BuildingElement modelElement = findModelElementByName(n.label);

        statNameLbl.setText(n.label);
        statTypeLbl.setText("Type : " + typeName(n.type));
        statFloorLbl.setText("Étage : " + floorName(n.floor));
        statLinksLbl.setText("Connexions : " + connectionCount(n.id));

        if (modelElement != null) {
            int max = modelElement.getMaxCapacity();
            int occ = modelElement.getCurrentOccupancy();

            if (n.type == NType.EXIT) {
                statOccupationLbl.setText("Occupation : sortie");
            } else {
                statOccupationLbl.setText("Occupation : " + occ + " / " + max);
            }

            statPassedLbl.setText("Agents passés : " + modelElement.getTotalAgentsPassed());
            statSpeedLbl.setText(String.format("Vitesse moy. : %.2f", modelElement.getAverageSpeed()));

            double density = max <= 0 ? 0 : (occ * 100.0 / max);

            statStatusLbl.setText(String.format(
                "Statut : %s · densité %.0f%%",
                modelElement.getStatus(),
                density
            ));

        } else {
            if (n.type == NType.ROOM || n.type == NType.HALL) {
                statOccupationLbl.setText("Capacité : " + n.cap + " personnes");
                statSpeedLbl.setText("Débit : —");
            } else if (n.type == NType.DOOR || n.type == NType.STAIR) {
                statOccupationLbl.setText("Capacité : —");
                statSpeedLbl.setText(String.format("Débit : %.1f pers/s", n.rate));
            } else if (n.type == NType.EXIT) {
                statOccupationLbl.setText("Occupation : sortie");
                statSpeedLbl.setText("Débit : —");
            }

            statPassedLbl.setText("Agents passés : —");
            statStatusLbl.setText("Statut : " + visualStatus(n));
        }
    }

    private BuildingElement findModelElementByName(String name) {
        if (name == null || controller == null || controller.getGraph() == null) {
            return null;
        }

        for (BuildingElement el : controller.getGraph().getElements()) {
            if (el.getName().equalsIgnoreCase(name)) {
                return el;
            }
        }

        return null;
    }

    private String typeName(NType type) {
        return switch (type) {
            case ROOM  -> "Salle";
            case DOOR  -> "Porte";
            case HALL  -> "Hall";
            case STAIR -> "Escalier";
            case EXIT  -> "Sortie";
        };
    }

    private String floorName(int floor) {
        if (floor >= 0 && floor < FLOOR_NAME.length) {
            return FLOOR_NAME[floor];
        }

        return "Étage " + floor;
    }

    private int connectionCount(String nodeId) {
        int count = 0;

        for (VEdge e : edges) {
            if (e.from.equals(nodeId) || e.to.equals(nodeId)) {
                count++;
            }
        }

        return count;
    }

    private String visualStatus(VNode n) {
        if (n.id.equals(fireNodeId)) {
            return "Feu départ";
        }

        if (fireSpread.contains(n.id)) {
            return "Touché par la propagation";
        }

        return "Normal";
    }

    // ── Save / Load ───────────────────────────────────────

    private void handleSave() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("*.bin", "*.bin"));
        File f = fc.showSaveDialog(stage);
        if (f != null) controller.saveSimulation(f.getAbsolutePath());
    }

    private void handleLoad() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("*.bin", "*.bin"));
        File f = fc.showOpenDialog(stage);
        if (f != null) controller.loadSimulation(f.getAbsolutePath());
    }

    private void goBack() {
        if (renderLoop != null) renderLoop.stop();
        if (fireTimer  != null) fireTimer.stop();
        controller.pause();
        new LoginView(stage, controller).show();
    }

    // ── Helpers ───────────────────────────────────────────

    private List<VNode> visibleNodes() {
        if (currentFloor == -1) return new ArrayList<>(nodes);
        List<VNode> out = new ArrayList<>();
        for (VNode n : nodes) if (n.floor == currentFloor) out.add(n);
        return out;
    }

    private VNode byId(String id) {
        if (id == null) return null;
        for (VNode n : nodes) if (n.id.equals(id)) return n;
        return null;
    }

    /** Hit-test: returns the topmost node under (mx,my). */
    private VNode hitNode(double mx, double my) {
        List<VNode> vis = visibleNodes();
        Collections.reverse(vis); // topmost drawn last
        for (VNode n : vis) {
            double r = switch (n.type) {
                case ROOM  -> 36;
                case HALL  -> 28;
                case STAIR -> 26;
                case DOOR  -> 12;
                case EXIT  -> 24;
            };
            if (Math.hypot(mx - n.x, my - n.y) <= r) return n;
        }
        return null;
    }

    // ── Style helpers ─────────────────────────────────────

    private Button sBtn(String text, String color) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color:" + color + ";-fx-text-fill:white;" +
                   "-fx-font-size:11px;-fx-padding:4 10;-fx-background-radius:5;-fx-cursor:hand;");
        return b;
    }

    private String defaultBtnStyle() {
        return "-fx-background-color:white;-fx-text-fill:#334155;" +
               "-fx-font-size:11px;-fx-padding:4 8;" +
               "-fx-border-color:#cbd5e1;-fx-border-radius:5;-fx-background-radius:5;" +
               "-fx-cursor:hand;";
    }

    private String activeBtnStyle() {
        return "-fx-background-color:#dbeafe;-fx-text-fill:#1d4ed8;" +
               "-fx-font-size:11px;-fx-padding:4 8;" +
               "-fx-border-color:#3b82f6;-fx-border-radius:5;-fx-background-radius:5;" +
               "-fx-cursor:hand;";
    }

}