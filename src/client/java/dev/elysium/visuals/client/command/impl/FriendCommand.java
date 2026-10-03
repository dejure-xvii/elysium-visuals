package dev.elysium.visuals.client.command.impl;

import dev.elysium.visuals.client.command.ChatOutput;
import dev.elysium.visuals.client.command.Command;
import dev.elysium.visuals.client.command.CommandException;
import dev.elysium.visuals.client.config.ConfigManager;
import dev.elysium.visuals.client.module.impl.utils.FriendsModule;
import dev.elysium.visuals.client.module.setting.StringListSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.ArrayList;
import java.util.List;

/** {@code .friend add|remove <ник>}, {@code .friend list} — edits the Friends module's list. */
public class FriendCommand extends Command {
	private static final List<String> SUBCOMMANDS = List.of("add", "remove", "list");

	public FriendCommand() {
		super("friend", "друзья: добавить, удалить, список", ".friend <add|remove|list> [ник]");
	}

	@Override
	public void execute(String[] args) throws CommandException {
		StringListSetting friends = FriendsModule.list();
		String sub = arg(args, 0);
		if (sub == null || friends == null) {
			throw usageError(usage());
		}
		switch (sub) {
			case "add" -> {
				String nick = nick(args, "add");
				if (friends.contains(nick)) {
					throw new CommandException(stored(friends, nick) + " уже в друзьях!");
				}
				if (!friends.isValid(nick)) {
					throw new CommandException("Некорректный ник: " + nick + " (латиница, цифры и _, до 16 символов)");
				}
				friends.add(nick);
				ConfigManager.saveIfDirty();
				ChatOutput.info(nick + " добавлен в друзья!");
			}
			case "remove" -> {
				String nick = nick(args, "remove");
				if (!friends.contains(nick)) {
					throw new CommandException(nick + " нет в списке друзей!");
				}
				String name = stored(friends, nick);
				friends.remove(nick);
				ConfigManager.saveIfDirty();
				ChatOutput.info(name + " удалён из друзей!");
			}
			case "list" -> {
				List<String> names = friends.get();
				if (names.isEmpty()) {
					ChatOutput.info("Список друзей пуст. Добавь: .friend add <ник>");
				} else {
					ChatOutput.send(ChatOutput.text("Друзья (" + names.size() + "): ")
							.append(ChatOutput.accent(String.join(", ", names))));
				}
			}
			default -> throw usageError(usage());
		}
	}

	private static String nick(String[] args, String sub) throws CommandException {
		if (args.length < 2) {
			throw usageError(".friend " + sub + " <ник>");
		}
		return args[1];
	}

	/** The nick as written in the list (it's matched case-insensitively). */
	private static String stored(StringListSetting friends, String nick) {
		for (String s : friends.get()) {
			if (s.equalsIgnoreCase(nick)) {
				return s;
			}
		}
		return nick;
	}

	@Override
	public List<String> complete(String[] args) {
		if (args.length == 1) {
			return SUBCOMMANDS;
		}
		StringListSetting friends = FriendsModule.list();
		String sub = arg(args, 0);
		if (args.length != 2 || friends == null) {
			return List.of();
		}
		if ("remove".equals(sub)) {
			return friends.get();
		}
		if ("add".equals(sub)) {
			// Players online who aren't friends yet (and not yourself).
			Minecraft mc = Minecraft.getInstance();
			List<String> online = new ArrayList<>();
			if (mc.getConnection() != null) {
				for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
					String name = info.getProfile().name();
					boolean self = mc.player != null && mc.player.getUUID().equals(info.getProfile().id());
					if (!self && !friends.contains(name)) {
						online.add(name);
					}
				}
			}
			online.sort(String.CASE_INSENSITIVE_ORDER);
			return online;
		}
		return List.of();
	}
}
