package dev.elysium.visuals.client.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;

/** The session fields the Alt Manager swaps when switching accounts. */
@Mixin(Minecraft.class)
public interface MinecraftSessionAccessor {
	@Mutable
	@Accessor("user")
	void elysium$setUser(User user);

	@Mutable
	@Accessor("profileFuture")
	void elysium$setProfileFuture(CompletableFuture<ProfileResult> future);

	@Mutable
	@Accessor("userApiService")
	void elysium$setUserApiService(UserApiService service);

	@Mutable
	@Accessor("userPropertiesFuture")
	void elysium$setUserPropertiesFuture(CompletableFuture<UserApiService.UserProperties> future);

	@Mutable
	@Accessor("profileKeyPairManager")
	void elysium$setProfileKeyPairManager(ProfileKeyPairManager manager);

	@Accessor("reportingContext")
	void elysium$setReportingContext(ReportingContext context);
}
