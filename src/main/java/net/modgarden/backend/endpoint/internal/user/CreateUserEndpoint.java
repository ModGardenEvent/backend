package net.modgarden.backend.endpoint.internal.user;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.Permissions;
import net.modgarden.backend.data.user.User;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.internal.InternalEndpoint;
import org.jetbrains.annotations.NotNull;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.POST;

@EndpointMethod(POST)
@EndpointPath("/internal/user/create")
public class CreateUserEndpoint extends InternalEndpoint {
	public CreateUserEndpoint() {
		super("user/create");
	}

	@Override
	protected void onRequest(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception {
		DatabaseAccess db = DatabaseAccess.get();

		Request request = decodeBody(ctx, Request.CODEC);
		String newUserId = db.createUser(request.username());

		ctx.result(ModGardenBackend.URL + "/v2/users/" + newUserId);
		ctx.status(201);
	}

	public record Request(String username) {
		public static final Codec<Request> CODEC = RecordCodecBuilder.create(inst -> inst.group(
				User.NEW_USERNAME_CODEC.fieldOf("username").forGetter(Request::username)
		).apply(inst, Request::new));
	}
}
