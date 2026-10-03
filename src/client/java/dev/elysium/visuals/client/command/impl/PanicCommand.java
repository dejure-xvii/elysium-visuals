package dev.elysium.visuals.client.command.impl;

import dev.elysium.visuals.client.Panic;
import dev.elysium.visuals.client.command.ChatOutput;
import dev.elysium.visuals.client.command.Command;

/** {@code .panic} — hides the client until the game is restarted. */
public class PanicCommand extends Command {
	public PanicCommand() {
		super("panic", "полностью скрыть клиент до перезапуска игры", ".panic");
	}

	@Override
	public void execute(String[] args) {
		// The confirmation is the last thing the client says: after this, chat commands are no longer intercepted.
		ChatOutput.info("Клиент скрыт до перезапуска игры. Настройки не изменены.");
		Panic.activate();
	}
}
