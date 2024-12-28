package net.modgarden.backend.util;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class KeyUtils {
	public static PrivateKey decodePem(String pem, String algorithm) throws InvalidKeySpecException, NoSuchAlgorithmException {
		String[] split = pem.split("\n");
		String[] removed = new String[split.length - 2];
		System.arraycopy(split, 1, removed, 0, split.length - 2);
		String encoded = String.join("", removed);

		byte[] decoded = Base64.getDecoder().decode(encoded);
        decoded = buildPkcs8KeyFromPkcs1Key(decoded);

		KeyFactory keyFactory = KeyFactory.getInstance(algorithm);
		PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
		return keyFactory.generatePrivate(keySpec);
	}

    private static byte[] buildPkcs8KeyFromPkcs1Key(byte[] innerKey) {
        var result = new byte[innerKey.length + 26];
        System.arraycopy(Base64.getDecoder().decode("MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKY="), 0, result, 0, 26);
        System.arraycopy(BigInteger.valueOf(result.length - 4).toByteArray(), 0, result, 2, 2);
        System.arraycopy(BigInteger.valueOf(innerKey.length).toByteArray(), 0, result, 24, 2);
        System.arraycopy(innerKey, 0, result, 26, innerKey.length);
        return result;
    }
}
