package net.modgarden.backend.data;

import net.modgarden.backend.ModGardenBackend;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

public final class NaturalId {
	private static final Pattern PATTERN = Pattern.compile("[a-z]{5}");
	private static final Pattern PATTERN_LEGACY = Pattern.compile("[0-9]+");
	private static final String alphabet = "abcdefghijklmnopqrstuvwxyz";

	private NaturalId() {}

	public static boolean isValid(String id) {
		return PATTERN.matcher(id).hasMatch();
	}

	public static boolean isValidLegacy(String id) {
		return isValid(id) || PATTERN_LEGACY.matcher(id).hasMatch();
	}

	public static String of(RandomGenerator random) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < 5; i++) {
			builder.append(alphabet.charAt(random.nextInt(alphabet.length())));
		}
		return builder.toString();
	}

	@NotNull
	public static String generateChecked(String table, String key) throws SQLException {
		String id = null;
		try (Connection connection1 = ModGardenBackend.createDatabaseConnection()) {
			while (id == null) {
				String naturalId = of(RandomGenerator.getDefault());
				var exists = connection1.prepareStatement("SELECT true FROM ? WHERE ? = ?");
				exists.setString(1, table);
				exists.setString(2, key);
				exists.setString(3, naturalId);
				if (exists.execute()) {
					id = naturalId;
				}
			}
		}
		return id;
	}
}
