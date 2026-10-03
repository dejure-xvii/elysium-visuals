package dev.elysium.visuals.client.command.impl;

import dev.elysium.visuals.ElysiumVisuals;
import dev.elysium.visuals.client.command.ChatOutput;
import dev.elysium.visuals.client.command.Command;
import dev.elysium.visuals.client.command.CommandException;
import dev.elysium.visuals.client.config.ConfigManager;
import dev.elysium.visuals.client.notify.Notifications;

import java.io.IOException;
import java.util.List;

/** {@code .cfg save|load|remove <имя>}, {@code .cfg reset}, {@code .cfg list}. */
public class CfgCommand extends Command {
	private static final List<String> SUBCOMMANDS = List.of("save", "load", "remove", "reset", "list");

	public CfgCommand() {
		super("cfg", "конфиги: сохранить, загрузить, удалить, сбросить, список", ".cfg <save|load|remove|reset|list> [имя]");
	}

	@Override
	public void execute(String[] args) throws CommandException {
		String sub = arg(args, 0);
		if (sub == null) {
			throw usageError(usage());
		}
		switch (sub) {
			case "save" -> {
				String name = name(args, "save");
				try {
					ConfigManager.saveNamed(name);
				} catch (IOException e) {
					throw failed("сохранить", name, e);
				}
				ChatOutput.info("Конфиг " + name + " сохранён!");
			}
			case "load" -> {
				String name = existing(args, "load");
				try {
					ConfigManager.loadNamed(name);
				} catch (IOException e) {
					throw failed("загрузить", name, e);
				}
				ChatOutput.info("Конфиг " + name + " успешно загружен!");
				Notifications.config("Загружен конфиг " + name);
			}
			case "remove" -> {
				String name = existing(args, "remove");
				try {
					ConfigManager.removeNamed(name);
				} catch (IOException e) {
					throw failed("удалить", name, e);
				}
				ChatOutput.info("Конфиг " + name + " удалён!");
			}
			case "reset" -> {
				ConfigManager.resetAll();
				ChatOutput.info("Настройки сброшены!");
			}
			case "list" -> {
				List<String> names = ConfigManager.listNamed();
				if (names.isEmpty()) {
					ChatOutput.info("Сохранённых конфигов нет. Создай: .cfg save <имя>");
				} else {
					ChatOutput.send(ChatOutput.text("Конфиги (" + names.size() + "): ")
							.append(ChatOutput.accent(String.join(", ", names))));
				}
			}
			default -> throw usageError(usage());
		}
	}

	/** The config name argument, validated. */
	private static String name(String[] args, String sub) throws CommandException {
		if (args.length < 2) {
			throw usageError(".cfg " + sub + " <имя>");
		}
		String name = ConfigManager.normalizeName(args[1]);
		if (name == null) {
			throw new CommandException("Имя конфига: до 32 символов, только латиница, цифры, _ и -");
		}
		return name;
	}

	private static String existing(String[] args, String sub) throws CommandException {
		String name = name(args, sub);
		if (!ConfigManager.exists(name)) {
			throw new CommandException("Конфиг " + name + " не найден!");
		}
		return name;
	}

	private static CommandException failed(String action, String name, IOException e) {
		ElysiumVisuals.LOGGER.error("Failed to {} config {}", action, name, e);
		return new CommandException("Не удалось " + action + " конфиг " + name + ": " + e.getMessage());
	}

	@Override
	public List<String> complete(String[] args) {
		if (args.length == 1) {
			return SUBCOMMANDS;
		}
		String sub = arg(args, 0);
		if (args.length == 2 && ("load".equals(sub) || "remove".equals(sub) || "save".equals(sub))) {
			return ConfigManager.listNamed();
		}
		return List.of();
	}
}
