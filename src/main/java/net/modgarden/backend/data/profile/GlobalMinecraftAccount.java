package net.modgarden.backend.data.profile;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.util.ExtraCodecs;
import net.modgarden.backend.util.SQLiteOps;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;

public record GlobalMinecraftAccount(UUID uuid,
                                     Optional<String> verifiedTo) {
    public static final Codec<GlobalMinecraftAccount> DIRECT_CODEC = RecordCodecBuilder.create(inst -> inst.group(
            ExtraCodecs.UUID_CODEC.fieldOf("uuid").forGetter(GlobalMinecraftAccount::uuid),
            Codec.STRING.optionalFieldOf("verified_to").forGetter(GlobalMinecraftAccount::verifiedTo)
    ).apply(inst, GlobalMinecraftAccount::new));
    public static final Codec<GlobalMinecraftAccount> CODEC = ExtraCodecs.UUID_CODEC.xmap(GlobalMinecraftAccount::query, GlobalMinecraftAccount::uuid);

    public static GlobalMinecraftAccount query(UUID id) {
        String query = "SELECT * FROM minecraft_accounts WHERE uuid='" + id + "'";
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(query)) {
            return DIRECT_CODEC.decode(SQLiteOps.INSTANCE, resultSet).getOrThrow().getFirst();
        } catch (IllegalStateException ex) {
            ModGardenBackend.LOG.error("Failed to decode minecraft account from result set. ", ex);;
            return null;
        } catch (SQLException ex) {
            ModGardenBackend.LOG.error("Could not find minecraft account '{}' inside database.", id);
            return null;
        }
    }
}
