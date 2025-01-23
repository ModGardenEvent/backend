package net.modgarden.backend.oauth.client;

import net.modgarden.backend.ModGardenBackend;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public record DiscordOAuthClient() implements OAuthClient {
	public static final String API_URL = "https://discord.com/api/v10/";

    @Override
	public <T> HttpResponse<T> get(String endpoint, HttpResponse.BodyHandler<T> bodyHandler, String... headers) throws IOException, InterruptedException {
		var req = HttpRequest.newBuilder(URI.create(API_URL + endpoint))
				.header("Authorization", "Bot " + ModGardenBackend.DOTENV.get("DISCORD_BOT_TOKEN"));
        if (headers.length > 0)
            req.headers(headers);

		return ModGardenBackend.HTTP_CLIENT.send(req.build(), bodyHandler);
	}

    @Override
    public <T> HttpResponse<T> post(String endpoint, HttpRequest.BodyPublisher bodyPublisher, HttpResponse.BodyHandler<T> bodyHandler, String... headers) throws IOException, InterruptedException {
        var req = HttpRequest.newBuilder(URI.create(API_URL + endpoint))
                .header("Authorization", "Bot " + ModGardenBackend.DOTENV.get("DISCORD_BOT_TOKEN"));
        if (headers.length > 0)
            req.headers(headers);
        req.POST(bodyPublisher);

        return ModGardenBackend.HTTP_CLIENT.send(req.build(), bodyHandler);
    }
}
