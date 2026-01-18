package net.modgarden.backend.endpoint.v2.project;

import com.mojang.serialization.Codec;
import io.javalin.http.Context;
import net.modgarden.backend.data.NaturalId;
import net.modgarden.backend.data.Permission;
import net.modgarden.backend.data.PermissionScope;
import net.modgarden.backend.data.Permissions;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.v2.AuthorizedProjectEndpoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.POST;

@EndpointMethod(POST)
@EndpointPath("/v2/project/create")
public class CreateProjectEndpoint extends AuthorizedProjectEndpoint {
	public CreateProjectEndpoint() {
		super("create", PermissionScope.USER, true);
	}

	@Override
	public void onRequest(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception {
		if (this.requireAnyPermissions(ctx, scopePermissions,
				Permission.PARTICIPATE)) return;

		Request request = decodeBody(ctx, Request.CODEC)
				.unwrap(ctx);

		if (request == null) return;

		DatabaseAccess db = DatabaseAccess.get();
		String projectId = db.createProject(userId, request.name());

		ctx.status(201);
		ctx.header("Location", "/v2/project/" + projectId);
	}

	@NotNull
	@SuppressWarnings("DataFlowIssue") // we don't care since this endpoint doesn't require project perms
	@Override
	protected String getProjectId(Context ctx) {
		return null;
	}

	public record Request(String name) {
		public static final Codec<Request> CODEC = Codec.STRING.xmap(Request::new, Request::name);
	}
}
