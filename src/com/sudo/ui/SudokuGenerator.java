// ==================== SudokuGenerator.java ====================
package com.sudo.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class SudokuGenerator {

    private final Random random = new Random();

    public int[][] generatePuzzle(int holes) {
        int[][] grid = new int[9][9];
        fill(grid);
        removeCells(grid, holes);
        return grid;
    }

    // fills the grid with a complete valid Sudoku
    private boolean fill(int[][] grid) {
        for (int i = 0; i < 81; i++) {
            int r = i / 9, c = i % 9;
            if (grid[r][c] == 0) {
                List<Integer> nums = randomNumbers();
                for (int num : nums) {
                    if (valid(grid, r, c, num)) {
                        grid[r][c] = num;
                        if (fill(grid)) return true;
                        grid[r][c] = 0;
                    }
                }
                return false;
            }
        }
        return true;
    }

    private List<Integer> randomNumbers() {
        List<Integer> nums = new ArrayList<>();
        for (int i = 1; i <= 9; i++) nums.add(i);
        Collections.shuffle(nums, random);
        return nums;
    }

    private void removeCells(int[][] grid, int holes) {
        // attempt to remove random cells while preserving unique solution
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < 81; i++) positions.add(i);
        Collections.shuffle(positions, random);

        int removed = 0;
        for (int pos : positions) {
            if (removed >= holes) break;
            int r = pos / 9, c = pos % 9;
            if (grid[r][c] == 0) continue;

            int backup = grid[r][c];
            grid[r][c] = 0;

            int[][] copy = deepCopy(grid);
            if (!hasUniqueSolution(copy)) {
                // revert
                grid[r][c] = backup;
            } else {
                removed++;
            }
        }
    }

    private boolean hasUniqueSolution(int[][] grid) {
        return countSolutions(grid, 0, 2) == 1; // stop after 2
    }

    private int countSolutions(int[][] grid, int index, int limit) {
        if (index == 81) return 1;
        int r = index / 9, c = index % 9;
        if (grid[r][c] != 0) return countSolutions(grid, index + 1, limit);
        int count = 0;
        for (int v = 1; v <= 9; v++) {
            if (valid(grid, r, c, v)) {
                grid[r][c] = v;
                count += countSolutions(grid, index + 1, limit);
                grid[r][c] = 0;
                if (count >= limit) return count;
            }
        }
        return count;
    }

    private boolean valid(int[][] grid, int r, int c, int val) {
        for (int j = 0; j < 9; j++) if (grid[r][j] == val) return false;
        for (int i = 0; i < 9; i++) if (grid[i][c] == val) return false;
        int br = (r/3)*3, bc = (c/3)*3;
        for (int i = br; i < br+3; i++) for (int j = bc; j < bc+3; j++) if (grid[i][j] == val) return false;
        return true;
    }

    private int[][] deepCopy(int[][] src) {
        int[][] dst = new int[9][9];
        for (int r = 0; r < 9; r++) System.arraycopy(src[r], 0, dst[r], 0, 9);
        return dst;
    }
}