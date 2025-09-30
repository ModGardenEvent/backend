package net.modgarden.backend.endpoint.v2.auth;

import io.javalin.http.Context;
import net.modgarden.backend.endpoint.v2.AuthEndpoint;
import org.jetbrains.annotations.NotNull;

public class GenerateKeyEndpoint extends AuthEndpoint {
	public GenerateKeyEndpoint() {
		super("generate_key");
	}

	@Override
	public void handle(@NotNull Context ctx) throws Exception {
		super.handle(ctx);

		try (
				var connection = this.getDatabaseConnection();
				var statement = connection.prepareStatement("UPDATE credentials SET ")
		) {
			String apiKey = AuthEndpoint.generateAPIKey();
		}
	}
}
