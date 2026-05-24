package net.modgarden.backend.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.*;

import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.*;
import net.modgarden.backend.data.event.game.MinecraftEventPlatform;
import net.modgarden.backend.data.project.ProjectMetadata;
import net.modgarden.backend.data.event.*;
import net.modgarden.backend.data.project.metadata.NoneProjectMetadata;
import net.modgarden.backend.data.project.metadata.ModProjectMetadata;
import net.modgarden.backend.data.project.platform.ModrinthSubmissionPlatform;
import net.modgarden.backend.data.project.SubmissionPlatform;
import net.modgarden.backend.data.project.Project;
import net.modgarden.backend.data.project.Submission;
import net.modgarden.backend.data.user.User;
import net.modgarden.backend.data.user.Bio;
import net.modgarden.backend.data.user.integration.DiscordUserIntegration;
import net.modgarden.backend.data.user.integration.MinecraftUserIntegration;
import net.modgarden.backend.data.user.integration.ModrinthUserIntegration;
import net.modgarden.backend.data.user.role.DiscordUserRoleIntegration;
import net.modgarden.backend.data.user.role.UserRole;
import net.modgarden.backend.endpoint.exception.HypertextException;
import net.modgarden.backend.endpoint.exception.NotFoundException;
import net.modgarden.backend.util.FallibleSupplier;
import net.modgarden.backend.util.LazyValue;
import net.modgarden.backend.util.UuidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// Centralized access to database operations.
public final class DatabaseAccess implements AutoCloseable {
	private static final ScopedValue<DatabaseAccess> SCOPED_VALUE = ScopedValue.newInstance();

	private final LazyValue<Connection> connection = LazyValue.of();

	private DatabaseAccess() {
	}

	/// Binds the [ScopedValue] of this [DatabaseAccess] to the current thread.
	///
	/// @return a [ScopedValue.Carrier] which should be used to wrap subsequent calls that need [DatabaseAccess].
	public static ScopedValue.Carrier bind() {
		return ScopedValue.where(SCOPED_VALUE, new DatabaseAccess());
	}

	/// @return the current thread's access to the database. This may differ from other threads.
	public static DatabaseAccess get() {
		return SCOPED_VALUE.orElseThrow(() -> new IllegalStateException("DatabaseAccess is not available in this " +
				"context"));
	}

	/// **Warning:** do not call [Connection#close()] or use it in a try-with-resources as this will prematurely close
	/// the connection.
	private Connection getConnection() throws SQLException {
		return this.connection.getOrCreate(ModGardenBackend::createDatabaseConnection);
	}

	/// @return the value returned by the supplier or the default value if an exception is thrown.
	public <T, X extends Throwable> T logIfThrown(FallibleSupplier<T, X> operation, T defaultValue) {
		try {
			return operation.get();
		} catch (Throwable t) {
			ModGardenBackend.LOG.error("Exception during DatabaseAccess operation", t);
			return defaultValue;
		}
	}

	/// @return the value returned by the supplier or the default value if an exception is thrown.
	public <X extends Throwable> boolean logIfThrown(FallibleSupplier<Boolean, X> operation) {
		return this.logIfThrown(operation, false);
	}

	// Auth

	public record ApiKey(String hash, UUID uuid, Instant expires, String name) {
	}

	public record ApiKeyScope(PermissionScope scope, String projectId, Permissions permissions) {
	}

	public Collection<ApiKey> getApiKeys(String userId) throws SQLException {
		try (var apiKeyStatement =
				     this.getConnection()
						     .prepareStatement("SELECT hash, uuid, expires, name FROM api_keys WHERE user_id = ?")) {
			apiKeyStatement.setString(1, userId);
			Collection<ApiKey> apiKeys = new ArrayList<>();
			ResultSet resultSet = apiKeyStatement.executeQuery();

			if (!resultSet.isBeforeFirst()) {
				return Collections.emptyList();
			}

			while (resultSet.next()) {
				apiKeys.add(new ApiKey(
						resultSet.getString("hash"),
						UuidUtils.fromBytes(resultSet.getBytes("uuid")),
						Instant.ofEpochMilli(resultSet.getLong("expires")),
						resultSet.getString("name")
				));
			}

			return apiKeys;
		}
	}

	public Optional<ApiKeyScope> getApiKeyScope(UUID uuid) throws SQLException {
		try (var apiKeyScopeStatement =
				     this.getConnection()
						     .prepareStatement("SELECT scope, project_id, permissions FROM api_key_scopes WHERE uuid = ?")) {
			apiKeyScopeStatement.setBytes(1, UuidUtils.toBytes(uuid));
			ResultSet resultSet = apiKeyScopeStatement.executeQuery();

			if (!resultSet.isBeforeFirst()) {
				return Optional.empty();
			}

			return Optional.of(new ApiKeyScope(
					PermissionScope.fromString(resultSet.getString("scope")),
					resultSet.getString("project_id"),
					new Permissions(resultSet.getLong("permissions"))
			));
		}
	}

	public Optional<ApiKeyScope> getApiKeyScope(UUID uuid, String projectId) throws SQLException {
		try (var apiKeyScopeStatement =
				     this.getConnection()
						     .prepareStatement("SELECT scope, project_id, permissions FROM api_key_scopes WHERE uuid = ? AND project_id = ?")) {
			apiKeyScopeStatement.setBytes(1, UuidUtils.toBytes(uuid));
			apiKeyScopeStatement.setString(2, projectId);
			ResultSet resultSet = apiKeyScopeStatement.executeQuery();

			if (!resultSet.isBeforeFirst()) {
				return Optional.empty();
			}

			return Optional.of(new ApiKeyScope(
					PermissionScope.fromString(resultSet.getString("scope")),
					resultSet.getString("project_id"),
					new Permissions(resultSet.getLong("permissions"))
			));
		}
	}

