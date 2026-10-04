package dev.elysium.visuals.client.hud.element;

import dev.elysium.visuals.client.hud.HudIcon;
import dev.elysium.visuals.client.hud.ListElement;
import dev.elysium.visuals.client.hud.style.HudData;
import dev.elysium.visuals.client.module.impl.utils.FriendsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.ArrayList;
import java.util.List;

/** Friends that are online on the current server: skin head, nick and a status dot. */
public class FriendsElement extends ListElement {
	private static final HudData.Block BLOCK = new HudData.Block(HudData.Kind.FRIENDS, "Друзья", HudIcon.FRIENDS,
			"Игрок", "Статус");

	public FriendsElement() {
		super("friends", "Список друзей", Anchor.TOP_LEFT);
	}

	@Override
	protected HudData.Block block() {
		return BLOCK;
	}

	private static HudData.Row row(String name) {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		PlayerInfo info = connection == null ? null : connection.getPlayerInfoIgnoreCase(name);
		HudData.Lead lead = info != null ? HudData.Lead.head(info.getSkin()) : HudData.Lead.icon(HudIcon.PLAYER);
		return HudData.Row.text(name, lead, name, "", "онлайн", true);
	}

	@Override
	protected List<HudData.Row> collect() {
		List<HudData.Row> rows = new ArrayList<>();
		for (String name : FriendsModule.onlineFriends()) {
			rows.add(row(name));
		}
		return rows;
	}

	@Override
	protected List<HudData.Row> sample() {
		var player = Minecraft.getInstance().player;
		return List.of(row(player != null ? player.getName().getString() : "Notch"));
	}
}
