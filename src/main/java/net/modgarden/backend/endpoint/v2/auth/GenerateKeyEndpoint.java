package net.modgarden.backend.endpoint.v2.auth;

import io.javalin.http.Context;
import net.modgarden.backend.endpoint.v2.AuthEndpoint;
import org.jetbrains.annotations.NotNull;

import javax.sql.rowset.serial.SerialBlob;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

public class GenerateKeyEndpoint extends AuthEndpoint {
	public GenerateKeyEndpoint() {
		super("generate_key");
	}

	@Override
	public void handle(@NotNull Context ctx, String userId) throws Exception {
		super.handle(ctx);

		try (
				var connection = this.getDatabaseConnection();
				var apiKeyStatement = connection.prepareStatement("INSERT INTO api_keys(user_id, salt, hash, expires) VALUES (?, ?, ?, ?)")
		) {
			String apiKey = AuthEndpoint.generateAPIKey();
			HashedSecret hashedSecret =
					AuthEndpoint.hashSecret(apiKey.getBytes(StandardCharsets.UTF_8));
			apiKeyStatement.setString(1, userId);
			apiKeyStatement.setBlob(2, new SerialBlob(hashedSecret.salt()));
			apiKeyStatement.setBlob(3, new SerialBlob(hashedSecret.hash()));
			apiKeyStatement.setLong(4, Instant.now().plus(Duration.ofDays(365)).getEpochSecond());
			apiKeyStatement.execute();
		}
	}
}
