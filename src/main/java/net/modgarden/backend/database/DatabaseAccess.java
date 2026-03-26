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
import net.modgarden.backend.data.event.Event;
import net.modgarden.backend.data.event.Genre;
import net.modgarden.backend.data.event.Project;
import net.modgarden.backend.data.event.Submission;
import net.modgarden.backend.data.event.metadata.DraftMetadata;
import net.modgarden.backend.data.event.metadata.ModMetadata;
import net.modgarden.backend.data.event.platform.ModrinthPlatform;
import net.modgarden.backend.data.user.User;
import net.modgarden.backend.data.user.Bio;
import net.modgarden.backend.data.user.integration.DiscordIntegration;
import net.modgarden.backend.data.user.integration.MinecraftIntegration;
import net.modgarden.backend.data.user.integration.ModrinthIntegration;
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
				return List.of();
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
				""")
			 // TODO: Awards, Events and Roles at a later date.
		) {
			usersStatement.setString(1, userId);
			ResultSet usersResult = usersStatement.executeQuery();

			if (!usersResult.isBeforeFirst()) {
				throw new NotFoundException("Could not find user '" + userId + "'");
			}

			String username = usersResult.getString("username");
			Instant created = Instant.ofEpochMilli(usersResult.getLong("created"));
			Permissions permissions = new Permissions(usersResult.getLong("permissions"));

			userBiosStatement.setString(1, userId);
			ResultSet userBiosResult = userBiosStatement.executeQuery();

			if (!usersResult.isBeforeFirst()) {
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
				integrations.put("discord", new DiscordIntegration(usersIntegrationDiscordResult.getString("discord_id")));
			}

			userIntegrationMinecraftStatement.setString(1, userId);
			ResultSet userIntegrationMinecraftResult = userIntegrationMinecraftStatement.executeQuery();

			if (userIntegrationMinecraftResult.isBeforeFirst()) {
				List<String> accounts = new ArrayList<>();
				while (userIntegrationMinecraftResult.next()) {
					accounts.add(userIntegrationMinecraftResult.getString("uuid"));
				}
				integrations.put("minecraft", new MinecraftIntegration(accounts));
			}

			userIntegrationModrinthStatement.setString(1, userId);
			ResultSet userIntegrationModrinthResult = userIntegrationModrinthStatement.executeQuery();

			if (userIntegrationModrinthResult.isBeforeFirst()) {
				integrations.put("modrinth", new ModrinthIntegration(userIntegrationModrinthResult.getString("modrinth_id")));
			}

			Set<String> projects = new LinkedHashSet<>();

			projectRolesStatement.setString(1, userId);
			ResultSet projectRolesResult = projectRolesStatement.executeQuery();

			while (projectRolesResult.next()) {
				projects.add(projectRolesResult.getString("project_id"));
			}

			Set<String> awards = new LinkedHashSet<>();
			Set<String> events = new LinkedHashSet<>();
			Set<String> roles = new LinkedHashSet<>();

			return new User(
					userId,
					username,
					bio,
					permissions,
					created,
					integrations,
					projects,
					awards,
					events,
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

	public boolean userExists(String id) throws SQLException {
		try (PreparedStatement prepared = this.getConnection()
				.prepareStatement("SELECT 1 FROM users WHERE id = ?")) {
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
				throw new NotFoundException("User does not exist.");
			}

			return new Permissions(resultSet.getLong("permissions"));
		}
	}

	// Projects

	private void createProject(String projectId, String ownerUserId, String name) throws SQLException {
		try (
				var projectStatement = this.getConnection().prepareStatement("""
					INSERT INTO projects (id)
					VALUES (?)
				""");
				var projectDraftMetadataStatement = this.getConnection().prepareStatement("""
					INSERT INTO project_draft_metadata (project_id, name)
					VALUES (?, ?)
				""");
				var projectRolesStatement = this.getConnection().prepareStatement("""
					INSERT OR IGNORE INTO project_roles (project_id, user_id, permissions)
					VALUES (?, ?, 1)
				""")
		) {
			projectStatement.setString(1, projectId);
			projectStatement.executeUpdate();

			projectDraftMetadataStatement.setString(1, projectId);
			projectDraftMetadataStatement.setString(2, name);
			projectDraftMetadataStatement.executeUpdate();

			projectRolesStatement.setString(1, projectId);
			projectRolesStatement.setString(2, ownerUserId);
			projectRolesStatement.executeUpdate();
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

	public void assertUserCanModifyMember(
			String projectId,
			String memberUserIdToModify,
			Permissions selfPermissions
	) throws SQLException, HypertextException {
		try (var memberPermissionsStatement = this.getConnection().prepareStatement("""
					SELECT permissions
					FROM project_roles
					WHERE project_id = ? AND user_id = ?
				""")) {
			memberPermissionsStatement.setString(1, projectId);
			memberPermissionsStatement.setString(2, memberUserIdToModify);
			ResultSet memberPermissionsResult = memberPermissionsStatement.executeQuery();
			Permissions memberPermissions = new Permissions(memberPermissionsResult.getLong(1));

			// If a non-administrator attempts to edit the permissions of an administrator, throw.
			if (memberPermissions.hasPermissions(Permission.ADMINISTRATOR) && !selfPermissions.hasPermissions(Permission.ADMINISTRATOR)) {
				throw new HypertextException(403, "Non-administrators may not edit administrators' permissions on projects");
			}
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
		Map<String, String> team = new HashMap<>();
		Map<String, Permissions> permissions = new HashMap<>();
		List<String> submissions = new ArrayList<>();

		try (
				var projectRolesStatement = connection.prepareStatement("""
					SELECT user_id, permissions, role_name
					FROM project_roles
					WHERE project_id = ?
				""");
				var projectDraftMetadataStatement = connection.prepareStatement("""
					SELECT name
					FROM project_draft_metadata
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

			projectDraftMetadataStatement.setString(1, projectId);
			ResultSet projectDraftMetadataResult = projectDraftMetadataStatement.executeQuery();

			Metadata metadata;
			if (projectModMetadataResult.isBeforeFirst()) {
				metadata = new ModMetadata(
						projectModMetadataResult.getString("mod_id"),
						projectModMetadataResult.getString("name"),
						projectModMetadataResult.getString("description"),
						projectModMetadataResult.getString("source_url")
				);
			} else if (projectDraftMetadataResult.isBeforeFirst()) {
				metadata = new DraftMetadata(
						projectDraftMetadataResult.getString("name")
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

	public void assertProjectExists(String projectId) throws SQLException, HypertextException {
		if (!this.projectExists(projectId)) {
			throw new NotFoundException("Project with ID " + projectId + " does not exist");
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

	public String createEmptySubmission(String eventId, String projectId) throws SQLException {
		try (var submissionsStatement = this.getConnection().prepareStatement("""
					INSERT INTO submissions (id, theme_id, project_id, submitted)
					VALUES (?, ?, ?, ?)
				""")) {
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
					SELECT theme_id, project_id, submitted
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

			Platform platform;
			// TODO: Implement download URL submission type.
			if (modrinthSubmissionTypeResult.isBeforeFirst()) {
				platform = new ModrinthPlatform(
						modrinthSubmissionTypeResult.getString("modrinth_id"),
						modrinthSubmissionTypeResult.getString("version_id")
				);
			} else {
				throw new HypertextException(400, "Submission does not have a valid 'platform'");
			}

			return new Submission(
					submissionId,
					submissionResult.getString("theme_id"),
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
					WHERE project_id = ? AND theme_id = ?
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
		return List.of(Genre.getModGarden(getEventIdsFromGenreSlug("mod-garden")));
	}

	public List<String> getGenreIds() throws SQLException {
		return List.of("modgr");
	}

	public List<String> getGenreSlugs() throws SQLException {
		return List.of("mod-garden");
	}

	public List<Event> getEvents() throws SQLException {
		try (var eventStatement = this.getConnection().prepareStatement("""
				SELECT id, theme_slug, event_slug, display_name, minecraft_version, loader, registration_open_time, registration_close_time, start_time, end_time, freeze_time
				FROM themes
			""");
			var discordIntegrationStatement = this.getConnection().prepareStatement("""
				SELECT id, role_id
				FROM event_integration_discord
			""")) {
			ResultSet discordIntegrationResultSet = discordIntegrationStatement.executeQuery();
			Map<String, String> roleIds = new HashMap<>();

			while (discordIntegrationResultSet.next()) {
				roleIds.put(discordIntegrationResultSet.getString("id"), discordIntegrationResultSet.getString("role_id"));
			}

			ResultSet resultSet = eventStatement.executeQuery();
			List<Event> events = new ArrayList<>();

			while (resultSet.next()) {
				events.add(new Event(
						resultSet.getString("id"),
						resultSet.getString("theme_slug"),
						resultSet.getString("event_slug"),
						resultSet.getString("display_name"),
						Optional.ofNullable(roleIds.getOrDefault(resultSet.getString("id"), null)),
						resultSet.getString("minecraft_version"),
						resultSet.getString("loader"),
						Instant.ofEpochMilli(resultSet.getLong("registration_open_time")),
						Instant.ofEpochMilli(resultSet.getLong("registration_close_time")),
						Instant.ofEpochMilli(resultSet.getLong("start_time")),
						Instant.ofEpochMilli(resultSet.getLong("end_time")),
						Instant.ofEpochMilli(resultSet.getLong("freeze_time"))
				));
			}

			return events;
		}
	}

	public List<String> getEventIdsFromGenreSlug(String genreSlug) throws SQLException {
		try (var eventStatement = this.getConnection().prepareStatement("""
				SELECT id
				FROM themes
				WHERE event_slug = ?
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
				SELECT theme_slug
				FROM themes
				WHERE event_slug = ?
			""")) {
			eventStatement.setString(1, genreSlug);
			ResultSet eventResult = eventStatement.executeQuery();
			List<String> slugs = new ArrayList<>();

			while (eventResult.next()) {
				slugs.add(eventResult.getString("theme_slug"));
			}

			return slugs;
		}
	}

	public String getEventSlug(String genreSlug, String eventId) throws SQLException, HypertextException {
		try (var eventStatement = this.getConnection().prepareStatement("""
					SELECT theme_slug
					FROM themes
					WHERE event_slug = ? AND id = ?
				""")) {
			eventStatement.setString(1, genreSlug);
			eventStatement.setString(2, eventId);
			var eventResult = eventStatement.executeQuery();

			if (!eventResult.isBeforeFirst()) {
				throw new NotFoundException("Event with ID '" + eventId + "' does not exist");
			}

			return eventResult.getString("theme_slug");
		}
	}

	public String getEventId(String genreSlug, String eventSlug) throws SQLException, HypertextException {
		try (var eventStatement = this.getConnection().prepareStatement("""
					SELECT id
					FROM themes
					WHERE event_slug = ? AND theme_slug = ?
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

	public Event getEventFromSlug(String genreSlug, String eventSlug) throws SQLException, HypertextException {
		try (var eventStatement = this.getConnection().prepareStatement("""
				SELECT id, theme_slug, event_slug, display_name, minecraft_version, loader, registration_open_time, registration_close_time, start_time, end_time, freeze_time
				FROM themes
				WHERE event_slug = ? AND theme_slug = ?
			""");
			var discordIntegrationStatement = this.getConnection().prepareStatement("""
				SELECT id, role_id
				FROM event_integration_discord
				WHERE id = ?
			""")) {
			eventStatement.setString(1, genreSlug);
			eventStatement.setString(2, eventSlug);
			var resultSet = eventStatement.executeQuery();
			discordIntegrationStatement.setString(1, resultSet.getString("id"));
			var discordIntegrationResultSet = discordIntegrationStatement.executeQuery();
			String discordRoleId = null;

			if (!resultSet.isBeforeFirst()) {
				throw new NotFoundException("Event '" + genreSlug + "/" + eventSlug + "' does not exist");
			}

			if (discordIntegrationResultSet.isBeforeFirst()) {
				discordRoleId = discordIntegrationResultSet.getString("role_id");
			}

			return new Event(
					resultSet.getString("id"),
					resultSet.getString("theme_slug"),
					resultSet.getString("event_slug"),
					resultSet.getString("display_name"),
					Optional.ofNullable(discordRoleId),
					resultSet.getString("minecraft_version"),
					resultSet.getString("loader"),
					Instant.ofEpochMilli(resultSet.getLong("registration_open_time")),
					Instant.ofEpochMilli(resultSet.getLong("registration_close_time")),
					Instant.ofEpochMilli(resultSet.getLong("start_time")),
					Instant.ofEpochMilli(resultSet.getLong("end_time")),
					Instant.ofEpochMilli(resultSet.getLong("freeze_time"))
			);
		}
	}

	public List<Submission> getEventSubmissions(String eventId) throws SQLException, HypertextException {
		try (
				var submissionStatement = this.getConnection().prepareStatement("""
					SELECT id, project_id, submitted
					FROM submissions
					WHERE theme_id = ?
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

				Platform platform;
				// TODO: Implement download URL submission type.
				if (modrinthSubmissionTypeResult.isBeforeFirst()) {
					platform = new ModrinthPlatform(
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
					WHERE theme_id = ?
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
