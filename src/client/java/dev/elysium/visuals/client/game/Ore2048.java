package dev.elysium.visuals.client.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * The rules of "Руда 2048": a 4×4 board of ores; sliding merges two equal
 * ores into the next one. Tiles keep an identity so the screen can animate
 * where each one came from.
 */
public final class Ore2048 {
	public static final int SIZE = 4;
	/** Exponent of the winning ore (2^11 = 2048, the Nether Star). */
	public static final int WIN = 11;
	private static final int UNDO_DEPTH = 10;

	public enum Dir { UP, DOWN, LEFT, RIGHT }

	/** A tile on the board. {@code exp} 1 = 2 (coal), 2 = 4, ... */
	public static final class Tile {
		public final int id;
		public int exp;
		public int row, col;
		/** Where the last move started (for the slide animation). */
		public int fromRow, fromCol;
		/** Merged into another tile this move: slides in, then disappears. */
		public boolean consumed;
		/** Created by a merge this move (pops). */
		public boolean merged;
		/** Spawned after this move (scales in). */
		public boolean spawned;

		Tile(int id, int exp, int row, int col) {
			this.id = id;
			this.exp = exp;
			this.row = this.fromRow = row;
			this.col = this.fromCol = col;
		}
	}

	private record Snapshot(int[] exps, int score) {
	}

	private final Random random = new Random();
	private final Tile[][] grid = new Tile[SIZE][SIZE];
	private final List<Tile> tiles = new ArrayList<>();
	private final Deque<Snapshot> undo = new ArrayDeque<>();
	private int nextId;
	private int score;
	private boolean won;
	private boolean keepPlaying;

	public Ore2048() {
		reset();
	}

	public void reset() {
		clear();
		score = 0;
		won = false;
		keepPlaying = false;
		undo.clear();
		spawn();
		spawn();
		for (Tile t : tiles) {
			t.spawned = true;
		}
	}

	private void clear() {
		tiles.clear();
		for (Tile[] row : grid) {
			java.util.Arrays.fill(row, null);
		}
	}

	public List<Tile> tiles() {
		return tiles;
	}

	public int score() {
		return score;
	}

	public boolean won() {
		return won && !keepPlaying;
	}

	public void keepPlaying() {
		keepPlaying = true;
	}

	public boolean canUndo() {
		return !undo.isEmpty();
	}

	public int highest() {
		int best = 0;
		for (Tile t : tiles) {
			if (!t.consumed) {
				best = Math.max(best, t.exp);
			}
		}
		return best;
	}

	/** Result of a move: whether anything moved, the merges made (their exponents) and points. */
	public record MoveResult(boolean moved, List<Tile> merges, int points) {
	}

	public MoveResult move(Dir dir) {
		// Drop the tiles consumed by the previous move and clear the animation flags.
		tiles.removeIf(t -> t.consumed);
		for (Tile t : tiles) {
			t.fromRow = t.row;
			t.fromCol = t.col;
			t.merged = false;
			t.spawned = false;
		}
		Snapshot before = snapshot();
		List<Tile> merges = new ArrayList<>();
		int points = 0;
		boolean moved = false;
		for (int line = 0; line < SIZE; line++) {
			// Cells of this line in the order tiles slide towards.
			int[][] cells = new int[SIZE][];
			for (int i = 0; i < SIZE; i++) {
				cells[i] = switch (dir) {
					case LEFT -> new int[]{line, i};
					case RIGHT -> new int[]{line, SIZE - 1 - i};
					case UP -> new int[]{i, line};
					case DOWN -> new int[]{SIZE - 1 - i, line};
				};
			}
			int target = 0;
			Tile last = null; // the last placed tile that can still merge
			for (int i = 0; i < SIZE; i++) {
				Tile t = grid[cells[i][0]][cells[i][1]];
				if (t == null) {
					continue;
				}
				grid[cells[i][0]][cells[i][1]] = null;
				if (last != null && last.exp == t.exp && !last.merged) {
					// Merge t into last.
					t.consumed = true;
					t.row = last.row;
					t.col = last.col;
					last.exp++;
					last.merged = true;
					points += 1 << last.exp;
					merges.add(last);
					moved = true;
					if (last.exp >= WIN && !won) {
						won = true;
					}
					continue;
				}
				int[] c = cells[target++];
				if (c[0] != t.row || c[1] != t.col) {
					moved = true;
				}
				t.row = c[0];
				t.col = c[1];
				grid[c[0]][c[1]] = t;
				last = t;
			}
		}
		if (!moved) {
			return new MoveResult(false, List.of(), 0);
		}
		score += points;
		undo.push(before);
		while (undo.size() > UNDO_DEPTH) {
			undo.removeLast();
		}
		Tile s = spawn();
		if (s != null) {
			s.spawned = true;
		}
		return new MoveResult(true, merges, points);
	}

	public boolean undo() {
		Snapshot s = undo.poll();
		if (s == null) {
			return false;
		}
		restore(s.exps(), s.score());
		return true;
	}

	public boolean canMove() {
		for (int r = 0; r < SIZE; r++) {
			for (int c = 0; c < SIZE; c++) {
				Tile t = grid[r][c];
				if (t == null) {
					return true;
				}
				if (c + 1 < SIZE && grid[r][c + 1] != null && grid[r][c + 1].exp == t.exp) {
					return true;
				}
				if (r + 1 < SIZE && grid[r + 1][c] != null && grid[r + 1][c].exp == t.exp) {
					return true;
				}
			}
		}
		return false;
	}

	private Tile spawn() {
		List<int[]> free = new ArrayList<>();
		for (int r = 0; r < SIZE; r++) {
			for (int c = 0; c < SIZE; c++) {
				if (grid[r][c] == null) {
					free.add(new int[]{r, c});
				}
			}
		}
		if (free.isEmpty()) {
			return null;
		}
		int[] cell = free.get(random.nextInt(free.size()));
		Tile t = new Tile(nextId++, random.nextInt(10) == 0 ? 2 : 1, cell[0], cell[1]);
		grid[cell[0]][cell[1]] = t;
		tiles.add(t);
		return t;
	}

	// ---------------------------------------------------------------------
	// Saving
	// ---------------------------------------------------------------------

	private Snapshot snapshot() {
		return new Snapshot(exps(), score);
	}

	/** The board row by row, 0 = empty. */
	public int[] exps() {
		int[] e = new int[SIZE * SIZE];
		for (int r = 0; r < SIZE; r++) {
			for (int c = 0; c < SIZE; c++) {
				e[r * SIZE + c] = grid[r][c] != null ? grid[r][c].exp : 0;
			}
		}
		return e;
	}

	public void restore(int[] exps, int score) {
		clear();
		for (int i = 0; i < SIZE * SIZE && i < exps.length; i++) {
			if (exps[i] > 0) {
				Tile t = new Tile(nextId++, exps[i], i / SIZE, i % SIZE);
				grid[t.row][t.col] = t;
				tiles.add(t);
			}
		}
		this.score = score;
		this.won = highest() >= WIN;
		this.keepPlaying = won;
	}
}
