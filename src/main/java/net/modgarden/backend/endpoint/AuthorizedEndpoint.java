package net.modgarden.backend.endpoint;

import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public abstract class AuthorizedEndpoint extends Endpoint {
	private static final String RANDOM_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-_/+=;!@#$%^&*()";
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	public AuthorizedEndpoint(String path) {
		super(path);
	}

	public static String generateRandomToken() {
			return generateSecureRandomString(10);
	}

	protected static String generateAPIKey() {
		// we use 72 because it divides neatly with 3 (72/3 = 24)
		return generateSecureRandomString(72);
	}

	protected static String generateSecureRandomString(int length) {
		byte[] bytes = new byte[length];
		SECURE_RANDOM.nextBytes(bytes);
		return new String(Base64.getEncoder().encode(bytes), StandardCharsets.UTF_8);
	}

	protected static String generateSalt(int length) {
		if (length < 16) throw new IllegalArgumentException("A salt length < 16 is not strong enough!");
		return SECURE_RANDOM
				.ints(length, 0, RANDOM_CHARS.length())
				.collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
				.toString();
	}

	@Override
	public void handle(@NotNull Context ctx) throws Exception {
		if (!validateAuth(ctx)) {
			return;
		}

		super.handle(ctx);
	}

	private boolean validateAuth(Context ctx) {
		boolean authorized = ("Basic " + ModGardenBackend.DOTENV.get("DISCORD_OAUTH_SECRET")).equals(
				ctx.header("Authorization"));
		if (!authorized) {
			ctx.result("Unauthorized.");
			ctx.status(401);
		}

		return authorized;
	}
}
