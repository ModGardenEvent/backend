package net.modgarden.backend;

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
import net.modgarden.backend.data.BackendError;
import net.modgarden.backend.data.DevelopmentModeData;
import net.modgarden.backend.data.Landing;
import net.modgarden.backend.data.award.Award;
import net.modgarden.backend.data.award.AwardInstance;
import net.modgarden.backend.data.event.Event;
import net.modgarden.backend.data.event.Project;
import net.modgarden.backend.data.event.Submission;
import net.modgarden.backend.data.fixer.DatabaseFixer;
import net.modgarden.backend.data.user.User;
import net.modgarden.backend.database.function.GenerateNaturalIdFunction;
import net.modgarden.backend.database.function.HasPermissionsFunction;
import net.modgarden.backend.database.function.UnixMillisFunction;
import net.modgarden.backend.endpoint.Endpoint;
import net.modgarden.backend.endpoint.v2.auth.DeleteKeyEndpoint;
import net.modgarden.backend.endpoint.v2.auth.GenerateKeyEndpoint;
import net.modgarden.backend.endpoint.v2.auth.ListKeysEndpoint;
import net.modgarden.backend.endpoint.v2.project.CreateProjectEndpoint;
import net.modgarden.backend.endpoint.v2.project.DeleteProjectEndpoint;
import net.modgarden.backend.endpoint.v2.submission.GetSubmissionByIdEndpoint;
import net.modgarden.backend.endpoint.v2.event.GetSubmissionByModIdEndpoint;
import net.modgarden.backend.endpoint.v2.project.GetProjectByIdEndpoint;
import net.modgarden.backend.endpoint.v2.project.GetProjectByModIdEndpoint;
import net.modgarden.backend.endpoint.v2.project.member.AddMemberEndpoint;
import net.modgarden.backend.endpoint.v2.project.member.RemoveMemberEndpoint;
import net.modgarden.backend.endpoint.v2.project.member.SetPermissionsEndpoint;
import net.modgarden.backend.endpoint.v2.project.member.SetRoleEndpoint;
import net.modgarden.backend.endpoint.v2.submission.DeleteSubmissionEndpoint;
import net.modgarden.backend.util.AuthUtil;
import net.modgarden.backend.util.ReadableOrderCodec;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.http.HttpClient;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;

public class ModGardenBackend {
	public static final Dotenv DOTENV = Dotenv.load();

	public static final String URL = "development".equals(DOTENV.get("env")) ? "http://localhost:7070" : "https://api.modgarden.net";
	public static final Logger LOG = LoggerFactory.getLogger(ModGardenBackend.class);

	private static final Map<Type, Codec<?>> CODEC_REGISTRY = new HashMap<>();

	public static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

	private static ModGardenBackend backend;

	private final Javalin app;

	private ModGardenBackend(Javalin app) {
		this.app = app;
	}

	public static void main(String[] args) {
		if ("development".equals(DOTENV.get("env")))
			((ch.qos.logback.classic.Logger)LOG).setLevel(Level.DEBUG);

		Landing.createInstance();

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

		registerCodec(Landing.class, Landing.CODEC);
		registerCodec(BackendError.class, BackendError.CODEC);
		registerCodec(Award.class, Award.DIRECT_CODEC);
		registerCodec(Event.class, Event.DIRECT_CODEC);
		registerCodec(Project.class, Project.DIRECT_CODEC);
		registerCodec(Submission.class, Submission.DIRECT_CODEC);
		registerCodec(User.class, User.DIRECT_CODEC);
		registerCodec(AwardInstance.FullAwardData.class, AwardInstance.FullAwardData.CODEC);
		registerCodec(GenerateKeyEndpoint.Response.class, GenerateKeyEndpoint.Response.CODEC);
		registerCodec(ListKeysEndpoint.Response.class, ListKeysEndpoint.Response.CODEC);

		AuthUtil.clearTokensEachFifteenMinutes();

		Javalin app = Javalin.create(config -> config.jsonMapper(createDFUMapper()));
		app.get("", Landing::getLandingJson);
		backend = new ModGardenBackend(app);

		backend.v2();

		app.error(400, BackendError::handleError);
		app.error(401, BackendError::handleError);
		app.error(403, BackendError::handleError);
		app.error(404, BackendError::handleError);
		app.error(422, BackendError::handleError);
		app.error(500, BackendError::handleError);
		app.start(7070);

		LOG.info("Mod Garden Backend Started!");
	}

