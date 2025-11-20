package net.modgarden.backend.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.modgarden.backend.oauth.OAuthService;
import net.modgarden.backend.oauth.client.ModrinthOAuthClient;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpResponse;

public class ModrinthUtils {
	public static String getSlugFromId(String modrinthProjectId) throws Exception {
		ModrinthOAuthClient authClient = OAuthService.MODRINTH.authenticate();
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
				throw new IllegalStateException("Attempted to get a non-JSON Object Modrinth Project whilst getting slug from ID.");
			}
			JsonObject project = potentialProject.getAsJsonObject();
			return project.getAsJsonPrimitive("slug").getAsString();
		}
	}

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
			String iconUrl = project.getAsJsonPrimitive("icon_url").getAsString();
			String bannerUrl = getBannerUrlFromModrinthProject(project);

			return new MetadataUtils.ExternalData(sourceUrl, iconUrl, bannerUrl);
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

	public static String getBannerUrlFromModrinthProject(JsonObject project) {
		if (project.has("gallery")) {
			for (JsonElement galleryElement : project.getAsJsonArray("gallery")) {
				JsonObject galleryObject = galleryElement.getAsJsonObject();
				if (galleryObject.getAsJsonPrimitive("featured").getAsBoolean()) {
					return galleryObject.getAsJsonPrimitive("url").getAsString();
				}
			}
		}
		return null;
	}
}
