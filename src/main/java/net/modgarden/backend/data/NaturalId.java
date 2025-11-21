package net.modgarden.backend.data;

import net.modgarden.backend.ModGardenBackend;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

public final class NaturalId {
	private static final Pattern PATTERN = Pattern.compile("^[a-z]{5}$");
	private static final Pattern PATTERN_LEGACY = Pattern.compile("[0-9]+");
	// warning: do not fucking change this until you verify with regex101.com
	// also pls create an account and then make a new regex101 and add it to the list below
	// https://regex101.com/r/e1Ygne/1
	// see also: regexlicensing.org
	private static final Pattern RESERVED_PATTERN =
			Pattern.compile("^((z{3}.*)|(.+bot)|(.+acc)|(abcde))$");
	private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz";
	private static final String MISSINGNO = "noacc";

	private NaturalId() {}

	public static boolean isReserved(String id) {
		return RESERVED_PATTERN.matcher(id).hasMatch();
	}

	public static boolean isValid(String id) {
		return PATTERN.matcher(id).hasMatch();
	}

	public static boolean isValidLegacy(String id) {
		return isValid(id) || PATTERN_LEGACY.matcher(id).hasMatch();
	}

	private static String generateUnchecked(int length) {
		StringBuilder builder = new StringBuilder();
		RandomGenerator random = RandomGenerator.getDefault();
		for (int i = 0; i < length; i++) {
			builder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
		}
		return builder.toString();
	}

	@NotNull
	public static String generate(String table, String key, String key2,
								  int length) throws SQLException {
		String id = null;
		try (Connection connection1 = ModGardenBackend.createDatabaseConnection()) {
			while (id == null) {
				String naturalId = generateUnchecked(length);
				PreparedStatement exists;
				if (key2 != null) {
					exists = connection1.prepareStatement("SELECT 1 FROM " + table + " WHERE ? = ? OR ? = ?");
				} else {
					exists = connection1.prepareStatement("SELECT 1 FROM " + table + " WHERE ? = ?");
				}
				exists.setString(1, key);
				exists.setString(2, naturalId);
				if (key2 != null) {
					exists.setString(3, key2);
					exists.setString(4, naturalId);
				}
				ResultSet resultSet = exists.executeQuery();
				if (!resultSet.getBoolean(1) && !isReserved(naturalId)) {
					id = naturalId;
				}
			}
		}
		return id;
	}

	public static String getMissingno() {
		return MISSINGNO;
	}
}