	public void deleteApiKey(UUID uuid) throws SQLException {
		try (var apiKeyExpiredStatement =
				     this.getConnection().prepareStatement("DELETE FROM api_keys WHERE uuid = ?")) {
			apiKeyExpiredStatement.setBytes(1, UuidUtils.toBytes(uuid));
			apiKeyExpiredStatement.execute();
		}
	}

	public void createApiKey(
			byte[] uuid,
			String userId,
			String hash,
			Instant expires,
			String name
	) throws SQLException {
		try (var apiKeyStatement =
				     this.getConnection().prepareStatement("INSERT INTO api_keys(uuid, user_id, hash, expires, name) VALUES (?, ?, ?, ?, ?)")) {
			apiKeyStatement.setBytes(1, uuid);
			apiKeyStatement.setString(2, userId);
			apiKeyStatement.setString(3, hash);
			apiKeyStatement.setLong(4, expires.toEpochMilli());
			apiKeyStatement.setString(5, name);
			apiKeyStatement.execute();
		}
	}

	public void createApiKeyScope(
			byte[] uuid,
			String scope,
			@Nullable String projectId,
			Permissions requestedPermissions
	) throws SQLException {
		try (var apiKeyScopeStatement =
				     this.getConnection().prepareStatement("INSERT INTO api_key_scopes(uuid, scope, project_id, permissions) VALUES (?, ?, ?, ?)")) {
			apiKeyScopeStatement.setBytes(1, uuid);
			apiKeyScopeStatement.setString(2, scope);
			if (projectId != null) {
				apiKeyScopeStatement.setString(3, projectId);
			} else {
				apiKeyScopeStatement.setNull(3, Types.NULL);
			}
			apiKeyScopeStatement.setLong(4, requestedPermissions.bits());
			apiKeyScopeStatement.execute();
		}
	}

	// Users

	/**
	 * @return The created user's ID.
	 */
	public String createUser(String username) throws SQLException {
		try (
				PreparedStatement insertUserStatement = this.getConnection()
						.prepareStatement("""
							INSERT INTO users (id, username, permissions, created)
							VALUES (?, ?, ?, unix_millis())
						""");
				PreparedStatement userBiosStatement = this.getConnection()
						.prepareStatement("""
							INSERT INTO user_bios (user_id, display_name, pronouns, description, avatar_url)
							VALUES (?, NULL, NULL, NULL, NULL)
						""")
		) {
			String id = NaturalId.generate("users", "id", null, 5);
			insertUserStatement.setString(1, id);
			insertUserStatement.setString(2, username);
			insertUserStatement.setLong(3, 0);
			insertUserStatement.execute();

			userBiosStatement.setString(1, id);
			userBiosStatement.execute();

			return id;
		}
	}

