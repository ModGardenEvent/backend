package net.modgarden.backend.data.award;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.ModGardenBackend;

public record Award(String id,
                    String slug,
                    String displayName,
                    String sprite,
                    String discordEmote,
                    String tooltip) {
    public static final Codec<Award> DIRECT_CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(Award::id),
            Codec.STRING.fieldOf("slug").forGetter(Award::slug),
            Codec.STRING.fieldOf("display_name").forGetter(Award::displayName),
            Codec.STRING.fieldOf("sprite").forGetter(Award::sprite),
            Codec.STRING.fieldOf("discord_emote").forGetter(Award::discordEmote),
            Codec.STRING.fieldOf("tooltip").forGetter(Award::tooltip)
    ).apply(inst, Award::new));
    public static final Codec<String> ID_CODEC = Codec.STRING.validate(Award::validate);
	public static final Codec<Award> CODEC = ID_CODEC.xmap(id -> innerQuery("id = ?", id), Award::id);

	private static Award innerQuery(String whereStatement, String id) {
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             PreparedStatement prepared = connection.prepareStatement("SELECT * FROM awards WHERE " + whereStatement)) {
            prepared.setString(1, id);
            ResultSet result = prepared.executeQuery();
            if (!result.isBeforeFirst())
                return null;
			return new Award(
					result.getString("id"),
					result.getString("slug"),
					result.getString("display_name"),
					result.getString("sprite"),
					result.getString("discord_emote"),
					result.getString("tooltip")
			);
        }  catch (SQLException ex) {
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
