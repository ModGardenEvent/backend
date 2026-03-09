package net.modgarden.backend.endpoint.v2.projects;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.POST;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.data.Permission;
import net.modgarden.backend.data.PermissionScope;
import net.modgarden.backend.data.Permissions;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.v2.AuthorizedProjectEndpoint;
import org.jetbrains.annotations.NotNull;

@EndpointMethod(POST)
@EndpointPath("/v2/projects")
public class CreateProjectEndpoint extends AuthorizedProjectEndpoint {
	public CreateProjectEndpoint() {
		super(PermissionScope.USER, true);
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
		ctx.header("Location", "/v2/projects/" + projectId);
	}

	@NotNull
	@SuppressWarnings("DataFlowIssue") // we don't care since this endpoint doesn't require project perms
	@Override
	protected String getProjectId(Context ctx) {
		return null;
	}

	public record Request(String name) {
		public static final Codec<Request> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("name").forGetter(Request::name)
		).apply(instance, Request::new));
	}
}
