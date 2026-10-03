package dev.elysium.visuals.client.command;

/** A user-facing command error; its message is shown in red in the chat. */
public class CommandException extends Exception {
	public CommandException(String message) {
		super(message);
	}
}
