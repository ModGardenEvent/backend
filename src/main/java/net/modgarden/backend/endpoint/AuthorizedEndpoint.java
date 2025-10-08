package net.modgarden.backend.endpoint;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import de.mkammerer.argon2.Argon2Advanced;
import de.mkammerer.argon2.Argon2Factory;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.NaturalId;
import net.modgarden.backend.data.Permission;
import net.modgarden.backend.data.PermissionScope;
import net.modgarden.backend.data.Permissions;
import net.modgarden.backend.endpoint.v2.auth.GenerateKeyEndpoint;
import org.jetbrains.annotations.NotNull;

import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
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

	protected abstract void handle(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception;

	@Override
	public final void handle(@NotNull Context ctx) throws Exception {
		ValidationResult validationResult = validateAuth(ctx);
		if (!validationResult.authorized()) {
			return;
		}

		super.handle(ctx);
		this.handle(ctx, validationResult.userId(), validationResult.scopePermissions());
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
	/// If an incident is not documented, create a sub-heading with the date
	/// and an ominous title.
	///
	/// ### `2025-10-06` No Incidents!
	/// Yay.
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

		JsonObject body;
		String projectId = null;
		if (this.hasBody) {
			try {
				body = JsonParser.parseString(ctx.body()).getAsJsonObject();
				JsonElement projectIdElement = body.get("project_id");
				if (projectIdElement != null) {
					projectId = projectIdElement.getAsString();
				}
			} catch (JsonSyntaxException | IllegalStateException e) {
				this.invalidBody(ctx, e.getMessage());
			}
		}

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
			try (
					var connection = this.getDatabaseConnection();
					var apiKeyStatement =
							connection.prepareStatement("SELECT hash, uuid, expires FROM api_keys WHERE user_id = ?");
					var apiKeyScopeStatement =
							connection.prepareStatement("SELECT scope, project_id, permissions FROM api_key_scopes WHERE uuid = ?")
			) {
				apiKeyStatement.setString(1, userId);
				ResultSet apiKeyResult = apiKeyStatement.executeQuery();
				if (!apiKeyResult.isBeforeFirst()) {
					this.setStatusUnauthorized(ctx);
					return ValidationResult.no();
				}

				while (!authorized && apiKeyResult.next()) {
					byte[] uuid = apiKeyResult.getBytes("uuid");
					apiKeyScopeStatement.setBytes(1, uuid);
					ResultSet apiKeyScopeResult = apiKeyScopeStatement.executeQuery();
					if (!apiKeyScopeResult.isBeforeFirst()) {
						this.setStatusUnauthorized(ctx);
						return ValidationResult.no();
					}

					// forbid expired keys
					if (Instant.now().isAfter(Instant.ofEpochMilli(apiKeyResult.getLong("expires")))) {
						this.setStatusUnauthorized(ctx);

						// remove expired key
						try (
								var apiKeyExpiredStatement =
										connection.prepareStatement("DELETE FROM api_keys WHERE uuid = ?")
						) {
							apiKeyExpiredStatement.setBytes(1, uuid);
							apiKeyExpiredStatement.execute();
						}

						return ValidationResult.no();
					}

					// validate permission scope matches
					PermissionScope scope = PermissionScope.fromString(apiKeyScopeResult.getString("scope"));
					if (scope != this.permissionScope && this.permissionScope != PermissionScope.ALL) {
						ctx.result("Permission scope " + scope + " does not match the scope " + this.permissionScope + " for this endpoint .");
						ctx.status(403);
						return ValidationResult.no();
					}

					// validate project ID matches
					if (!(this instanceof GenerateKeyEndpoint) && projectId != null && !projectId.equals(apiKeyScopeResult.getString("project_id"))) {
						ctx.result("Project ID " +  projectId + " does not match the project ID for this scope.");
						ctx.status(403);
						return ValidationResult.no();
					}

					String hash = apiKeyResult.getString("hash");
					authorized = verifySecret(hash, secret);

					// give this endpoint the permissions as specified by the API key
					if (authorized) {
						scopePermissions.grantPermissions(new Permissions(apiKeyScopeResult.getLong("permissions")));
						// Disallow permissions the user doesn't already have
						switch (scope) {
							case USER -> {
								Permissions userPermissions = this.getDatabaseAccess()
										.getUserPermissions(userId)
										.unwrap(ctx);
								if (userPermissions == null) {
									this.setStatusUnauthorized(ctx);
									return ValidationResult.no();
								}
								scopePermissions = userPermissions;
								scopePermissions = scopePermissions.restrict(userPermissions.bits());
							}
							case PROJECT -> {
								Permissions projectPermissions = this.getDatabaseAccess()
										.getProjectPermissions(userId, projectId)
										.unwrap(ctx);
								if (projectPermissions == null) {
									this.setStatusUnauthorized(ctx);
									return ValidationResult.no();
								}
								scopePermissions = projectPermissions;
								scopePermissions = scopePermissions.restrict(projectPermissions.bits());
							}
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

	protected boolean requirePermissions(Context ctx, Permissions scopePermissions, Permissions permissions) {
		if (!scopePermissions.hasPermissions(permissions)) {
			ctx.status(403);
			ctx.result("User lacks permission; required " + permissions);
			return false;
		}

		return true;
	}

	protected boolean requirePermissions(Context ctx, Permissions scopePermissions, Permission... permissions) {
		return requirePermissions(ctx, scopePermissions, new Permissions(permissions));
	}

	private record ValidationResult(boolean authorized, String userId, Permissions scopePermissions) {
		public static ValidationResult no() {
			return new ValidationResult(false, NaturalId.getMissingno(), new Permissions());
		}
	}
}
