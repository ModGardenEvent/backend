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
import io.javalin.http.Handler;
import io.javalin.json.JsonMapper;
import net.modgarden.backend.data.BackendError;
import net.modgarden.backend.data.DevelopmentModeData;
import net.modgarden.backend.data.Landing;
import net.modgarden.backend.data.NaturalId;
import net.modgarden.backend.data.award.Award;
import net.modgarden.backend.data.award.AwardInstance;
import net.modgarden.backend.data.event.Event;
import net.modgarden.backend.data.event.Project;
import net.modgarden.backend.data.event.Submission;
import net.modgarden.backend.data.fixer.DatabaseFixer;
import net.modgarden.backend.data.profile.MinecraftAccount;
import net.modgarden.backend.data.profile.User;
import net.modgarden.backend.endpoint.Endpoint;
import net.modgarden.backend.endpoint.v2.auth.GenerateKeyEndpoint;
import net.modgarden.backend.handler.v1.discord.*;
import net.modgarden.backend.handler.v1.RegistrationHandler;
import net.modgarden.backend.util.AuthUtil;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.Function;

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
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ModGardenBackend {
	public static final Dotenv DOTENV = Dotenv.load();

	public static final String URL = "development".equals(DOTENV.get("env")) ? "http://localhost:7070" : "https://api.modgarden.net";
	public static final Logger LOG = LoggerFactory.getLogger(ModGardenBackend.class);

	private static final Map<Type, Codec<?>> CODEC_REGISTRY = new HashMap<>();

	public static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

	public static final String SAFE_URL_REGEX = "[a-zA-Z0-9!@$()`.+,_\"-]+";

	private static ModGardenBackend backend;

	private final Javalin app;

	private ModGardenBackend(Javalin app) {
		this.app = app;
	}

	public static void main(String[] args) {
		if ("development".equals(DOTENV.get("env")))
			((ch.qos.logback.classic.Logger)LOG).setLevel(Level.DEBUG);

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

		CODEC_REGISTRY.put(Landing.class, Landing.CODEC);
		CODEC_REGISTRY.put(BackendError.class, BackendError.CODEC);
		CODEC_REGISTRY.put(Award.class, Award.DIRECT_CODEC);
		CODEC_REGISTRY.put(Event.class, Event.DIRECT_CODEC);
		CODEC_REGISTRY.put(MinecraftAccount.class, MinecraftAccount.CODEC);
		CODEC_REGISTRY.put(Project.class, Project.DIRECT_CODEC);
		CODEC_REGISTRY.put(Submission.class, Submission.DIRECT_CODEC);
		CODEC_REGISTRY.put(User.class, User.DIRECT_CODEC);
		CODEC_REGISTRY.put(AwardInstance.FullAwardData.class, AwardInstance.FullAwardData.CODEC);

		CODEC_REGISTRY.put(RegistrationHandler.Body.class, RegistrationHandler.Body.CODEC);
		CODEC_REGISTRY.put(DiscordBotLinkHandler.Body.class, DiscordBotLinkHandler.Body.CODEC);
		CODEC_REGISTRY.put(DiscordBotProfileHandler.PostBody.class, DiscordBotProfileHandler.PostBody.CODEC);
		CODEC_REGISTRY.put(DiscordBotProfileHandler.DeleteBody.class, DiscordBotProfileHandler.DeleteBody.CODEC);
		CODEC_REGISTRY.put(DiscordBotUnlinkHandler.Body.class, DiscordBotUnlinkHandler.Body.CODEC);

		CODEC_REGISTRY.put(DiscordBotTeamManagementHandler.InviteBody.class, DiscordBotTeamManagementHandler.InviteBody.CODEC);
		CODEC_REGISTRY.put(DiscordBotTeamManagementHandler.AcceptInviteBody.class, DiscordBotTeamManagementHandler.AcceptInviteBody.CODEC);
		CODEC_REGISTRY.put(DiscordBotTeamManagementHandler.DeclineInviteBody.class, DiscordBotTeamManagementHandler.DeclineInviteBody.CODEC);
		CODEC_REGISTRY.put(DiscordBotTeamManagementHandler.RemoveMemberBody.class, DiscordBotTeamManagementHandler.RemoveMemberBody.CODEC);

		Landing.createInstance();
		AuthUtil.clearTokensEachFifteenMinutes();
		DiscordBotTeamManagementHandler.clearInvitesEachDay();

		Javalin app = Javalin.create(config -> config.jsonMapper(createDFUMapper()));
		app.get("", Landing::getLandingJson);
		backend = new ModGardenBackend(app);

		backend.v1();

		app.error(400, BackendError::handleError);
		app.error(401, BackendError::handleError);
		app.error(404, BackendError::handleError);
		app.error(422, BackendError::handleError);
		app.error(500, BackendError::handleError);
		app.start(7070);

		LOG.info("Mod Garden Backend Started!");
	}

	public void v1() {
		get1("award/{award}", Award::getAwardType);

		get1("event/{event}", Event::getEvent);
		get1("event/{event}/submissions", Submission::getSubmissionsByEvent);

		get1("events", Event::getEvents);
		get1("events/current/registration", Event::getCurrentRegistrationEvent);
		get1("events/current/development", Event::getCurrentDevelopmentEvent);
		get1("events/current/prefreeze", Event::getCurrentPreFreezeEvent);
		get1("events/active", Event::getActiveEvents);

		get1("mcaccount/{mcaccount}", MinecraftAccount::getAccount);

		get1("project/{project}", Project::getProject);

		get1("submission/{submission}", Submission::getSubmission);

		get1("user/{user}", User::getUser);
		get1("user/{user}/projects", Project::getProjectsByUser);
		get1("user/{user}/submissions", Submission::getSubmissionsByUser);
		get1("user/{user}/submissions/{event}", Submission::getSubmissionsByUserAndEvent);
		get1("user/{user}/awards", Award::getAwardsByUser);

		post1("discord/register", RegistrationHandler::discordBotRegister);

		get1("discord/oauth/modrinth", DiscordBotOAuthHandler::authModrinthAccount);
		get1("discord/oauth/minecraft", DiscordBotOAuthHandler::authMinecraftAccount);
		get1("discord/oauth/minecraft/challenge", DiscordBotOAuthHandler::getMicrosoftCodeChallenge);

		post1("discord/submission/create/modrinth", DiscordBotSubmissionHandler::submitModrinth);
		post1("discord/submission/modify/version/modrinth", DiscordBotSubmissionHandler::setVersionModrinth);
		post1("discord/submission/delete", DiscordBotSubmissionHandler::unsubmit);

		post1("discord/link", DiscordBotLinkHandler::link);
		post1("discord/unlink", DiscordBotUnlinkHandler::unlink);

		post1("discord/modify/username", DiscordBotProfileHandler::modifyUsername);
		post1("discord/modify/displayname", DiscordBotProfileHandler::modifyDisplayName);
		post1("discord/modify/pronouns", DiscordBotProfileHandler::modifyPronouns);
		post1("discord/modify/avatar", DiscordBotProfileHandler::modifyAvatarUrl);

		post1("discord/remove/pronouns", DiscordBotProfileHandler::removePronouns);
		post1("discord/remove/avatar", DiscordBotProfileHandler::removeAvatarUrl);

		post1("discord/project/user/invite", DiscordBotTeamManagementHandler::sendInvite);
		post1("discord/project/user/accept", DiscordBotTeamManagementHandler::acceptInvite);
		post1("discord/project/user/decline", DiscordBotTeamManagementHandler::declineInvite);
		post1("discord/project/user/remove", DiscordBotTeamManagementHandler::removeMember);
	}

	public void v2() {
		post2(GenerateKeyEndpoint::new);
	}

	private void get1(String endpoint, Handler consumer) {
		this.app.get("/v1/" + endpoint, consumer);
	}

	private void post1(String endpoint, Handler consumer) {
		this.app.post("/v1/" + endpoint, consumer);
	}

	private void get2(Supplier<Endpoint> endpointSupplier) {
		Endpoint endpoint = endpointSupplier.get();
		this.app.get("/v2/" + endpoint.getPath(), endpoint);
	}

	private void post2(Supplier<Endpoint> endpointSupplier) {
		Endpoint endpoint = endpointSupplier.get();
		this.app.post("/v2/" + endpoint.getPath(), endpoint);
	}

	private void put2(Supplier<Endpoint> endpointSupplier) {
		Endpoint endpoint = endpointSupplier.get();
		this.app.put("/v2/" + endpoint.getPath(), endpoint);
	}

	public static Connection createDatabaseConnection() throws SQLException {
		String url = "jdbc:sqlite:database.db";
		return DriverManager.getConnection(url);
	}

	private static void createDatabaseContents() {
		try (Connection connection = createDatabaseConnection();
			 Statement statement = connection.createStatement()) {
			statement.addBatch("""
			CREATE TABLE IF NOT EXISTS users (
				id TEXT UNIQUE NOT NULL,
				username TEXT UNIQUE NOT NULL,
				display_name TEXT NOT NULL,
				pronouns TEXT,
				avatar_url TEXT,
				created INTEGER NOT NULL,
				permissions INTEGER NOT NULL,
				PRIMARY KEY(id)
			)
			""");
			statement.addBatch("""
			CREATE TABLE IF NOT EXISTS events (
				id TEXT UNIQUE NOT NULL,
				slug TEXT UNIQUE NOT NULL,
				event_type_slug TEXT NOT NULL,
				display_name TEXT NOT NULL,
				discord_role_id TEXT,
				minecraft_version TEXT NOT NULL,
				loader TEXT NOT NULL,
				registration_time INTEGER NOT NULL,
				start_time INTEGER NOT NULL,
				end_time INTEGER NOT NULL,
				freeze_time INTEGER NOT NULL,
				PRIMARY KEY (id)
			)
			""");
			statement.addBatch("""
			CREATE TABLE IF NOT EXISTS projects (
				id TEXT UNIQUE NOT NULL,
				slug TEXT UNIQUE NOT NULL,
				PRIMARY KEY (id)
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
			CREATE TABLE IF NOT EXISTS minecraft_accounts (
				uuid TEXT UNIQUE NOT NULL,
				user_id TEXT NOT NULL,
				FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE,
				PRIMARY KEY (uuid)
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
				role TEXT NOT NULL CHECK (role IN ('author', 'builder')),
				FOREIGN KEY (project_id) REFERENCES projects(id) ON UPDATE CASCADE ON DELETE CASCADE,
				FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE,
				PRIMARY KEY (code)
			)
			""");
			statement.addBatch("""
			CREATE TABLE IF NOT EXISTS api_keys (
				uuid BLOB NOT NULL,
				user_id TEXT NOT NULL,
				salt BLOB NOT NULL,
				hash BLOB NOT NULL,
				expires INTEGER NOT NULL,
				FOREIGN KEY (user_id) REFERENCES users(id),
				PRIMARY KEY (uuid)
			)
			""");
			statement.addBatch("""
			CREATE TABLE IF NOT EXISTS api_key_scopes (
				uuid BLOB NOT NULL,
				scope TEXT CHECK (scope in ('PROJECT', 'USER')),
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
				salt BLOB NOT NULL,
				hash BLOB NOT NULL,
				last_updated INTEGER NOT NULL,
				FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE,
				PRIMARY KEY (user_id)
			)
			""");
			statement.addBatch("""
			CREATE TABLE IF NOT EXISTS integration_modrinth (
				user_id TEXT NOT NULL,
				modrinth_id TEXT NOT NULL,
				FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE,
				PRIMARY KEY (user_id)
			)
			""");
			statement.addBatch("""
			CREATE TABLE IF NOT EXISTS integration_discord (
				user_id TEXT NOT NULL,
				discord_id TEXT NOT NULL,
				FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE,
				PRIMARY KEY (user_id)
			)
			""");
			Function.create(
					connection, "generate_natural_id", new Function() {
						@Override
						protected void xFunc() throws SQLException {
							String table = this.value_text(0);
							String key = this .value_text(1);
							int length = this.value_int(2);
							this.result(NaturalId.generate(table, key, length));
						}
					}
			);
			Function.create(
					connection, "generate_natural_id_from_number", new Function() {
						@Override
						protected void xFunc() throws SQLException {
							int number = this.value_int(0);
							int length = this.value_int(1);
							this.result(NaturalId.generateFromNumber(number, length));
						}
					}
			);
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
