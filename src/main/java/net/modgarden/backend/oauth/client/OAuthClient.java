package net.modgarden.backend.oauth.client;

import java.io.IOException;
import java.net.http.HttpResponse;

public interface OAuthClient {
    default String get(String endpoint) throws IOException, InterruptedException {
        return getResponse(endpoint).body();
    }

    default HttpResponse<String> getResponse(String endpoint) throws IOException, InterruptedException {
        return getResponse(endpoint, HttpResponse.BodyHandlers.ofString());
    }

    <T> HttpResponse<T> getResponse(String endpoint, HttpResponse.BodyHandler<T> bodyHandler) throws IOException, InterruptedException;
}
