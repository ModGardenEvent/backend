package net.modgarden.backend.oauth.client;

import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.Landing;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public record ModrinthOAuthClient() implements OAuthClient {
	public static final String API_URL = "https://api.modrinth.com/";

    @Override
	public <T> HttpResponse<T> getResponse(String endpoint, HttpResponse.BodyHandler<T> bodyHandler) throws IOException, InterruptedException {
		var req = HttpRequest.newBuilder(URI.create(API_URL + endpoint))
				.header("User-Agent", "ModGardenEvent/backend/" + Landing.getInstance().version() + " (modgarden.net)")
				.build();

		return ModGardenBackend.HTTP_CLIENT.send(req, bodyHandler);
	}
}
