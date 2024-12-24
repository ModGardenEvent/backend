package net.modgarden.backend.data.award;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.util.SQLiteOps;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public record Award(String id,
                    String slug,
                    String displayName,
                    String discordEmote,
                    Optional<String> tooltip) {
    public static final Codec<Award> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(Award::id),
            Codec.STRING.fieldOf("slug").forGetter(Award::slug),
            Codec.STRING.fieldOf("display_name").forGetter(Award::displayName),
            Codec.STRING.fieldOf("discord_emote").forGetter(Award::discordEmote),
            Codec.STRING.optionalFieldOf("tooltip").forGetter(Award::tooltip)
    ).apply(inst, Award::new));
    public static final Codec<String> ID_CODEC = Codec.STRING.validate(Award::validate);

    public static void getAwardType(Context ctx) {
        String path = ctx.pathParam("award");
        if (!path.matches(ModGardenBackend.SAFE_URL_REGEX)) {
            ctx.result("Illegal characters in path '" + path + "'.");
            ctx.status(422);
            return;
        }
        Award award = innerQuery("slug = ?", path);
        if (award == null)
            award = innerQuery("id = ?", path);

        if (award == null) {
            ModGardenBackend.LOG.error("Could not find award '{}'.", path);
            ctx.result("Could not find award '" + path + "'.");
            ctx.status(404);
            return;
        }

        ctx.json(award);
    }

    private static Award innerQuery(String whereStatement, String id) {
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             PreparedStatement prepared = connection.prepareStatement("SELECT * FROM awards WHERE " + whereStatement)) {
            prepared.setString(1, id);
            ResultSet result = prepared.executeQuery();
            if (!result.isBeforeFirst())
                return null;
            return CODEC.decode(SQLiteOps.INSTANCE, result).getOrThrow().getFirst();
        } catch (IllegalStateException ex) {
            ModGardenBackend.LOG.error("Could not decode award. ", ex);
        } catch (SQLException ex) {
            ModGardenBackend.LOG.error("Exception in SQL query.", ex);
        }
        return null;
    }

    private static DataResult<String> validate(String id) {
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             PreparedStatement prepared = connection.prepareStatement("SELECT 1 FROM awards WHERE id = ?")) {
            prepared.setString(1, id);
            ResultSet result = prepared.executeQuery();
            if (result != null && result.getBoolean(1))
                return DataResult.success(id);
        } catch (SQLException ex) {
            ModGardenBackend.LOG.error("Exception in SQL query.", ex);
        }
        return DataResult.error(() -> "Failed to get award with id '" + id + "'.");
    }
}
