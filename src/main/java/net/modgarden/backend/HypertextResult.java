package net.modgarden.backend;

import io.javalin.http.Context;
import org.jetbrains.annotations.Nullable;

public final class HypertextResult<T> {
	private final boolean success;
	private final int status;
	private String message;
	private T object;

	public HypertextResult(int status, String message) {
		this.success = false;
		this.status = status;
		this.message = message;
	}

	public HypertextResult(T object) {
		this.success = true;
		this.status = 200;
		this.object = object;
	}

	public boolean isSuccess() {
		return success;
	}

	public int getStatus() {
		return status;
	}

	public String getMessage() {
		if (success) throw new IllegalStateException("result succeeded");
		return message;
	}

	public T getObject() {
		if (!success) throw new IllegalStateException("result failed");
		return object;
	}

	@Nullable
	public T unwrap(Context ctx) {
		if (!success) {
			ctx.result(message);
			ctx.status(status);
			return null;
		}

		return object;
	}
}