	public User getUserFromId(
			@NotNull String userId
	) throws SQLException, HypertextException {
		try (PreparedStatement usersStatement = this.getConnection()
				.prepareStatement("""
						SELECT username, created, permissions
						FROM users
						WHERE id = ?
				""");
			 PreparedStatement userBiosStatement = this.getConnection()
			 	.prepareStatement("""
						SELECT display_name, pronouns, description, avatar_url
						FROM user_bios
						WHERE user_id = ?
				""");
			 PreparedStatement userBioFieldsStatement = this.getConnection()
			 	.prepareStatement("""
						SELECT field_name, field_value
						FROM user_bio_fields
						WHERE user_id = ?
						ORDER BY ROWID
				""");
			 PreparedStatement userIntegrationDiscordStatement = this.getConnection()
				.prepareStatement("""
						SELECT discord_id
						FROM user_integration_discord
						WHERE user_id = ?
				""");
			 PreparedStatement userIntegrationMinecraftStatement = this.getConnection()
				.prepareStatement("""
						SELECT uuid
						FROM user_integration_minecraft
						WHERE user_id = ?
				""");
			 PreparedStatement userIntegrationModrinthStatement = this.getConnection()
				.prepareStatement("""
						SELECT modrinth_id
						FROM user_integration_modrinth
						WHERE user_id = ?
				""");
			 PreparedStatement projectRolesStatement = this.getConnection()
				.prepareStatement("""
						SELECT project_id
						FROM project_roles
						WHERE user_id = ?
				""");
			 PreparedStatement eventsStatement = this.getConnection()
					 .prepareStatement("""
						SELECT event_id
						FROM submissions
						WHERE project_id = ?
				""");
			 PreparedStatement userRolesStatement = this.getConnection()
				.prepareStatement("""
						SELECT role_id
						FROM user_roles
						WHERE user_id = ?
				""")
		) {
			usersStatement.setString(1, userId);
			ResultSet usersResult = usersStatement.executeQuery();

			if (!usersResult.isBeforeFirst()) {
				throw new NotFoundException("Could not find user '" + userId + "'");
			}

			String username = usersResult.getString("username");
			Permissions permissions = new Permissions(usersResult.getLong("permissions"));
			Instant created = Instant.ofEpochMilli(usersResult.getLong("created"));

			userBiosStatement.setString(1, userId);
			ResultSet userBiosResult = userBiosStatement.executeQuery();

			if (!userBiosResult.isBeforeFirst()) {
				throw new NotFoundException("Could not find bio for user '" + userId + "'");
			}

			String displayName = userBiosResult.getString("display_name");
			String pronouns = userBiosResult.getString("pronouns");
			String description = userBiosResult.getString("description");
			String avatarUrl = userBiosResult.getString("avatar_url");
			Map<String, String> fields = new LinkedHashMap<>();

			userBioFieldsStatement.setString(1, userId);
			ResultSet userBioFieldsResult = userBioFieldsStatement.executeQuery();
			while (userBioFieldsResult.next()) {
				fields.put(
						userBioFieldsResult.getString("field_name"),
						userBioFieldsResult.getString("field_value")
				);
			}

			Bio bio = new Bio(
					displayName,
					pronouns,
					description,
					avatarUrl,
					fields
			);

			Map<String, Integration> integrations = new LinkedHashMap<>();

			userIntegrationDiscordStatement.setString(1, userId);
			ResultSet usersIntegrationDiscordResult = userIntegrationDiscordStatement.executeQuery();

			if (usersIntegrationDiscordResult.isBeforeFirst()) {
				integrations.put("discord", new DiscordUserIntegration(usersIntegrationDiscordResult.getString("discord_id")));
			}

			userIntegrationMinecraftStatement.setString(1, userId);
			ResultSet userIntegrationMinecraftResult = userIntegrationMinecraftStatement.executeQuery();

			if (userIntegrationMinecraftResult.isBeforeFirst()) {
				List<String> accounts = new ArrayList<>();
				while (userIntegrationMinecraftResult.next()) {
					accounts.add(userIntegrationMinecraftResult.getString("uuid"));
				}
				integrations.put("minecraft", new MinecraftUserIntegration(accounts));
			}

			userIntegrationModrinthStatement.setString(1, userId);
			ResultSet userIntegrationModrinthResult = userIntegrationModrinthStatement.executeQuery();

			if (userIntegrationModrinthResult.isBeforeFirst()) {
				integrations.put("modrinth", new ModrinthUserIntegration(userIntegrationModrinthResult.getString("modrinth_id")));
			}

			Set<String> projects = new LinkedHashSet<>();

			projectRolesStatement.setString(1, userId);
			ResultSet projectRolesResult = projectRolesStatement.executeQuery();

			while (projectRolesResult.next()) {
				projects.add(projectRolesResult.getString("project_id"));
			}

			Set<String> events = new LinkedHashSet<>();

			for (String projectId : projects) {
				eventsStatement.setString(1, projectId);
				ResultSet eventsResult = eventsStatement.executeQuery();

				while (eventsResult.next()) {
					events.add(eventsResult.getString("event_id"));
				}
			}

			// TODO: Awards at a later date.
			Set<String> awards = new LinkedHashSet<>();

			Set<String> roles = new LinkedHashSet<>();

			userRolesStatement.setString(1, userId);
			ResultSet userRolesResult = userRolesStatement.executeQuery();

			while (userRolesResult.next()) {
				roles.add(userRolesResult.getString("role_id"));
			}

			return new User(
					userId,
					username,
					bio,
					permissions,
					created,
					integrations,
					projects,
					events,
					awards,
					roles
			);
		}
	}

	public String getUserIdFromUsername(
			@NotNull String username
	) throws SQLException, HypertextException {
		try (PreparedStatement prepared = this.getConnection()
			.prepareStatement("""
						SELECT id
						FROM users
						WHERE username = ?
				""")
		) {
			prepared.setString(1, username);
			ResultSet result = prepared.executeQuery();
			if (!result.isBeforeFirst()) {
				throw new HypertextException(404, "Could not find user '" + username + "'");
			}

			return result.getString("id");
		}
	}

	public String getUserIdFromDiscordId(String discordId) throws SQLException, HypertextException {
		try (PreparedStatement prepared = this.getConnection()
				.prepareStatement("""
						SELECT user_id
						FROM user_integration_discord
						WHERE discord_id = ?
				""")
		) {
			prepared.setString(1, discordId);
			ResultSet result = prepared.executeQuery();
			if (!result.isBeforeFirst()) {
				throw new HypertextException(404, "Could not find user from Discord ID '" + discordId + "'");
			}

			return result.getString("user_id");
		}
	}

	public boolean userIdExists(String id) throws SQLException {
		try (PreparedStatement prepared = this.getConnection()
				.prepareStatement("SELECT 1 FROM users WHERE id = ?")) {
			prepared.setString(1, id);
			ResultSet result = prepared.executeQuery();
			return result != null && result.getBoolean(1);
		}
	}

	public boolean usernameExists(String id) throws SQLException {
		try (PreparedStatement prepared = this.getConnection()
				.prepareStatement("SELECT 1 FROM users WHERE username = ?")) {
			prepared.setString(1, id);
			ResultSet result = prepared.executeQuery();
			return result != null && result.getBoolean(1);
		}
	}

	public Permissions getUserPermissions(String userId) throws SQLException, HypertextException {
		try (var userStatement = this.getConnection()
				.prepareStatement("SELECT permissions FROM users WHERE id = ?")) {
			userStatement.setString(1, userId);
			ResultSet resultSet = userStatement.executeQuery();
			if (!resultSet.isBeforeFirst()) {
				throw new NotFoundException("User '" + userId + "' does not exist.");
			}

			return new Permissions(resultSet.getLong("permissions"));
		}
	}

