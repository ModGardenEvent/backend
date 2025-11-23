package net.modgarden.backend.endpoint.v2.submission;

import io.javalin.http.Context;
import net.modgarden.backend.endpoint.Endpoint;
import org.jetbrains.annotations.NotNull;

public abstract class GetSubmissionEndpoint extends Endpoint {
	public GetSubmissionEndpoint(String path) {
		super(2, path);
	}

	@Override
	public abstract void handle(@NotNull Context ctx) throws Exception;
}
