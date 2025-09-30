package net.modgarden.backend.endpoint;

import de.mkammerer.argon2.Argon2Advanced;
import de.mkammerer.argon2.Argon2Factory;
import de.mkammerer.argon2.Argon2Version;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public abstract class AuthorizedEndpoint extends Endpoint {
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();
	/// OWASP [recommends](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html) Argon2id.
	private static final Argon2Advanced ARGON =
			Argon2Factory.createAdvanced(Argon2Factory.Argon2Types.ARGON2id);
	private static final Argon2Version ARGON_2_VERSION = Argon2Version.V13;

	public AuthorizedEndpoint(String path) {
		super(path);
	}

	public static String generateRandomToken() {
			return generateSecretString(10);
	}

	protected static String generateAPIKey() {
		// we use 72 because it divides neatly with 3 (72/3 = 24)
		return generateSecretString(72);
	}

	/// Generate a secret (e.g. password) in [String] form.
	protected static String generateSecretString(int length) {
		byte[] bytes = new byte[length];
		SECURE_RANDOM.nextBytes(bytes);
		return new String(Base64.getEncoder().encode(bytes), StandardCharsets.UTF_8);
	}

	/// Generate a salted hash for a secret (e.g. password).
	protected static HashedSecret hashSecret(byte[] bytes) {
		byte[] salt = generateSalt(16);
		// https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html#argon2id
		byte[] hash = ARGON.rawHash(
				3,
				12288,
				1,
				bytes,
				salt
		);
		return new HashedSecret(salt, hash);
	}

	/// Verify that a secret (e.g. password) matches the given salt and hash.
	protected static boolean verifySecret(HashedSecret hashedSecret, byte[] secret) {
		return ARGON.verifyAdvanced(
				3,
				12228,
				1,
				secret,
				hashedSecret.salt(),
				null,
				null,
				hashedSecret.hash().length,
				ARGON_2_VERSION,
				hashedSecret.hash()
		);
	}

	protected static byte[] generateSalt(int length) {
		if (length < 16) throw new IllegalArgumentException("A salt length < 16 is not strong enough!");
		byte[] bytes = new byte[length];
		SECURE_RANDOM.nextBytes(bytes);
		return bytes;
	}

	protected abstract void handle(@NotNull Context ctx, String userId) throws Exception;

	@Override
	public final void handle(@NotNull Context ctx) throws Exception {
		if (!validateAuth(ctx)) {
			return;
		}

		super.handle(ctx);
		this.handle(ctx, "grbot"); // todo un-hardcode when we make proper user_id:password auth
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

	public record HashedSecret(byte[] salt, byte[] hash) {}
}
