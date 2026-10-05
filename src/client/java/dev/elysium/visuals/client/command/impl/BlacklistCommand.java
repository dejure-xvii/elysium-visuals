package dev.elysium.visuals.client.command.impl;

import dev.elysium.visuals.client.command.ChatOutput;
import dev.elysium.visuals.client.command.Command;
import dev.elysium.visuals.client.command.CommandException;
import dev.elysium.visuals.client.config.ConfigManager;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.impl.player.PvPHelper;
import dev.elysium.visuals.client.module.setting.BooleanSetting;
import dev.elysium.visuals.client.module.setting.StringListSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.ArrayList;
import java.util.List;

/** {@code .blacklist add|remove <ник>}, {@code .blacklist list} — PvPHelper's chat black list. */
public class BlacklistCommand extends Command {
	private static final List<String> SUBCOMMANDS = List.of("add", "remove", "list");

	public BlacklistCommand() {
		super("blacklist", "чёрный список чата: добавить, удалить, список", ".blacklist <add|remove|list> [ник]");
	}

	@Override
	public void execute(String[] args) throws CommandException {
		StringListSetting list = PvPHelper.blacklist();
		String sub = arg(args, 0);
		if (sub == null || list == null) {
			throw usageError(usage());
		}
		switch (sub) {
			case "add" -> {
				String nick = nick(args, "add");
				if (list.contains(nick)) {
					throw new CommandException(nick + " уже в чёрном списке");
				}
				if (!list.isValid(nick)) {
					throw new CommandException("Некорректный ник: " + nick + " (латиница, цифры и _, 3–16 символов)");
				}
				list.add(nick);
				enableBlacklist();
				ConfigManager.saveIfDirty();
				ChatOutput.info(nick + " в чёрном списке: его сообщения скрыты");
			}
			case "remove" -> {
				String nick = nick(args, "remove");
				if (!list.contains(nick)) {
					throw new CommandException(nick + " нет в чёрном списке");
				}
				list.remove(nick);
				ConfigManager.saveIfDirty();
				ChatOutput.info(nick + " убран из чёрного списка");
			}
			case "list" -> {
				List<String> names = list.get();
				if (names.isEmpty()) {
					ChatOutput.info("Чёрный список пуст. Добавь: .blacklist add <ник>");
				} else {
					ChatOutput.send(ChatOutput.text("Чёрный список (" + names.size() + "): ")
							.append(ChatOutput.accent(String.join(", ", names))));
				}
			}
			default -> throw usageError(usage());
		}
	}

	/** Adding someone means the list should work: PvPHelper and its black list get switched on. */
	private static void enableBlacklist() {
		PvPHelper m = ModuleManager.get().find(PvPHelper.class);
		if (m == null) {
			return;
		}
		m.settings().stream().filter(s -> s.id().equals("blacklist_on")).findFirst()
				.ifPresent(s -> ((BooleanSetting) s).set(true));
		if (!m.isEnabled()) {
			m.setEnabled(true);
		}
	}

	private static String nick(String[] args, String sub) throws CommandException {
		if (args.length < 2) {
			throw usageError(".blacklist " + sub + " <ник>");
		}
		return args[1];
	}

	@Override
	public List<String> complete(String[] args) {
		if (args.length <= 1) {
			return SUBCOMMANDS;
		}
		if (args.length == 2) {
			String sub = args[0].toLowerCase();
			if (sub.equals("remove")) {
				StringListSetting list = PvPHelper.blacklist();
				return list == null ? List.of() : list.get();
			}
			if (sub.equals("add") && Minecraft.getInstance().getConnection() != null) {
				List<String> names = new ArrayList<>();
				for (PlayerInfo info : Minecraft.getInstance().getConnection().getOnlinePlayers()) {
					names.add(info.getProfile().name());
				}
				return names;
			}
		}
		return List.of();
	}
}
