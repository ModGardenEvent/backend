package net.modgarden.backend.data.award;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.mkammerer.snowflakeid.SnowflakeIdGenerator;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public record Award(String id,
                    String slug,
                    String displayName,
                    String sprite,
                    String discordEmote,
                    String tooltip) {
    public static final SnowflakeIdGenerator ID_GENERATOR = SnowflakeIdGenerator.createDefault(4);
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
            ModGardenBackend.LOG.debug("Could not find award '{}'.", path);
            ctx.result("Could not find award '" + path + "'.");
            ctx.status(404);
            return;
        }

        ModGardenBackend.LOG.debug("Successfully queried award from path '{}'", path);
        ctx.json(award);
    }

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

	public static void getAwardsByUser(Context ctx) {
		String user = ctx.pathParam("user");
		if (!user.matches(ModGardenBackend.SAFE_URL_REGEX)) {
			ctx.result("Illegal characters in path '" + user + "'.");
			ctx.status(422);
			return;
		}
		var queryString = selectAllByUser(user);
		try (Connection connection = ModGardenBackend.createDatabaseConnection()) {
			PreparedStatement prepared = connection.prepareStatement(queryString);
			ResultSet result = prepared.executeQuery();
			var awards = new JsonArray();
			while (result.next()) {
				var award = new JsonObject();
				award.addProperty("award_id", result.getString("award_id"));
				award.addProperty("awarded_to", result.getString("awarded_to"));
				award.addProperty("custom_data", result.getString("custom_data"));
				award.addProperty("slug", result.getString("slug"));
				award.addProperty("display_name", result.getString("display_name"));
				award.addProperty("sprite", result.getString("sprite"));
				award.addProperty("discord_emote", result.getString("discord_emote"));
				award.addProperty("tooltip", result.getString("tooltip"));
				award.addProperty("tier", result.getString("tier"));
				award.addProperty("submission_id", result.getString("submission_id"));
				awards.add(award);
			}
			ctx.json(awards);
		} catch (SQLException ex) {
			ModGardenBackend.LOG.error("Exception in SQL query.", ex);
		}
	}

	public static String selectAllByUser(String user) {
		return """
				SELECT i.award_id, i.awarded_to, i.custom_data, a.slug,
				 a.display_name, a.sprite, a.discord_emote, a.tooltip, i.submission_id,
				 COALESCE(i.tier_override, a.tier) as tier
				FROM award_instances i
				INNER JOIN awards a ON a.id = i.award_id
				INNER JOIN users u ON u.id = i.awarded_to
				WHERE u.id = '%s' OR u.username = '%s'
				GROUP BY i.award_id
				""".formatted(user, user);
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
