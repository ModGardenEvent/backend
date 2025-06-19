package net.modgarden.backend.oauth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import net.modgarden.backend.oauth.client.*;
import net.modgarden.backend.util.KeyUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Date;

public enum OAuthService {
    DISCORD("1305609404837527612", OAuthService::authenticateDiscord),
    MODRINTH("Q2tuKyb4", OAuthService::authenticateModrinth),
	GITHUB("Iv23li4vLb7sDuZOiRmf", OAuthService::authenticateGithub),
	MINECRAFT_SERVICES(" e7ee42f6-e542-4ce6-9f7b-1d31941e84c6", OAuthService::authenticateMinecraftServices);

	public final String clientId;
	private final OAuthClientSupplier authSupplier;


	OAuthService(String cId, OAuthClientSupplier supplier) {
        clientId = cId;
        authSupplier = supplier;
	}

    @NotNull
    static OAuthClient authenticateDiscord(String unused) {
        return new DiscordOAuthClient();
    }

	@NotNull
    static OAuthClient authenticateModrinth(String unused) {
        return new ModrinthOAuthClient();
    }

	@NotNull
	static OAuthClient authenticateGithub(String clientId) {
		try {
			String pem = Files.readString(Path.of("./github.pem"), Charset.defaultCharset());

			PrivateKey key = KeyUtils.decodePem(pem, "RSA");

 			var jwt = Jwts.builder()
					.setIssuedAt(new Date())
					.setExpiration(new Date(System.currentTimeMillis() + 600))
					.setIssuer(clientId)
					.signWith(key, SignatureAlgorithm.RS256)
					.compact();

			return new GithubOAuthClient(jwt);
		} catch (IOException | InvalidKeySpecException | NoSuchAlgorithmException ex) {
			throw new RuntimeException(ex);
		}
	}

	static OAuthClient authenticateMinecraftServices(String unused) {
		return new MinecraftServicesOAuthClient();
	}

	@NotNull
	public <T extends OAuthClient> T authenticate() {
		return (T) authSupplier.authenticate(clientId);
	}
}
