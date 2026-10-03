package dev.elysium.visuals.client.command.impl;

import dev.elysium.visuals.client.command.ChatOutput;
import dev.elysium.visuals.client.command.Command;
import dev.elysium.visuals.client.command.CommandManager;

/** {@code .help} — every command with its syntax and a short description. */
public class HelpCommand extends Command {
	public HelpCommand() {
		super("help", "список команд", ".help");
	}

	@Override
	public void execute(String[] args) {
		ChatOutput.info("Команды (Tab — автодополнение):");
		for (Command c : CommandManager.get().commands()) {
			ChatOutput.send(ChatOutput.accent(c.usage()).append(ChatOutput.dim(" — " + c.description())));
		}
	}
}
