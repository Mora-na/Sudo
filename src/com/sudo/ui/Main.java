// Project: Sudoku JavaFX - Dynamic Visualization
// Files contained below: Main.java, SudokuSolver.java, SudokuGenerator.java

// ==================== Main.java ====================
package com.sudo.ui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.List;

public class Main extends Application {

    private final TextField[][] cells = new TextField[9][9];
    private final SudokuSolver solver = new SudokuSolver();
    private final SudokuGenerator generator = new SudokuGenerator();

    private Timeline playTimeline;
    private List<SudokuSolver.Step> currentSteps;
    private int stepIndex;

    // dynamic params
    private int solveSpeedMs = 80;      // milliseconds per step
    private int removeCount = 45;       // holes for generated puzzle

    private Label speedLabel = new Label();
    private Label diffLabel = new Label();

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();

        GridPane gridPane = createGrid();
        root.setCenter(gridPane);

        HBox controlRow1 = new HBox(10);
        controlRow1.setAlignment(Pos.CENTER);

        Button solveBtn = new Button("Solve");
        Button stopBtn = new Button("Stop");
        Button resetBtn = new Button("Reset");
        Button randomBtn = new Button("Random");

        solveBtn.setPrefWidth(90);
        stopBtn.setPrefWidth(90);
        resetBtn.setPrefWidth(90);
        randomBtn.setPrefWidth(90);

        controlRow1.getChildren().addAll(solveBtn, stopBtn, resetBtn, randomBtn);

        // second row: speed and difficulty controls
        HBox controlRow2 = new HBox(8);
        controlRow2.setAlignment(Pos.CENTER);

        Button speedMinus = new Button("Speed -");
        Button speedPlus = new Button("Speed +");
        Button easier = new Button("Easier");
        Button harder = new Button("Harder");

        controlRow2.getChildren().addAll(speedMinus, speedPlus, easier, harder);

        VBox controls = new VBox(8, controlRow1, controlRow2);
        controls.setPadding(new Insets(10));
        controls.setAlignment(Pos.CENTER);

        // status labels
        updateLabels();
        HBox status = new HBox(20, speedLabel, diffLabel);
        status.setAlignment(Pos.CENTER);
        VBox bottom = new VBox(8, controls, status);
        bottom.setPadding(new Insets(10));

        root.setBottom(bottom);

        // handlers
        solveBtn.setOnAction(e -> startSolveAnimation());
        stopBtn.setOnAction(e -> stopAnimation());
        resetBtn.setOnAction(e -> resetBoard());
        randomBtn.setOnAction(e -> generateRandomPuzzle());

        speedMinus.setOnAction(e -> {
            solveSpeedMs = Math.min(1000, solveSpeedMs + 20); // slower
            updateLabels();
        });
        speedPlus.setOnAction(e -> {
            solveSpeedMs = Math.max(10, solveSpeedMs - 20); // faster
            updateLabels();
        });
        easier.setOnAction(e -> {
            removeCount = Math.max(20, removeCount - 3);
            updateLabels();
        });
        harder.setOnAction(e -> {
            removeCount = Math.min(64, removeCount + 3);
            updateLabels();
        });

