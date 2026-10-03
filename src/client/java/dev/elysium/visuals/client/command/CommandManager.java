package dev.elysium.visuals.client.command;

import dev.elysium.visuals.ElysiumVisuals;
import dev.elysium.visuals.client.Panic;
import dev.elysium.visuals.client.command.impl.CfgCommand;
import dev.elysium.visuals.client.command.impl.FakePlayerCommand;
import dev.elysium.visuals.client.command.impl.FriendCommand;
import dev.elysium.visuals.client.command.impl.HelpCommand;
import dev.elysium.visuals.client.command.impl.PanicCommand;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Chat commands starting with {@value #PREFIX}. Such messages are handled on
 * the client and never sent to the server.
 */
public final class CommandManager {
	public static final String PREFIX = ".";
	private static final CommandManager INSTANCE = new CommandManager();

	private final List<Command> commands = new ArrayList<>();

	private CommandManager() {
	}

	public static CommandManager get() {
		return INSTANCE;
	}

	/** Add new commands here. */
	private void registerCommands() {
		register(new HelpCommand());
		register(new CfgCommand());
		register(new FriendCommand());
		register(new FakePlayerCommand());
		register(new PanicCommand());
	}

	public void init() {
		registerCommands();
		// Returning false cancels sending: the message never leaves the client.
		ClientSendMessageEvents.ALLOW_CHAT.register(message -> !handle(message));
		ChatCompletion.init();
	}

	private void register(Command command) {
		if (find(command.name()) != null) {
			throw new IllegalStateException("Duplicate command: " + command.name());
		}
		commands.add(command);
	}

	public List<Command> commands() {
		return Collections.unmodifiableList(commands);
	}

	public Command find(String name) {
		for (Command c : commands) {
			if (c.name().equalsIgnoreCase(name)) {
				return c;
			}
		}
		return null;
	}

	/**
	 * True if {@code message} is one of our commands: the prefix followed by a
	 * letter, so "..." or ". ok" in normal chat still go to the server. After
	 * {@code .panic} nothing is intercepted.
	 */
	public static boolean isCommand(String message) {
		return !Panic.isActive() && message.length() > PREFIX.length() && message.startsWith(PREFIX)
				&& Character.isLetter(message.charAt(PREFIX.length()));
	}

	/** Runs a command message; returns true if it was ours (and must not be sent). */
	public boolean handle(String message) {
		if (!isCommand(message)) {
			return false;
		}
		String[] words = message.substring(PREFIX.length()).trim().split("\\s+");
		Command command = find(words[0]);
		if (command == null) {
			ChatOutput.error("Неизвестная команда. Напиши " + PREFIX + "help");
			return true;
		}
		try {
			command.execute(Arrays.copyOfRange(words, 1, words.length));
		} catch (CommandException e) {
			ChatOutput.error(e.getMessage());
		} catch (RuntimeException e) {
			ElysiumVisuals.LOGGER.error("Command failed: {}", message, e);
			ChatOutput.error("Ошибка при выполнении команды: " + e.getMessage());
		}
		return true;
	}

	/** What Tab completion replaces: the start index of the last word and the candidates for it. */
	public record Completion(int start, List<String> candidates) {
	}

	/** Completion candidates for the last word of {@code input}, filtered by what is already typed. */
	public Completion complete(String input) {
		if (!isCommand(input) && !input.equals(PREFIX)) {
			return new Completion(input.length(), List.of());
		}
		int lastSpace = input.lastIndexOf(' ');
		if (lastSpace < 0) {
			// Still typing the command name.
			List<String> names = new ArrayList<>();
			for (Command c : commands) {
				names.add(PREFIX + c.name());
			}
			return new Completion(0, filter(names, input));
		}
		String[] words = input.substring(PREFIX.length()).split(" ", -1);
		Command command = find(words[0]);
		if (command == null) {
			return new Completion(input.length(), List.of());
		}
		String[] args = Arrays.copyOfRange(words, 1, words.length);
		return new Completion(lastSpace + 1, filter(command.complete(args), args[args.length - 1]));
	}

	private static List<String> filter(List<String> candidates, String typed) {
		String prefix = typed.toLowerCase(Locale.ROOT);
		List<String> result = new ArrayList<>();
		for (String c : candidates) {
			if (c.toLowerCase(Locale.ROOT).startsWith(prefix) && !result.contains(c)) {
				result.add(c);
			}
		}
		return result;
	}
}
