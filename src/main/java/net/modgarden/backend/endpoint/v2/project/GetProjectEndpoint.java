package net.modgarden.backend.endpoint.v2.project;

import io.javalin.http.Context;
import net.modgarden.backend.endpoint.Endpoint;
import net.modgarden.backend.endpoint.EndpointPath;
import org.jetbrains.annotations.NotNull;

// TODO: Require view project permissions or being a member of the project to view draft projects.
@EndpointPath("/v2/project")
public abstract class GetProjectEndpoint extends Endpoint {
	public GetProjectEndpoint(String path) {
		super(2, "project/" + path);
	}

	@Override
	public abstract void handle(@NotNull Context ctx) throws Exception;
}
