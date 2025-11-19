package net.modgarden.backend.data;

import com.mojang.serialization.MapCodec;

public interface Platform {
	String getName();
	MapCodec<? extends Platform> getCodec();
}
