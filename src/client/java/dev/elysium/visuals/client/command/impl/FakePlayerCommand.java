package dev.elysium.visuals.client.command.impl;

import dev.elysium.visuals.client.command.ChatOutput;
import dev.elysium.visuals.client.command.Command;
import dev.elysium.visuals.client.command.CommandException;
import dev.elysium.visuals.client.module.impl.utils.FakePlayer;

import java.util.List;

/** {@code .fakeplayer spawn|remove} — the FakePlayer module from the chat. */
public class FakePlayerCommand extends Command {
	public FakePlayerCommand() {
		super("fakeplayer", "локальная копия тебя для проверки эффектов", ".fakeplayer <spawn|remove>");
	}

	@Override
	public void execute(String[] args) throws CommandException {
		FakePlayer module = FakePlayer.instance();
		String sub = arg(args, 0);
		if (sub == null || module == null) {
			throw usageError(usage());
		}
		switch (sub) {
			case "spawn" -> {
				if (module.isEnabled()) {
					if (!module.spawn()) {
						throw new CommandException("Нужно быть в мире");
					}
				} else {
					module.setEnabled(true);
				}
				ChatOutput.info("FakePlayer появился!");
			}
			case "remove" -> {
				if (!module.isEnabled()) {
					throw new CommandException("FakePlayer и так не заспавнен");
				}
				module.setEnabled(false);
				ChatOutput.info("FakePlayer удалён!");
			}
			default -> throw usageError(usage());
		}
	}

	@Override
	public List<String> complete(String[] args) {
		return args.length == 1 ? List.of("spawn", "remove") : List.of();
	}
}