	public UserRole getUserRoleFromId(String roleId) throws SQLException, HypertextException {
		try (var userRoleDefinitionStatement = this.getConnection()
				.prepareStatement("""
						SELECT name, permissions, created
						FROM user_role_definitions
						WHERE id = ?
					""");
		     var userRoleIntegrationDiscordStatement = this.getConnection()
					 .prepareStatement("""
							SELECT discord_role_id
							FROM user_role_integration_discord
							WHERE role_id = ?
						""")
		) {
			userRoleDefinitionStatement.setString(1, roleId);
			ResultSet resultSet = userRoleDefinitionStatement.executeQuery();
			if (!resultSet.isBeforeFirst()) {
				throw new NotFoundException("User role '" + roleId + "' does not exist.");
			}

			String name = resultSet.getString("name");
			Permissions permissions = new Permissions(resultSet.getLong("permissions"));
			Instant created = Instant.ofEpochMilli(resultSet.getLong("created"));

			Map<String, Integration> integrations = new LinkedHashMap<>();

			userRoleIntegrationDiscordStatement.setString(1, roleId);
			ResultSet userRolesIntegrationDiscordResult = userRoleIntegrationDiscordStatement.executeQuery();

			if (userRolesIntegrationDiscordResult.isBeforeFirst()) {
				integrations.put(
						"discord",
						new DiscordUserRoleIntegration(
								userRolesIntegrationDiscordResult.getString("discord_role_id")
						)
				);
			}

			return new UserRole(
					roleId,
					name,
					permissions,
					created,
					integrations
			);
		}
	}

	public void setUsername(String userId, String newUsername) throws SQLException {
		try (
				var statement = this.getConnection().prepareStatement("""
					UPDATE users
					SET username = ?
					WHERE id = ?
				""")
		) {
			statement.setString(1, newUsername);
			statement.setString(2, userId);
			statement.executeUpdate();
		}
	}

	public void setUserBioDisplayName(String userId, @Nullable String displayName) throws SQLException {
		try (
				var statement = this.getConnection().prepareStatement("""
					UPDATE user_bios
					SET display_name = ?
					WHERE user_id = ?
				""")
		) {
			statement.setString(1, displayName);
			statement.setString(2, userId);
			statement.executeUpdate();
		}
	}

	public void setUserBioPronouns(String userId, @Nullable String pronouns) throws SQLException {
		try (
				var statement = this.getConnection().prepareStatement("""
					UPDATE user_bios
					SET pronouns = ?
					WHERE user_id = ?
				""")
		) {
			statement.setString(1, pronouns);
			statement.setString(2, userId);
			statement.executeUpdate();
		}
	}

	public void setUserBioDescription(String userId, @Nullable String description) throws SQLException {
		try (
				var statement = this.getConnection().prepareStatement("""
					UPDATE user_bios
					SET description = ?
					WHERE user_id = ?
				""")
		) {
			statement.setString(1, description);
			statement.setString(2, userId);
			statement.executeUpdate();
		}
	}

	public void setUserBioAvatarUrl(String userId, @Nullable String avatarUrl) throws SQLException {
		try (
				var statement = this.getConnection().prepareStatement("""
					UPDATE user_bios
					SET avatar_url = ?
					WHERE user_id = ?
				""")
		) {
			statement.setString(1, avatarUrl);
			statement.setString(2, userId);
			statement.executeUpdate();
		}
	}

	public void addUserBioField(String userId, String fieldName, String fieldValue) throws SQLException {
		try (
				var statement = this.getConnection().prepareStatement("""
					INSERT INTO user_bio_fields (user_id, field_name, field_value)
					VALUES (?, ?, ?)
				""")
		) {
			statement.setString(1, userId);
			statement.setString(2, fieldName);
			statement.setString(3, fieldValue);
			statement.executeUpdate();
		}
	}

	public void removeUserBioField(String userId, String fieldName) throws SQLException {
		try (
				var statement = this.getConnection().prepareStatement("""
					DELETE FROM user_bio_fields
					WHERE user_id = ? AND field_name = ?
				""")
		) {
			statement.setString(1, userId);
			statement.setString(2, fieldName);
			statement.executeUpdate();
		}
	}

	public void setUserDiscordIntegration(String userId, String discordId) throws SQLException {
		try (
				var statement = this.getConnection().prepareStatement("""
					INSERT OR REPLACE INTO user_integration_discord (user_id, discord_id)
					VALUES (?, ?)
				""")
		) {
			statement.setString(1, userId);
			statement.setString(2, discordId);
			statement.executeUpdate();
		}
	}

	public void removeUserDiscordIntegration(String userId) throws SQLException {
		try (
				var statement = this.getConnection().prepareStatement("""
					DELETE FROM user_integration_discord
					WHERE user_id = ?
				""")
		) {
			statement.setString(1, userId);
			statement.executeUpdate();
		}
	}

	// Projects

	private void createProject(String projectId, String ownerUserId, String name) throws SQLException {
		try (
				var projectStatement = this.getConnection().prepareStatement("""
					INSERT INTO projects (id)
					VALUES (?)
				""");
				var projectNoneMetadataStatement = this.getConnection().prepareStatement("""
					INSERT INTO project_none_metadata (project_id, name)
					VALUES (?, ?)
				""");
				var projectRolesStatement = this.getConnection().prepareStatement("""
					INSERT OR IGNORE INTO project_roles (project_id, user_id, permissions)
					VALUES (?, ?, 1)
				""")
		) {
			projectStatement.setString(1, projectId);
			projectStatement.execute();

			projectNoneMetadataStatement.setString(1, projectId);
			projectNoneMetadataStatement.setString(2, name);
			projectNoneMetadataStatement.execute();

			projectRolesStatement.setString(1, projectId);
			projectRolesStatement.setString(2, ownerUserId);
			projectRolesStatement.execute();
		}
	}

	public String createProject(String ownerUserId, String name) throws SQLException {
		String projectId = NaturalId.generate("projects", "id", null, 5);
		this.createProject(projectId, ownerUserId, name);
		return projectId;
	}

