package net.modgarden.backend.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpResponse;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.modgarden.backend.endpoint.exception.BadRequestException;
import net.modgarden.backend.endpoint.exception.HypertextException;
import net.modgarden.backend.endpoint.exception.InternalServerException;
import net.modgarden.backend.oauth.client.ModrinthOAuthClient;
import org.jetbrains.annotations.NotNull;

public class ModrinthUtils {
	public static MetadataUtils.ExternalData getModrinthExternalData(@NotNull ModrinthOAuthClient authClient,
																	 @NotNull String modrinthProjectId) throws HypertextException {
		HttpResponse<InputStream> projectResponse;
		try {
			 projectResponse = authClient
					.get(
							"v3/project/" + modrinthProjectId,
							HttpResponse.BodyHandlers.ofInputStream()
					);
		} catch (IOException | InterruptedException e) {
			throw new BadRequestException("Failed to get Modrinth project", e);
		}

		try (
				InputStream projectStream = projectResponse.body();
				InputStreamReader projectStreamReader = new InputStreamReader(projectStream)
		) {
			JsonElement potentialProject = JsonParser.parseReader(projectStreamReader);
			if (!potentialProject.isJsonObject()) {
				throw new InternalServerException("Attempted to get a non-JSON Object Modrinth Project whilst getting project metadata.");
			}
			JsonObject project = potentialProject.getAsJsonObject();

			String sourceUrl = getSourceUrlFromModrinthProject(project);

			return new MetadataUtils.ExternalData(sourceUrl);
		} catch (IOException e) {
			throw new InternalServerException("Failed to decode external Modrinth metadata.");
		}
	}

	public static String getSourceUrlFromModrinthProject(JsonObject project) {
		JsonElement linkUrls = project.get("link_urls");
		if (linkUrls.isJsonObject() && linkUrls.getAsJsonObject().has("source")) {
			JsonElement source = linkUrls.getAsJsonObject().get("source");
			return source.getAsJsonObject()
					.getAsJsonPrimitive("url")
					.getAsString();
		}
		return null;
	}
}
