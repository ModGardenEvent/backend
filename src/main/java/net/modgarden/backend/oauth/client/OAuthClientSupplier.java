package net.modgarden.backend.oauth.client;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

@FunctionalInterface
public interface OAuthClientSupplier {
	@NotNull
	OAuthClient authenticate(String clientId);
}