	public void deleteProject(String projectId) throws SQLException {
		Connection connection = this.getConnection();

		try (
				var projectStatement = connection.prepareStatement("""
					DELETE FROM projects
					WHERE id = ?
				""")
		) {
			projectStatement.setString(1, projectId);
			projectStatement.executeUpdate();
		}
	}

	public void setRoleName(String projectId, String userId, String roleName) throws SQLException {
		try (var updateStatement = this.getConnection().prepareStatement("""
					UPDATE project_roles
					SET role_name = ?
					WHERE project_id = ? AND user_id = ?
				""")) {
			updateStatement.setString(1, roleName);
			updateStatement.setString(2, projectId);
			updateStatement.setString(3, userId);
			updateStatement.executeUpdate();
		}
	}

	public String getProjectIdFromSubmissionId(
			@NotNull String submissionId
	) throws SQLException, NotFoundException {
		try (var submissionIdStatement = this.getConnection().prepareStatement("""
					SELECT project_id
					FROM submissions
					WHERE id = ?
				""")) {
			submissionIdStatement.setString(1, submissionId);
			ResultSet submissionResult = submissionIdStatement.executeQuery();
			if (!submissionResult.isBeforeFirst()) {
				throw new NotFoundException("Could not find submission '" + submissionId + "'");
			}

			return submissionResult.getString("project_id");
		}
	}

	public boolean canUserModifyMember(
			String projectId,
			String targetUserIdToModify,
			Permissions selfPermissions
	) throws SQLException, HypertextException {
		try (var memberPermissionsStatement = this.getConnection().prepareStatement("""
					SELECT permissions
					FROM project_roles
					WHERE project_id = ? AND user_id = ?
				""")) {
			memberPermissionsStatement.setString(1, projectId);
			memberPermissionsStatement.setString(2, targetUserIdToModify);
			ResultSet memberPermissionsResult = memberPermissionsStatement.executeQuery();
			Permissions memberPermissions = new Permissions(memberPermissionsResult.getLong(1));

			// Return true if self can edit project, and if the target is not an administrator, or if self is an administrator.
			return selfPermissions.hasPermissions(Permission.EDIT_PROJECT) && (!memberPermissions.hasPermissions(Permission.ADMINISTRATOR) || selfPermissions.hasPermissions(Permission.ADMINISTRATOR));
		}
	}

	public void setProjectMemberPermissions(
			Permissions permissions,
			String projectId,
			String userId
	) throws SQLException {
		try (var updateStatement = this.getConnection().prepareStatement("""
					UPDATE project_roles
					SET permissions = ?
					WHERE project_id = ? AND user_id = ?
				""")) {
			updateStatement.setLong(1, permissions.bits());
			updateStatement.setString(2, projectId);
			updateStatement.setString(3, userId);
			updateStatement.executeUpdate();
		}
	}

	public Project getProjectFromId(
			@NotNull String projectId
	) throws SQLException, HypertextException {
		Connection connection = this.getConnection();
		Map<String, String> team = new LinkedHashMap<>();
		Map<String, Permissions> permissions = new LinkedHashMap<>();
		List<String> submissions = new ArrayList<>();

		try (
				var projectRolesStatement = connection.prepareStatement("""
					SELECT user_id, permissions, role_name
					FROM project_roles
					WHERE project_id = ?
				""");
				var projectNoneMetadataStatement = connection.prepareStatement("""
					SELECT name
					FROM project_none_metadata
					WHERE project_id = ?
				""");
				var projectModMetadataStatement = connection.prepareStatement("""
					SELECT mod_id, name, description, source_url
					FROM project_mod_metadata
					WHERE project_id = ?
				""");
				var submissionsStatement = connection.prepareStatement("""
					SELECT id
					FROM submissions
					WHERE project_id = ?
					ORDER BY ROWID
				""")
		) {
			projectModMetadataStatement.setString(1, projectId);
			ResultSet projectModMetadataResult = projectModMetadataStatement.executeQuery();

			projectNoneMetadataStatement.setString(1, projectId);
			ResultSet projectNoneMetadataResult = projectNoneMetadataStatement.executeQuery();

			ProjectMetadata metadata;
			if (projectModMetadataResult.isBeforeFirst()) {
				metadata = new ModProjectMetadata(
						projectModMetadataResult.getString("mod_id"),
						projectModMetadataResult.getString("name"),
						projectModMetadataResult.getString("description"),
						projectModMetadataResult.getString("source_url")
				);
			} else if (projectNoneMetadataResult.isBeforeFirst()) {
				metadata = new NoneProjectMetadata(
						projectNoneMetadataResult.getString("name")
				);
			} else {
				throw new NotFoundException("Could not find metadata for project '" + projectId + "'");
			}


			projectRolesStatement.setString(1, projectId);
			ResultSet projectRolesResult = projectRolesStatement.executeQuery();
			while (projectRolesResult.next()) {
				String projectRoleUserId = projectRolesResult.getString("user_id");
				team.put(projectRoleUserId, projectRolesResult.getString("role_name"));
				permissions.put(projectRoleUserId, new Permissions(projectRolesResult.getLong("permissions")));
			}

			submissionsStatement.setString(1, projectId);
			ResultSet submissionsResult = submissionsStatement.executeQuery();
			while (submissionsResult.next()) {
				submissions.add(submissionsResult.getString("id"));
			}

			return new Project(
					projectId,
					metadata,
					team,
					permissions,
					submissions
			);
		}
	}

	public boolean hasProjectMemberPermissions(String userId, String projectId) throws SQLException, HypertextException {
		Connection connection = this.getConnection();

		try (var userStatement = connection.prepareStatement("""
					SELECT permissions
					FROM project_roles
					WHERE user_id = ? AND project_id = ?
				""")) {
			userStatement.setString(1, userId);
			userStatement.setString(2, projectId);
			ResultSet resultSet = userStatement.executeQuery();
			return resultSet.isBeforeFirst();
		}
	}

