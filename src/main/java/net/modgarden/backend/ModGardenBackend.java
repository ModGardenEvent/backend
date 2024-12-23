package net.modgarden.backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.json.JsonMapper;
import net.modgarden.backend.data.BackendError;
import net.modgarden.backend.data.Landing;
import net.modgarden.backend.data.event.Event;
import net.modgarden.backend.data.event.Project;
import net.modgarden.backend.data.event.Submission;
import net.modgarden.backend.data.profile.MinecraftAccount;
import net.modgarden.backend.data.profile.User;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

public class ModGardenBackend {
	public static final Logger LOG = LoggerFactory.getLogger("Mod Garden Backend");
    private static final Map<Type, Codec<?>> CODEC_REGISTRY = new HashMap<>();

    public static final String SAFE_URL_REGEX = "[a-zA-Z0-9!@$()`.+,_\"-]+";;
    private static final int DATABASE_SCHEMA_VERSION = 1;

    public static void main(String[] args) {
        try {
            if (new File("./database.db").createNewFile()) {
                LOG.info("Successfuly created database file.");
                createDatabaseContents();
            }
            updateSchemaVersion();
        } catch (IOException ex) {
            LOG.error("Failed to create database file.", ex);
        }

        CODEC_REGISTRY.put(Landing.class, Landing.CODEC);
        CODEC_REGISTRY.put(BackendError.class, BackendError.CODEC);
        CODEC_REGISTRY.put(Event.class, Event.CODEC);
        CODEC_REGISTRY.put(MinecraftAccount.class, MinecraftAccount.CODEC);
        CODEC_REGISTRY.put(Project.class, Project.CODEC);
        CODEC_REGISTRY.put(Submission.class, Submission.CODEC);
        CODEC_REGISTRY.put(User.class, User.CODEC);

		Javalin app = Javalin.create(config -> config.jsonMapper(createDFUMapper()));
		app.get("", Landing::getLandingJson);
        app.get("/users/{user}", User::getUser);
        app.get("/events/{event}", Event::getEvent);
        app.get("/projects/{project}", Project::getProject);
        app.get("/submissions/{submission}", Submission::getSubmission);
        app.get("/mcaccounts/{mcaccount}", MinecraftAccount::getAccount);
        app.error(404, BackendError::handleError);
        app.error(422, BackendError::handleError);
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
                        "discord_id TEXT UNIQUE NOT NULL," +
                        "modrinth_id TEXT UNIQUE," +
                        "PRIMARY KEY(id)" +
                    ")");
            statement.addBatch("CREATE TABLE IF NOT EXISTS events (" +
                        "id TEXT UNIQUE NOT NULL," +
                        "slug TEXT UNIQUE NOT NULL," +
                        "display_name TEXT NOT NULL," +
                        "start_date INTEGER NOT NULL," +
                        "PRIMARY KEY (id)" +
                    ")");
            statement.addBatch("CREATE TABLE IF NOT EXISTS projects (" +
                        "id TEXT UNIQUE NOT NULL," +
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
                        "verified INTEGER NOT NULL DEFAULT 0," +
                        "FOREIGN KEY (user_id) REFERENCES users(id)," +
                        "PRIMARY KEY (uuid)" +
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
            @NotNull
            @Override
            public String toJsonString(@NotNull Object obj, @NotNull Type type) {
                if (!CODEC_REGISTRY.containsKey(type))
                    throw new UnsupportedOperationException("Cannot encode object type " + type);
                return ((Codec<Object>)CODEC_REGISTRY.get(type)).encodeStart(JsonOps.INSTANCE, obj).getOrThrow().toString();
            }

            @Override
            public <T> T fromJsonString(@NotNull String json, @NotNull Type type) {
                if (!CODEC_REGISTRY.containsKey(type))
                    throw new UnsupportedOperationException("Cannot decode object type " + type);;
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
