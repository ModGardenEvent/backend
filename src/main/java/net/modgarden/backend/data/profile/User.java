package net.modgarden.backend.data.profile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.event.Project;
import net.modgarden.backend.util.SQLiteOps;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public record User(String id,
                   String discordId,
                   Optional<String> modrinthId,
                   List<Project> projects,
                   List<MinecraftAccount> minecraftAccounts) {
    public static final Codec<User> DIRECT_CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(User::id),
            Codec.STRING.fieldOf("discord_id").forGetter(User::discordId),
            Codec.STRING.optionalFieldOf("modrinth_id").forGetter(User::modrinthId),
            Project.CODEC.listOf().optionalFieldOf("projects", List.of()).forGetter(User::projects),
            Codec.withAlternative(MinecraftAccount.DIRECT_CODEC, GlobalMinecraftAccount.CODEC, MinecraftAccount::new).listOf().optionalFieldOf("minecraft_accounts", List.of()).forGetter(User::minecraftAccounts)
    ).apply(inst, User::new)));
    public static final Codec<User> CODEC = Codec.STRING.xmap(User::queryFromId, User::id);

    public static void getUser(Context ctx) {
        String path = ctx.pathParam("user");
        User user = query(path.toLowerCase(Locale.ROOT));
        if (user == null) {
            ModGardenBackend.LOG.error("Could not find user '{}'.", path);
            ctx.result("Could not find user '" + path + "'.");
            ctx.status(404);
            return;
        }

        ModGardenBackend.LOG.info("Successfully queried user from path {}.", path);
        ctx.json(ctx.jsonMapper().fromJsonString(DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, user).getOrThrow().toString(), User.class));
    }

    @Nullable
    public static User query(String path) {
        User user = queryFromModrinthUsername(path);

        if (user == null)
            user = queryFromId(path);

        if (user == null && path.startsWith("discord:")) {
            path = path.substring(8);
            user = queryFromDiscordId(path);
        }

        if (user == null && path.startsWith("modrinth:")) {
            path = path.substring(9);
            user = queryFromModrinthId(path);
        }

        return user;
    }

    private static User queryFromId(String id) {
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
             ResultSet resultSet = statement.executeQuery("SELECT * FROM users WHERE id='" + id + "'")) {
            return DIRECT_CODEC.decode(SQLiteOps.INSTANCE, resultSet).getOrThrow().getFirst();
        } catch (IllegalStateException ex) {
            logException(ex);
            return null;
        } catch (SQLException e) {
            return null;
        }
    }

    private static User queryFromDiscordId(String discordId) {
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
             ResultSet resultSet = statement.executeQuery("SELECT * FROM users WHERE discord_id='" + discordId + "'")) {
            return DIRECT_CODEC.decode(SQLiteOps.INSTANCE, resultSet).getOrThrow().getFirst();
        } catch (IllegalStateException ex) {
            logException(ex);
            return null;
        } catch (SQLException e) {
            return null;
        }
    }

    private static User queryFromModrinthId(String modrinthId) {
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
             ResultSet resultSet = statement.executeQuery("SELECT * FROM users WHERE modrinth_id='" + modrinthId + "'")) {
            return DIRECT_CODEC.decode(SQLiteOps.INSTANCE, resultSet).getOrThrow().getFirst();
        } catch (IllegalStateException ex) {
            logException(ex);
            return null;
        } catch (SQLException e) {
            return null;
        }
    }

    private static User queryFromModrinthUsername(String modrinthUsername) {
        try {
            String modrinthId = getUserModrinthId(modrinthUsername);

            try (Connection connection = ModGardenBackend.createDatabaseConnection();
                 Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
                 ResultSet resultSet = statement.executeQuery("SELECT * FROM users WHERE modrinth_id='" + modrinthId + "'")) {
                return DIRECT_CODEC.decode(SQLiteOps.INSTANCE, resultSet).getOrThrow().getFirst();
            } catch (SQLException e) {
                return null;
            }
        } catch (IllegalStateException ex) {
            logException(ex);
            return null;
        } catch (IOException ex) {
            return null;
        }
    }

    private static String getUserModrinthId(String modrinthUsername) throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            ClassicHttpRequest request = ClassicRequestBuilder.get("https://api.modrinth.com/v2/user/" + modrinthUsername).build();
            return httpClient.execute(request, response -> {
                if (response.getCode() != 200)
                    return null;
                 InputStream stream = response.getEntity().getContent();
                 try (InputStreamReader reader = new InputStreamReader(stream)) {
                     JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                     return json.getAsJsonPrimitive("id").getAsString();
                 }
            });
        }
    }

    private static void logException(IllegalStateException ex) {
        ModGardenBackend.LOG.error("Failed to decode user from result set. ", ex);
    }
}
