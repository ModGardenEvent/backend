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
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.Integration;
import net.modgarden.backend.data.permission.Permission;
import net.modgarden.backend.data.permission.Permissions;
import net.modgarden.backend.util.NullableWrapper;
import net.modgarden.backend.util.codec.ExtraCodecs;
import net.modgarden.backend.util.codec.NullableCodec;

public record UserRole(
		String id,
		String name,
		Permissions permissions,
		Instant created,
		Map<String, Integration> integrations
) {
	public static final Codec<String> ID_CODEC = Codec.STRING.validate(UserRole::validate);
	private static final Map<String, Codec<Integration>> INTEGRATION_CODECS = Map.ofEntries(
			entry(DiscordUserRoleIntegration.ID, fromCodec(DiscordUserRoleIntegration.CODEC))
	);
	private static final Codec<String> INTEGRATION_CODEC_KEY = Codec.STRING.validate(key -> {
		if (!INTEGRATION_CODECS.containsKey(key)) {
			return DataResult.error(() -> "Integration type '" + key + "' does not exist for user role definitions");
		}
		return DataResult.success(key);
	});
	public static final Codec<Map<String, Integration>> INTEGRATION_CODEC = Codec.dispatchedMap(
			UserRole.INTEGRATION_CODEC_KEY,
			INTEGRATION_CODECS::get
	);
	public static final Codec<Map<String, NullableWrapper<Integration>>> MODIFIABLE_INTEGRATION_CODEC = Codec.dispatchedMap(
			UserRole.INTEGRATION_CODEC_KEY,
			s -> NullableCodec.nullable(INTEGRATION_CODECS.get(s))
	);
	public static final Codec<UserRole> DIRECT_CODEC = RecordCodecBuilder.create(inst -> inst.group(
			Codec.STRING.fieldOf("id").forGetter(UserRole::id),
			Codec.STRING.fieldOf("name").forGetter(UserRole::name),
			Permission.STRING_PERMISSIONS_CODEC.fieldOf("permissions").forGetter(UserRole::permissions),
			ExtraCodecs.INSTANT_CODEC.fieldOf("created").forGetter(UserRole::created),
			Codec.dispatchedMap(INTEGRATION_CODEC_KEY, INTEGRATION_CODECS::get).fieldOf("integrations").forGetter(UserRole::integrations)
	).apply(inst, UserRole::new));

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
