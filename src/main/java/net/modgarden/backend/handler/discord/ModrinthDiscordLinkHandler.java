package net.modgarden.backend.handler.discord;

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
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class ModrinthDiscordLinkHandler {
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
                    HttpRequest.BodyPublishers.ofString(AuthUtil.createBody(getAuthorizationBody(code))),
                    HttpResponse.BodyHandlers.ofInputStream(),
                    "Content-Type", "application/x-www-form-urlencoded",
                    "Authorization", ModGardenBackend.DOTENV.get("MODRINTH_OAUTH_SECRET")
            );
            String token;
            try (InputStreamReader reader = new InputStreamReader(tokenResponse.body())) {
                JsonElement tokenJson = JsonParser.parseReader(reader);
                if (!tokenJson.isJsonObject() || !tokenJson.getAsJsonObject().has("access_token")) {
                    ctx.status(500);
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
            String linkToken = insertTokenIntoDatabase(ctx, userId);
            ctx.status(200);
            ctx.result("Successfully created link code for Modrinth account.\n\n" +
                    "Your link code is: " + linkToken + "\n\n" +
                    "This code will expire when used or in approximately 15 minutes.\n\n" +
                    "Please return to Discord for Step 2.");
        } catch (IOException | InterruptedException ex) {
            ModGardenBackend.LOG.error("Failed to handle Modrinth OAuth response: ", ex);
            ctx.status(500);
            ctx.result("Internal Error.");
        }
    }

    public static String insertTokenIntoDatabase(Context ctx, String modrinthId) {
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             var checkStatement = connection.prepareStatement("SELECT 1 FROM link_codes WHERE code = ?");
             var insertStatement = connection.prepareStatement("INSERT INTO link_codes(code, account_id, service, expiration_time) VALUES (?, ?, ?, ?)")) {
            String token = null;
            while (token == null) {
                checkStatement.clearParameters();
                String potential = AuthUtil.generateRandomToken();
                checkStatement.setString(1, potential);
                ResultSet result = checkStatement.executeQuery();
                if (!result.getBoolean(1))
                    token = potential;
            }
            insertStatement.setString(1, token);
            insertStatement.setString(2, modrinthId);
            insertStatement.setString(3, LinkCode.Service.MODRINTH.serializedName());
            insertStatement.setLong(4, AuthUtil.getTokenExpirationTime());
            insertStatement.execute();
            return token;
        } catch (SQLException ex) {
            ModGardenBackend.LOG.error("Exception in SQL query.", ex);
            ctx.result("Internal Error.");
            ctx.status(500);
        }
        return null;
    }

    private static Map<String, String> getAuthorizationBody(String code) {
        var params = new HashMap<String, String>();
        params.put("code", code);
        params.put("client_id", OAuthService.MODRINTH.clientId);
        params.put("redirect_uri", ModGardenBackend.URL + "/link/discord/modrinth");
        params.put("grant_type", "authorization_code");
        return params;
    }
}
