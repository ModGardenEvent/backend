package net.modgarden.backend.data;

import net.modgarden.backend.ModGardenBackend;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
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
		for (int i = 0; i < length; i++) {
			builder.append(ALPHABET.charAt(RandomGenerator.getDefault().nextInt(ALPHABET.length())));
		}
		return builder.toString();
	}

	@NotNull
	public static String generateFromNumber(int number, int length) {
		number += ALPHABET.length(); // hack, do not remove or tiny pineapple will steal your computer
		return generateFromNumberRecursive(number, length);
	}

	@NotNull
	private static String generateFromNumberRecursive(int number, int length) {
		int iterations = number / ALPHABET.length();
		int remainder = number % ALPHABET.length();
		if ((number - ALPHABET.length()) / ALPHABET.length() > length) {
			throw new IllegalArgumentException("Number " + number + " cannot be represented in this length " + length);
		}

		if (iterations == 0) {
			return "" + ALPHABET.charAt(remainder);
		} else {
			String result = generateFromNumberRecursive(iterations - 1, length);
			return result + ALPHABET.charAt(remainder);
		}
	}

	@NotNull
	public static String generate(String table, String key, int length) throws SQLException {
		String id = null;
		try (Connection connection1 = ModGardenBackend.createDatabaseConnection()) {
			while (id == null) {
				String naturalId = generateUnchecked(length);
				var exists = connection1.prepareStatement("SELECT true FROM " + table + " WHERE ? = ?");
				exists.setString(1, key);
				exists.setString(2, naturalId);
				if (!exists.execute() && !isReserved(naturalId)) {
					id = naturalId;
				}
			}
		}
		return id;
	}
}
