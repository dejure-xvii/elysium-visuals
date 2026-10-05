package dev.elysium.visuals.client.gps;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Server events whose coordinates AutoEventGPS picks up from boss bars and
 * chat. A new server or event is one entry in {@link #SERVERS}: an event is
 * found by any of its keywords, followed (within a short stretch of text) by
 * 2 or 3 numbers — "x z" or "x y z", with optional "x:"/"y:"/"z:" labels.
 * For example "Редкий Аирдроп: 123 -1230 Босс не убит" gives x=123, z=-1230.
 */
public final class EventPatterns {
	/** An event; {@code keywords} are matched in lower case, without color codes. */
	public record Event(String id, String label, List<String> keywords) {
	}

	public record Server(String id, String label, List<Event> events) {
	}

	/** A recognised event with its coordinates. */
	public record Match(Event event, int x, int z) {
	}

	public static final List<Server> SERVERS = List.of(
			new Server("reallyworld", "ReallyWorld", List.of(
					new Event("airdrop", "Аирдроп", List.of("аирдроп", "аир дроп", "airdrop")),
					new Event("talisman", "Талисман", List.of("талисман")),
					new Event("trader", "Тайный торговец", List.of("тайный торговец", "торговец", "торговца")))));

	/** How far after the keyword the coordinates may start (characters). */
	private static final int SEARCH_WINDOW = 80;
	private static final String SEP = "[\\s,;/|]+";
	private static final Pattern COORDS = Pattern.compile(
			"(?<![\\d.])(?:[xх]\\s*[:=]?\\s*)?(-?\\d{1,8})" + SEP
					+ "(?:[yу]\\s*[:=]?\\s*)?(-?\\d{1,8})"
					+ "(?:" + SEP + "(?:z\\s*[:=]?\\s*)?(-?\\d{1,8}))?(?![\\d.])");
	private static final Pattern FORMATTING = Pattern.compile("§.");

	private EventPatterns() {
	}

	public static Server server(String id) {
		for (Server s : SERVERS) {
			if (s.id().equals(id)) {
				return s;
			}
		}
		return SERVERS.getFirst();
	}

	/** The first enabled event mentioned in {@code text} with coordinates after it, or null. */
	public static Match find(Server server, Set<String> enabled, String text) {
		String clean = FORMATTING.matcher(text).replaceAll("").toLowerCase(Locale.ROOT);
		for (Event e : server.events()) {
			if (!enabled.contains(e.id())) {
				continue;
			}
			for (String keyword : e.keywords()) {
				int at = clean.indexOf(keyword);
				if (at < 0) {
					continue;
				}
				int from = at + keyword.length();
				String after = clean.substring(from, Math.min(clean.length(), from + SEARCH_WINDOW));
				Matcher m = COORDS.matcher(after);
				if (m.find()) {
					int a = Integer.parseInt(m.group(1)), b = Integer.parseInt(m.group(2));
					// Three numbers are x y z; two are x z.
					return m.group(3) != null ? new Match(e, a, Integer.parseInt(m.group(3))) : new Match(e, a, b);
				}
			}
		}
		return null;
	}
}
