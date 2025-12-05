// ==================== SudokuSolver.java ====================
package com.sudo.ui;

import java.util.ArrayList;
import java.util.List;

public class SudokuSolver {

    public enum StepType { PUT, CLEAR }

    public static class Step {
        public final StepType type;
        public final int r, c, val;
        public Step(StepType type, int r, int c, int val) {
            this.type = type; this.r = r; this.c = c; this.val = val;
        }
    }

    private boolean solved;
    private List<Step> steps;

    /**
     * Generate list of steps (PUT / CLEAR) representing recursive backtracking
     * to reach one valid solution from given grid. Does NOT try to enumerate
     * all solutions — stops at first valid solution.
     */
    public List<Step> generateSteps(int[][] grid) {
        // copy grid
        int[][] work = new int[9][9];
        for (int r = 0; r < 9; r++) System.arraycopy(grid[r], 0, work[r], 0, 9);

        steps = new ArrayList<>();
        solved = false;
        backtrack(work);
        return steps;
    }

    private boolean backtrack(int[][] grid) {
        // find next empty
        int sr = -1, sc = -1;
        for (int i = 0; i < 81; i++) {
            int r = i / 9, c = i % 9;
            if (grid[r][c] == 0) { sr = r; sc = c; break; }
        }
        if (sr == -1) {
            solved = true;
            return true; // solved
        }

        for (int v = 1; v <= 9 && !solved; v++) {
            if (valid(grid, sr, sc, v)) {
                grid[sr][sc] = v;
                steps.add(new Step(StepType.PUT, sr, sc, v));

                if (backtrack(grid)) return true;

                // backtrack
                grid[sr][sc] = 0;
                steps.add(new Step(StepType.CLEAR, sr, sc, 0));
            }
        }
        return false;
    }

    private boolean valid(int[][] grid, int r, int c, int val) {
        for (int j = 0; j < 9; j++) if (grid[r][j] == val) return false;
        for (int i = 0; i < 9; i++) if (grid[i][c] == val) return false;
        int br = (r/3)*3, bc = (c/3)*3;
        for (int i = br; i < br+3; i++) for (int j = bc; j < bc+3; j++) if (grid[i][j] == val) return false;
        return true;
    }
}