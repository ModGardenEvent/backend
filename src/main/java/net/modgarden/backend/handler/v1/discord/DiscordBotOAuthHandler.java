package net.modgarden.backend.handler.v1.discord;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.LinkCode;
import net.modgarden.backend.oauth.OAuthService;
import net.modgarden.backend.util.AuthUtil;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

public class DiscordBotOAuthHandler {
    public static void authModrinthAccount(Context ctx) {
        String code = ctx.queryParam("code");
        if (code == null) {
            ctx.status(422);
            ctx.result("Modrinth access code is not specified.");
            return;
        }

        var authClient = OAuthService.MODRINTH.authenticate();
        try {
            var tokenResponse = authClient.post("_internal/oauth/token",
                    HttpRequest.BodyPublishers.ofString(AuthUtil.createBody(getModrinthAuthorizationBody(code))),
                    HttpResponse.BodyHandlers.ofInputStream(),
                    "Content-Type", "application/x-www-form-urlencoded",
                    "Authorization", ModGardenBackend.DOTENV.get("MODRINTH_OAUTH_SECRET")
            );
            String token;
            try (InputStreamReader reader = new InputStreamReader(tokenResponse.body())) {
                JsonElement tokenJson = JsonParser.parseReader(reader);
                if (!tokenJson.isJsonObject() || !tokenJson.getAsJsonObject().has("access_token")) {
                    ctx.status(422);
                    ctx.result("Invalid Modrinth access token.");
                    return;
                }
                token = tokenJson.getAsJsonObject().get("access_token").getAsString();
            }

            var userResponse = authClient.get("v2/user",
                    HttpResponse.BodyHandlers.ofInputStream(),
                    "Content-Type", "application/x-www-form-urlencoded",
                    "Authorization", token
            );

            String userId;
            try (InputStreamReader reader = new InputStreamReader(userResponse.body())) {
                JsonElement userJson = JsonParser.parseReader(reader);
                if (!userJson.isJsonObject() || !userJson.getAsJsonObject().has("id")) {
                    ctx.status(500);
                    ctx.result("Failed to get user id from Modrinth access token.");
                    return;
                }
                userId = userJson.getAsJsonObject().get("id").getAsString();
            }
            String linkToken = AuthUtil.insertTokenIntoDatabase(ctx, userId, LinkCode.Service.MODRINTH);
            if (linkToken == null) {
                ctx.status(500);
                ctx.result("Internal error whilst generating token.");
                return;
            }
            ctx.status(200);
            ctx.result("Successfully created link code for Modrinth account.\n\n" +
                    "Your link code is: " + linkToken + "\n\n" +
                    "This code will expire when used or in approximately 15 minutes.\n\n" +
                    "Please return to Discord for Step 2.");
        } catch (IOException | InterruptedException ex) {
            ModGardenBackend.LOG.error("Failed to handle Modrinth OAuth response.", ex);
            ctx.status(500);
            ctx.result("Internal error.");
        }
    }

	public static void authMinecraftAccount(Context ctx) {
		// FIXME: Remove when implemented.
		if (true) {
			ctx.status(500);
			ctx.result("Minecraft account linking not implemented yet.");
			return;
		}

		String code = ctx.queryParam("code");
		if (code == null) {
			ctx.status(422);
			ctx.result("Modrinth access code is not specified.");
			return;
		}

		// FIXME: Microsoft -> Minecraft Java Account API Authentication.
		try {
			String userId = null;

			String linkToken = AuthUtil.insertTokenIntoDatabase(ctx, userId, LinkCode.Service.MINECRAFT);
			if (linkToken == null) {
				ctx.status(500);
				ctx.result("Internal error whilst generating token.");
				return;
			}
			ctx.status(200);
			ctx.result("Successfully created link code for Minecraft account.\n\n" +
					"Your link code is: " + linkToken + "\n\n" +
					"This code will expire when used or in approximately 15 minutes.\n\n" +
					"Please return to Discord for Step 2.");
		} catch (Exception ex) {
			ModGardenBackend.LOG.error("Failed to handle Minecraft OAuth response.", ex);
			ctx.status(500);
			ctx.result("Internal error.");
		}
	}

	private static Map<String, String> getModrinthAuthorizationBody(String code) {
		var params = new HashMap<String, String>();
		params.put("code", code);
		params.put("client_id", OAuthService.MODRINTH.clientId);
		params.put("redirect_uri", ModGardenBackend.URL + "/v1/discord/oauth/modrinth");
		params.put("grant_type", "authorization_code");
		return params;
	}

	private static Map<String, String> getMinecraftAuthorizationBody(String code) {
		var params = new HashMap<String, String>();
		params.put("code", code);
		params.put("client_id", OAuthService.MODRINTH.clientId);
		params.put("redirect_uri", ModGardenBackend.URL + "/v1/discord/oauth/minecraft");
		params.put("grant_type", "authorization_code");
		return params;
	}
}
