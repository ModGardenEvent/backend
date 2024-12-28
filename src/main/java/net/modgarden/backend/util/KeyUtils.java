package net.modgarden.backend.util;

import net.modgarden.backend.ModGardenBackend;
import org.apache.hc.client5.http.utils.Base64;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;

public class KeyUtils {
	public static PrivateKey decodePem(String pem, String algorithm) throws InvalidKeySpecException, NoSuchAlgorithmException {
		String[] split = pem.split("\n");
		String[] removed = new String[split.length - 2];
		System.arraycopy(split, 1, removed, 0, split.length - 2);
		String encoded = String.join("", removed);

		ModGardenBackend.LOG.info(encoded);

		byte[] decoded = Base64.decodeBase64(encoded);

		KeyFactory keyFactory = KeyFactory.getInstance(algorithm);
		PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
		return keyFactory.generatePrivate(keySpec);
	}
}
