package net.modgarden.backend.data.profile;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.event.Event;
import net.modgarden.backend.data.event.Project;
import net.modgarden.backend.util.ExtraCodecs;
import net.modgarden.backend.util.SQLiteOps;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public record GlobalMinecraftAccount(UUID uuid,
                                     Optional<String> verifiedTo) {
    public static final Codec<GlobalMinecraftAccount> DIRECT_CODEC = RecordCodecBuilder.create(inst -> inst.group(
            ExtraCodecs.UUID_CODEC.fieldOf("uuid").forGetter(GlobalMinecraftAccount::uuid),
            Codec.STRING.optionalFieldOf("verified_to").forGetter(GlobalMinecraftAccount::verifiedTo)
    ).apply(inst, GlobalMinecraftAccount::new));
    public static final Codec<GlobalMinecraftAccount> CODEC = ExtraCodecs.UUID_CODEC.xmap(GlobalMinecraftAccount::query, GlobalMinecraftAccount::uuid);

    public static void getAccount(Context ctx) {
        String path = ctx.pathParam("mcaccount");
        GlobalMinecraftAccount account = query(UUID.fromString(path));
        if (account == null) {
            ModGardenBackend.LOG.error("Could not find Minecraft account '{}'.", path);
            ctx.result("Could not find Minecraft account '" + path + "'.");
            ctx.status(404);
            return;
        }

        ctx.json(ctx.jsonMapper().fromJsonString(DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, account).getOrThrow().toString(), Event.class));
    }

    public static GlobalMinecraftAccount query(UUID id) {
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             PreparedStatement prepared = connection.prepareStatement("SELECT * FROM minecraft_accounts WHERE uuid=?")) {
            prepared.setString(1, id.toString());
            return DIRECT_CODEC.decode(SQLiteOps.INSTANCE, prepared.executeQuery()).getOrThrow().getFirst();
        } catch (IllegalStateException ex) {
            ModGardenBackend.LOG.error("Failed to decode minecraft account from result set. ", ex);;
            return null;
        } catch (SQLException ex) {
            ModGardenBackend.LOG.error("Could not find minecraft account '{}' inside database.", id);
            return null;
        }
    }
}
