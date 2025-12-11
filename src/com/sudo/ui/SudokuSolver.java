package com.sudo.ui;

public class SudokuSolver {

    public enum StepType { PUT, CLEAR }

    // Simple backtracking solver that records steps (PUT and CLEAR for backtracking)
    public java.util.List<Step> generateSteps(int[][] grid) {
        java.util.List<Step> steps = new java.util.ArrayList<>();
        int[][] a = new int[9][9];
        for (int i = 0; i < 9; i++) System.arraycopy(grid[i], 0, a[i], 0, 9);

        if (!solveWithSteps(a, steps)) {
            return null;
        }
        return steps;
    }

    private boolean solveWithSteps(int[][] grid, java.util.List<Step> steps) {
        int[] rc = findEmpty(grid);
        if (rc == null) return true;
        int r = rc[0], c = rc[1];
        for (int v = 1; v <= 9; v++) {
            if (valid(grid, r, c, v)) {
                grid[r][c] = v;
                steps.add(new Step(StepType.PUT, r, c, v));
                if (solveWithSteps(grid, steps)) return true;
                // backtrack
                grid[r][c] = 0;
                steps.add(new Step(StepType.CLEAR, r, c, 0));
            }
        }
        return false;
    }

    private int[] findEmpty(int[][] grid) {
        for (int i = 0; i < 9; i++) for (int j = 0; j < 9; j++) if (grid[i][j] == 0) return new int[]{i, j};
        return null;
    }

    public static class Step {
        public StepType type;
        public int r, c, val;

        public Step(StepType t, int r, int c, int v) {
            this.type = t;
            this.r = r;
            this.c = c;
            this.val = v;
        }
    }

    private boolean valid(int[][] grid, int r, int c, int val) {
        for (int j = 0; j < 9; j++) if (grid[r][j] == val) return false;
        for (int i = 0; i < 9; i++) if (grid[i][c] == val) return false;
        int br = (r/3)*3, bc = (c/3)*3;
        for (int i = br; i < br+3; i++) for (int j = bc; j < bc+3; j++) if (grid[i][j] == val) return false;
        return true;
    }
}
