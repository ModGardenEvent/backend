package net.modgarden.backend.endpoint.v2.genres;

import net.modgarden.backend.endpoint.Endpoint;
import net.modgarden.backend.endpoint.EndpointPath;

@EndpointPath("/v2/genres")
public abstract class GenresEndpoint extends Endpoint {
	protected GenresEndpoint(String path) {
		super(2, "genres/" + path);
	}

	protected GenresEndpoint() {
		super(2, "genres");
	}
}
