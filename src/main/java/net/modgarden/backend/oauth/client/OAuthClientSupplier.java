package net.modgarden.backend.oauth.client;

import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface OAuthClientSupplier {
	@NotNull
	OAuthClient authenticate(String clientId);
}