	public void v2() {
		post(GenerateKeyEndpoint::new);
		delete(DeleteKeyEndpoint::new);
		get(ListKeysEndpoint::new);

		post(CreateProjectEndpoint::new);
		put(AddMemberEndpoint::new);
		put(SetPermissionsEndpoint::new);
		put(SetRoleEndpoint::new);
		delete(DeleteProjectEndpoint::new);
		delete(RemoveMemberEndpoint::new);
		get(GetProjectByIdEndpoint::new);
		get(GetProjectByModIdEndpoint::new);

		delete(DeleteSubmissionEndpoint::new);
		get(GetSubmissionByIdEndpoint::new);
		get(GetSubmissionByModIdEndpoint::new);
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
				created INTEGER NOT NULL
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
			CREATE TRIGGER user_role_trigger INSERT ON user_roles BEGIN
				UPDATE users SET permissions = permissions | role_permissions FROM (
					SELECT permissions AS role_permissions FROM user_role_definitions WHERE id = NEW.role_id
				);
			END
			""");
			statement.addBatch("""
			CREATE TRIGGER user_role_trigger DELETE ON user_roles BEGIN
				UPDATE users SET permissions = permissions & ~role_permissions FROM (
					SELECT permissions AS role_permissions FROM user_role_definitions WHERE id = OLD.role_id
				);
			END
			""");
			statement.addBatch("""
			CREATE TABLE IF NOT EXISTS user_bios (
				user_id TEXT UNIQUE NOT NULL,
				display_name TEXT NOT NULL,
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
			CREATE UNIQUE INDEX idx_user_id_field_name ON user_bio_fields(field_name, field_value)
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
				uuid TEXT UNIQUE NOT NULL,
				user_id TEXT NOT NULL,
				FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE,
				PRIMARY KEY (uuid)
			)
			""");
			statement.addBatch("""
			CREATE TABLE IF NOT EXISTS events (
				id TEXT UNIQUE NOT NULL,
				slug TEXT UNIQUE NOT NULL,
				event_type_slug TEXT NOT NULL,
				display_name TEXT NOT NULL,
				minecraft_version TEXT NOT NULL,
				loader TEXT NOT NULL,
				registration_open_time INTEGER NOT NULL,
				registration_close_time INTEGER NOT NULL,
				start_time INTEGER NOT NULL,
				end_time INTEGER NOT NULL,
				freeze_time INTEGER NOT NULL,
				PRIMARY KEY (id)
			)
			""");
			statement.addBatch("""
			CREATE TABLE IF NOT EXISTS event_integration_discord (
				id TEXT UNIQUE NOT NULL,
				role_id TEXT NOT NULL,
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
				CREATE TABLE IF NOT EXISTS project_draft_metadata (
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
				event TEXT NOT NULL,
				project_id TEXT NOT NULL,
				submitted INTEGER NOT NULL,
				FOREIGN KEY (project_id) REFERENCES projects(id) ON UPDATE CASCADE ON DELETE CASCADE,
				FOREIGN KEY (event) REFERENCES events(id) ON UPDATE CASCADE ON DELETE CASCADE,
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

	private static void registerCodec(Type type, Codec<?> codec) {
		CODEC_REGISTRY.put(type, new ReadableOrderCodec<>(codec));
	}

	private static JsonMapper createDFUMapper() {
		return new JsonMapper() {
			private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

			@SuppressWarnings("unchecked")
			@Override
			public @NotNull String toJsonString(@NotNull Object obj, @NotNull Type type) {
				if (obj instanceof JsonElement)
					return GSON.toJson(obj);
				if (!CODEC_REGISTRY.containsKey(type))
					throw new UnsupportedOperationException("Cannot encode object type " + type);
				return ((Codec<Object>)CODEC_REGISTRY.get(type)).encodeStart(JsonOps.INSTANCE, obj).getOrThrow().toString();
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
