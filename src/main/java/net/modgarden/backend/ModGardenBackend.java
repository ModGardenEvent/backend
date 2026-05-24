package net.modgarden.backend;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.http.HttpClient;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;

import ch.qos.logback.classic.Level;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import io.github.cdimascio.dotenv.Dotenv;
import io.javalin.Javalin;
import io.javalin.json.JsonMapper;
import net.modgarden.backend.data.ExceptionPage;
import net.modgarden.backend.data.DevelopmentModeData;
import net.modgarden.backend.data.LandingPage;
import net.modgarden.backend.data.award.Award;
import net.modgarden.backend.data.award.AwardInstance;
import net.modgarden.backend.data.event.Event;
import net.modgarden.backend.data.event.Genre;
import net.modgarden.backend.data.project.Project;
import net.modgarden.backend.data.project.Submission;
import net.modgarden.backend.data.fixer.DatabaseFixer;
import net.modgarden.backend.data.user.User;
import net.modgarden.backend.data.user.role.UserRole;
import net.modgarden.backend.database.function.GenerateNaturalIdFunction;
import net.modgarden.backend.database.function.HasPermissionsFunction;
import net.modgarden.backend.database.function.UnixMillisFunction;
import net.modgarden.backend.endpoint.Endpoint;
import net.modgarden.backend.endpoint.exception.HypertextException;
import net.modgarden.backend.endpoint.internal.user.CreateUserEndpoint;
import net.modgarden.backend.endpoint.internal.user.ModifyUserEndpoint;
import net.modgarden.backend.endpoint.v2.auth.api_key.DeleteKeyEndpoint;
import net.modgarden.backend.endpoint.v2.auth.api_key.GenerateKeyEndpoint;
import net.modgarden.backend.endpoint.v2.auth.api_key.ListKeysEndpoint;
import net.modgarden.backend.endpoint.v2.events.GetEventEndpoint;
import net.modgarden.backend.endpoint.v2.events.GetEventSubmissionsEndpoint;
import net.modgarden.backend.endpoint.v2.events.ListEventsEndpoint;
import net.modgarden.backend.endpoint.v2.genres.GetGenreEndpoint;
import net.modgarden.backend.endpoint.v2.genres.ListGenresEndpoint;
import net.modgarden.backend.endpoint.v2.projects.CreateProjectEndpoint;
import net.modgarden.backend.endpoint.v2.projects.DeleteProjectEndpoint;
import net.modgarden.backend.endpoint.v2.projects.GetProjectEndpoint;
import net.modgarden.backend.endpoint.v2.projects.ModifyMembersEndpoint;
import net.modgarden.backend.endpoint.v2.projects.SetPermissionsEndpoint;
import net.modgarden.backend.endpoint.v2.roles.GetRoleEndpoint;
import net.modgarden.backend.endpoint.v2.submissions.CreateSubmissionEndpoint;
import net.modgarden.backend.endpoint.v2.submissions.DeleteSubmissionEndpoint;
import net.modgarden.backend.endpoint.v2.submissions.GetSubmissionEndpoint;
import net.modgarden.backend.endpoint.v2.users.GetUserEndpoint;
import net.modgarden.backend.util.AuthUtil;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModGardenBackend {
	public static final Dotenv DOTENV = Dotenv.load();

	public static final String URL = "development".equals(DOTENV.get("env")) ? "http://localhost:7070" : "https://api.modgarden.net";
	public static final Logger LOG = LoggerFactory.getLogger(ModGardenBackend.class);

	private static final Map<Type, Codec<?>> CODEC_REGISTRY = new HashMap<>();

	public static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

	private final Javalin app;

	private ModGardenBackend(Javalin app) {
		this.app = app;
	}

	public static void main() {
		if ("development".equals(DOTENV.get("env")))
			((ch.qos.logback.classic.Logger)LOG).setLevel(Level.DEBUG);

		LandingPage.createInstance();

		try {
			boolean createdFile = new File("./database.db").createNewFile();
			DatabaseFixer.createFixers();
			if (createdFile) {
				createDatabaseContents();
				updateSchemaVersion();
				LOG.debug("Successfully created database file.");
			}
			DatabaseFixer.fixDatabase();
			if (!createdFile) {
				updateSchemaVersion();
			}
		} catch (IOException ex) {
			LOG.error("Failed to create database file.", ex);
		}

		registerCodec(LandingPage.class, LandingPage.CODEC);
		registerCodec(ExceptionPage.class, ExceptionPage.CODEC);
		registerCodec(Award.class, Award.DIRECT_CODEC);
		registerCodec(Event.class, Event.DIRECT_CODEC);
		registerCodec(Genre.class, Genre.DIRECT_CODEC);
		registerCodec(Project.class, Project.DIRECT_CODEC);
		registerCodec(Submission.class, Submission.DIRECT_CODEC);
		registerCodec(User.class, User.DIRECT_CODEC);
		registerCodec(UserRole.class, UserRole.DIRECT_CODEC);
		registerCodec(AwardInstance.FullAwardData.class, AwardInstance.FullAwardData.CODEC);
		registerCodec(GenerateKeyEndpoint.Response.class, GenerateKeyEndpoint.Response.CODEC);
		registerCodec(ListKeysEndpoint.Response.class, ListKeysEndpoint.Response.CODEC);

		AuthUtil.clearTokensEachFifteenMinutes();

		Javalin app = Javalin.create(config -> config.jsonMapper(createDFUMapper()));
		app.get("", LandingPage::getLandingJson);

		ModGardenBackend backend = new ModGardenBackend(app);
		backend.v2();
		backend.internal();

		app.exception(HypertextException.class, (e, ctx) -> {
			ctx.status(e.getStatus());
			ctx.result(e.getMessage());
			ExceptionPage.handleError(ctx);
		});
		app.exception(NullPointerException.class, (e, ctx) -> {
			ctx.status(404);
			ctx.result(e.getMessage());
			LOG.error("", e);
			ExceptionPage.handleError(ctx);
		});
		app.exception(SQLException.class, (e, ctx) -> {
			ctx.status(500);
			ctx.result("Internal Error");
			LOG.error("", e);
			ExceptionPage.handleError(ctx);
		});
		app.exception(UnsupportedOperationException.class, (e, ctx) -> {
			ctx.status(500);
			ctx.result("Internal Error");
			LOG.error("", e);
			ExceptionPage.handleError(ctx);
		});

		app.start(7070);

		LOG.info("Mod Garden Backend Started!");
	}

	public void v2() {
		post(GenerateKeyEndpoint::new);
		delete(DeleteKeyEndpoint::new);
		get(ListKeysEndpoint::new);

		post(CreateProjectEndpoint::new);
		patch(ModifyMembersEndpoint::new);
		patch(SetPermissionsEndpoint::new);
		delete(DeleteProjectEndpoint::new);
		get(GetProjectEndpoint::new);

		get(GetGenreEndpoint::new);
		get(ListGenresEndpoint::new);

		get(GetEventEndpoint::new);
		get(GetEventSubmissionsEndpoint::new);
		get(ListEventsEndpoint::new);

		post(CreateSubmissionEndpoint::new);
		delete(DeleteSubmissionEndpoint::new);
		get(GetSubmissionEndpoint::new);

		get(GetUserEndpoint::new);

		get(GetRoleEndpoint::new);
	}

	public void internal() {
		post(CreateUserEndpoint::new);
		patch(ModifyUserEndpoint::new);
	}

	private void get(Supplier<Endpoint> endpointSupplier) {
		Endpoint endpoint = endpointSupplier.get();
		this.app.get(endpoint.getPath(), endpoint);
	}

	private void post(Supplier<Endpoint> endpointSupplier) {
		Endpoint endpoint = endpointSupplier.get();
		this.app.post(endpoint.getPath(), endpoint);
	}

	private void put(Supplier<Endpoint> endpointSupplier) {
		Endpoint endpoint = endpointSupplier.get();
		this.app.put(endpoint.getPath(), endpoint);
	}

	private void patch(Supplier<Endpoint> endpointSupplier) {
		Endpoint endpoint = endpointSupplier.get();
		this.app.patch(endpoint.getPath(), endpoint);
	}

	private void delete(Supplier<Endpoint> endpointSupplier) {
		Endpoint endpoint = endpointSupplier.get();
		this.app.delete(endpoint.getPath(), endpoint);
	}

	public static void registerDatabaseFunctions(Connection connection) throws SQLException {
		GenerateNaturalIdFunction.INSTANCE.create(connection);
		HasPermissionsFunction.INSTANCE.create(connection);
		UnixMillisFunction.INSTANCE.create(connection);
	}

	public static Connection createDatabaseConnection() throws SQLException {
		String url = "jdbc:sqlite:database.db";
		Properties props = new Properties();
		props.setProperty("foreign_keys", "true");
		Connection connection = DriverManager.getConnection(url, props);
		registerDatabaseFunctions(connection);
		return connection;
	}

	private static void createDatabaseContents() {
		try (Connection connection = createDatabaseConnection();
			 Statement statement = connection.createStatement()) {
			statement.addBatch("""
			CREATE TABLE IF NOT EXISTS users (
				id TEXT UNIQUE NOT NULL,
				username TEXT UNIQUE NOT NULL,
				created INTEGER NOT NULL,
				permissions INTEGER NOT NULL,
				PRIMARY KEY(id)
			)
			""");
			statement.addBatch("""
			CREATE TABLE IF NOT EXISTS user_role_definitions (
				id TEXT UNIQUE NOT NULL,
				name TEXT NOT NULL,
				permissions INTEGER NOT NULL,
				created INTEGER NOT NULL,
				PRIMARY KEY (id)
			)
			""");
			statement.addBatch("""
			CREATE TABLE IF NOT EXISTS user_role_integration_discord (
				role_id TEXT UNIQUE NOT NULL,
				discord_role_id TEXT NOT NULL,
				permissions INTEGER NOT NULL,
				PRIMARY KEY (role_id)
			)
			""");
			statement.addBatch("""
			CREATE TABLE IF NOT EXISTS user_roles (
				role_id TEXT NOT NULL,
				user_id TEXT NOT NULL,
				FOREIGN KEY (role_id) REFERENCES user_role_definitions(id) ON UPDATE CASCADE ON DELETE CASCADE,
				FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE
			)
			""");
			statement.addBatch("""
			CREATE UNIQUE INDEX idx_user_roles_two_ids ON user_roles(role_id, user_id)
			""");
			statement.addBatch("""
			CREATE TRIGGER user_role_trigger_insert INSERT ON user_roles BEGIN
				UPDATE users SET permissions = permissions | role_permissions FROM (
					SELECT permissions AS role_permissions FROM user_role_definitions WHERE id = NEW.role_id
				) WHERE id == NEW.user_id;
			END
			""");
			statement.addBatch("""
			CREATE TRIGGER user_role_trigger_delete DELETE ON user_roles BEGIN
				UPDATE users SET permissions = permissions & ~role_permissions FROM (
					SELECT permissions AS role_permissions FROM user_role_definitions WHERE id = OLD.role_id
				) WHERE id == OLD.user_id;

				-- Recalculate entire set of roles to ensure the permissions we removed aren't present elsewhere
				UPDATE users SET permissions = permissions | role_permissions FROM (
					SELECT permissions AS role_permissions FROM user_role_definitions
						INNER JOIN user_roles ON user_roles.role_id == user_role_definitions.id
					WHERE role_id != OLD.role_id AND user_id == OLD.user_id
				) WHERE id = OLD.user_id;
			END
			""");
			statement.addBatch("""
			CREATE TABLE IF NOT EXISTS user_bios (
				user_id TEXT UNIQUE NOT NULL,
				display_name TEXT,
				pronouns TEXT,
				description TEXT,
				avatar_url TEXT,
				FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE,
				PRIMARY KEY (user_id)
			)
			""");
			statement.addBatch("""
			CREATE TABLE IF NOT EXISTS user_bio_fields (
				user_id TEXT NOT NULL,
				field_name TEXT NOT NULL,
				field_value TEXT NOT NULL,
				FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE
			)
			""");
			statement.addBatch("""
			CREATE UNIQUE INDEX idx_user_id_field_name ON user_bio_fields(user_id, field_name)
			""");
			statement.addBatch("""
			CREATE TABLE IF NOT EXISTS api_keys (
				uuid BLOB NOT NULL,
				user_id TEXT NOT NULL,
				hash TEXT NOT NULL,
				expires INTEGER NOT NULL,
				name TEXT NOT NULL,
				FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE,
				PRIMARY KEY (uuid)
			)
			""");
			statement.addBatch("""
			CREATE TABLE IF NOT EXISTS api_key_scopes (
				uuid BLOB NOT NULL,
				scope TEXT NOT NULL CHECK (scope in ('PROJECT', 'USER')),
				project_id TEXT,
				permissions INTEGER NOT NULL,
				FOREIGN KEY (project_id) REFERENCES projects(id) ON UPDATE CASCADE ON DELETE CASCADE,
				FOREIGN KEY (uuid) REFERENCES api_keys(uuid) ON UPDATE CASCADE ON DELETE CASCADE,
				PRIMARY KEY (uuid)
			)
			""");
			statement.addBatch("""
			CREATE TABLE IF NOT EXISTS passwords (
				user_id TEXT NOT NULL,
				hash TEXT NOT NULL,
				last_updated INTEGER NOT NULL,
				FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE,
				PRIMARY KEY (user_id)
			)
			""");
			statement.addBatch("""
			CREATE TABLE IF NOT EXISTS user_integration_modrinth (
				user_id TEXT NOT NULL,
				modrinth_id TEXT NOT NULL,
				FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE,
				PRIMARY KEY (user_id)
			)
			""");
			statement.addBatch("""
			CREATE TABLE IF NOT EXISTS user_integration_discord (
				user_id TEXT NOT NULL,
				discord_id TEXT NOT NULL,
				FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE,
				PRIMARY KEY (user_id)
			)
			""");
			statement.addBatch("""
			CREATE TABLE IF NOT EXISTS user_integration_minecraft (
				user_id TEXT NOT NULL,
				uuid TEXT UNIQUE NOT NULL,
				FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE,
				PRIMARY KEY (uuid)
			)
			""");
			statement.addBatch("""
			CREATE TABLE IF NOT EXISTS events (
				id TEXT UNIQUE NOT NULL,
				slug TEXT UNIQUE NOT NULL,
				genre_slug TEXT NOT NULL,
				PRIMARY KEY (id)
			)
			""");
			statement.addBatch("""
			CREATE TABLE IF NOT EXISTS event_metadata (
				id TEXT UNIQUE NOT NULL,
				name TEXT NOT NULL,
				description TEXT,
				FOREIGN KEY (id) REFERENCES events(id) ON UPDATE CASCADE ON DELETE CASCADE,
				PRIMARY KEY (id)
			)
			""");
			statement.addBatch("""
			CREATE TABLE IF NOT EXISTS event_times (
				id TEXT UNIQUE NOT NULL,
				registration_open TEXT NOT NULL,
				registration_close TEXT NOT NULL,
				development_start TEXT NOT NULL,
				development_end TEXT NOT NULL,
				pack_freeze TEXT NOT NULL,
				FOREIGN KEY (id) REFERENCES events(id) ON UPDATE CASCADE ON DELETE CASCADE,
				PRIMARY KEY (id)
			)
			""");
			statement.addBatch("""
			CREATE TABLE IF NOT EXISTS event_roles (
				id TEXT UNIQUE NOT NULL,
				participant TEXT,
				theme_award TEXT,
				team_pick_award TEXT,
				FOREIGN KEY (id) REFERENCES events(id) ON UPDATE CASCADE ON DELETE CASCADE,
				FOREIGN KEY (participant, theme_award, team_pick_award) REFERENCES user_role_definitions(id) ON UPDATE CASCADE,
				PRIMARY KEY (id)
			)
			""");
			statement.addBatch("""
			CREATE TABLE IF NOT EXISTS event_platform_minecraft (
				id TEXT UNIQUE NOT NULL,
				mod_loader TEXT NOT NULL,
				game_version TEXT NOT NULL,
				FOREIGN KEY (id) REFERENCES events(id) ON UPDATE CASCADE ON DELETE CASCADE,
				PRIMARY KEY (id)
			)
			""");
			statement.addBatch("""
			CREATE TABLE IF NOT EXISTS projects (
				id TEXT UNIQUE NOT NULL,
				PRIMARY KEY (id)
			)
			""");
			statement.addBatch("""
				CREATE TABLE IF NOT EXISTS project_none_metadata (
					project_id TEXT UNIQUE NOT NULL,
					name TEXT NOT NULL,
					FOREIGN KEY (project_id) REFERENCES projects(id) ON UPDATE CASCADE ON DELETE CASCADE,
					PRIMARY KEY (project_id)
				)
			""");
			statement.addBatch("""
				CREATE TABLE IF NOT EXISTS project_mod_metadata (
					project_id TEXT UNIQUE NOT NULL,
					mod_id TEXT NOT NULL,
					name TEXT NOT NULL,
					description TEXT,
					source_url TEXT NOT NULL,
					FOREIGN KEY (project_id) REFERENCES projects(id) ON UPDATE CASCADE ON DELETE CASCADE,
					PRIMARY KEY (project_id)
				)
			""");
			statement.addBatch("""
			CREATE TABLE IF NOT EXISTS project_roles (
				project_id TEXT NOT NULL,
				user_id TEXT NOT NULL,
				permissions INTEGER NOT NULL DEFAULT 0,
				role_name TEXT NOT NULL DEFAULT 'Member',
				FOREIGN KEY (project_id) REFERENCES projects(id) ON UPDATE CASCADE ON DELETE CASCADE,
				FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE
			)
			""");
			// This ensures that users cannot be listed twice on the same project
			statement.addBatch("""
			CREATE UNIQUE INDEX idx_project_roles_two_ids ON project_roles(project_id, user_id)
			""");
			statement.addBatch("""
			CREATE TABLE IF NOT EXISTS submissions (
				id TEXT UNIQUE NOT NULL,
				event_id TEXT NOT NULL,
				project_id TEXT NOT NULL,
				submitted INTEGER NOT NULL,
				FOREIGN KEY (project_id) REFERENCES projects(id) ON UPDATE CASCADE ON DELETE CASCADE,
				FOREIGN KEY (event_id) REFERENCES events(id) ON UPDATE CASCADE ON DELETE CASCADE,
				PRIMARY KEY(id)
			)
			""");
			statement.addBatch("""
			CREATE TABLE IF NOT EXISTS submission_type_modrinth (
				submission_id TEXT NOT NULL,
				modrinth_id TEXT NOT NULL,
				version_id TEXT NOT NULL,
				FOREIGN KEY (submission_id) REFERENCES submissions(id) ON UPDATE CASCADE ON DELETE CASCADE,
				PRIMARY KEY (submission_id)
			)
			""");
			statement.addBatch("""
			CREATE TABLE IF NOT EXISTS awards (
				id TEXT UNIQUE NOT NULL,
				slug TEXT UNIQUE NOT NULL,
				display_name TEXT NOT NULL,
				sprite TEXT NOT NULL,
				discord_emote TEXT NOT NULL,
				tooltip TEXT,
				tier TEXT NOT NULL CHECK (tier in ('COMMON', 'UNCOMMON', 'RARE', 'LEGENDARY')),
				PRIMARY KEY (id)
			)
			""");
			statement.addBatch("""
			CREATE TABLE IF NOT EXISTS award_instances (
				award_id TEXT UNIQUE NOT NULL,
				awarded_to TEXT NOT NULL,
				custom_data TEXT,
				submission_id TEXT,
				tier_override TEXT CHECK (tier_override in ('COMMON', 'UNCOMMON', 'RARE', 'LEGENDARY')),
				FOREIGN KEY (award_id) REFERENCES awards(id) ON UPDATE CASCADE ON DELETE CASCADE,
				FOREIGN KEY (awarded_to) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE,
				FOREIGN KEY (submission_id) REFERENCES submissions(id) ON UPDATE CASCADE ON DELETE CASCADE,
				PRIMARY KEY (award_id, awarded_to)
			)
			""");
			statement.addBatch("""
			CREATE TABLE IF NOT EXISTS link_codes (
				code TEXT NOT NULL,
				account_id TEXT NOT NULL,
				service TEXT NOT NULL,
				expires INTEGER NOT NULL,
				PRIMARY KEY (code)
			)
			""");
			statement.addBatch("""
			CREATE TABLE IF NOT EXISTS team_invites (
				code TEXT NOT NULL,
				project_id TEXT NOT NULL,
				user_id TEXT NOT NULL,
				expires INTEGER NOT NULL,
				role TEXT NOT NULL DEFAULT 'Member',
				permissions INTEGER NOT NULL DEFAULT 0,
				FOREIGN KEY (project_id) REFERENCES projects(id) ON UPDATE CASCADE ON DELETE CASCADE,
				FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE,
				PRIMARY KEY (code)
			)
			""");

			statement.executeBatch();
		} catch (SQLException ex) {
			LOG.error("Failed to create database tables. ", ex);
			return;
		}
		LOG.debug("Created database tables.");

		if ("development".equals(DOTENV.get("env"))) {
			DevelopmentModeData.insertDevelopmentModeData();
		}
	}

	private static void updateSchemaVersion() {
		try (Connection connection = createDatabaseConnection();
			 Statement statement = connection.createStatement()) {
			statement.addBatch("CREATE TABLE IF NOT EXISTS schema (version INTEGER NOT NULL, PRIMARY KEY(version))");
			statement.addBatch("DELETE FROM schema");
			statement.executeBatch();
			try (PreparedStatement prepared = connection.prepareStatement("INSERT INTO schema VALUES (?)")) {
				prepared.setInt(1, DatabaseFixer.getSchemaVersion());
				prepared.execute();
			}
		} catch (SQLException ex) {
			LOG.error("Failed to update database schema version. ", ex);
			return;
		}
		LOG.debug("Updated database schema version.");
	}

	private static <T> void registerCodec(Class<T> type, Codec<T> codec) {
		CODEC_REGISTRY.put(type, codec);
	}

	private static JsonMapper createDFUMapper() {
		// Primitives
		registerCodec(String.class, Codec.STRING);

		return new JsonMapper() {
			private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

			@Override
			public @NotNull String toJsonString(@NotNull Object obj, @NotNull Type type) {
				if (obj instanceof JsonElement)
					return GSON.toJson(obj);

				if (obj instanceof Collection<?> collection) {
					if (collection.isEmpty()) {
						return "[]";
					} else {
						StringBuilder builder = new StringBuilder().append('[');

						int i = 0;
						for (Object inner : collection) {
							if (i > 0) {
								builder.append(',');
							}

							builder.append(this.toJsonString(inner, inner.getClass()));
							i++;
						}

						builder.append(']');

						return builder.toString();
					}
				}

				if (!CODEC_REGISTRY.containsKey(type))
					throw new UnsupportedOperationException("Cannot encode object type " + type);
				//noinspection unchecked
				return ((Codec<Object>)CODEC_REGISTRY.get(type))
						.encodeStart(JsonOps.INSTANCE, obj)
						.getOrThrow()
						.toString();
			}

			@SuppressWarnings("unchecked")
			@Override
			public @NotNull <T> T fromJsonString(@NotNull String json, @NotNull Type type) {
				if (!CODEC_REGISTRY.containsKey(type))
					throw new UnsupportedOperationException("Cannot decode object type " + type);
				return (T) CODEC_REGISTRY.get(type).decode(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow().getFirst();
			}

			@SuppressWarnings("unchecked")
			@Override
			public @NotNull <T> T fromJsonStream(@NotNull InputStream json, @NotNull Type type) {
				if (!CODEC_REGISTRY.containsKey(type))
					throw new UnsupportedOperationException("Cannot decode object type " + type);
				try (InputStreamReader reader = new InputStreamReader(json)) {
					return (T) CODEC_REGISTRY.get(type).decode(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow().getFirst();
				} catch (IOException ex) {
					throw new UnsupportedOperationException("Failed to handle JSON input stream.", ex);
				}
			}
		};
	}
}
