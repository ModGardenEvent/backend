package net.modgarden.backend.endpoint.v2.events;

import net.modgarden.backend.endpoint.Endpoint;
import net.modgarden.backend.endpoint.EndpointPath;

@EndpointPath("/v2/events")
public abstract class EventsEndpoint extends Endpoint {
	protected EventsEndpoint(String path) {
		super(2, "events/" + path);
	}
}