	public Permissions getProjectMemberPermissions(String userId, String projectId) throws SQLException, HypertextException {
		Connection connection = this.getConnection();

		try (var userStatement = connection.prepareStatement("""
					SELECT permissions
					FROM project_roles
					WHERE user_id = ? AND project_id = ?
				""")) {
			userStatement.setString(1, userId);
			userStatement.setString(2, projectId);
			ResultSet resultSet = userStatement.executeQuery();
			if (!resultSet.isBeforeFirst()) {
				throw new NotFoundException("User does not have a role in the specified project.");
			}

			return new Permissions(resultSet.getLong("permissions"));
		}
	}

	public void addProjectMember(String projectId, String userId) throws SQLException {
		try (
				var insertStatement = this.getConnection().prepareStatement("""
					INSERT OR IGNORE INTO project_roles (project_id, user_id)
					VALUES (?, ?)
				""")
		) {
			insertStatement.setString(1, projectId);
			insertStatement.setString(2, userId);
			insertStatement.executeUpdate();
		}
	}

	public void removeProjectMember(String projectId, String userId) throws SQLException {
		try (var deleteStatement = this.getConnection().prepareStatement("""
					DELETE FROM project_roles
					WHERE project_id = ? AND user_id = ?
				""")) {
			deleteStatement.setString(1, projectId);
			deleteStatement.setString(2, userId);
			deleteStatement.executeUpdate();
		}
	}

	public int getProjectAdministratorCount(String projectId) throws SQLException {
		try (var permissionCountStatement = this.getConnection().prepareStatement("""
					SELECT COUNT(*)
					FROM project_roles
					WHERE project_id = ? AND has_permissions(permissions, 1)
				""")) {
			permissionCountStatement.setString(1, projectId);
			ResultSet permissionCountResult = permissionCountStatement.executeQuery();
			return permissionCountResult.getInt(1);
		}
	}

	public boolean projectExists(String projectId) throws SQLException {
		try (var projectStatement =
				     this.getConnection().prepareStatement("SELECT id FROM projects WHERE id = ?")) {
			projectStatement.setString(1, projectId);
			ResultSet resultSet = projectStatement.executeQuery();
			return resultSet.isBeforeFirst();
		}
	}

	public String getLatestProjectIdFromModId(String modId) throws SQLException, HypertextException {
		try (var projectModMetadataStatement = this.getConnection().prepareStatement("""
					SELECT project_id
					FROM project_mod_metadata
					WHERE mod_id = ?
					ORDER BY -ROWID
				""")) {
			projectModMetadataStatement.setString(1, modId);
			ResultSet projectMetadataResult = projectModMetadataStatement.executeQuery();

			if (!projectMetadataResult.isBeforeFirst()) {
				throw new NotFoundException("Project with mod ID " + modId + " does not exist");
			}

			return projectMetadataResult.getString("project_id");
		}
	}

	// Submissions

	public String getLatestSubmissionIdFromModId(String modId) throws SQLException, HypertextException {
		Project project = this.getProjectFromId(this.getLatestProjectIdFromModId(modId));
		return project.submissions().getLast();
	}

	public boolean areSubmissionsOpenForEvent(String eventId) throws SQLException {
		try (
				var submissionsOpenStatement = this.getConnection().prepareStatement("""
					SELECT 1
					FROM event_times
					WHERE event_id = ? AND development_start <= ? AND development_end > ?
				""")
		) {
			long currentMs = System.currentTimeMillis();
			submissionsOpenStatement.setString(1, eventId);
			submissionsOpenStatement.setLong(2, currentMs);
			submissionsOpenStatement.setLong(3, currentMs);
			ResultSet submissionsOpenResult = submissionsOpenStatement.executeQuery();
			return submissionsOpenResult != null && submissionsOpenResult.getBoolean(1);
		}
	}

	public String createEmptySubmission(String eventId, String projectId) throws SQLException, HypertextException {
		try (
				var submissionsStatement = this.getConnection().prepareStatement("""
					INSERT INTO submissions (id, event_id, project_id, submitted)
					VALUES (?, ?, ?, ?)
				""")
		) {
			String submissionId = NaturalId.generate("submissions", "id", null, 5);
			submissionsStatement.setString(1, submissionId);
			submissionsStatement.setString(2, eventId);
			submissionsStatement.setString(3, projectId);
			submissionsStatement.setLong(4, System.currentTimeMillis());
			submissionsStatement.executeUpdate();
			return submissionId;
		}
	}

	public void deleteSubmission(String submissionId) throws SQLException {
		Connection connection = this.getConnection();

		try (
				var submissionsStatement = connection.prepareStatement("""
					DELETE FROM submissions
					WHERE id = ?
				""");
				var typeModrinthStatement = connection.prepareStatement("""
					DELETE FROM submission_type_modrinth
					WHERE submission_id = ?
				""")
		) {
			submissionsStatement.setString(1, submissionId);
			submissionsStatement.executeUpdate();

			typeModrinthStatement.setString(1, submissionId);
			typeModrinthStatement.executeUpdate();
		}
	}

