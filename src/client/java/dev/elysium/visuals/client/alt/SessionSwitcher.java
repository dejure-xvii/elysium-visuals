package dev.elysium.visuals.client.alt;

import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import dev.elysium.visuals.ElysiumVisuals;
import dev.elysium.visuals.client.mixin.MinecraftSessionAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.util.Util;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Replaces the game's session (name, UUID, token and the services bound to them) without a restart. */
final class SessionSwitcher {
	private SessionSwitcher() {
	}

	/** Must run on the render thread. {@code accessToken} null = offline account. */
	static void apply(String name, UUID id, String accessToken) {
		Minecraft mc = Minecraft.getInstance();
		boolean online = accessToken != null;
		User user = new User(name, id, online ? accessToken : "0", Optional.empty(), Optional.empty());
		UserApiService api = UserApiService.OFFLINE;
		if (online) {
			try {
				api = new YggdrasilAuthenticationService(mc.getProxy()).createUserApiService(accessToken);
			} catch (Exception e) {
				ElysiumVisuals.LOGGER.warn("Account services unavailable: {}", e.getClass().getSimpleName());
			}
		}
		UserApiService service = api;
		MinecraftSessionAccessor a = (MinecraftSessionAccessor) mc;
		a.elysium$setUser(user);
		a.elysium$setProfileFuture(online
				? CompletableFuture.supplyAsync(() -> mc.services().sessionService().fetchProfile(id, true), Util.nonCriticalIoPool())
				: CompletableFuture.completedFuture((ProfileResult) null));
		a.elysium$setUserApiService(service);
		a.elysium$setUserPropertiesFuture(CompletableFuture.supplyAsync(() -> {
			try {
				return service.fetchProperties();
			} catch (AuthenticationException e) {
				return UserApiService.OFFLINE_PROPERTIES;
			}
		}, Util.nonCriticalIoPool()));
		a.elysium$setProfileKeyPairManager(online
				? ProfileKeyPairManager.create(service, user, mc.gameDirectory.toPath())
				: ProfileKeyPairManager.EMPTY_KEY_MANAGER);
		a.elysium$setReportingContext(ReportingContext.create(ReportEnvironment.local(), service));
		ElysiumVisuals.LOGGER.info("Switched account to {} ({})", name, online ? "Microsoft" : "offline");
	}
}
