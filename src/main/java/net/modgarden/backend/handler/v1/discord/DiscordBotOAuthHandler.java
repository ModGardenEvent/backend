package net.modgarden.backend.handler.v1.discord;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.*;
import io.javalin.http.Context;
import io.jsonwebtoken.Jwts;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.LinkCode;
import net.modgarden.backend.oauth.OAuthService;
import net.modgarden.backend.util.AuthUtil;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class DiscordBotOAuthHandler {
    public static void authModrinthAccount(Context ctx) {
        String code = ctx.queryParam("code");
        if (code == null) {
            ctx.status(400);
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
                    ctx.status(400);
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

	private static final Cache<String, String> CODE_CHALLENGE_TO_VERIFIER = CacheBuilder.newBuilder()
			.expireAfterWrite(15, TimeUnit.MINUTES)
			.build();

	public static void getMicrosoftCodeChallenge(Context ctx) {
		if (!("Basic " + ModGardenBackend.DOTENV.get("DISCORD_OAUTH_SECRET")).equals(ctx.header("Authorization"))) {
			ctx.result("Unauthorized.");
			ctx.status(401);
			return;
		}
		ctx.status(200);
		try {
			ctx.result(createCodeChallenge());
		} catch (NoSuchAlgorithmException e) {
			ctx.result("Failed to generate code challenge, this shouldn't happen.");
			ctx.status(500);
		}
	}

	public static String createCodeChallenge() throws NoSuchAlgorithmException {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		var codeVerifier = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

		String codeChallenge;
		codeChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
				.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII)));

		CODE_CHALLENGE_TO_VERIFIER.put(codeChallenge, codeVerifier);

		ModGardenBackend.LOG.debug("Code Verifier: {}", codeVerifier);
		ModGardenBackend.LOG.debug("Code Challenge: {}", codeChallenge);

		return codeChallenge;
	}

	private static PublicKey minecraftPublicKey = null;

	public static void authMinecraftAccount(Context ctx) {
		String code = ctx.queryParam("code");
		if (code == null) {
			ctx.status(400);
			ctx.result("Microsoft access code is not specified.");
			return;
		}

		String challengeCode = ctx.queryParam("state");
		if (challengeCode == null) {
			ctx.status(400);
			ctx.result("Code challenge state is not specified.");
			return;
		}
		String verifier = CODE_CHALLENGE_TO_VERIFIER.getIfPresent(challengeCode);
		if (verifier == null) {
			ctx.status(400);
			ctx.result("Code challenge verifier has expired. Please retry.");
			return;
		}
		CODE_CHALLENGE_TO_VERIFIER.invalidate(challengeCode);

		try {
			String microsoftToken = null;
			var microsoftTokenRequest = HttpRequest.newBuilder(URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/token"))
					.header("Content-Type", "application/x-www-form-urlencoded")
					.headers("Origin", ModGardenBackend.URL + "/v1/discord/oauth/modrinth")
					.POST(HttpRequest.BodyPublishers.ofString(AuthUtil.createBody(getMicrosoftAuthorizationBody(code, verifier))));
			var microsoftTokenResponse = ModGardenBackend.HTTP_CLIENT.send(microsoftTokenRequest.build(), HttpResponse.BodyHandlers.ofInputStream());

			try (InputStreamReader microsoftTokenReader = new InputStreamReader(microsoftTokenResponse.body())) {
				JsonElement microsoftTokenJson = JsonParser.parseReader(microsoftTokenReader);
				if (microsoftTokenJson.isJsonObject()) {
					JsonPrimitive accessToken = microsoftTokenJson.getAsJsonObject().getAsJsonPrimitive("access_token");
					if (accessToken != null && accessToken.isString()) {
						microsoftToken = accessToken.getAsString();
					}
				}
			}

			if (microsoftToken == null) {
				ctx.status(500);
				ctx.result("Failed to get Microsoft access token from OAuth code.");
				return;
			}

			String xblToken = null;
			String userHash = null;
			var xblUserRequest = HttpRequest.newBuilder(URI.create("https://user.auth.xboxlive.com/user/authenticate"))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(getXboxLiveAuthenticationBody(microsoftToken)));
			var xblUserResponse = ModGardenBackend.HTTP_CLIENT.send(xblUserRequest.build(), HttpResponse.BodyHandlers.ofInputStream());

			try (InputStreamReader xblUserReader = new InputStreamReader(xblUserResponse.body())) {
				JsonElement xblUserJson = JsonParser.parseReader(xblUserReader);
				if (xblUserJson.isJsonObject()) {
					JsonElement token = xblUserJson.getAsJsonObject().get("Token");
					if (token != null && token.isJsonPrimitive() && token.getAsJsonPrimitive().isString()) {
						xblToken = token.getAsString();
					}
					JsonElement displayClaims = xblUserJson.getAsJsonObject().get("DisplayClaims");
					if (displayClaims != null && displayClaims.isJsonObject() && displayClaims.getAsJsonObject().get("xui").isJsonArray()) {
						JsonArray xui = displayClaims.getAsJsonObject().getAsJsonArray("xui");
						JsonElement uhs = xui.get(0);
						if (uhs.isJsonObject() && uhs.getAsJsonObject().getAsJsonPrimitive("uhs").isString()) {
							userHash = uhs.getAsJsonObject().getAsJsonPrimitive("uhs").getAsString();
						}
					}
				}
			}

			if (xblToken == null) {
				ctx.status(500);
				ctx.result("Failed to get Xbox Live access token from Microsoft access token.");
				return;
			}
			if (userHash == null) {
				ctx.status(500);
				ctx.result("Failed to get user hash from Microsoft access token.");
				return;
			}

			String xstsToken = null;
			var xblXstsRequest = HttpRequest.newBuilder(URI.create("https://xsts.auth.xboxlive.com/xsts/authorize"))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(getXboxLiveAuthorizationBody(xblToken)));
			var xblXstsResponse = ModGardenBackend.HTTP_CLIENT.send(xblXstsRequest.build(), HttpResponse.BodyHandlers.ofInputStream());
			if (xblXstsResponse.statusCode() == 401) {
				String errorResponse = "Could not authorize with Xbox Live.";
				try (InputStreamReader xerrReader = new InputStreamReader(xblXstsResponse.body())) {
					JsonElement xerrJson = JsonParser.parseReader(xerrReader);
					if (xerrJson.isJsonObject()) {
						JsonPrimitive xErr = xerrJson.getAsJsonObject().getAsJsonPrimitive("xErr");
						if (xErr.isNumber()) {
							long err = xErr.getAsLong();
							if (err == 2148916227L) {
								errorResponse = "You are banned from Xbox.";
							}
							if (err == 2148916233L) {
								errorResponse = "You do not have an Xbox account.";
							}
							if (err == 2148916235L) {
								errorResponse = "This account is from a country where Xbox Live is not available or banned.";
							}
							if (err == 2148916236L || err == 2148916237L) {
								errorResponse = "This account needs adult verification on the Xbox page. (Required in South Korea)";
							}
							if (err == 2148916238L) {
								errorResponse = "This account is owned by somebody under 18 years old and cannot proceed unless added to a family by an adult.";
							}
						}
					}
				}
				ctx.status(401);
				ctx.result(errorResponse);
				return;
			}

			try (InputStreamReader xblXstsReader = new InputStreamReader(xblXstsResponse.body())) {
				JsonElement xblXstsJson = JsonParser.parseReader(xblXstsReader);
				if (xblXstsJson.isJsonObject()) {
					JsonPrimitive token = xblXstsJson.getAsJsonObject().getAsJsonPrimitive("Token");
					if (token.getAsJsonPrimitive().isString()) {
						xstsToken = token.getAsString();
					}
					JsonObject displayClaims = xblXstsJson.getAsJsonObject().getAsJsonObject("DisplayClaims");
					JsonArray xui = displayClaims.getAsJsonArray("xui");
					JsonElement uhs = xui.get(0);
					if (uhs.isJsonPrimitive() && uhs.getAsJsonPrimitive().isString()) {
						if (!uhs.getAsString().equals(userHash)) {
							ctx.status(500);
							ctx.result("User hash between authentication and authorization do not match.");
							return;
						}
					}
				}
			}

			if (xstsToken == null) {
				ctx.status(500);
				ctx.result("Failed to get XSTS token from Microsoft access token.");
				return;
			}

			var minecraftServices = OAuthService.MINECRAFT_SERVICES.authenticate();

			String minecraftAccessToken = null;
			var minecraftAuthResponse = minecraftServices.post(
							"authentication/login_with_xbox",
							HttpRequest.BodyPublishers.ofString(getMinecraftAuthenticationBody(userHash, xstsToken)),
							HttpResponse.BodyHandlers.ofInputStream(),
							"Content-Type", "application/json",
							"Accept", "application/json"
					);

			try (InputStreamReader minecraftAuthReader = new InputStreamReader(minecraftAuthResponse.body())) {
				JsonElement minecraftAuthJson = JsonParser.parseReader(minecraftAuthReader);
				if (minecraftAuthJson.isJsonObject()) {
					JsonPrimitive accessToken = minecraftAuthJson.getAsJsonObject().getAsJsonPrimitive("access_token");
					if (accessToken.isString()) {
						minecraftAccessToken = accessToken.getAsString();
					}
				}
			}
			if (minecraftAccessToken == null) {
				ctx.status(500);
				ctx.result("Internal error whilst generating token.");
				return;
			}

			boolean ownsGame = false;
			var entitlementsResponse = minecraftServices.get("entitlements/mcstore",
					HttpResponse.BodyHandlers.ofInputStream(),
					"Authorization", "Bearer " + minecraftAccessToken);
			try (InputStreamReader entitlementsReader = new InputStreamReader(entitlementsResponse.body())) {
				JsonElement minecraftEntitlementsJson = JsonParser.parseReader(entitlementsReader);

				if (minecraftEntitlementsJson.isJsonObject()) {
					JsonArray items = minecraftEntitlementsJson.getAsJsonObject().getAsJsonArray("items");
					Optional<JsonPrimitive> javaSignaturePrimitive = items.asList().stream().filter(jsonElement ->  {
						if (!jsonElement.isJsonObject())
							return false;
						JsonPrimitive name = jsonElement.getAsJsonObject().getAsJsonPrimitive("name");
						if (name == null || !name.isString())
							return false;
						return "product_minecraft".equals(name.getAsString());
					}).map(jsonElement -> jsonElement.getAsJsonObject().getAsJsonPrimitive("signature")).filter(Objects::nonNull).findAny();

					if (javaSignaturePrimitive.isPresent() && javaSignaturePrimitive.get().isString()) {
						String javaSignature = javaSignaturePrimitive.get().getAsString();

						if (minecraftPublicKey == null) {
							URL resource = ModGardenBackend.class.getResource("/mojang_public.key");
							if (resource == null) {
								ctx.status(500);
								ctx.result("Mojang public key is not specified internally.");
								return;
							}
							String key = Files.readString(Path.of(resource.toURI()), Charset.defaultCharset());

							key = key.replace("-----BEGIN PUBLIC KEY-----", "")
									.replaceAll("\n", "")
									.replace("-----END PUBLIC KEY-----", "");

							byte[] bytes = Base64.getDecoder().decode(key);
							var keyFactory = KeyFactory.getInstance("RSA");
							var keySpec = new X509EncodedKeySpec(bytes);
							minecraftPublicKey = keyFactory.generatePublic(keySpec);
						}

						try {
							Jwts.parserBuilder()
									.setSigningKey(minecraftPublicKey)
									.build()
									.parseClaimsJws(javaSignature);
							ownsGame = true;
						} catch (Exception ignored) {
							// The account cannot be verified with Mojang's publickey, therefore they probably don't own the game.
						}
					}
				}
			}

			if (!ownsGame) {
				ctx.status(401);
				ctx.result("You do not own a copy of Minecraft. Please purchase a copy of the game to proceed.");
				return;
			}

			String uuid = null;
			var minecraftProfileResponse = minecraftServices.get("minecraft/profile",
					HttpResponse.BodyHandlers.ofInputStream(),
					"Authorization", "Bearer " + minecraftAccessToken);
			try (InputStreamReader minecraftProfileReader = new InputStreamReader(minecraftProfileResponse.body())) {
				JsonElement minecraftProfileJson = JsonParser.parseReader(minecraftProfileReader);
				if (minecraftProfileJson.isJsonObject()) {
					uuid = minecraftProfileJson.getAsJsonObject().getAsJsonPrimitive("id").getAsString();
				}
			}
			if (uuid == null) {
				ctx.status(500);
				ctx.result("Internal error whilst generating token.");
				return;
			}

			String linkToken = AuthUtil.insertTokenIntoDatabase(ctx, uuid, LinkCode.Service.MINECRAFT);
			if (linkToken == null) {
				ctx.status(500);
				ctx.result("Internal error whilst generating token.");
				return;
			}
			ctx.status(200);
			ctx.header("Content-Type", "application/json");
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
		var body = new HashMap<String, String>();
		body.put("code", code);
		body.put("client_id", OAuthService.MODRINTH.clientId);
		body.put("redirect_uri", ModGardenBackend.URL + "/v1/discord/oauth/modrinth");
		body.put("grant_type", "authorization_code");
		return body;
	}

	private static Map<String, String> getMicrosoftAuthorizationBody(String code, String verifier) {
		var body = new HashMap<String, String>();
		body.put("code", code);
		body.put("client_id", OAuthService.MINECRAFT_SERVICES.clientId);
		body.put("scope", "XboxLive.signIn");
		body.put("grant_type", "authorization_code");
		body.put("redirect_uri", ModGardenBackend.URL + "/v1/discord/oauth/minecraft");
		body.put("code_verifier", verifier);
		return body;
	}

	private static String getXboxLiveAuthenticationBody(String code) {
		var body = new JsonObject();
		var properties = new JsonObject();

		properties.addProperty("AuthMethod", "RPS");
		properties.addProperty("SiteName", "user.auth.xboxlive.com");
		properties.addProperty("RpsTicket", "d=" + code);

		body.add("Properties", properties);
		body.addProperty("RelyingParty", "http://auth.xboxlive.com");
		body.addProperty("TokenType", "JWT");

		return body.toString();
	}

	private static String getXboxLiveAuthorizationBody(String xblToken) {
		var body = new JsonObject();
		var properties = new JsonObject();

		var userTokens = new JsonArray();
		userTokens.add(xblToken);

		properties.addProperty("SandboxId", "RETAIL");
		properties.add("UserTokens", userTokens);

		body.add("Properties", properties);
		body.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
		body.addProperty("TokenType", "JWT");

		return body.toString();
	}

	private static String getMinecraftAuthenticationBody(String userHash, String xstsToken) {
		var body = new JsonObject();
		body.addProperty("identityToken", "XBL3.0 x=" + userHash + ";" + xstsToken);
		return body.toString();
	}
}
