package net.modgarden.backend.oauth.client;

import java.io.IOException;

public interface OAuthClient {
    String get(String endpoint) throws IOException, InterruptedException;
}
