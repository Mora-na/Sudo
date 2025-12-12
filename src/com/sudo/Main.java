package com.sudo;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
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

    private final Object workerLock = new Object();
    private final javafx.scene.control.Label speedLabel = new javafx.scene.control.Label();
    private final javafx.scene.control.Label diffLabel = new javafx.scene.control.Label();
    private final javafx.scene.control.Label messageLabel = new javafx.scene.control.Label();
    // 保存当前题目的完整解，以及标记哪些是给定格
    private final int[][] currentSolution = new int[9][9];
    private final boolean[][] isGiven = new boolean[9][9];
    private final int[][] initialPuzzle = new int[9][9];  // 用于保存程序启动时的初始状态（若无 random）
    private final int[][] randomPuzzle = new int[9][9];   // 用于保存点击 random 时生成的题目
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

    //    private final int[][] currentPuzzleState = new int[9][9]; // 用来保存当前题目的状态
    private boolean isPuzzleGenerated = false;

    // 在类中定义一个 Timeline 类型的实例变量
    private Timeline currentTimeline;

    public static void main(String[] args) {
        launch();
    }

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
        Button validateBtn = new Button("Validate"); // 验证按钮
        Button clearButton = new Button("Clear");

        solveBtn.setPrefWidth(90);
        pauseBtn.setPrefWidth(90);
        resetBtn.setPrefWidth(90);
        randomBtn.setPrefWidth(90);
        validateBtn.setPrefWidth(90);
        clearButton.setPrefWidth(90);

        controlRow1.getChildren().addAll(solveBtn, pauseBtn, resetBtn, clearButton, randomBtn, validateBtn);

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

        // 提示信息显示在按钮上方
        messageLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #333;");
        messageLabel.setMinHeight(30); // 预留空间
        messageLabel.setAlignment(Pos.CENTER);

        vbox.getChildren().add(messageLabel);   // <--- 新增
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

        randomBtn.setOnAction(e -> {
            isPuzzleGenerated = true;
            // 生成一个新的数独题目
            int[][] generatedPuzzle = generateRandomPuzzle();
            // 保存随机生成的题目
            System.arraycopy(generatedPuzzle, 0, randomPuzzle, 0, 9);
            // 同时保存初始题目（假设这是首次点击）
//            System.arraycopy(generatedPuzzle, 0, initialPuzzle, 0, 9);
            // 填充格子、更新界面等
            fillPuzzle(generatedPuzzle);
        });

        // 验证按钮处理：严格验证用户填写
        validateBtn.setOnAction(e -> validateBoard());

        clearButton.setOnAction(e -> {
            // 清空所有格子的内容
            for (int i = 0; i < 9; i++) {
                for (int j = 0; j < 9; j++) {
                    cells[i][j].setText("");  // 清空文本框

//                    // 清空非给定格子的内容并恢复为可编辑状态
//                    if (!isGiven[i][j]) {
//                        currentPuzzleState[i][j] = 0;  // 将当前状态置为 0
//                        cells[i][j].setEditable(true); // 允许编辑
//                        cells[i][j].setFocusTraversable(true); // 允许聚焦
//                    }

                    // 给定格子不改变
                    if (isGiven[i][j]) {
                        cells[i][j].setStyle(cellStyleWithBackground(i, j, "#e6e6ff")); // 给定数字的背景色
                        cells[i][j].setEditable(false); // 禁止编辑
                        cells[i][j].setFocusTraversable(false); // 禁止聚焦
                    }
                }
            }
        });

        resetBtn.setOnAction(e -> {
            int[][] puzzleToReset;
            if (isPuzzleGenerated) {
                // 如果点击过 random，重置到 random 状态
                puzzleToReset = randomPuzzle;
            } else {
                // 否则重置到启动时的初始状态
                puzzleToReset = initialPuzzle;
            }

            // 填充恢复后的数独题目
            fillPuzzle(puzzleToReset);
        });


        speedMinus.setOnAction(e -> {
            synchronized (workerLock) {
                if (solveSpeedMs < 10) solveSpeedMs = Math.min(10, solveSpeedMs + 1);
                else solveSpeedMs = Math.min(1000, solveSpeedMs + 20);
                updateLabels();
                workerLock.notifyAll();
            }
        });
        speedPlus.setOnAction(e -> {
            synchronized (workerLock) {
                if (solveSpeedMs > 10) solveSpeedMs = Math.max(10, solveSpeedMs - 20);
                else solveSpeedMs = Math.max(0, solveSpeedMs - 1);
                updateLabels();
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
                tf.setStyle(baseCellStyle(r, c));
                cells[r][c] = tf;
                gridPane.add(tf, c, r);
            }
        }

        // 生成并填充数独题目
        int[][] generatedPuzzle = generateRandomPuzzle();  // 获取随机数独题目
        fillPuzzle(generatedPuzzle);  // 填充到界面

        System.arraycopy(generatedPuzzle, 0, initialPuzzle, 0, 9);

        return gridPane;
    }

    private String baseCellStyle(int r, int c) {
        StringBuilder sb = new StringBuilder();
        sb.append("-fx-font-size:20; -fx-alignment: center; -fx-border-color: #444;");
        if (r % 3 == 0) sb.append("-fx-border-top-width:3;");
        else sb.append("-fx-border-top-width:1;");
        if (c % 3 == 0) sb.append("-fx-border-left-width:3;");
        else sb.append("-fx-border-left-width:1;");
        if (r == 8) sb.append("-fx-border-bottom-width:3;");
        else sb.append("-fx-border-bottom-width:1;");
        if (c == 8) sb.append("-fx-border-right-width:3;");
        else sb.append("-fx-border-right-width:1;");
        return sb.toString();
    }

    private String cellStyleWithBackground(int r, int c, String bgColor) {
        return baseCellStyle(r, c) + " -fx-background-color: " + bgColor + ";";
    }

    private int[][] generateRandomPuzzle() {
        int[][] full = generator.generateFull();
        if (full == null) {
            System.err.println("generator.generateFull() 返回 null，无法生成题目。");
            showMessage("生成完整解失败，请检查 SudokuGenerator。");
            return new int[9][9]; // 返回一个空的数独题目
        }

        // 保存完整解
        for (int i = 0; i < 9; i++) System.arraycopy(full[i], 0, currentSolution[i], 0, 9);

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

        Platform.runLater(() -> {
            for (int r = 0; r < 9; r++) {
                for (int c = 0; c < 9; c++) {
                    if (puzzle[r][c] == 0) {
                        isGiven[r][c] = false;  // 该格为用户可编辑
                        cells[r][c].setText(""); // 清空文本框
                        cells[r][c].setEditable(true); // 允许编辑
                        cells[r][c].setFocusTraversable(true);
                        cells[r][c].setStyle(cellStyleWithBackground(r, c, "white"));
                    } else {
                        isGiven[r][c] = true;  // 该格为给定格
                        cells[r][c].setText(String.valueOf(puzzle[r][c]));
                        cells[r][c].setEditable(false); // 禁止编辑
                        cells[r][c].setFocusTraversable(false);
                        cells[r][c].setStyle(cellStyleWithBackground(r, c, "#e6e6ff")); // 给定格背景色
                    }
                }
            }
        });


        return puzzle;  // 返回生成的数独题目
    }

    private void updateLabels() {
        speedLabel.setText("Speed: " + solveSpeedMs + " ms/step");
        diffLabel.setText("Difficulty (holes): " + removeCount);
    }

    private void startSolveAnimation() {
        stopAnimation();

        // 严格校验当前用户输入：任何非空非 1~9 的值都视为非法并阻止求解
        boolean illegalInput = false;
        int[][] grid = new int[9][9];

        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                String t = cells[r][c].getText().trim();
                if (t.isEmpty()) {
                    grid[r][c] = 0;
                    continue;
                }
                int v;
                try {
                    v = Integer.parseInt(t);
                } catch (NumberFormatException ex) {
                    // 非整数 -> 标红并记录非法
                    cells[r][c].setStyle(cellStyleWithBackground(r, c, "#ffb3b3"));
                    illegalInput = true;
                    continue;
                }
                if (v < 1 || v > 9) {
                    cells[r][c].setStyle(cellStyleWithBackground(r, c, "#ffb3b3"));
                    illegalInput = true;
                } else {
                    grid[r][c] = v;
                }
            }
        }

        if (illegalInput) {
            // 提示并放弃求解
            showMessage("存在非法输入（非1-9的整数或非法字符），已标红，取消求解。");
            return;
        }

        // 现在调用 solver 生成步骤
        currentSteps = solver.generateSteps(grid);

        if (currentSteps == null || currentSteps.isEmpty()) {
            // 显示提示（一行居中）；不要弹窗
            showMessage("无法生成求解步骤（可能无解或输入冲突），已取消求解。");

            // 如果程序已有完整解（currentSolution），将完整解写入可编辑格（不覆盖给定格）
            fillSolutionIntoGridIfAvailable();

            // 直接返回，取消求解动画
            return;
        }


        // lock board while animating
        setBoardEditable(false);

        stepIndex = 0;
        running = true;
        paused = false;

        workerThread = new Thread(() -> {
            try {
                while (running) {
                    if (currentSteps == null || stepIndex >= currentSteps.size()) {
                        break;
                    }
                    synchronized (workerLock) {
                        while (paused && running) {
                            workerLock.wait();
                        }
                    }
                    if (!running) break;
                    SudokuSolver.Step step = currentSteps.get(stepIndex++);
                    applyStepToUI(step);
                    long startWait = System.nanoTime();
                    synchronized (workerLock) {
                        while (running) {
                            if (paused) break;
                            long elapsed = (System.nanoTime() - startWait) / 1_000_000L;
                            long delay = solveSpeedMs;
                            long remaining = delay - elapsed;
                            if (remaining <= 0) break;
                            workerLock.wait(remaining);
                        }
                    }
                }
            } catch (InterruptedException ex) {
                // 线程中断，退出
            } finally {
                running = false;
                paused = false;
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
            // 保护给定格：不给定格不被 solver 改写
            if (isGiven[step.r][step.c]) {
                return;
            }
            if (step.type == SudokuSolver.StepType.PUT) {
                cells[step.r][step.c].setText(String.valueOf(step.val));
                cells[step.r][step.c].setStyle(cellStyleWithBackground(step.r, step.c, "#d1ffd1"));
            } else if (step.type == SudokuSolver.StepType.CLEAR) {
                cells[step.r][step.c].setText("");
                cells[step.r][step.c].setStyle(cellStyleWithBackground(step.r, step.c, "#ffd1d1"));
            }
        });
    }

    private void stopAnimation() {
        running = false;
        paused = false;
        synchronized (workerLock) {
            workerLock.notifyAll();
        }
        if (workerThread != null) {
            workerThread.interrupt();
            workerThread = null;
        }
        setBoardEditable(true);
        if (pauseBtn != null) Platform.runLater(() -> pauseBtn.setText("Pause"));
    }

    private void setBoardEditable(boolean editable) {
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                TextField tf = cells[r][c];
                if (isGiven[r][c]) {
                    tf.setEditable(false);
                    tf.setFocusTraversable(false);
                } else {
                    tf.setEditable(editable);
                    tf.setFocusTraversable(editable);
                }
            }
        }
    }

    /**
     * 验证当前界面用户填写的答案（不依赖解，只按数独规则验证）。
     * 规则：
     * - 非空且非 1~9 → 标红
     * - 行重复 → 标红
     * - 列重复 → 标红
     * - 3×3 宫重复 → 标红
     * - 给定格保持淡紫色
     * - 正确但非给定格标绿色
     * - 空格为白色
     */
    private void validateBoard() {
        stopAnimation();

        // 收集数字（0=空）
        int[][] grid = new int[9][9];
        boolean[][] error = new boolean[9][9];  // 标记错误格

        // ① 首先检查是否为合法数字（非空且非 1-9）
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                String t = cells[r][c].getText().trim();
                if (t.isEmpty()) {
                    grid[r][c] = 0;
                    continue;
                }
                try {
                    int v = Integer.parseInt(t);
                    if (v < 1 || v > 9) {
                        error[r][c] = true;
                    } else {
                        grid[r][c] = v;
                    }
                } catch (Exception e) {
                    error[r][c] = true;
                }
            }
        }

        // ② 行检查（重复）
        for (int r = 0; r < 9; r++) {
            int[] cnt = new int[10];
            for (int c = 0; c < 9; c++) {
                int v = grid[r][c];
                if (v != 0) {
                    cnt[v]++;
                }
            }
            for (int c = 0; c < 9; c++) {
                int v = grid[r][c];
                if (v != 0 && cnt[v] > 1) {
                    error[r][c] = true;
                }
            }
        }

        // ③ 列检查（重复）
        for (int c = 0; c < 9; c++) {
            int[] cnt = new int[10];
            for (int r = 0; r < 9; r++) {
                int v = grid[r][c];
                if (v != 0) cnt[v]++;
            }
            for (int r = 0; r < 9; r++) {
                int v = grid[r][c];
                if (v != 0 && cnt[v] > 1) {
                    error[r][c] = true;
                }
            }
        }

        // ④ 宫格检查（重复）
        for (int br = 0; br < 3; br++) {
            for (int bc = 0; bc < 3; bc++) {
                int[] cnt = new int[10];
                for (int r = br * 3; r < br * 3 + 3; r++) {
                    for (int c = bc * 3; c < bc * 3 + 3; c++) {
                        int v = grid[r][c];
                        if (v != 0) cnt[v]++;
                    }
                }
                for (int r = br * 3; r < br * 3 + 3; r++) {
                    for (int c = bc * 3; c < bc * 3 + 3; c++) {
                        int v = grid[r][c];
                        if (v != 0 && cnt[v] > 1) {
                            error[r][c] = true;
                        }
                    }
                }
            }
        }

        // ⑤ 根据错误标记设置样式
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {

                if (isGiven[r][c]) {
                    // 给定格保持淡紫色
                    cells[r][c].setStyle(cellStyleWithBackground(r, c, "#e6e6ff"));
                    continue;
                }

                if (error[r][c]) {
                    // 错误格红色
                    cells[r][c].setStyle(cellStyleWithBackground(r, c, "#ffb3b3"));
                } else {
                    String t = cells[r][c].getText().trim();
                    if (t.isEmpty()) {
                        // 空格保持白色
                        cells[r][c].setStyle(cellStyleWithBackground(r, c, "white"));
                    } else {
                        // 正确填写格绿色
                        cells[r][c].setStyle(cellStyleWithBackground(r, c, "#d1ffd1"));
                    }
                }
            }
        }

        // 在样式设置完之后，显示验证结果提示（在按钮上方一行居中）
        boolean anyError = false;
        boolean anyEmpty = false;
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (error[r][c]) anyError = true;
                if (cells[r][c].getText().trim().isEmpty()) anyEmpty = true;
            }
        }

        if (anyError) {
            showMessage("验证完成：验证失败（存在冲突或非法输入）。");
        } else if (anyEmpty) {
            showMessage("验证完成：所有已填格合法，但仍有空格。");
        } else {
            showMessage("验证完成：验证成功，所有格子符合数独规则。");
        }


    }

    private void showMessage(String msg) {
        // 设置提示消息文本
        messageLabel.setText(msg);

        // 通过颜色来区分不同的提示
        if (msg.contains("验证成功") || msg.contains("生成")) {
            messageLabel.setStyle("-fx-text-fill: green; -fx-font-size: 14px; -fx-padding: 5px;");
        } else if (msg.contains("验证失败") || msg.contains("冲突")) {
            messageLabel.setStyle("-fx-text-fill: red; -fx-font-size: 14px; -fx-padding: 5px;");
        } else {
            messageLabel.setStyle("-fx-text-fill: black; -fx-font-size: 14px; -fx-padding: 5px;");
        }

        // 如果已经有正在运行的 Timeline，则取消它
        if (currentTimeline != null) {
            currentTimeline.stop();
        }

        // 创建新的 Timeline，3秒后清空消息
        currentTimeline = new Timeline(new KeyFrame(Duration.seconds(3), // 设置3秒后清空
                e -> messageLabel.setText("")));
        currentTimeline.setCycleCount(1);
        currentTimeline.play();
    }


    /**
     * 如果 currentSolution 有解（任意格非0），将完整解填入到可编辑/非给定的格子中（不修改给定格）。
     * 填入时将格子标为绿色（表示正确填入），但不改变可编辑状态（保持用户可编辑以便复写）。
     */
    private void fillSolutionIntoGridIfAvailable() {
        boolean hasSolution = false;
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                if (currentSolution[i][j] != 0) {
                    hasSolution = true;
                    break;
                }
            }
            if (hasSolution) break;
        }
        if (!hasSolution) {
            // 没有已知完整解，直接返回（不弹窗）
            return;
        }

        Platform.runLater(() -> {
            for (int r = 0; r < 9; r++) {
                for (int c = 0; c < 9; c++) {
                    if (isGiven[r][c]) {
                        // 给定格保持淡紫色
                        cells[r][c].setStyle(cellStyleWithBackground(r, c, "#e6e6ff"));
                        continue;
                    }
                    int val = currentSolution[r][c];
                    if (val == 0) {
                        cells[r][c].setText("");
                        cells[r][c].setStyle(cellStyleWithBackground(r, c, "white"));
                    } else {
                        cells[r][c].setText(String.valueOf(val));
                        // 标为正确填入的绿色，但保持可编辑（便于用户继续修改）
                        cells[r][c].setStyle(cellStyleWithBackground(r, c, "#d1ffd1"));
                    }
                }
            }
        });
    }

    private void fillPuzzle(int[][] puzzle) {
        Platform.runLater(() -> {
            for (int r = 0; r < 9; r++) {
                for (int c = 0; c < 9; c++) {
                    if (isGiven[r][c]) {
                        cells[r][c].setText(String.valueOf(puzzle[r][c])); // 填充给定数字
                        cells[r][c].setStyle(cellStyleWithBackground(r, c, "#e6e6ff")); // 给定数字的背景色
                    } else {
                        cells[r][c].setText(""); // 清空非给定数字的格子
                        cells[r][c].setStyle(cellStyleWithBackground(r, c, "white")); // 恢复为空的格子背景色
                    }
                }
            }
        });
    }


}
