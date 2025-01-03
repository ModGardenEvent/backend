package net.modgarden.backend.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.oauth.OAuthService;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class DiscordAuthTokenUtil {
    // TODO: Use this for web signups.
    public static JsonElement getUserFromCode(Context ctx) throws IOException, InterruptedException {
        var tokenReponse = HttpRequest.newBuilder(URI.create("https://discord.com/api/v10/oauth2/token"))
                .headers("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(createBody(getAuthorizationBody(ctx.queryParam("code")))))
                .build();
        var tokenStream = ModGardenBackend.HTTP_CLIENT.send(tokenReponse, HttpResponse.BodyHandlers.ofInputStream());
        JsonElement tokenElement = JsonParser.parseReader(new InputStreamReader(tokenStream.body()));
        if (tokenStream.statusCode() != 200) {
            ctx.result("Could not obtain token from Discord: " + tokenElement.getAsJsonObject().get("error_description").getAsString());
            ctx.status(tokenStream.statusCode());
            return null;
        }
        String accessToken = tokenElement.getAsJsonObject().get("access_token").getAsString();
        String tokenType = tokenElement.getAsJsonObject().get("token_type").getAsString();

        var userRequest = HttpRequest.newBuilder(URI.create("https://discord.com/api/v10/users/@me"))
                .header("Authorization", tokenType + " " + accessToken)
                .build();
        var userStream = ModGardenBackend.HTTP_CLIENT.send(userRequest, HttpResponse.BodyHandlers.ofInputStream());
        if (userStream.statusCode() != 200) {
            ctx.result("Could not obtain user from Discord.");
            ctx.status(userStream.statusCode());
            return null;
        }
        return JsonParser.parseReader(new InputStreamReader(userStream.body()));
    }

    private static Map<String, String> getAuthorizationBody(String code) {
        var params = new HashMap<String, String>();
        params.put("client_id", OAuthService.DISCORD.clientId);
        params.put("client_secret", ModGardenBackend.DOTENV.get("DISCORD_OAUTH_CLIENT_SECRET"));
        params.put("code", code);
        params.put("grant_type", "authorization_code");
        params.put("redirect_uri", "http://localhost:7070/register/discord");
        return params;
    }

    private static String createBody(Map<String, String> params) {
        return params.entrySet()
                .stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }
}
