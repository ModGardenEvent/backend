package net.modgarden.backend;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.json.JsonMapper;
import net.modgarden.backend.data.BackendError;
import net.modgarden.backend.data.Landing;
import net.modgarden.backend.data.event.Event;
import net.modgarden.backend.data.event.Project;
import net.modgarden.backend.data.profile.User;
import net.modgarden.backend.util.SQLiteOps;
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
import java.util.HashMap;
import java.util.Map;

public class ModGardenBackend {
	public static final Logger LOG = LoggerFactory.getLogger("Mod Garden Backend");
    private static final Map<Type, Codec<?>> CODEC_REGISTRY = new HashMap<>();
    private static Landing landing = null;

    public static void main(String[] args) {
        try {
            createDatabaseFile("database.db");
        } catch (IOException ex) {
            LOG.error("Failed to create database file.", ex);
        }

        CODEC_REGISTRY.put(Landing.class, Landing.CODEC);
        CODEC_REGISTRY.put(BackendError.class, BackendError.CODEC);
        CODEC_REGISTRY.put(Event.class, Event.DIRECT_CODEC);
        CODEC_REGISTRY.put(Project.class, Project.DIRECT_CODEC);
        CODEC_REGISTRY.put(User.class, User.DIRECT_CODEC);

		Javalin app = Javalin.create(config -> config.jsonMapper(createDFUMapper()));
		app.get("", ModGardenBackend::getLandingJson);
        app.get("/user/{user}", User::getUser);
		app.error(404, ModGardenBackend::handleError);
		app.start(7070);
		LOG.info("Mod Garden Backend Started!");
	}

    public static Connection createDatabaseConnection() throws SQLException {
        String url = "jdbc:sqlite:database.db";
        return DriverManager.getConnection(url);
    }

    public static Connection createTempDatabaseConnection() throws SQLException {
        try {
            createDatabaseFile("temp.db");
        } catch (IOException ex) {
            LOG.error("Failed to create temporary database file.", ex);
        }
        String url = "jdbc:sqlite:temp.db";
        return DriverManager.getConnection(url);
    }

    public static void dropTempFile() {
        File databaseFile = new File("./temp.db");
        if (databaseFile.delete())
            LOG.info("Delted temporary database file.");
    }

    private static void createDatabaseFile(String fileName) throws IOException {
        File databaseFile = new File("./" + fileName);
        if (databaseFile.createNewFile())
            LOG.info("Created new database file.");
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
		ctx.json(new BackendError(ctx.status().toString(), ctx.result()));
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
