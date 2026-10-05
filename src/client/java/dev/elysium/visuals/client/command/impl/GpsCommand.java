package dev.elysium.visuals.client.command.impl;

import dev.elysium.visuals.client.command.ChatOutput;
import dev.elysium.visuals.client.command.Command;
import dev.elysium.visuals.client.command.CommandException;
import dev.elysium.visuals.client.gps.GpsTarget;
import dev.elysium.visuals.client.module.ModuleManager;
import dev.elysium.visuals.client.module.impl.utils.Gps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.List;

/** {@code .gps <x> <z>} sets the GPS mark, {@code .gps off} removes it. */
public class GpsCommand extends Command {
	public GpsCommand() {
		super("gps", "метка GPS на координаты", ".gps <x> <z> | .gps off");
	}

	@Override
	public void execute(String[] args) throws CommandException {
		String first = arg(args, 0);
		if ("off".equals(first)) {
			if (GpsTarget.get() == null) {
				throw new CommandException("Метка GPS не стоит");
			}
			GpsTarget.clear();
			ChatOutput.info("Метка GPS убрана");
			return;
		}
		if (args.length != 2) {
			throw usageError(usage());
		}
		int x = coordinate(args[0]), z = coordinate(args[1]);
		GpsTarget.set(x, z, "GPS");
		Gps gps = ModuleManager.get().find(Gps.class);
		if (gps != null && !gps.isEnabled()) {
			gps.setEnabled(true);
		}
		double d = GpsTarget.distance();
		ChatOutput.send(ChatOutput.text("Метка GPS: ").append(ChatOutput.accent(x + " " + z))
				.append(ChatOutput.dim(d >= 0 ? " · " + Math.round(d) + " бл." : "")));
	}

	private static int coordinate(String s) throws CommandException {
		try {
			double v = Double.parseDouble(s.replace(',', '.'));
			if (Math.abs(v) > 30_000_000) {
				throw new CommandException("Координата вне мира: " + s);
			}
			return (int) Math.floor(v);
		} catch (NumberFormatException e) {
			throw new CommandException("Не число: " + s + ". Использование: .gps <x> <z>");
		}
	}

	@Override
	public List<String> complete(String[] args) {
		LocalPlayer p = Minecraft.getInstance().player;
		if (args.length == 1) {
			return p == null ? List.of("off") : List.of("off", String.valueOf(p.getBlockX()));
		}
		if (args.length == 2 && p != null && !"off".equalsIgnoreCase(args[0])) {
			return List.of(String.valueOf(p.getBlockZ()));
		}
		return List.of();
	}
}
