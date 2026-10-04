package dev.elysium.visuals.client.alt;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.BooleanSupplier;

/**
 * Microsoft sign-in, the same flow as Elysium Launcher: OAuth device code →
 * Xbox Live → XSTS → Minecraft services. Requests only go to Microsoft,
 * Xbox Live and Mojang hosts. Tokens are never logged and never put into
 * exception messages.
 */
public final class MicrosoftAuth {
	/** Azure application (client) ID: the launcher's one. Set it here, or with -Delysium.msClientId / ELYSIUM_MS_CLIENT_ID. */
	private static final String CLIENT_ID = "00000000-0000-0000-0000-000000000000";
	private static final String SCOPE = "XboxLive.signin offline_access";
	private static final String DEVICE_CODE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
	private static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

	private MicrosoftAuth() {
	}

	public static String clientId() {
		String id = System.getProperty("elysium.msClientId");
		if (id == null || id.isBlank()) {
			id = System.getenv("ELYSIUM_MS_CLIENT_ID");
		}
		return id == null || id.isBlank() ? CLIENT_ID : id.trim();
	}

	/** Whether a real Client ID is set (the placeholder can't sign in). */
	public static boolean configured() {
		return !clientId().equals("00000000-0000-0000-0000-000000000000");
	}

	/** A sign-in failure with a message for the user; never contains tokens. */
	public static final class AuthException extends Exception {
		public AuthException(String message) {
			super(message, null, false, false);
		}
	}

	public record DeviceCode(String deviceCode, String userCode, String verificationUri, long expiresIn, long interval) {
	}

	public record MsTokens(String accessToken, String refreshToken) {
	}

	public record Profile(String id, String name, String skinUrl) {
	}

	public record Session(String accessToken, Profile profile) {
	}

	// ---------------------------------------------------------------------
	// OAuth
	// ---------------------------------------------------------------------

	public static DeviceCode deviceCode() throws AuthException {
		JsonObject r = postForm(DEVICE_CODE_URL, Map.of("client_id", clientId(), "scope", SCOPE), "Microsoft");
		return new DeviceCode(str(r, "device_code"), str(r, "user_code"), str(r, "verification_uri"),
				r.has("expires_in") ? r.get("expires_in").getAsLong() : 900, r.has("interval") ? r.get("interval").getAsLong() : 5);
	}

	/** Waits until the user enters the code. Returns null if cancelled. */
	public static MsTokens pollToken(DeviceCode code, BooleanSupplier cancelled) throws AuthException {
		long deadline = System.currentTimeMillis() + code.expiresIn() * 1000;
		long interval = Math.max(1, code.interval()) * 1000;
		while (System.currentTimeMillis() < deadline) {
			for (long slept = 0; slept < interval; slept += 100) {
				if (cancelled.getAsBoolean()) {
					return null;
				}
				sleep(100);
			}
			HttpResponse<String> res = send(form(TOKEN_URL, Map.of(
					"grant_type", "urn:ietf:params:oauth:grant-type:device_code",
					"client_id", clientId(),
					"device_code", code.deviceCode())), "Microsoft");
			JsonObject r = json(res.body());
			if (res.statusCode() == 200 && r != null && r.has("access_token")) {
				return new MsTokens(str(r, "access_token"), str(r, "refresh_token"));
			}
			String error = r != null ? str(r, "error") : null;
			if ("authorization_pending".equals(error)) {
				continue;
			}
			if ("slow_down".equals(error)) {
				interval += 5000;
				continue;
			}
			if ("authorization_declined".equals(error)) {
				throw new AuthException("Вход отменён в браузере");
			}
			if ("expired_token".equals(error)) {
				throw new AuthException("Код устарел, попробуйте ещё раз");
			}
			throw new AuthException("Microsoft отклонил вход" + (error != null ? " (" + error + ")" : ""));
		}
		throw new AuthException("Код устарел, попробуйте ещё раз");
	}

	public static MsTokens refresh(String refreshToken) throws AuthException {
		JsonObject r = postForm(TOKEN_URL, Map.of(
				"grant_type", "refresh_token",
				"client_id", clientId(),
				"refresh_token", refreshToken,
				"scope", SCOPE), "Microsoft");
		String access = str(r, "access_token");
		if (access == null) {
			throw new AuthException("Сессия Microsoft истекла, войдите заново");
		}
		String rotated = str(r, "refresh_token");
		return new MsTokens(access, rotated != null ? rotated : refreshToken);
	}

	// ---------------------------------------------------------------------
	// Xbox Live → Minecraft
	// ---------------------------------------------------------------------

