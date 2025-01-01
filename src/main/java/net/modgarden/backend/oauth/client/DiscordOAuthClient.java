package net.modgarden.backend.oauth.client;

import com.google.gson.JsonObject;
import net.modgarden.backend.ModGardenBackend;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.stream.Collectors;

public record DiscordOAuthClient(String clientId) implements OAuthClient {
	public static final String API_URL = "https://discord.com/api/";
	public static final String CONTENT_TYPE = "application/x-www-form-urlencoded";
    public static final String GRANT_TYPE = "authorization_code";
    public static final String REDIRECT_URI = "http://localhost:7070";
    public static final String SCOPE = "identify";

    @Override
	public String get(String endpoint) throws IOException, InterruptedException {
		var req = HttpRequest.newBuilder(URI.create(API_URL + endpoint))
				.header("Content-Type", CONTENT_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(getBody(clientId)))
				.build();

		return ModGardenBackend.HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString()).body();
	}

    private static String getBody(String clientId) {
        var params = new HashMap<String, String>();
        params.put("client_id", clientId);
        params.put("client_secret", ModGardenBackend.DOTENV.get("DISCORD_CLIENT_SECRET"));
        params.put("grant_type", GRANT_TYPE);
        params.put("redirect_uri", REDIRECT_URI);
        params.put("scope", SCOPE);
        return params.entrySet()
                .stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }
}
