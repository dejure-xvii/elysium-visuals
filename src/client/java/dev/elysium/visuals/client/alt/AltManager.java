package dev.elysium.visuals.client.alt;

import dev.elysium.visuals.ElysiumVisuals;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Account operations for the Alt Manager screen: the shared list, adding,
 * renaming and removing, Microsoft sign-in and switching the game's session.
 * Network work runs off the render thread; the session swap runs on it.
 */
public final class AltManager {
	private static List<AltAccount> accounts = new ArrayList<>();

	private AltManager() {
	}

	public static List<AltAccount> accounts() {
		return accounts;
	}

	public static void reload() {
		accounts = AccountStore.load();
	}

	/** The account the game is signed in with right now (matched by UUID), or null. */
	public static String currentId() {
		UUID now = Minecraft.getInstance().getUser().getProfileId();
		for (AltAccount a : accounts) {
			try {
				if (a.profileId().equals(now) && a.username().equalsIgnoreCase(Minecraft.getInstance().getUser().getName())) {
					return a.id();
				}
			} catch (RuntimeException ignored) {
			}
		}
		return null;
	}

	/** Can the session be switched now? Not while connected to a server. */
	public static boolean canSwitch() {
		Minecraft mc = Minecraft.getInstance();
		return mc.getConnection() == null || mc.isLocalServer();
	}

	private static void upsert(AltAccount account) {
		reload();
		List<AltAccount> list = new ArrayList<>(accounts);
		list.removeIf(a -> a.id().equals(account.id()));
		list.add(account);
		AccountStore.save(list);
		accounts = list;
	}

	/** @return an error message, or null when added */
	public static String addOffline(String name) {
		if (!AltAccount.validName(name)) {
			return "Ник: 3–16 символов, латиница, цифры и _";
		}
		AltAccount a = AltAccount.offline(name);
		if (accounts.stream().anyMatch(x -> x.id().equals(a.id()))) {
			return "Такой аккаунт уже есть";
		}
		upsert(a);
		return null;
	}

	/** Offline accounts only. @return an error message, or null when renamed */
	public static String rename(AltAccount account, String name) {
		if (account.microsoft()) {
			return "Ник Microsoft-аккаунта меняется на minecraft.net";
		}
		if (!AltAccount.validName(name)) {
			return "Ник: 3–16 символов, латиница, цифры и _";
		}
		AltAccount renamed = AltAccount.offline(name);
		boolean wasCurrent = account.id().equals(currentId());
		reload();
		List<AltAccount> list = new ArrayList<>(accounts);
		int i = indexOf(list, account.id());
		list.removeIf(a -> a.id().equals(renamed.id()) && !a.id().equals(account.id()));
		if (i >= 0 && i < list.size() && list.get(i).id().equals(account.id())) {
			list.set(i, renamed);
		} else {
			list.add(renamed);
		}
		AccountStore.save(list);
		accounts = list;
		if (wasCurrent && canSwitch()) {
			SessionSwitcher.apply(renamed.username(), renamed.profileId(), null);
		}
		return null;
	}

