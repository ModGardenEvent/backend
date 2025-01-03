package net.modgarden.backend.oauth.client;

import net.modgarden.backend.ModGardenBackend;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public record DiscordOAuthClient() implements OAuthClient {
	public static final String API_URL = "https://discord.com/api/v10/";

    @Override
	public <T> HttpResponse<T> getResponse(String endpoint, HttpResponse.BodyHandler<T> bodyHandler) throws IOException, InterruptedException {
		var req = HttpRequest.newBuilder(URI.create(API_URL + endpoint))
				.header("Authorization", "Bot " + ModGardenBackend.DOTENV.get("DISCORD_BOT_TOKEN"))
				.build();

		return ModGardenBackend.HTTP_CLIENT.send(req, bodyHandler);
	}
}