	public static Session minecraft(String msAccessToken) throws AuthException {
		JsonObject xblBody = new JsonObject();
		JsonObject props = new JsonObject();
		props.addProperty("AuthMethod", "RPS");
		props.addProperty("SiteName", "user.auth.xboxlive.com");
		props.addProperty("RpsTicket", "d=" + msAccessToken);
		xblBody.add("Properties", props);
		xblBody.addProperty("RelyingParty", "http://auth.xboxlive.com");
		xblBody.addProperty("TokenType", "JWT");
		JsonObject xbl = postJson("https://user.auth.xboxlive.com/user/authenticate", xblBody, null, "Xbox Live");
		String xblToken = str(xbl, "Token");

		JsonObject xstsBody = new JsonObject();
		JsonObject xp = new JsonObject();
		xp.addProperty("SandboxId", "RETAIL");
		JsonArray tokens = new JsonArray();
		tokens.add(xblToken);
		xp.add("UserTokens", tokens);
		xstsBody.add("Properties", xp);
		xstsBody.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
		xstsBody.addProperty("TokenType", "JWT");
		HttpResponse<String> xstsRes = send(jsonRequest("https://xsts.auth.xboxlive.com/xsts/authorize", xstsBody, null), "Xbox Live");
		JsonObject xsts = json(xstsRes.body());
		if (xstsRes.statusCode() == 401 && xsts != null && xsts.has("XErr")) {
			long err = xsts.get("XErr").getAsLong();
			if (err == 2148916233L) {
				throw new AuthException("У аккаунта нет профиля Xbox, создайте его на xbox.com");
			}
			if (err == 2148916238L) {
				throw new AuthException("Детский аккаунт: его нужно добавить в семью Microsoft");
			}
			throw new AuthException("Xbox Live отклонил вход (" + err + ")");
		}
		if (xstsRes.statusCode() / 100 != 2 || xsts == null) {
			throw new AuthException("Xbox Live недоступен (HTTP " + xstsRes.statusCode() + ")");
		}
		String uhs = xsts.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();

		JsonObject login = new JsonObject();
		login.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + str(xsts, "Token"));
		JsonObject mc = postJson("https://api.minecraftservices.com/authentication/login_with_xbox", login, null, "Minecraft");
		String mcToken = str(mc, "access_token");

		HttpResponse<String> profileRes = send(HttpRequest.newBuilder(URI.create("https://api.minecraftservices.com/minecraft/profile"))
				.timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + mcToken).GET().build(), "Minecraft");
		if (profileRes.statusCode() == 404) {
			throw new AuthException("На этом аккаунте нет Minecraft Java Edition");
		}
		JsonObject p = json(profileRes.body());
		if (profileRes.statusCode() / 100 != 2 || p == null) {
			throw new AuthException("Профиль Minecraft недоступен (HTTP " + profileRes.statusCode() + ")");
		}
		String skin = null;
		if (p.has("skins")) {
			for (JsonElement e : p.getAsJsonArray("skins")) {
				JsonObject s = e.getAsJsonObject();
				if (skin == null || "ACTIVE".equals(str(s, "state"))) {
					skin = str(s, "url");
				}
			}
		}
		if (skin != null) {
			skin = skin.replaceFirst("^http://", "https://");
		}
		return new Session(mcToken, new Profile(str(p, "id"), str(p, "name"), skin));
	}

	// ---------------------------------------------------------------------
	// HTTP
	// ---------------------------------------------------------------------

	private static HttpRequest form(String url, Map<String, String> fields) {
		StringJoiner body = new StringJoiner("&");
		fields.forEach((k, v) -> body.add(URLEncoder.encode(k, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(v, StandardCharsets.UTF_8)));
		return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
	}

	private static HttpRequest jsonRequest(String url, JsonObject body, String bearer) {
		HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
				.header("Content-Type", "application/json").header("Accept", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()));
		if (bearer != null) {
			b.header("Authorization", "Bearer " + bearer);
		}
		return b.build();
	}

	private static JsonObject postForm(String url, Map<String, String> fields, String service) throws AuthException {
		return ok(send(form(url, fields), service), service);
	}

	private static JsonObject postJson(String url, JsonObject body, String bearer, String service) throws AuthException {
		return ok(send(jsonRequest(url, body, bearer), service), service);
	}

	private static HttpResponse<String> send(HttpRequest request, String service) throws AuthException {
		try {
			return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (IOException e) {
			throw new AuthException("Нет связи с " + service);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AuthException("Вход прерван");
		}
	}

	private static JsonObject ok(HttpResponse<String> res, String service) throws AuthException {
		JsonObject o = json(res.body());
		if (res.statusCode() / 100 != 2 || o == null) {
			String error = o != null ? str(o, "error") : null;
			// Only the status and the OAuth error code: the body may echo request data.
			throw new AuthException(service + " отклонил запрос (HTTP " + res.statusCode() + (error != null ? ", " + error : "") + ")");
		}
		return o;
	}

	private static JsonObject json(String body) {
		try {
			JsonElement e = JsonParser.parseString(body);
			return e.isJsonObject() ? e.getAsJsonObject() : null;
		} catch (Exception e) {
			return null;
		}
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
	}

	private static void sleep(long ms) throws AuthException {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AuthException("Вход прерван");
		}
	}
}
