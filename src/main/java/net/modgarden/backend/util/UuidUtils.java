package net.modgarden.backend.util;

import java.nio.ByteBuffer;
import java.util.UUID;

/// God damn it java
public final class UuidUtils {
	private UuidUtils() {}

	public static byte[] toBytes(UUID uuid) {
		ByteBuffer byteBuffer = ByteBuffer.allocate(16);
		byteBuffer.putLong(uuid.getMostSignificantBits());
		byteBuffer.putLong(uuid.getLeastSignificantBits());
		return byteBuffer.array();
	}

	public static UUID fromBytes(byte[] uuid) {
		ByteBuffer byteBuffer = ByteBuffer.wrap(uuid);
		long mostSignificantBits = byteBuffer.getLong();
		long leastSignificantBits = byteBuffer.getLong();
		return new UUID(mostSignificantBits, leastSignificantBits);
	}

	public static byte[] randomBytes() {
		return toBytes(UUID.randomUUID());
	}
}
