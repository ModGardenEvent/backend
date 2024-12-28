package net.modgarden.backend.oauth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.oauth.client.OAuthClientSupplier;
import net.modgarden.backend.oauth.client.OAuthClient;
import net.modgarden.backend.oauth.client.GithubOAuthClient;
import net.modgarden.backend.util.KeyUtils;
import org.apache.hc.client5.http.utils.Base64;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;

public enum OAuthService {
	GITHUB("Iv23li4vLb7sDuZOiRmf", OAuthService::authenticateGithub);

	private final String CLIENT_ID;
	private final OAuthClientSupplier AUTH_SUPPLIER;


	OAuthService(String clientId, OAuthClientSupplier supplier) {
		CLIENT_ID = clientId;
		AUTH_SUPPLIER = supplier;
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
					.signWith(key)
					.compact();

			return new GithubOAuthClient(jwt);
		} catch (IOException | InvalidKeySpecException | NoSuchAlgorithmException ex) {
			throw new RuntimeException(ex);
		}
	}

	@NotNull
	public OAuthClient authenticate() {
		return AUTH_SUPPLIER.authenticate(CLIENT_ID);
	}
}