	public Submission getSubmission(
			@NotNull String submissionId
	) throws Exception {
		Connection connection = this.getConnection();

		try (
				var submissionStatement = connection.prepareStatement("""
					SELECT event_id, project_id, submitted
					FROM submissions
					WHERE id = ?
				""");
				var modrinthSubmissionTypeStatement = connection.prepareStatement("""
					SELECT modrinth_id, version_id
					FROM submission_type_modrinth
					WHERE submission_id = ?
				""")
		) {
			submissionStatement.setString(1, submissionId);
			ResultSet submissionResult = submissionStatement.executeQuery();
			if (!submissionResult.isBeforeFirst()) {
				throw new NotFoundException("Could not find submission '" + submissionId + "'");
			}

			modrinthSubmissionTypeStatement.setString(1, submissionId);
			ResultSet modrinthSubmissionTypeResult = modrinthSubmissionTypeStatement.executeQuery();

			SubmissionPlatform platform;
			// TODO: Implement download URL submission type.
			if (modrinthSubmissionTypeResult.isBeforeFirst()) {
				platform = new ModrinthSubmissionPlatform(
						modrinthSubmissionTypeResult.getString("modrinth_id"),
						modrinthSubmissionTypeResult.getString("version_id")
				);
			} else {
				throw new HypertextException(400, "Submission does not have a valid 'platform'");
			}

			return new Submission(
					submissionId,
					submissionResult.getString("event_id"),
					Instant.ofEpochMilli(submissionResult.getLong("submitted")),
					this.getProjectFromId(submissionResult.getString("project_id")),
					platform
			);
		}
	}

	@Nullable
	public String getSubmissionId(String projectId, String eventId) throws SQLException {
		try (var submissionsStatement = this.getConnection().prepareStatement("""
					SELECT id
					FROM submissions
					WHERE project_id = ? AND event_id = ?
				""")) {
			submissionsStatement.setString(1, projectId);
			submissionsStatement.setString(2, eventId);
			ResultSet resultSet = submissionsStatement.executeQuery();

			if (!resultSet.isBeforeFirst()) {
				return null;
			}

			return resultSet.getString("id");
		}
	}

	// Events

	public Genre getGenreById(String id) throws SQLException, HypertextException {
		if (!"modgr".equals(id)) {
			throw new NotFoundException("Genre with ID '" + id + "' does not exist");
		}

		return this.getGenres().getFirst();
	}

	public Genre getGenreBySlug(String slug) throws SQLException, HypertextException {
		if (!"mod-garden".equals(slug)) {
			throw new NotFoundException("Genre with slug '" + slug + "' does not exist");
		}

		return this.getGenres().getFirst();
	}

	public List<Genre> getGenres() throws SQLException {
		return List.of(Genre.getModGarden(getEventIds("mod-garden")));
	}

	public List<String> getGenreIds() throws SQLException {
		return List.of("modgr");
	}

	public List<String> getGenreSlugs() throws SQLException {
		return List.of("mod-garden");
	}

	public List<Event> getEvents() throws SQLException, HypertextException {
		try (var eventStatement = this.getConnection().prepareStatement("""
				SELECT slug, genre_slug
				FROM events
			""")) {
			ResultSet resultSet = eventStatement.executeQuery();
			List<Event> events = new ArrayList<>();

			while (resultSet.next()) {
				events.add(getEvent(
						resultSet.getString("genre_slug"),
						resultSet.getString("slug")
				));
			}

			return events;
		}
	}

	public List<String> getEventIds(String genreSlug) throws SQLException {
		try (var eventStatement = this.getConnection().prepareStatement("""
				SELECT id
				FROM events
				WHERE genre_slug = ?
			""")) {
			eventStatement.setString(1, genreSlug);
			ResultSet eventResult = eventStatement.executeQuery();
			List<String> ids = new ArrayList<>();

			while (eventResult.next()) {
				ids.add(eventResult.getString("id"));
			}

			return ids;
		}
	}

	public List<String> getEventSlugs(String genreSlug) throws SQLException {
		try (var eventStatement = this.getConnection().prepareStatement("""
				SELECT event_slug
				FROM events
				WHERE genre_slug = ?
			""")) {
			eventStatement.setString(1, genreSlug);
			ResultSet eventResult = eventStatement.executeQuery();
			List<String> slugs = new ArrayList<>();

			while (eventResult.next()) {
				slugs.add(eventResult.getString("event_slug"));
			}

			return slugs;
		}
	}

	public String getEventSlug(String genreSlug, String eventId) throws SQLException, HypertextException {
		try (var eventStatement = this.getConnection().prepareStatement("""
					SELECT event_slug
					FROM events
					WHERE genre_id = ? AND id = ?
				""")) {
			eventStatement.setString(1, genreSlug);
			eventStatement.setString(2, eventId);
			var eventResult = eventStatement.executeQuery();

			if (!eventResult.isBeforeFirst()) {
				throw new NotFoundException("Event with ID '" + eventId + "' does not exist");
			}

			return eventResult.getString("event_slug");
		}
	}

	public String getEventId(String genreSlug, String eventSlug) throws SQLException, HypertextException {
		try (var eventStatement = this.getConnection().prepareStatement("""
					SELECT id
					FROM events
					WHERE genre_id = ? AND event_slug = ?
				""")) {
			eventStatement.setString(1, genreSlug);
			eventStatement.setString(2, eventSlug);
			var eventResult = eventStatement.executeQuery();

			if (!eventResult.isBeforeFirst()) {
				throw new NotFoundException("Event '" + genreSlug + "/" + eventSlug + " does not exist");
			}

			return eventResult.getString("id");
		}
	}

