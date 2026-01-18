package net.modgarden.backend.endpoint;

import de.mkammerer.argon2.Argon2Advanced;
import de.mkammerer.argon2.Argon2Factory;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.NaturalId;
import net.modgarden.backend.data.Permission;
import net.modgarden.backend.data.PermissionScope;
import net.modgarden.backend.data.Permissions;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.v2.auth.GenerateKeyEndpoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Collectors;

public abstract class AuthorizedEndpoint extends Endpoint {
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();
	/// OWASP [recommends](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html) Argon2id.
	private static final Argon2Advanced ARGON =
			Argon2Factory.createAdvanced(Argon2Factory.Argon2Types.ARGON2id);
	private final PermissionScope permissionScope;
	private final boolean hasBody;
	private final static String characters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()-_+=[]{}|/?;:,.<>~";

	public AuthorizedEndpoint(int version, String path, PermissionScope permissionScope, boolean hasBody) {
		super(version, path);
		this.permissionScope = permissionScope;
		this.hasBody = hasBody;
	}

	AuthorizedEndpoint(String path, PermissionScope permissionScope, boolean hasBody) {
		super(path);
		this.permissionScope = permissionScope;
		this.hasBody = hasBody;
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
		StringBuilder stringBuilder = new StringBuilder();
		for (int i = 0; i < length; i++) {
			int randomInt = SECURE_RANDOM.nextInt(0, characters.length() - 1);
			stringBuilder.append(characters.charAt(randomInt));
		}
		return stringBuilder.toString();
	}

	/// Generate a salted hash for a secret (e.g. password).
	protected static String hashSecret(String secret) {
		// https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html
		return ARGON.hash(
				2,
				19 * 1024,
				1,
				secret.toCharArray()
		);
	}

	/// Verify that a secret (e.g. password) matches the given salt and hash.
	protected static boolean verifySecret(String hash, String secret) {
		return ARGON.verify(hash, secret.toCharArray());
	}

