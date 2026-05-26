package net.modgarden.backend.endpoint.v2.projects;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.POST;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.data.permission.Permission;
import net.modgarden.backend.data.permission.PermissionPredicate;
import net.modgarden.backend.data.permission.PermissionScope;
import net.modgarden.backend.data.permission.Permissions;
import net.modgarden.backend.database.DatabaseAccess;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.Response;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@EndpointMethod(POST)
@EndpointPath("/v2/projects")
public class CreateProjectEndpoint extends AuthorizedProjectEndpoint {
	public CreateProjectEndpoint() {
		super(PermissionScope.USER, true);
	}

	@Override
	public Response onRequest(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception {
		Request request = decodeBody(ctx, Request.CODEC);

		DatabaseAccess db = DatabaseAccess.get();
		String projectId = db.createProject(userId, request.name());

		return Response.created("/v2/projects/" + projectId);
	}

	@Nullable
	@Override
	protected PermissionPredicate requiredPermissions() {
		return PermissionPredicate.all(Permission.PARTICIPATE);
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
