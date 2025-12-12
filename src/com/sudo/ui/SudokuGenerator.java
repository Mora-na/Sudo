package com.sudo.ui;

public class SudokuGenerator {

    public int[][] generateFull() {
        int[][] grid = new int[9][9];
        fill(0, 0, grid);
        return grid;
    }

    private boolean fill(int r, int c, int[][] grid) {
        if (r == 9) return true;
        int nr = (c == 8) ? r + 1 : r;
        int nc = (c == 8) ? 0 : c + 1;

        java.util.List<Integer> vals = new java.util.ArrayList<>();
        for (int i = 1; i <= 9; i++) vals.add(i);
        java.util.Collections.shuffle(vals);

        for (int v : vals) {
            if (valid(grid, r, c, v)) {
                grid[r][c] = v;
                if (fill(nr, nc, grid)) return true;
                grid[r][c] = 0;
            }
        }
        return false;
    }

    private boolean valid(int[][] grid, int r, int c, int val) {
        for (int j = 0; j < 9; j++) if (grid[r][j] == val) return false;
        for (int i = 0; i < 9; i++) if (grid[i][c] == val) return false;
        int br = (r / 3) * 3, bc = (c / 3) * 3;
        for (int i = br; i < br + 3; i++) for (int j = bc; j < bc + 3; j++) if (grid[i][j] == val) return false;
        return true;
    }
}