	protected abstract void onRequest(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception;

	@Override
	public final void onRequest(@NotNull Context ctx) throws Exception {
		ValidationResult validationResult = validateAuth(ctx);
		if (!validationResult.authorized()) {
			return;
		}

		this.onRequest(ctx, validationResult.userId(), validationResult.scopePermissions());
	}

	protected @Nullable String getProjectId(Context ctx) throws SQLException {
		return null;
	}

	/// # Caution
	/// Modifying this method is a dangerous game.
	///
	/// If you choose to continue, know that a single logical error
	/// can and likely will cause serious security vulnerabilities.
	///
	/// Do not fuck with auth roulette.
	/// Test your code before pushing to prod, please.
	///
	/// **You have been warned.**
	///
	/// ## Past Incidents
	/// Security incidents related to this method are detailed below.
	/// If an incident is not documented, create a sub-heading with
	/// the date, the severity (Minimal, Moderate, Severe), known usage
	/// (None, Rare, Common, Unknown), and an ominous title.
	///
	/// ### `2025-10-06` (Minimal/None) No Incidents!
	/// Yay.
	/// ### `2025-10-08` (Moderate/None) Scope Leak
	/// Prior to commit `bb823cd`, API keys were not correctly scoped.
	/// Instead, API keys' permissions were restricted only to a
	/// users' permissions. This is dangerous because administrators'
	/// and project owners' API keys could access anything they could
	/// access which violates the [Principle of Least Privilege](https://en.wikipedia.org/wiki/Principle_of_least_privilege).
	private ValidationResult validateAuth(Context ctx) throws SQLException {
		String authorization = ctx.header("Authorization");
		if (authorization == null) {
			ctx.result("Unauthorized.");
			ctx.status(401);
			return ValidationResult.no();
		}

		boolean authorized = ("Basic " + ModGardenBackend.DOTENV.get("DISCORD_OAUTH_SECRET")).equals(
				authorization);
		Permissions scopePermissions = new Permissions();
		// we know this is GardenBot. let it bypass everything
		if (authorized) {
			scopePermissions = new Permissions(Permission.ADMINISTRATOR);
			return new ValidationResult(true, "grbot", scopePermissions);
		}

		String projectId = this.getProjectId(ctx);

		String idSecretPair = authorization.split(" ")[1];
		String[] idSecretPairSplit = idSecretPair.split(":");
		String userId = idSecretPairSplit[0];
		String secret = null;
		if (idSecretPairSplit.length > 1) {
			secret = Arrays.stream(idSecretPairSplit)
					.skip(1)
					.collect(Collectors.joining(":"));
		}

		if (secret != null) {
			DatabaseAccess db = DatabaseAccess.get();

			Collection<DatabaseAccess.ApiKey> apiKeyResult = db.getApiKeys(userId);
			if (apiKeyResult.isEmpty()) {
				this.setStatusUnauthorized(ctx);
				return ValidationResult.no();
			}

			Iterator<DatabaseAccess.ApiKey> it = apiKeyResult.iterator();
			while (!authorized && it.hasNext()) {
				DatabaseAccess.ApiKey apiKey = it.next();
				Optional<DatabaseAccess.ApiKeyScope> optionalApiKeyScope = db.getApiKeyScope(apiKey.uuid());
				if (optionalApiKeyScope.isEmpty()) {
					this.setStatusUnauthorized(ctx);
					return ValidationResult.no();
				}

				DatabaseAccess.ApiKeyScope apiKeyScope = optionalApiKeyScope.get();

				// forbid expired keys
				if (Instant.now().isAfter(apiKey.expires())) {
					this.setStatusUnauthorized(ctx);
					db.deleteApiKey(apiKey.uuid());
					return ValidationResult.no();
				}

				// validate permission scope matches
				PermissionScope scope = apiKeyScope.scope();
				if (scope != this.permissionScope && this.permissionScope != PermissionScope.ALL) {
					ctx.result("Permission scope " + scope + " does not match the scope " + this.permissionScope + " for this endpoint .");
					ctx.status(403);
					return ValidationResult.no();
				}

				// validate project ID matches
				if (!(this instanceof GenerateKeyEndpoint) && projectId != null && !projectId.equals(apiKeyScope.projectId())) {
					ctx.result("Project ID " + projectId + " does not match the project ID for this scope.");
					ctx.status(403);
					return ValidationResult.no();
				}

				String hash = apiKey.hash();
				authorized = verifySecret(hash, secret);

				// give this endpoint the permissions as specified by the API key
				if (authorized) {
					Permissions apiKeyPermissions = apiKeyScope.permissions();
					// Disallow permissions the user doesn't already have
					switch (scope) {
						case USER -> {
							Permissions userPermissions = db.getUserPermissions(userId)
									.unwrap(ctx);
							if (userPermissions == null) {
								this.setStatusUnauthorized(ctx);
								return ValidationResult.no();
							}
							scopePermissions = userPermissions;
							scopePermissions = scopePermissions.restrict(apiKeyPermissions.bits());
						}
						case PROJECT -> {
							Permissions projectPermissions = db.getProjectMemberPermissions(userId, projectId)
									.unwrap(ctx);
							if (projectPermissions == null) {
								this.setStatusUnauthorized(ctx);
								return ValidationResult.no();
							}
							scopePermissions = projectPermissions;
							scopePermissions = scopePermissions.restrict(apiKeyPermissions.bits());
						}
					}
				}
			}
		}
		if (!authorized && !ctx.status().isError()) {
			this.setStatusUnauthorized(ctx);
			return ValidationResult.no();
		}

		return new ValidationResult(authorized, userId, scopePermissions);
	}

	protected void setStatusUnauthorized(Context ctx) {
		ctx.result("Unauthorized.");
		ctx.status(401);
	}

	protected void setStatusForbidden(Context ctx) {
		ctx.result("Forbidden.");
		ctx.status(403);
	}

	private boolean requireAllPermissions(Context ctx, Permissions scopePermissions, Permissions permissions) {
		if (!scopePermissions.hasPermissions(permissions)) {
			ctx.status(403);
			ctx.result("User lacks permission; required " + permissions);
			return true;
		}

		return false;
	}

	private boolean requireAnyPermissions(Context ctx, Permissions scopePermissions, Permissions permissions) {
		if (!scopePermissions.hasAnyPermissions(permissions)) {
			ctx.status(403);
			ctx.result("User lacks permission; required any of " + permissions);
			return false;
		}

		return true;
	}

	protected boolean requireAllPermissions(Context ctx, Permissions scopePermissions, Permission... permissions) {
		return requireAllPermissions(ctx, scopePermissions, new Permissions(permissions));
	}

	protected boolean requireAnyPermissions(Context ctx, Permissions scopePermissions, Permission... permissions) {
		return requireAnyPermissions(ctx, scopePermissions, new Permissions(permissions));
	}

	private record ValidationResult(boolean authorized, String userId, Permissions scopePermissions) {
		public static ValidationResult no() {
			return new ValidationResult(false, NaturalId.getMissingno(), new Permissions());
		}
	}
}
