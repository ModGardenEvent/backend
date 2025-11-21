package net.modgarden.backend.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.Landing;
import net.modgarden.backend.data.event.Project;
import net.modgarden.backend.oauth.OAuthService;
import net.modgarden.backend.oauth.client.ModrinthOAuthClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.*;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

// Imo, it's okay to hardcode this to Fabric for now.
// Especially considering we likely won't be running events outside it any time soon.
public class MetadataUtils {
	private static final String USER_AGENT = "ModGardenEvent/backend/" + Landing.getInstance().version() + " (modgarden.net)";

	public static Project.Metadata getMetadataFromModrinth(String modrinthProjectId,
														   String modrinthVersionId) throws Exception {
		ModrinthOAuthClient authClient = OAuthService.MODRINTH.authenticate();

		ExternalData externalData = ModrinthUtils.getModrinthExternalData(authClient, modrinthProjectId);

		HttpResponse<InputStream> versionResponse = authClient
				.get(
						"v3/version/" + modrinthVersionId,
						HttpResponse.BodyHandlers.ofInputStream()
				);
		try (
				InputStream versionStream = versionResponse.body();
				InputStreamReader versionStreamReader = new InputStreamReader(versionStream)
		) {
			JsonElement potentialVersion = JsonParser.parseReader(versionStreamReader);
			if (!potentialVersion.isJsonObject()) {
				throw new IllegalStateException("Attempted to get a non-JSON Object Modrinth Version whilst getting project metadata.");
			}
			JsonObject version = potentialVersion.getAsJsonObject();
			URI jarUri = null;
			for (JsonElement potentialFile : version.getAsJsonArray("files")) {
				if (potentialFile.isJsonObject()) {
					JsonObject file = potentialFile.getAsJsonObject();
					if (file.getAsJsonPrimitive("primary").getAsBoolean()) {
						jarUri = URI.create(file.getAsJsonPrimitive("url").getAsString());
						break;
					}
				}
			}

			if (jarUri == null) {
				throw new IllegalStateException("Could not find valid primary version URL from Modrinth version whilst getting project metadata.");
			}

			List<String> loaders = new ArrayList<>();
			for (JsonElement element : version.getAsJsonArray("loaders")) {
				loaders.add(element.getAsJsonPrimitive().getAsString());
			}

			if (loaders.contains("fabric")) {
				return getMetadataFromFabricModJson(jarUri, externalData);
			}
			throw new UnsupportedOperationException("All modloaders associated with the specified version are not implemented.");
		}
	}

	public static Project.Metadata getMetadataFromFabricModJson(@NotNull URI jarUri,
																@NotNull ExternalData externalData) throws Exception {
		var request = HttpRequest.newBuilder()
				.header("User-Agent", USER_AGENT)
				.uri(jarUri)
				.build();

		Path temporaryFolder = Path.of("./.tmp");
		HttpResponse<Path> response = ModGardenBackend.HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofFile(temporaryFolder));
		Path temporaryFilePath = response.body();

		Project.Metadata metadata;
		try (
				JarFile jarFile = new JarFile(temporaryFilePath.toFile());
				InputStream fmjStream = getFmjAsStream(jarFile);
				InputStreamReader fmjStreamReader = new InputStreamReader(fmjStream)
		) {
			JsonElement potentialFmj = JsonParser.parseReader(fmjStreamReader);
			if (!potentialFmj.isJsonObject()) {
				throw new IllegalStateException("Attempted to get a non-JSONObject fabric.mod.json whilst getting project metadata.");
			}

			JsonObject fmj = potentialFmj.getAsJsonObject();

			String modId = fmj.getAsJsonPrimitive("id").getAsString();
			String name = fmj.getAsJsonPrimitive("name").getAsString();
			String description = fmj.getAsJsonPrimitive("description").getAsString();

			String sourceUrl = getFmjSourceUrl(fmj, externalData);
			// TODO: Handle Icon and Banner Uploads to CDN.
			String iconUrl = "placeholder";
			String bannerUrl = "placeholder";

			metadata = new Project.Metadata(
					modId,
					name,
					description,
					sourceUrl,
					iconUrl,
					bannerUrl
			);
		}

		if (Files.deleteIfExists(temporaryFilePath)) {
			if (Files.isDirectory(temporaryFolder)) {
				try (var directoryStream = Files.newDirectoryStream(temporaryFolder)) {
					if (!directoryStream.iterator().hasNext()) {
						Files.deleteIfExists(temporaryFolder);
					}
				}
			}
		}

		return metadata;
	}

	private static InputStream getFmjAsStream(JarFile file) throws Exception {
		ZipEntry entry = file.getEntry("fabric.mod.json");
		if (entry != null) {
			return file.getInputStream(entry);
		}
		throw new NullPointerException("The specified JAR is not a Fabric mod.");
	}

	private static String getFmjSourceUrl(JsonObject fmj, ExternalData data) {
		if (fmj.has("contact")) {
			JsonElement contact = fmj.getAsJsonObject("contact");
			if (contact.getAsJsonObject().has("sources")) {
				return contact.getAsJsonObject().getAsJsonPrimitive("sources").getAsString();
			}
		}
		if (data.externalSourceUrl() != null) {
			return data.externalSourceUrl();
		}
		throw new RuntimeException("Could not find source URL from either fabric.mod.json or external data.");
	}

	public record ExternalData(@Nullable String externalSourceUrl,
							   @Nullable String externalIconUrl,
							   @Nullable String externalBannerUrl) {

	}
}
