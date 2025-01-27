package net.modgarden.backend;

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
import net.modgarden.backend.data.Landing;
import net.modgarden.backend.data.award.Award;
import net.modgarden.backend.data.event.Event;
import net.modgarden.backend.data.event.Project;
import net.modgarden.backend.data.event.Submission;
import net.modgarden.backend.data.profile.MinecraftAccount;
import net.modgarden.backend.data.profile.User;
import net.modgarden.backend.handler.discord.DiscordLinkHandler;
import net.modgarden.backend.handler.discord.ModrinthDiscordLinkHandler;
import net.modgarden.backend.handler.RegistrationHandler;
import net.modgarden.backend.util.AuthUtil;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public class ModGardenBackend {
    public static final String URL = "https://api.modgarden.net";
	public static final Logger LOG = LoggerFactory.getLogger("Mod Garden Backend");
    private static final Map<Type, Codec<?>> CODEC_REGISTRY = new HashMap<>();

	public static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

	public static final Dotenv DOTENV = Dotenv.load();

    public static final String SAFE_URL_REGEX = "[a-zA-Z0-9!@$()`.+,_\"-]+";;
    private static final int DATABASE_SCHEMA_VERSION = 1;

    public static void main(String[] args) {
        try {
            if (new File("./database.db").createNewFile()) {
                LOG.info("Successfuly created database file.");
            }
            createDatabaseContents();
            updateSchemaVersion();
        } catch (IOException ex) {
            LOG.error("Failed to create database file.", ex);
        }

		CODEC_REGISTRY.put(Landing.class, Landing.CODEC);
        CODEC_REGISTRY.put(BackendError.class, BackendError.CODEC);
        CODEC_REGISTRY.put(Award.class, Award.CODEC);
        CODEC_REGISTRY.put(Event.class, Event.CODEC);
        CODEC_REGISTRY.put(MinecraftAccount.class, MinecraftAccount.CODEC);
        CODEC_REGISTRY.put(Project.class, Project.CODEC);
        CODEC_REGISTRY.put(Submission.class, Submission.CODEC);
        CODEC_REGISTRY.put(User.class, User.CODEC);

        Landing.createInstance();
        AuthUtil.clearTokensEachFifteenMinutes();

		Javalin app = Javalin.create(config -> config.jsonMapper(createDFUMapper()));
		app.get("", Landing::getLandingJson);
        app.get("/award/{award}", Award::getAwardType);
        app.get("/event/{event}", Event::getEvent);
        app.get("/mcaccount/{mcaccount}", MinecraftAccount::getAccount);
        app.get("/project/{project}", Project::getProject);
        app.get("/submission/{submission}", Submission::getSubmission);
        app.get("/user/{user}", User::getUser);

        app.get("/link/discord/modrinth", ModrinthDiscordLinkHandler::authModrinthAccount);
        app.post("/link/discord", DiscordLinkHandler::link);

        app.post("/register/discord", RegistrationHandler::registerThroughDiscordBot);

        app.error(400, BackendError::handleError);
        app.error(401, BackendError::handleError);
        app.error(404, BackendError::handleError);
        app.error(422, BackendError::handleError);
        app.error(500, BackendError::handleError);
		app.start(7070);
		LOG.info("Mod Garden Backend Started!");
    }

    public static Connection createDatabaseConnection() throws SQLException {
        String url = "jdbc:sqlite:database.db";
        return DriverManager.getConnection(url);
    }

    private static void createDatabaseContents() {
        try (Connection connection = createDatabaseConnection();
             Statement statement = connection.createStatement()) {
            statement.addBatch("CREATE TABLE IF NOT EXISTS users (" +
                        "id TEXT UNIQUE NOT NULL," +
                        "username TEXT UNIQUE NOT NULL," +
                        "display_name TEXT NOT NULL," +
                        "discord_id TEXT UNIQUE NOT NULL," +
                        "modrinth_id TEXT UNIQUE," +
                        "created INTEGER NOT NULL," +
                        "PRIMARY KEY(id)" +
                    ")");
            statement.addBatch("CREATE TABLE IF NOT EXISTS events (" +
                        "id TEXT UNIQUE NOT NULL," +
                        "slug TEXT UNIQUE NOT NULL," +
                        "display_name TEXT NOT NULL," +
                        "minecraft_version TEXT NOT NULL," +
                        "loader TEXT NOT NULL," +
                        "loader_version TEXT NOT NULL," +
                        "started INTEGER NOT NULL," +
                        "PRIMARY KEY (id)" +
                    ")");
            statement.addBatch("CREATE TABLE IF NOT EXISTS projects (" +
                        "id TEXT UNIQUE NOT NULL," +
                        "slug TEXT UNIQUE NOT NULL," +
                        "modrinth_id TEXT UNIQUE NOT NULL," +
                        "attributed_to TEXT NOT NULL," +
                        "FOREIGN KEY (attributed_to) REFERENCES users(id)," +
                        "PRIMARY KEY (id)" +
                    ")");
            statement.addBatch("CREATE TABLE IF NOT EXISTS project_authors (" +
                        "project_id TEXT NOT NULL," +
                        "user_id TEXT NOT NULL," +
                        "FOREIGN KEY (project_id) REFERENCES projects(id)," +
                        "FOREIGN KEY (user_id) REFERENCES users(id)," +
                        "PRIMARY KEY (project_id, user_id)" +
                    ")");
            statement.addBatch("CREATE TABLE IF NOT EXISTS submissions (" +
                        "id TEXT UNIQUE NOT NULL," +
                        "project_id TEXT NOT NULL," +
                        "event TEXT NOT NULL," +
                        "modrinth_version_id TEXT NOT NULL," +
                        "submitted_at INTEGER NOT NULL," +
                        "FOREIGN KEY (project_id) REFERENCES projects(id)," +
                        "FOREIGN KEY (event) REFERENCES events(id)," +
                        "PRIMARY KEY(id)" +
                    ")");
            statement.addBatch("CREATE TABLE IF NOT EXISTS minecraft_accounts (" +
                        "uuid TEXT UNIQUE NOT NULL," +
                        "user_id TEXT NOT NULL," +
                        "FOREIGN KEY (user_id) REFERENCES users(id)," +
                        "PRIMARY KEY (uuid)" +
                    ")");
            statement.addBatch("CREATE TABLE IF NOT EXISTS awards (" +
                        "id TEXT UNIQUE NOT NULL," +
                        "slug TEXT UNIQUE NOT NULL," +
                        "display_name TEXT NOT NULL," +
                        "sprite TEXT NOT NULL," +
                        "discord_emote TEXT NOT NULL," +
                        "tooltip TEXT," +
                        "PRIMARY KEY (id)" +
                    ")");
            statement.addBatch("CREATE TABLE IF NOT EXISTS award_instances (" +
                        "award_id TEXT NOT NULL," +
                        "awarded_to TEXT NOT NULL," +
                        "custom_data TEXT," +
                        "FOREIGN KEY (award_id) REFERENCES awards(id)," +
                        "FOREIGN KEY (awarded_to) REFERENCES users(id)," +
                        "PRIMARY KEY (award_id, awarded_to)" +
                    ")");
            statement.addBatch("CREATE TABLE IF NOT EXISTS link_codes (" +
                        "code TEXT NOT NULL," +
                        "account_id TEXT NOT NULL," +
                        "service TEXT NOT NULL," +
                        "expires INTEGER NOT NULL," +
                        "PRIMARY KEY (code)" +
                    ")");
            statement.executeBatch();
        } catch (SQLException ex) {
            LOG.error("Failed to create database tables. ", ex);
            return;
        }
        LOG.info("Created database tables.");
    }

    private static void updateSchemaVersion() {
        try (Connection connection = createDatabaseConnection();
             Statement statement = connection.createStatement()) {
            statement.addBatch("CREATE TABLE IF NOT EXISTS schema (version INTEGER NOT NULL, PRIMARY KEY(version))");
            statement.addBatch("DELETE FROM schema");
            statement.executeBatch();
            try (PreparedStatement prepared = connection.prepareStatement("INSERT INTO schema VALUES (?)")) {
                prepared.setInt(1, DATABASE_SCHEMA_VERSION);
                prepared.execute();
            }
        } catch (SQLException ex) {
            LOG.error("Failed to update database schema version. ", ex);
            return;
        }
        LOG.info("Updated database schema version.");
    }

    private static JsonMapper createDFUMapper() {
        return new JsonMapper() {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();

            @NotNull
            @Override
            public String toJsonString(@NotNull Object obj, @NotNull Type type) {
                if (obj instanceof JsonElement)
                    return gson.toJson(obj);
                if (!CODEC_REGISTRY.containsKey(type))
                    throw new UnsupportedOperationException("Cannot encode object type " + type);
                return ((Codec<Object>)CODEC_REGISTRY.get(type)).encodeStart(JsonOps.INSTANCE, obj).getOrThrow().toString();
            }

            @Override
            public <T> T fromJsonString(@NotNull String json, @NotNull Type type) {
                if (!CODEC_REGISTRY.containsKey(type))
                    throw new UnsupportedOperationException("Cannot decode object type " + type);
                return (T) CODEC_REGISTRY.get(type).decode(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow().getFirst();
            }

            @NotNull
            @Override
            public <T> T fromJsonStream(@NotNull InputStream json, @NotNull Type type) {
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
