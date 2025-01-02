package net.modgarden.backend.oauth.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.oauth.OAuthService;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public record DiscordOAuthClient(String clientId) implements OAuthClient {
	public static final String API_URL = "https://discord.com/api/v10/";
	public static final String CONTENT_TYPE = "application/x-www-form-urlencoded";
    public static final String SCOPE = "identify";

    public static void authorizeDiscordUser(Context ctx) {
        String code = ctx.queryParam("code");

        if (code == null) {
            ctx.result("No params within link.");
            ctx.status(404);
            return;
        }

        DiscordOAuthClient client = OAuthService.DISCORD.authenticate();
        try {
            var auth = client.get("oauth2/token", new String[] { "Content-Type", CONTENT_TYPE }, client.getAuthorizationBody(code));
            if (auth.statusCode() != 200) {
                ctx.result(auth.body());
                ctx.status(auth.statusCode());
                return;
            }
            ModGardenBackend.LOG.info(auth.body());

            JsonElement json = JsonParser.parseString(auth.body());
            String accessToken = json.getAsJsonObject().get("access_token").getAsString();

            var user = client.get("users/@me", new String[] { "Authorization", "Bearer " + accessToken }, Collections.emptyMap());
            if (user.statusCode() != 200) {
                ctx.result(user.body());
                ctx.status(user.statusCode());
                return;
            }
            ModGardenBackend.LOG.info(user.body());

            // TODO: Handle refresh tokens.
            String refreshToken = json.getAsJsonObject().get("refresh_token").getAsString();
        } catch (IOException | InterruptedException e) {
            ctx.result("Failed to handle OAuth2 redirect.");
            ctx.status(422);
            return;
        }
    }

    @Override
	public <T> HttpResponse<T> getResponse(String endpoint, HttpResponse.BodyHandler<T> bodyHandler) throws IOException, InterruptedException {
		var req = HttpRequest.newBuilder(URI.create(API_URL + endpoint))
				.header("Authorization", "Bot " + ModGardenBackend.DOTENV.get("DISCORD_BOT_TOKEN"))
				.build();

		return ModGardenBackend.HTTP_CLIENT.send(req, bodyHandler);
	}

    public HttpResponse<String> get(String endpoint,
                                    String[] headers,
                                    Map<String, String> body) throws IOException, InterruptedException {
        var builder = HttpRequest.newBuilder(URI.create(API_URL + endpoint));
        if (headers.length > 0)
            builder.headers(headers);
        if (!body.isEmpty())
            builder.POST(HttpRequest.BodyPublishers.ofString(createBody(body)));
        var req = builder.build();

        return ModGardenBackend.HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
    }

    public Map<String, String> getAuthorizationBody(String code) {
        var params = new HashMap<String, String>();
        params.put("client_id", clientId);
        params.put("client_secret", ModGardenBackend.DOTENV.get("DISCORD_CLIENT_SECRET"));
        params.put("code", code);
        params.put("grant_type", "authorization_code");
        params.put("redirect_uri", "http://localhost:7070/oauth2/discord/redirect");
        params.put("scope", SCOPE);
        return params;
    }

    public Map<String, String> getRefreshBody(String refreshToken) {
        var params = new HashMap<String, String>();
        params.put("client_id", clientId);
        params.put("client_secret", ModGardenBackend.DOTENV.get("DISCORD_CLIENT_SECRET"));
        params.put("grant_type", "refresh_token");
        params.put("refresh_token", refreshToken);
        return params;
    }

    private static String createBody(Map<String, String> params) {
        return params.entrySet()
                .stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }
}
