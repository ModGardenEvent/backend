package net.modgarden.backend.data.user.role;

import static java.util.Map.entry;
import static net.modgarden.backend.data.Integration.fromCodec;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.Integration;
import net.modgarden.backend.data.Permissions;

public record UserRole(
		String id,
		String name,
		Permissions permissions,
		Instant created
) {
	public static final Codec<String> ID_CODEC = Codec.STRING.validate(UserRole::validate);
	private static final Map<String, Codec<Integration>> INTEGRATION_CODECS = Map.ofEntries(
			entry("discord", fromCodec(DiscordIntegration.CODEC))
	);

	private static DataResult<String> validate(String id) {
		try (
				Connection connection = ModGardenBackend.createDatabaseConnection();
				PreparedStatement prepared = connection.prepareStatement("SELECT 1 FROM user_role_definitions WHERE id = ?")) {
			prepared.setString(1, id);
			ResultSet result = prepared.executeQuery();
			if (result != null && result.getBoolean(1))
				return DataResult.success(id);
		} catch (SQLException ex) {
			ModGardenBackend.LOG.error("Exception in SQL query.", ex);
		}
		return DataResult.error(() -> "Failed to get user role with id '" + id + "'");
	}
}
