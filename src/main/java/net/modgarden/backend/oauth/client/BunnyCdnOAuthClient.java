package net.modgarden.backend.oauth.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import net.modgarden.backend.ModGardenBackend;

@SuppressWarnings("UastIncorrectHttpHeaderInspection")
public class BunnyCdnOAuthClient implements OAuthClient {
	public static final String API_URL = "https://ny.storage.bunnycdn.com/mod-garden/";

    @Override
	public <T> HttpResponse<T> get(String endpoint, HttpResponse.BodyHandler<T> bodyHandler, String... headers) throws IOException, InterruptedException {
		var req = HttpRequest.newBuilder(URI.create(API_URL + endpoint))
				.header("AccessKey", ModGardenBackend.DOTENV.get("BUNNY_CDN_KEY"));
		if (headers.length > 0)
			req.headers(headers);

		return ModGardenBackend.HTTP_CLIENT.send(req.build(), bodyHandler);
	}

    @Override
    public <T> HttpResponse<T> post(String endpoint, HttpRequest.BodyPublisher bodyPublisher, HttpResponse.BodyHandler<T> bodyHandler, String... headers) throws IOException, InterruptedException {
		var req = HttpRequest.newBuilder(URI.create(API_URL + endpoint))
				.header("AccessKey", ModGardenBackend.DOTENV.get("BUNNY_CDN_KEY"));
		if (headers.length > 0)
			req.headers(headers);
		req.POST(bodyPublisher);

		return ModGardenBackend.HTTP_CLIENT.send(req.build(), bodyHandler);
    }

	public <T> HttpResponse<T> put(String endpoint, HttpRequest.BodyPublisher bodyPublisher, HttpResponse.BodyHandler<T> bodyHandler, String... headers) throws IOException, InterruptedException {
		var req = HttpRequest.newBuilder(URI.create(API_URL + endpoint))
				.header("AccessKey", ModGardenBackend.DOTENV.get("BUNNY_CDN_KEY"));
		if (headers.length > 0)
			req.headers(headers);
		req.PUT(bodyPublisher);

		return ModGardenBackend.HTTP_CLIENT.send(req.build(), bodyHandler);
	}
}
