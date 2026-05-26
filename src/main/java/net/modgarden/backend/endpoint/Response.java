package net.modgarden.backend.endpoint;

import java.util.Map;

import net.modgarden.backend.endpoint.exception.HypertextException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// A non-error HTTP response.
///
/// For HTTP errors, see [HypertextException].
public class Response {
	private final int status;
	private final @Nullable Object body;
	private final Map<String, String> headers;

	private Response(int status) {
		this.status = status;
		this.body = null;
		this.headers = Map.of();
	}

	private Response(int status, @NotNull Object body) {
		this.status = status;
		this.body = body;
		this.headers = Map.of();
	}

	private Response(int status, @Nullable Object body, Map<String, String> headers) {
		this.status = status;
		this.body = body;
		this.headers = headers;
	}

	public static Response ok(Object body) {
		return new Response(200, body);
	}

	public static Response ok() {
		return new Response(200);
	}

	public static Response created(String location) {
		return new Response(201, null, Map.of("Location", location));
	}

	public int getStatus() {
		return this.status;
	}

	@Nullable
	public Object getBody() {
		return this.body;
	}

	public Map<String, String> getHeaders() {
		return this.headers;
	}
}
