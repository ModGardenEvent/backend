package net.modgarden.backend.endpoint.internal.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.data.event.*;
import net.modgarden.backend.data.event.game.MinecraftEventPlatform;
import net.modgarden.backend.data.permission.Permissions;
import net.modgarden.backend.data.user.role.UserRole;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.Response;
import net.modgarden.backend.endpoint.exception.BadRequestException;
import net.modgarden.backend.endpoint.internal.InternalEndpoint;
import net.modgarden.backend.util.NullableWrapper;
import net.modgarden.backend.util.codec.NullableCodec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.POST;

@EndpointMethod(POST)
@EndpointPath("/internal/event/modify/{genre_id}/{event_id}")
public class ModifyEventEndpoint extends InternalEndpoint {
	public ModifyEventEndpoint() {
		super("event/modify/{genre_id}/{event_id}");
	}

	@Override
	protected Response onRequest(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception {
		DatabaseAccess db = DatabaseAccess.get();

		Request request = decodeBody(ctx, Request.CODEC);
		String genreIdToModify = ctx.pathParam("genre_id");
		String eventIdToModify = ctx.pathParam("event_id");

		Event event = db.getEventBySlug(db.getGenreSlug(genreIdToModify), db.getEventSlug(genreIdToModify, eventIdToModify));
		String eventId = event.id();

		EventMetadata.Modifiable metadata = request.metadata();
		if (metadata != null) {
			if (metadata.name() != null) {
				db.setEventName(eventId, metadata.name());
			}
			if (metadata.description() != null) {
				db.setEventDescription(eventId, metadata.description().value());
			}
		}

		EventTimes.Modifiable times = request.times();
		if (times != null) {
			if (times.registrationOpen() != null) {
				db.setEventRegistrationOpen(eventId, times.registrationOpen());
			}
			if (times.registrationClose() != null) {
				db.setEventRegistrationClose(eventId, times.registrationClose());
			}
			if (times.developmentStart() != null) {
				db.setEventDevelopmentStart(eventId, times.developmentStart());
			}
			if (times.developmentEnd() != null) {
				db.setEventDevelopmentEnd(eventId, times.developmentEnd());
			}
			if (times.packFreeze() != null) {
				db.setEventRegistrationClose(eventId, times.packFreeze());
			}
		}

		if (request.platform() != null) {
			//noinspection SwitchStatementWithTooFewBranches // future proofing
			switch (request.platform().game()) {
				case MinecraftEventPlatform.ID -> {
					MinecraftEventPlatform platform = (MinecraftEventPlatform) request.platform();
					db.setEventMcModLoader(eventId, platform.modLoader());
					db.setEventMcGameVersion(eventId, platform.gameVersion());
				}
				default -> throw new BadRequestException("Unsupported event platform '" + request.platform().game() + "'");
			}
		}

		for (Map.Entry<String, NullableWrapper<String>> entry : request.roles().entrySet()) {
			String roleKey = entry.getKey();
			NullableWrapper<String> roleId = entry.getValue();

			if (roleId.isPresent()) {
				db.addUserRoleToEvent(eventId, roleKey, roleId.value());
			} else {
				db.removeUserRoleFromEvent(eventId, roleKey);
			}
		}

		return Response.ok();
	}

	public record Request(@Nullable EventMetadata.Modifiable metadata,
	                      @Nullable EventTimes.Modifiable times,
	                      @Nullable EventPlatform platform,
	                      Map<String, NullableWrapper<String>> roles) {
		public static final Codec<Request> CODEC = RecordCodecBuilder.create(inst -> inst.group(
				EventMetadata.Modifiable.CODEC
						.optionalFieldOf("metadata")
						.forGetter(o -> Optional.ofNullable(o.metadata)),
				EventTimes.Modifiable.CODEC
						.optionalFieldOf("times")
						.forGetter(o -> Optional.ofNullable(o.times)),
				Event.PLATFORM_CODEC
						.optionalFieldOf("platform")
						.forGetter(o -> Optional.ofNullable(o.platform)),
				Codec.unboundedMap(Codec.STRING, NullableCodec.nullable(UserRole.ID_CODEC))
						.optionalFieldOf("roles", Collections.emptyMap())
						.forGetter(Request::roles)
		).apply(inst, (metadata, times, platform, roles) ->
				new Request(metadata.orElse(null), times.orElse(null), platform.orElse(null), roles)));
	}
}
