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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

public class ModGardenBackend {
	public static final Logger LOG = LoggerFactory.getLogger("Mod Garden Backend");
    private static final Map<Type, Codec<?>> CODEC_REGISTRY = new HashMap<>();
    private static Landing landing = null;

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
        CODEC_REGISTRY.put(Event.class, Event.DIRECT_CODEC);
        CODEC_REGISTRY.put(MinecraftAccount.class, MinecraftAccount.DIRECT_CODEC);
        CODEC_REGISTRY.put(Project.class, Project.DIRECT_CODEC);
        CODEC_REGISTRY.put(User.class, User.DIRECT_CODEC);

		Javalin app = Javalin.create(config -> config.jsonMapper(createDFUMapper()));
		app.get("", ModGardenBackend::getLandingJson);
        app.get("/user/{user}", User::getUser);
        app.get("/event/{event}", Event::getEvent);
        app.get("/project/{project}", Project::getProject);
        app.get("/submission/{submission}", Submission::getSubmission);
        app.get("/mcaccount/{mcaccount}", MinecraftAccount::getAccount);
        app.error(422, ModGardenBackend::handleError);
		app.error(404, ModGardenBackend::handleError);
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
                    "id TEXT PRIMARY KEY," +
                    "discord_id TEXT," +
                    "modrinth_id TEXT)");
            statement.addBatch("CREATE TABLE IF NOT EXISTS events (" +
                    "id TEXT PRIMARY KEY," +
                    "slug TEXT," +
                    "display_name TEXT," +
                    "start_date TEXT," +
                    "submissions BLOB" +
                    ")");
            statement.addBatch("CREATE TABLE IF NOT EXISTS projects (" +
                    "id TEXT PRIMARY KEY," +
                    "attributed_to TEXT," +
                    "authors BLOB," +
                    "events BLOB" +
                    ")");
            statement.addBatch("CREATE TABLE IF NOT EXISTS submissions (" +
                    "id TEXT PRIMARY KEY," +
                    "project_id TEXT," +
                    "event TEXT," +
                    "submitted_at TEXT" +
                    ")");
            statement.addBatch("CREATE TABLE IF NOT EXISTS minecraft_accounts (" +
                    "uuid TEXT PRIMARY KEY," +
                    "verified_to TEXT)");
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
            statement.addBatch("CREATE TABLE IF NOT EXISTS schema_version (" +
                    "id INTEGER PRIMARY KEY)");
            statement.addBatch("DELETE FROM schema_version");
            statement.addBatch("INSERT INTO schema_version VALUES (" + DATABASE_SCHEMA_VERSION + ")");
        } catch (SQLException ex) {
            LOG.error("Failed to update database schema version. ", ex);
            return;
        }
        LOG.info("Updated database schema version.");
    }

	private static void getLandingJson(Context ctx) {
        if (landing == null) {
            InputStream landingFile = ModGardenBackend.class.getResourceAsStream("/landing.json");
            if (landingFile == null) {
                LOG.error("Could not find 'landing.json' resource file.");
                ctx.result("Could not find landing file.");
                ctx.status(404);
                return;
            }
            landing = ctx.jsonMapper().fromJsonStream(landingFile, Landing.class);
        }

		ctx.json(landing);
	}

	private static void handleError(Context ctx) {
		ctx.json(new BackendError(ctx.status().getMessage(), ctx.result()));
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