	public Event getEvent(String genreSlug, String eventSlug) throws SQLException, HypertextException {
		try (
			 var eventStatement = this.getConnection().prepareStatement("""
				SELECT id, slug, genre_slug
				FROM events
				WHERE genre_slug = ? AND slug = ?
			""");
			 var eventMetadataStatement = this.getConnection().prepareStatement("""
				SELECT name, description
				FROM event_metadata
				WHERE event_id = ?
			""");
			 var eventTimesStatement = this.getConnection().prepareStatement("""
				SELECT registration_open, registration_close, development_start, development_end, pack_freeze
				FROM event_times
				WHERE event_id = ?
			""");
		     var minecraftPlatformStatement = this.getConnection().prepareStatement("""
				SELECT mod_loader, game_version
				FROM event_platform_minecraft
				WHERE event_id = ?
			""");
		     var rolesStatement = this.getConnection().prepareStatement("""
				SELECT role_key, role_id
				FROM event_roles
				WHERE event_id = ?
			""")) {
			eventStatement.setString(1, genreSlug);
			eventStatement.setString(2, eventSlug);
			var eventResultSet = eventStatement.executeQuery();

			if (!eventResultSet.isBeforeFirst()) {
				throw new NotFoundException("Event '" + genreSlug + "/" + eventSlug + "' does not exist");
			}

			String eventId = eventResultSet.getString("id");

			eventMetadataStatement.setString(1, eventId);
			var eventMetadataResultSet = eventMetadataStatement.executeQuery();

			if (!eventMetadataResultSet.isBeforeFirst()) {
				throw new HypertextException(500, "Event '" + genreSlug + "/" + eventSlug + "' does not have associated metadata");
			}

			eventTimesStatement.setString(1, eventId);
			var eventTimesResultSet = eventTimesStatement.executeQuery();

			if (!eventTimesResultSet.isBeforeFirst()) {
				throw new HypertextException(500, "Event '" + genreSlug + "/" + eventSlug + "' does not have associated times");
			}

			EventPlatform platform;

			minecraftPlatformStatement.setString(1, eventId);
			var minecraftPlatformResultSet = minecraftPlatformStatement.executeQuery();

			if (!minecraftPlatformResultSet.isBeforeFirst()) {
				throw new HypertextException(500, "Event '" + genreSlug + "/" + eventSlug + "' does not have an associated platform");
			}

			platform = new MinecraftEventPlatform(
					minecraftPlatformResultSet.getString("mod_loader"),
					minecraftPlatformResultSet.getString("game_version")
			);

			Map<String, String> roles = new LinkedHashMap<>();

			rolesStatement.setString(1, eventId);
			ResultSet rolesResultSet = rolesStatement.executeQuery();

			while (rolesResultSet.next()) {
				String roleKey = rolesResultSet.getString("role_key");
				String roleId = rolesResultSet.getString("role_id");
				roles.put(roleKey, roleId);
			}

			return new Event(
					eventId,
					eventSlug,
					new EventMetadata(
							eventMetadataResultSet.getString("name"),
							eventMetadataResultSet.getString("description")
					),
					new EventTimes(
							Instant.ofEpochMilli(eventTimesResultSet.getLong("registration_open")),
							Instant.ofEpochMilli(eventTimesResultSet.getLong("registration_close")),
							Instant.ofEpochMilli(eventTimesResultSet.getLong("development_start")),
							Instant.ofEpochMilli(eventTimesResultSet.getLong("development_end")),
							Instant.ofEpochMilli(eventTimesResultSet.getLong("pack_freeze"))
					),
					platform,
					roles
			);
		}
	}

	public List<Submission> getEventSubmissions(String eventId) throws SQLException, HypertextException {
		try (
				var submissionStatement = this.getConnection().prepareStatement("""
					SELECT id, project_id, submitted
					FROM submissions
					WHERE event_id = ?
				""");
				var modrinthSubmissionTypeStatement = this.getConnection().prepareStatement("""
					SELECT modrinth_id, version_id
					FROM submission_type_modrinth
					WHERE submission_id = ?
				""")
		) {
			submissionStatement.setString(1, eventId);
			ResultSet submissionResult = submissionStatement.executeQuery();
			if (!submissionResult.isBeforeFirst()) {
				throw new NotFoundException("Could not find submission for event ID '" + eventId + "'");
			}

			List<Submission> submissions = new ArrayList<>();

			while (submissionResult.next()) {
				modrinthSubmissionTypeStatement.setString(1, submissionResult.getString("id"));
				ResultSet modrinthSubmissionTypeResult = modrinthSubmissionTypeStatement.executeQuery();

				SubmissionPlatform platform;
				// TODO: Implement download URL submission type.
				if (modrinthSubmissionTypeResult.isBeforeFirst()) {
					platform = new ModrinthSubmissionPlatform(
							modrinthSubmissionTypeResult.getString("modrinth_id"),
							modrinthSubmissionTypeResult.getString("version_id")
					);
				} else {
					throw new HypertextException(400, "Submission does not have a valid 'platform'");
				}

				submissions.add(new Submission(
						submissionResult.getString("id"),
						eventId,
						Instant.ofEpochMilli(submissionResult.getLong("submitted")),
						this.getProjectFromId(submissionResult.getString("project_id")),
						platform
				));
			}

			return submissions;
		}
	}

	public List<String> getEventSubmissionIds(String eventId) throws SQLException, HypertextException {
		try (
				var submissionStatement = this.getConnection().prepareStatement("""
					SELECT id
					FROM submissions
					WHERE event_id = ?
				""")
		) {
			submissionStatement.setString(1, eventId);
			ResultSet submissionResult = submissionStatement.executeQuery();
			if (!submissionResult.isBeforeFirst()) {
				throw new NotFoundException("Could not find submission for event ID '" + eventId + "'");
			}

			List<String> submissionIds = new ArrayList<>();

			while (submissionResult.next()) {
				submissionIds.add(submissionResult.getString("id"));
			}

			return submissionIds;
		}
	}

	@Override
	public void close() throws Exception {
		this.connection.ifPresent(Connection::close);
	}
}
