package net.modgarden.backend.endpoint.v2.project;

import com.mojang.serialization.Codec;
import io.javalin.http.Context;
import net.modgarden.backend.data.NaturalId;
import net.modgarden.backend.data.PermissionScope;
import net.modgarden.backend.data.Permissions;
import net.modgarden.backend.endpoint.EndpointMethod;
import net.modgarden.backend.endpoint.EndpointPath;
import net.modgarden.backend.endpoint.v2.AuthorizedProjectEndpoint;
import org.jetbrains.annotations.NotNull;

import static net.modgarden.backend.endpoint.EndpointMethod.Method.POST;

@EndpointMethod(POST)
@EndpointPath("/v2/project/create")
public class CreateProjectEndpoint extends AuthorizedProjectEndpoint {
	public CreateProjectEndpoint() {
		super("create", PermissionScope.USER, true);
	}

	@Override
	public void handle(@NotNull Context ctx, String userId, Permissions scopePermissions) throws Exception {
		String generatedProjectId = NaturalId.generate("projects", "id", null, 5);
		Request request = decodeBody(ctx, Request.CODEC)
				.unwrap(ctx);

		if (request == null) return;

		try (
				var connection = this.getDatabaseConnection();
				var projectStatement = connection.prepareStatement("""
					INSERT INTO projects (id)
					VALUES (?)
				""");
				var projectDraftMetadataStatement = connection.prepareStatement("""
					INSERT INTO project_draft_metadata (project_id, name)
					VALUES (?, ?)
				""");
				var projectRolesStatement = connection.prepareStatement("""
					INSERT OR IGNORE INTO project_roles (project_id, user_id, permissions)
					VALUES (?, ?, 1)
				""")
		) {
			projectStatement.setString(1, generatedProjectId);
			projectStatement.executeUpdate();

			projectDraftMetadataStatement.setString(1, generatedProjectId);
			projectDraftMetadataStatement.setString(2, request.name());
			projectDraftMetadataStatement.executeUpdate();

			projectRolesStatement.setString(1, generatedProjectId);
			projectRolesStatement.setString(2, userId);
			projectRolesStatement.executeUpdate();

			ctx.status(201);
		}
	}

	public record Request(String name) {
		public static final Codec<Request> CODEC = Codec.STRING.xmap(Request::new, Request::name);
	}
}
