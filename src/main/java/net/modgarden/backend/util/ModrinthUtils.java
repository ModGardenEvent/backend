package net.modgarden.backend.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.modgarden.backend.oauth.client.ModrinthOAuthClient;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpResponse;

public class ModrinthUtils {
	public static MetadataUtils.ExternalData getModrinthExternalData(@NotNull ModrinthOAuthClient authClient,
																	 @NotNull String modrinthProjectId) throws Exception {
		HttpResponse<InputStream> projectResponse = authClient
				.get(
						"v3/project/" + modrinthProjectId,
						HttpResponse.BodyHandlers.ofInputStream()
				);

		try (
				InputStream projectStream = projectResponse.body();
				InputStreamReader projectStreamReader = new InputStreamReader(projectStream)
		) {
			JsonElement potentialProject = JsonParser.parseReader(projectStreamReader);
			if (!potentialProject.isJsonObject()) {
				throw new IllegalStateException("Attempted to get a non-JSON Object Modrinth Project whilst getting project metadata.");
			}
			JsonObject project = potentialProject.getAsJsonObject();

			String sourceUrl = getSourceUrlFromModrinthProject(project);

			return new MetadataUtils.ExternalData(sourceUrl);
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