        Scene scene = new Scene(root, 660, 760);
        stage.setScene(scene);
        stage.setTitle("Sudoku Solver - Dynamic Visual");
        stage.show();
    }

    private GridPane createGrid() {
        GridPane gridPane = new GridPane();
        gridPane.setHgap(4);
        gridPane.setVgap(4);
        gridPane.setAlignment(Pos.CENTER);
        gridPane.setPadding(new Insets(12));

        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                TextField tf = new TextField();
                tf.setPrefSize(60, 60);
                tf.setStyle("-fx-font-size:20; -fx-alignment: center;");
                // borders for 3x3
                String style = "-fx-font-size:20; -fx-alignment: center; -fx-border-color: #444;";
                if (r % 3 == 0) style += "-fx-border-top-width:3;";
                if (c % 3 == 0) style += "-fx-border-left-width:3;";
                if (r == 8) style += "-fx-border-bottom-width:3;";
                if (c == 8) style += "-fx-border-right-width:3;";
                tf.setStyle(style);

                cells[r][c] = tf;
                gridPane.add(tf, c, r);
            }
        }
        return gridPane;
    }

    private void updateLabels() {
        speedLabel.setText("Speed: " + solveSpeedMs + " ms/step");
        diffLabel.setText("Difficulty (holes): " + removeCount);
    }

    private void startSolveAnimation() {
        stopAnimation();
        // read board from UI
        int[][] grid = new int[9][9];
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                String t = cells[r][c].getText().trim();
                grid[r][c] = t.isEmpty() ? 0 : Integer.parseInt(t);
            }
        }

        // generate steps (including backtrack clear steps)
        currentSteps = solver.generateSteps(grid);
        if (currentSteps == null || currentSteps.isEmpty()) {
            System.out.println("No steps generated (maybe already full or invalid)");
            return;
        }

        // lock board while animating
        setBoardEditable(false);

        stepIndex = 0;
        playTimeline = new Timeline();
        playTimeline.setCycleCount(currentSteps.size());

        playTimeline.getKeyFrames().add(new KeyFrame(Duration.millis(solveSpeedMs), ev -> {
            if (stepIndex >= currentSteps.size()) {
                stopAnimation();
                setBoardEditable(true);
                return;
            }
            SudokuSolver.Step step = currentSteps.get(stepIndex++);
            applyStepToUI(step);
        }));

        playTimeline.setOnFinished(e -> setBoardEditable(true));
        playTimeline.play();
    }

    private void applyStepToUI(SudokuSolver.Step step) {
        Platform.runLater(() -> {
            if (step.type == SudokuSolver.StepType.PUT) {
                cells[step.r][step.c].setText(String.valueOf(step.val));
                cells[step.r][step.c].setStyle("-fx-background-color: #d1ffd1; -fx-font-size:22; -fx-border-color:#444;");
            } else if (step.type == SudokuSolver.StepType.CLEAR) {
                cells[step.r][step.c].setText("");
                cells[step.r][step.c].setStyle("-fx-background-color: #ffd1d1; -fx-font-size:22; -fx-border-color:#444;");
            }
        });
    }

    private void stopAnimation() {
        if (playTimeline != null) {
            playTimeline.stop();
            playTimeline.getKeyFrames().clear();
            playTimeline = null;
        }
        // ensure board editable after stop
        setBoardEditable(true);
    }

    private void resetBoard() {
        stopAnimation();
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                cells[r][c].setText("");
                cells[r][c].setStyle("-fx-font-size:20; -fx-alignment: center; -fx-border-color:#444;");
                // reapply thicker borders
                String style = "-fx-font-size:20; -fx-alignment: center; -fx-border-color: #444;";
                if (r % 3 == 0) style += "-fx-border-top-width:3;";
                if (c % 3 == 0) style += "-fx-border-left-width:3;";
                if (r == 8) style += "-fx-border-bottom-width:3;";
                if (c == 8) style += "-fx-border-right-width:3;";
                cells[r][c].setStyle(style);
                cells[r][c].setEditable(true);
            }
        }
    }

    private void generateRandomPuzzle() {
        stopAnimation();
        int[][] puzzle = generator.generatePuzzle(removeCount);
        applyPuzzleToUI(puzzle);
    }

    private void applyPuzzleToUI(int[][] puzzle) {
        Platform.runLater(() -> {
            for (int r = 0; r < 9; r++) {
                for (int c = 0; c < 9; c++) {
                    int v = puzzle[r][c];
                    TextField tf = cells[r][c];
                    if (v == 0) {
                        tf.setText("");
                        tf.setEditable(true);
                        tf.setStyle("-fx-background-color: #ffffff; -fx-font-size:22; -fx-border-color:#444;");
                    } else {
                        tf.setText(String.valueOf(v));
                        tf.setEditable(false); // givens not editable
                        tf.setStyle("-fx-background-color: #eeeeee; -fx-font-size:22; -fx-border-color:#444;");
                    }
                }
            }
        });
    }

    private void setBoardEditable(boolean editable) {
        Platform.runLater(() -> {
            for (int r = 0; r < 9; r++) {
                for (int c = 0; c < 9; c++) {
                    // only change editable if cell isn't a given (gray)
                    String bg = cells[r][c].getStyle();
                    if (bg != null && bg.contains("#eeeeee")) {
                        // keep givens non-editable
                        cells[r][c].setEditable(false);
                    } else {
                        cells[r][c].setEditable(editable);
                    }
                }
            }
        });
    }

    public static void main(String[] args) {
        launch();
    }
}
