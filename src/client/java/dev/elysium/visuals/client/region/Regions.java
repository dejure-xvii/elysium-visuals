package dev.elysium.visuals.client.region;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What RegionHelper knows: WorldGuard regions (from {@code /rg info} in chat)
 * and the current WorldEdit selection (from the CUI channel, or from
 * WorldEdit's chat replies when the server doesn't use CUI).
 */
public final class Regions {
	/** A cuboid region; {@code box} is in block coordinates, max inclusive (as WorldGuard shows it). */
	public record Region(String name, BlockPos min, BlockPos max) {
		public AABB box() {
			return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
		}
	}

	private static final String NUM = "(-?\\d+(?:\\.\\d+)?)";
	private static final String TRIPLE = "\\(?\\s*" + NUM + "\\s*,\\s*" + NUM + "\\s*,\\s*" + NUM + "\\s*\\)?";
	/** "Bounds: (1, 2, 3) -> (4, 5, 6)" / "Границы: (…) -> (…)" from {@code /rg info}. */
	private static final Pattern BOUNDS = Pattern.compile(TRIPLE + "\\s*(?:->|→|—)\\s*" + TRIPLE);
	/** "Region: name (type=cuboid, …)" / "Регион: name …". */
	private static final Pattern REGION_NAME = Pattern.compile("(?:region|регион)\\s*:?\\s*([A-Za-z0-9_\\-]+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	private static final Pattern POSITION = Pattern.compile("\\(\\s*" + NUM + "\\s*,\\s*" + NUM + "\\s*,\\s*" + NUM + "\\s*\\)");
	private static final Pattern FORMATTING = Pattern.compile("§.");

	private static final Map<String, Region> regions = new LinkedHashMap<>();
	private static String lastRegionName;
	private static BlockPos pos1, pos2;
	private static boolean fromCui;

	private Regions() {
	}

	public static List<Region> regions() {
		return new ArrayList<>(regions.values());
	}

	public static BlockPos pos1() {
		return pos1;
	}

	public static BlockPos pos2() {
		return pos2;
	}

	/** True if the selection came over CUI (the server keeps it up to date). */
	public static boolean selectionFromCui() {
		return fromCui;
	}

	/** Size of the selection in blocks (x, y, z), or null without both points. */
	public static int[] selectionSize() {
		if (pos1 == null || pos2 == null) {
			return null;
		}
		return new int[]{Math.abs(pos1.getX() - pos2.getX()) + 1, Math.abs(pos1.getY() - pos2.getY()) + 1,
				Math.abs(pos1.getZ() - pos2.getZ()) + 1};
	}

	public static void clearRegions() {
		regions.clear();
		lastRegionName = null;
	}

	public static void clearSelection() {
		pos1 = pos2 = null;
		fromCui = false;
	}

	/** Forgets everything (on leaving a world). */
	public static void reset() {
		clearRegions();
		clearSelection();
	}

	// --- WorldEdit CUI ----------------------------------------------------------------------------

	/** One CUI message from the server: "s|cuboid", "p|0|x|y|z|area", "p|1|…". */
	public static void onCui(String message) {
		String[] p = message.split("\\|");
		if (p.length == 0) {
			return;
		}
		switch (p[0]) {
			case "s" -> {
				clearSelection();
				fromCui = true;
			}
			case "p" -> {
				if (p.length >= 5) {
					BlockPos pos = parseBlock(p[2], p[3], p[4]);
					if (pos != null) {
						fromCui = true;
						if ("0".equals(p[1])) {
							pos1 = pos;
						} else if ("1".equals(p[1])) {
							pos2 = pos;
						}
					}
				}
			}
			default -> {
				// Other shapes (polygons, ellipsoids) aren't drawn; the cuboid points still are.
			}
		}
	}

	// --- Chat ---------------------------------------------------------------------------------------

	/** Reads a chat line: WorldGuard region info and WorldEdit position replies. */
	public static void onChat(String line) {
		String text = FORMATTING.matcher(line).replaceAll("");
		String lower = text.toLowerCase(Locale.ROOT);
		Matcher name = REGION_NAME.matcher(text);
		if (name.find() && !lower.contains("->")) {
			lastRegionName = name.group(1);
		}
		Matcher bounds = BOUNDS.matcher(text);
		if ((lower.contains("bounds") || lower.contains("границ") || lower.contains("->")) && bounds.find()) {
			BlockPos a = parseBlock(bounds.group(1), bounds.group(2), bounds.group(3));
			BlockPos b = parseBlock(bounds.group(4), bounds.group(5), bounds.group(6));
			if (a != null && b != null) {
				String key = lastRegionName != null ? lastRegionName : "регион " + (regions.size() + 1);
				regions.put(key, new Region(key, BlockPos.min(a, b), BlockPos.max(a, b)));
			}
			return;
		}
		if (fromCui) {
			return; // CUI keeps the selection exact
		}
		Matcher pos = POSITION.matcher(text);
		if (!pos.find()) {
			return;
		}
		BlockPos at = parseBlock(pos.group(1), pos.group(2), pos.group(3));
		if (at == null) {
			return;
		}
		boolean first = lower.contains("first position") || lower.contains("первая точка") || lower.contains("первая позиция")
				|| lower.contains("pos1") || lower.contains("позиция 1") || lower.contains("точка 1");
		boolean second = lower.contains("second position") || lower.contains("вторая точка") || lower.contains("вторая позиция")
				|| lower.contains("pos2") || lower.contains("позиция 2") || lower.contains("точка 2");
		if (first) {
			pos1 = at;
		} else if (second) {
			pos2 = at;
		}
	}

	private static BlockPos parseBlock(String x, String y, String z) {
		try {
			return BlockPos.containing(Double.parseDouble(x), Double.parseDouble(y), Double.parseDouble(z));
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
