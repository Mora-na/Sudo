package com.sudo.ui;

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

import java.util.List;

public class Main extends Application {

    private final TextField[][] cells = new TextField[9][9];
    private final SudokuSolver solver = new SudokuSolver();
    private final SudokuGenerator generator = new SudokuGenerator();

    private final Object workerLock = new Object();
    private Thread workerThread;
    private volatile boolean running = false;
    private volatile boolean paused = false;
    private List<SudokuSolver.Step> currentSteps;

    // UI controls referenced across methods
    private Button pauseBtn;
    private int stepIndex;

    // dynamic params
    private volatile int solveSpeedMs = 80;      // milliseconds per step
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
        pauseBtn = new Button("Pause");
        Button resetBtn = new Button("Reset");
        Button randomBtn = new Button("Random");

        solveBtn.setPrefWidth(90);
        pauseBtn.setPrefWidth(90);
        resetBtn.setPrefWidth(90);
        randomBtn.setPrefWidth(90);

        controlRow1.getChildren().addAll(solveBtn, pauseBtn, resetBtn, randomBtn);

        // second row: speed and difficulty controls
        HBox controlRow2 = new HBox(8);
        controlRow2.setAlignment(Pos.CENTER);

        Button speedMinus = new Button("Speed -");
        Button speedPlus = new Button("Speed +");
        Button easier = new Button("Easier");
        Button harder = new Button("Harder");

        controlRow2.getChildren().addAll(speedMinus, speedPlus, speedLabel, easier, harder, diffLabel);

        VBox vbox = new VBox(12);
        vbox.setAlignment(Pos.CENTER);
        vbox.getChildren().addAll(controlRow1, controlRow2);

        root.setBottom(vbox);

        updateLabels();

        solveBtn.setOnAction(e -> startSolveAnimation());
        pauseBtn.setOnAction(e -> {
            // toggle pause/continue when animation running
            if (running) {
                if (!paused) {
                    paused = true;
                    pauseBtn.setText("Continue");
                } else {
                    paused = false;
                    pauseBtn.setText("Pause");
                    synchronized (workerLock) {
                        workerLock.notifyAll();
                    }
                }
            }
        });
        resetBtn.setOnAction(e -> resetBoard());
        randomBtn.setOnAction(e -> generateRandomPuzzle());

        speedMinus.setOnAction(e -> {
            // make slower: if currently below 10ms, increase by 1ms until 10, otherwise increase by 20ms
            if (solveSpeedMs < 10) solveSpeedMs = Math.min(10, solveSpeedMs + 1);
            else solveSpeedMs = Math.min(1000, solveSpeedMs + 20);
            updateLabels();
            // notify worker to apply change immediately
            synchronized (workerLock) {
                workerLock.notifyAll();
            }
        });
        speedPlus.setOnAction(e -> {
            // make faster: larger steps until 10ms, after that reduce by 1ms steps down to 0
            if (solveSpeedMs > 10) solveSpeedMs = Math.max(10, solveSpeedMs - 20);
            else solveSpeedMs = Math.max(0, solveSpeedMs - 1);
            updateLabels();
            synchronized (workerLock) {
                workerLock.notifyAll();
            }
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

        // initial random puzzle
        generateRandomPuzzle();

        return gridPane;
    }

    private void generateRandomPuzzle() {
        // generate a solved grid and then remove numbers to create puzzle
        int[][] full = generator.generateFull();
        int[][] puzzle = new int[9][9];
        for (int i = 0; i < 9; i++) System.arraycopy(full[i], 0, puzzle[i], 0, 9);

        int removed = 0;
        java.util.Random rnd = new java.util.Random();
        while (removed < removeCount) {
            int r = rnd.nextInt(9);
            int c = rnd.nextInt(9);
            if (puzzle[r][c] != 0) {
                puzzle[r][c] = 0;
                removed++;
            }
        }

        // write to UI
        Platform.runLater(() -> {
            for (int r = 0; r < 9; r++) {
                for (int c = 0; c < 9; c++) {
                    if (puzzle[r][c] == 0) {
                        cells[r][c].setText("");
                        cells[r][c].setStyle("-fx-font-size:20; -fx-alignment: center; -fx-border-color:#444;");
                    } else {
                        cells[r][c].setText(String.valueOf(puzzle[r][c]));
                        cells[r][c].setStyle("-fx-font-size:20; -fx-alignment: center; -fx-border-color:#444; -fx-background-color:#e6e6ff;");
                    }
                }
            }
        });
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
        running = true;
        paused = false;
        // start worker thread to perform steps with dynamic, real-time adjustable delay
        workerThread = new Thread(() -> {
            try {
                while (running) {
                    if (currentSteps == null || stepIndex >= currentSteps.size()) {
                        break;
                    }
                    // wait while paused
                    synchronized (workerLock) {
                        while (paused && running) {
                            workerLock.wait();
                        }
                    }
                    if (!running) break;
                    SudokuSolver.Step step = currentSteps.get(stepIndex++);
                    applyStepToUI(step);
                    // wait for solveSpeedMs milliseconds but be responsive to changes and pause
                    long startWait = System.nanoTime();
                    synchronized (workerLock) {
                        while (running) {
                            if (paused) break; // go back to outer loop to handle pause
                            long elapsed = (System.nanoTime() - startWait) / 1_000_000L;
                            long delay = solveSpeedMs;
                            long remaining = delay - elapsed;
                            if (remaining <= 0) break;
                            // wait up to remaining ms, will wake early on notify to re-evaluate
                            workerLock.wait(remaining);
                        }
                    }
                }
            } catch (InterruptedException ex) {
                // thread interrupted: exit
            } finally {
                running = false;
                paused = false;
                // ensure UI unlocked
                Platform.runLater(() -> {
                    setBoardEditable(true);
                    if (pauseBtn != null) pauseBtn.setText("Pause");
                });
            }
        }, "Sudoku-Worker");
        workerThread.setDaemon(true);
        workerThread.start();
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
        // stop worker thread
        running = false;
        paused = false;
        synchronized (workerLock) {
            workerLock.notifyAll();
        }
        if (workerThread != null) {
            workerThread.interrupt();
            workerThread = null;
        }
        // ensure board editable after stop
        setBoardEditable(true);
        // reset pause button text if present
        if (pauseBtn != null) Platform.runLater(() -> pauseBtn.setText("Pause"));
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
            }
        }
    }

    public static void main(String[] args) {
        launch();
    }

    private void setBoardEditable(boolean editable) {
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                TextField tf = cells[r][c];
                tf.setEditable(editable);
                tf.setFocusTraversable(editable);
            }
        }
    }

}