	private static int indexOf(List<AltAccount> list, String id) {
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).id().equals(id)) {
				return i;
			}
		}
		return -1;
	}

	public static void remove(AltAccount account) {
		reload();
		List<AltAccount> list = new ArrayList<>(accounts);
		list.removeIf(a -> a.id().equals(account.id()));
		AccountStore.save(list);
		accounts = list;
		if (account.microsoft()) {
			SecretStore.delete(account.id());
		}
	}

	/** Signs the game in with {@code account}. Completes on the render thread; fails with a user-facing message. */
	public static CompletableFuture<Void> login(AltAccount account) {
		if (!canSwitch()) {
			return CompletableFuture.failedFuture(new MicrosoftAuth.AuthException("Сначала выйдите с сервера"));
		}
		Minecraft mc = Minecraft.getInstance();
		if (!account.microsoft()) {
			SessionSwitcher.apply(account.username(), account.profileId(), null);
			return CompletableFuture.completedFuture(null);
		}
		return CompletableFuture.supplyAsync(() -> {
			try {
				String refresh = SecretStore.get(account.id());
				if (refresh == null) {
					throw new MicrosoftAuth.AuthException("Нет сохранённого входа, войдите через Microsoft заново");
				}
				if (!MicrosoftAuth.configured()) {
					throw new MicrosoftAuth.AuthException("Вход Microsoft пока не настроен (нет Client ID)");
				}
				MicrosoftAuth.MsTokens ms = MicrosoftAuth.refresh(refresh);
				SecretStore.put(account.id(), ms.refreshToken());
				return MicrosoftAuth.minecraft(ms.accessToken());
			} catch (MicrosoftAuth.AuthException e) {
				throw new CompletionException(e);
			}
		}).thenAcceptAsync(session -> {
			MicrosoftAuth.Profile p = session.profile();
			upsert(new AltAccount(account.id(), AltAccount.Kind.MICROSOFT, p.name(), p.id(), p.skinUrl()));
			SessionSwitcher.apply(p.name(), AltAccount.parseUuid(p.id()), session.accessToken());
		}, mc);
	}

	/** A running Microsoft device-code sign-in. */
	public static final class MsLogin {
		private final AtomicBoolean cancelled = new AtomicBoolean();
		private volatile MicrosoftAuth.DeviceCode code;
		private volatile String status = "Получаем код…";
		private volatile String error;
		private volatile boolean done;

		public MicrosoftAuth.DeviceCode code() {
			return code;
		}

		public String status() {
			return status;
		}

		public String error() {
			return error;
		}

		public boolean done() {
			return done;
		}

		public void cancel() {
			cancelled.set(true);
		}
	}

	/** Starts a Microsoft sign-in; on success the account is added, saved and signed in. */
	public static MsLogin startMicrosoft(Consumer<AltAccount> onDone) {
		MsLogin login = new MsLogin();
		Minecraft mc = Minecraft.getInstance();
		CompletableFuture.runAsync(() -> {
			try {
				login.code = MicrosoftAuth.deviceCode();
				login.status = "Введите код на странице Microsoft";
				MicrosoftAuth.MsTokens ms = MicrosoftAuth.pollToken(login.code, login.cancelled::get);
				if (ms == null) {
					login.done = true;
					return;
				}
				login.status = "Входим в Xbox Live и Minecraft…";
				MicrosoftAuth.Session session = MicrosoftAuth.minecraft(ms.accessToken());
				MicrosoftAuth.Profile p = session.profile();
				AltAccount account = new AltAccount(p.id(), AltAccount.Kind.MICROSOFT, p.name(), p.id(), p.skinUrl());
				if (ms.refreshToken() != null) {
					SecretStore.put(account.id(), ms.refreshToken());
				}
				mc.execute(() -> {
					upsert(account);
					if (canSwitch()) {
						SessionSwitcher.apply(p.name(), AltAccount.parseUuid(p.id()), session.accessToken());
					}
					login.status = "Готово";
					login.done = true;
					onDone.accept(account);
				});
			} catch (MicrosoftAuth.AuthException e) {
				login.error = e.getMessage();
				login.done = true;
			} catch (RuntimeException e) {
				ElysiumVisuals.LOGGER.warn("Microsoft sign-in failed: {}", e.getClass().getSimpleName());
				login.error = "Не удалось войти";
				login.done = true;
			}
		});
		return login;
	}

	// ---------------------------------------------------------------------
	// Random nicknames
	// ---------------------------------------------------------------------

	private static final String[] FIRST = {"Swift", "Silent", "Lucky", "Frost", "Shadow", "Crimson", "Amber", "Nova", "Pixel",
			"Storm", "Lunar", "Echo", "Rapid", "Mystic", "Iron", "Golden", "Wild", "Cosmic", "Neon", "Velvet"};
	private static final String[] SECOND = {"Fox", "Wolf", "Raven", "Blaze", "Miner", "Knight", "Tiger", "Hawk", "Ghost", "Drake",
			"Panda", "Lynx", "Comet", "Golem", "Viper", "Otter", "Falcon", "Bear", "Spark", "Owl"};
	private static final Random RANDOM = new Random();

	public static String randomNick() {
		String base = FIRST[RANDOM.nextInt(FIRST.length)] + SECOND[RANDOM.nextInt(SECOND.length)];
		String suffix = RANDOM.nextInt(3) == 0 ? "_" + RANDOM.nextInt(100) : String.valueOf(RANDOM.nextInt(1000));
		String nick = base + suffix;
		return nick.length() > 16 ? nick.substring(0, 16) : nick;
	}
}
