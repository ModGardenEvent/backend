package net.modgarden.backend.endpoint.internal.user;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.data.event.*;
import net.modgarden.backend.data.permission.Permissions;
import net.modgarden.backend.data.user.role.UserRole;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.Response;
import net.modgarden.backend.endpoint.internal.InternalEndpoint;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.POST;

@EndpointMethod(POST)
@EndpointPath("/internal/event/create")
public class CreateEventEndpoint extends InternalEndpoint {
	public CreateEventEndpoint() {
		super("event/create");
	}

	@Override
	protected Response onRequest(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception {
		DatabaseAccess db = DatabaseAccess.get();

		Request request = decodeBody(ctx, Request.CODEC);
		String newEventId = db.createEvent(
				request.genre(),
				request.slug(),
				request.metadata().name(),
				request.times(),
				request.platform()
		);

		return Response.created("/v2/events/" + newEventId);
	}

	public record Request(String genre,
	                      String slug,
	                      Metadata metadata,
	                      EventTimes times,
	                      EventPlatform platform) {
		public static final Codec<Request> CODEC = RecordCodecBuilder.create(inst -> inst.group(
				Genre.ID_CODEC
						.fieldOf("genre")
						.forGetter(Request::genre),
				Codec.STRING
						.fieldOf("slug")
						.forGetter(Request::slug),
				Metadata.CODEC
						.fieldOf("metadata")
						.forGetter(Request::metadata),
				EventTimes.CODEC
						.fieldOf("times")
						.forGetter(Request::times),
				Event.PLATFORM_CODEC
						.fieldOf("platform")
						.forGetter(Request::platform)
		).apply(inst, Request::new));
	}

	public record Metadata(String name) {
		public static final Codec<Metadata> CODEC = RecordCodecBuilder.create(inst -> inst.group(
				Codec.STRING.fieldOf("name").forGetter(Metadata::name)
		).apply(inst, Metadata::new));
	}
}
