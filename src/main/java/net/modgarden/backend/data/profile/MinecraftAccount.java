package net.modgarden.backend.data.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JavaOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.util.ExtraCodecs;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;

public record MinecraftAccount(UUID uuid,
                               String userId) {
    public static final Codec<MinecraftAccount> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            ExtraCodecs.UUID_CODEC.fieldOf("uuid").forGetter(MinecraftAccount::uuid),
            Codec.STRING.fieldOf("user_id").forGetter(MinecraftAccount::userId)
    ).apply(inst, MinecraftAccount::new));

    public static void getAccount(Context ctx) {
        String path = ctx.pathParam("mcaccount");
        if (!path.matches(ModGardenBackend.SAFE_URL_REGEX)) {
            ctx.result("Illegal characters in path '" + path + "'.");
            ctx.status(422);
            return;
        }
        MinecraftAccount account = query(path.toLowerCase(Locale.ROOT));
        if (account == null) {
            ModGardenBackend.LOG.debug("Could not find Minecraft account '{}'.", path);
            ctx.result("Could not find Minecraft account '" + path + "'.");
            ctx.status(404);
            return;
        }

        ModGardenBackend.LOG.debug("Successfully queried minecraft account from path '{}'", path);
        ctx.json(account);
    }

    public static MinecraftAccount query(String path) {
        MinecraftAccount account = queryFromUsername(path);

        if (account == null)
            account = queryFromUuid(path);

        return account;
    }

    private static MinecraftAccount queryFromUsername(String username) {
        try {
            String uuid = getUuidFromUsername(username);
            if (uuid != null)
                return queryFromUuid(uuid);
            return null;
        } catch (IOException | InterruptedException ex) {
            return null;
        }
    }

    private static String getUuidFromUsername(String username) throws IOException, InterruptedException {
        var req = HttpRequest.newBuilder(URI.create("https://api.mojang.com/users/profiles/minecraft/" + username))
                .build();
        HttpResponse<InputStream> stream = ModGardenBackend.HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (stream.statusCode() != 200)
            return null;
        try (InputStreamReader reader = new InputStreamReader(stream.body())) {
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonObject())
                return null;
            return element.getAsJsonObject().getAsJsonPrimitive("id").toString();
        }
    }

    private static MinecraftAccount queryFromUuid(String uuid) {
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             PreparedStatement prepared = connection.prepareStatement("SELECT * FROM minecraft_accounts WHERE uuid=?")) {
            prepared.setString(1, uuid);
            ResultSet result = prepared.executeQuery();
            if (!result.isBeforeFirst())
                return null;
			var decodedUUID = ExtraCodecs.UUID_CODEC.decode(JavaOps.INSTANCE, result.getString("uuid")).getOrThrow().getFirst();
			return new MinecraftAccount(
					decodedUUID,
					result.getString("user_id")
			);
        } catch (SQLException ex) {
            ModGardenBackend.LOG.error("Exception in SQL query.", ex);
        }
        return null;
    }
}
