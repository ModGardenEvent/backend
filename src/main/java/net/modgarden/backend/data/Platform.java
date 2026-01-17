package net.modgarden.backend.data;

import com.mojang.serialization.MapCodec;

import java.sql.Connection;

public interface Platform {
	String typeName();
	MapCodec<? extends Platform> getCodec();

	void addToDatabase(Connection connection, String gardenProjectId, String submissionId) throws Exception;

	static <T extends Platform> MapCodec<Platform> fromMapCodec(MapCodec<T> codec) {
		//noinspection unchecked
		return codec.xmap(
				t -> t,
				metadata -> (T)metadata // We can't encode unless an unsafe cast happens.
		);
	}
}
