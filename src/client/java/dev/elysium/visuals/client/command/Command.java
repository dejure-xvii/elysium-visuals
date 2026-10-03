package dev.elysium.visuals.client.command;

import java.util.List;
import java.util.Locale;

/**
 * A chat command such as {@code .friend add Notch}. A new command is one class:
 *
 * <pre>{@code
 * public class PingCommand extends Command {
 *     public PingCommand() {
 *         super("ping", "Проверка связи", ".ping");
 *     }
 *
 *     @Override
 *     public void execute(String[] args) throws CommandException {
 *         ChatOutput.info("Pong!");
 *     }
 * }
 * }</pre>
 * and one line in {@link CommandManager#registerCommands()}. Names and
 * sub-commands are matched case-insensitively.
 */
public abstract class Command {
	private final String name;
	private final String description;
	private final String usage;

	/**
	 * @param usage shown in {@code .help}, e.g. {@code ".cfg <save|load|remove|reset|list> [имя]"}
	 */
	protected Command(String name, String description, String usage) {
		this.name = name.toLowerCase(Locale.ROOT);
		this.description = description;
		this.usage = usage;
	}

	/** Runs the command; {@code args} are the words after the name. Throw {@link CommandException} to report an error. */
	public abstract void execute(String[] args) throws CommandException;

	/**
	 * Tab completion candidates for the last word of {@code args} (which may be
	 * empty when the input ends with a space). Filtering by the typed prefix is
	 * done by the caller.
	 */
	public List<String> complete(String[] args) {
		return List.of();
	}

	public String name() {
		return name;
	}

	public String description() {
		return description;
	}

	public String usage() {
		return usage;
	}

	/** Shorthand for an error that shows the correct syntax. */
	protected static CommandException usageError(String usage) {
		return new CommandException("Использование: " + usage);
	}

	/** The argument at {@code index}, lower-cased, or null if missing. */
	protected static String arg(String[] args, int index) {
		return index < args.length ? args[index].toLowerCase(Locale.ROOT) : null;
	}
}
