package net.modgarden.backend.oauth.client;

import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.Landing;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public record ModrinthOAuthClient() implements OAuthClient {
	public static final String API_URL = "https://api.modrinth.com/";
	private static final String USER_AGENT = "ModGardenEvent/backend/" + Landing.getInstance().version() + " (modgarden.net)";

    @Override
	public <T> HttpResponse<T> get(String endpoint, HttpResponse.BodyHandler<T> bodyHandler, String... headers) throws IOException, InterruptedException {
		var req = HttpRequest.newBuilder(URI.create(API_URL + endpoint))
				.header("User-Agent", USER_AGENT);
        if (headers.length > 0)
            req.headers(headers);

		return ModGardenBackend.HTTP_CLIENT.send(req.build(), bodyHandler);
	}

    @Override
    public <T> HttpResponse<T> post(String endpoint, HttpRequest.BodyPublisher bodyPublisher, HttpResponse.BodyHandler<T> bodyHandler, String... headers) throws IOException, InterruptedException {
        var req = HttpRequest.newBuilder(URI.create(API_URL + endpoint))
                .header("User-Agent", USER_AGENT);
        if (headers.length > 0)
            req.headers(headers);
        req.POST(bodyPublisher);

        return ModGardenBackend.HTTP_CLIENT.send(req.build(), bodyHandler);
    }
}
