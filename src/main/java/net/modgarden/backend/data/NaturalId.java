package net.modgarden.backend.data;

import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.oauth.OAuthService;
import net.modgarden.backend.oauth.client.BunnyCdnOAuthClient;
import org.jetbrains.annotations.NotNull;

public final class NaturalId {
	private static final Pattern PATTERN = Pattern.compile("^[a-z]{5}$");
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

	public static String generateCdnLink(String basePath, int length) throws Exception {
		String id = null;
		while (id == null) {
			String naturalId = generateUnchecked(length);
			BunnyCdnOAuthClient client = OAuthService.BUNNY_CDN.authenticate();
			HttpResponse<Void> response = client.get(basePath + "/" + naturalId, HttpResponse.BodyHandlers.discarding());
			if (response.statusCode() == 404) {
				id = naturalId;
			}
		}
		return basePath + "/" + id;
	}

	public static String getMissingno() {
		return MISSINGNO;
	}
}
