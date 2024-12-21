package net.modgarden.backend.data.profile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.util.ExtraCodecs;
import net.modgarden.backend.util.SQLiteOps;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public record MinecraftAccount(UUID uuid,
                               List<String> linkedTo,
                               boolean verified) {
    public static final Codec<MinecraftAccount> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            ExtraCodecs.UUID_CODEC.fieldOf("uuid").forGetter(MinecraftAccount::uuid),
            Codec.STRING.listOf().fieldOf("linked_to").forGetter(MinecraftAccount::linkedTo),
            Codec.BOOL.fieldOf("verified").forGetter(MinecraftAccount::verified)
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
            ModGardenBackend.LOG.error("Could not find Minecraft account '{}'.", path);
            ctx.result("Could not find Minecraft account '" + path + "'.");
            ctx.status(404);
            return;
        }

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
        } catch (IOException ex) {
            return null;
        }
    }

    private static String getUuidFromUsername(String username) throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            ClassicHttpRequest request = ClassicRequestBuilder.get("https://api.mojang.com/users/profiles/minecraft/" + username).build();
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

    private static MinecraftAccount queryFromUuid(String uuid) {
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             PreparedStatement prepared = connection.prepareStatement("SELECT * FROM minecraft_accounts WHERE uuid=?")) {
            prepared.setString(1, uuid);
            ResultSet result = prepared.executeQuery();
            if (result == null)
                return null;
            return CODEC.decode(SQLiteOps.INSTANCE, result).getOrThrow().getFirst();
        } catch (IllegalStateException ex) {
            ModGardenBackend.LOG.error("Failed to decode minecraft account from result set. ", ex);;
            return null;
        } catch (SQLException ex) {
            return null;
        }
    }

    public record UserInstance(UUID uuid, boolean verified) {
        public static final Codec<UserInstance> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                ExtraCodecs.UUID_CODEC.fieldOf("uuid").forGetter(UserInstance::uuid),
                Codec.BOOL.fieldOf("verified").forGetter(UserInstance::verified)
        ).apply(inst, UserInstance::new));
    }
}
