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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
            MinecraftAccount.CODEC.listOf().optionalFieldOf("minecraft_accounts", List.of()).forGetter(User::minecraftAccounts)
    ).apply(inst, User::new)));
    public static final Codec<User> SQLITE_CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(User::id),
            Codec.STRING.fieldOf("discord_id").forGetter(User::discordId),
            Codec.STRING.optionalFieldOf("modrinth_id").forGetter(User::modrinthId),
            Project.CODEC.listOf().optionalFieldOf("projects", List.of()).forGetter(User::projects),
            GlobalMinecraftAccount.CODEC.listOf().xmap(Set::copyOf, List::copyOf).optionalFieldOf("minecraft_accounts", Set.of())
                    .forGetter(user -> user.minecraftAccounts().stream().map(minecraftAccount -> minecraftAccount.asGlobal().orElse(null)).filter(Objects::nonNull).collect(Collectors.toSet()))
    ).apply(inst, User::new)));
    public static final Codec<User> CODEC = Codec.STRING.xmap(User::queryFromId, User::id);

    public User(String id,
                String discordId,
                Optional<String> modrinthId,
                List<Project> projects,
                Set<GlobalMinecraftAccount> globalAccounts) {
        this(id, discordId, modrinthId, projects, globalAccounts.stream().map(globalAccount -> new MinecraftAccount(globalAccount, id)).toList());
    }


    public static void getUser(Context ctx) {
        String path = ctx.pathParam("user");
        User user = null;

        try {
            user = queryFromModrinthUsername(path);
        } catch (RuntimeException ignored) {}

        try {
            user = queryFromId(path);
        } catch (RuntimeException ignored) {}

        if (path.startsWith("discord:")) {
            try {
                path = path.substring(8);
                user = queryFromDiscordId(path);
            } catch (RuntimeException ignored) {}
        }

        if (path.startsWith("modrinth:")) {
            try {
                path = path.substring(9);
                user = queryFromModrinthId(path);
            } catch (RuntimeException ignored) {}
        }

        if (user == null) {
            ModGardenBackend.LOG.error("Could not find user '{}'.", path);
            ctx.result("Could not find user '" + path + "'.");
            ctx.status(404);
            return;
        }

        ctx.json(ctx.jsonMapper().fromJsonString(DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, user).getOrThrow().toString(), User.class));
    }

    public static User queryFromId(String id) {
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
             ResultSet resultSet = statement.executeQuery("SELECT * FROM users WHERE id='" + id + "'")) {
            return SQLITE_CODEC.decode(SQLiteOps.INSTANCE, resultSet).getOrThrow().getFirst();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static User queryFromDiscordId(String discordId) {
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
             ResultSet resultSet = statement.executeQuery("SELECT * FROM users WHERE discord_id='" + discordId + "'")) {
            return SQLITE_CODEC.decode(SQLiteOps.INSTANCE, resultSet).getOrThrow().getFirst();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static User queryFromModrinthId(String modrinthId) {
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
             ResultSet resultSet = statement.executeQuery("SELECT * FROM users WHERE modrinth_id='" + modrinthId + "'")) {
            return SQLITE_CODEC.decode(SQLiteOps.INSTANCE, resultSet).getOrThrow().getFirst();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static User queryFromModrinthUsername(String modrinthUsername) {
        try {
            String modrinthId = getUserModrinthId(modrinthUsername);

            try (Connection connection = ModGardenBackend.createDatabaseConnection();
                 Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
                 ResultSet resultSet = statement.executeQuery("SELECT * FROM users WHERE modrinth_id='" + modrinthId + "'")) {
                return SQLITE_CODEC.decode(SQLiteOps.INSTANCE, resultSet).getOrThrow().getFirst();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getUserModrinthId(String modrinthUsername) throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            ClassicHttpRequest request = ClassicRequestBuilder.get("https://api.modrinth.com/v2/user/" + modrinthUsername).build();
            return httpClient.execute(request, response -> {
                 InputStream stream = response.getEntity().getContent();
                 try (InputStreamReader reader = new InputStreamReader(stream)) {
                     JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                     return json.getAsJsonPrimitive("id").getAsString();
                 }
            });
        }
    }
}
