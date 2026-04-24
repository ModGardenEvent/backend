package net.modgarden.backend.util;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.LandingPage;
import net.modgarden.backend.data.Metadata;
import net.modgarden.backend.data.event.metadata.ModMetadata;
import net.modgarden.backend.endpoint.exception.HypertextException;
import net.modgarden.backend.endpoint.exception.NotFoundException;
import net.modgarden.backend.oauth.OAuthService;
import net.modgarden.backend.oauth.client.ModrinthOAuthClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// Imo, it's okay to hardcode this to Fabric for now.
/// We can definitely implement resource/data-packs later.
/// @see Metadata
/// @see ModMetadata
public class MetadataUtils {
	private static final String USER_AGENT = "ModGardenEvent/backend/" + LandingPage.getInstance().version() + " (modgarden.net)";

	public static Metadata getMetadataFromModrinth(String modrinthProjectId,
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
			URI primaryUri = null;
			for (JsonElement potentialFile : version.getAsJsonArray("files")) {
				if (potentialFile.isJsonObject()) {
					JsonObject file = potentialFile.getAsJsonObject();
					if (file.getAsJsonPrimitive("primary").getAsBoolean()) {
						primaryUri = URI.create(file.getAsJsonPrimitive("url").getAsString());
						break;
					}
				}
			}

			if (primaryUri == null) {
				throw new IllegalStateException("Could not find valid primary download URL from Modrinth version whilst getting project metadata.");
			}

			for (JsonElement element : version.getAsJsonArray("loaders")) {
				String loader = element.getAsJsonPrimitive().getAsString();
				if (loader.equals("fabric")) {
					return getMetadataFromFabricModJson(primaryUri, externalData);
				}
			}

			throw new HypertextException(422, "All mod-loaders associated with the specified version are unsupported.");
		}
	}

	public static Metadata getMetadataFromFabricModJson(@NotNull URI jarUri,
														@NotNull ExternalData externalData) throws Exception {
		var request = HttpRequest.newBuilder()
				.header("User-Agent", USER_AGENT)
				.uri(jarUri)
				.build();

		Path temporaryFolder = Path.of("./.tmp");
		HttpResponse<Path> response = ModGardenBackend.HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofFile(temporaryFolder));
		Path temporaryFilePath = response.body();

		Metadata metadata;
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

			metadata = new ModMetadata(
					modId,
					name,
					description,
					sourceUrl
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
		throw new HypertextException(422, "The specified JAR is not a Fabric mod.");
	}

	private static String getFmjSourceUrl(JsonObject fmj, ExternalData data) throws NotFoundException {
		if (data.externalSourceUrl() != null) {
			return data.externalSourceUrl();
		}
		if (fmj.has("contact")) {
			JsonElement contact = fmj.getAsJsonObject("contact");
			if (contact.getAsJsonObject().has("sources")) {
				return contact.getAsJsonObject().getAsJsonPrimitive("sources").getAsString();
			}
		}
		throw new NotFoundException("Could not find source URL from either fabric.mod.json or external data.");
	}

	public record ExternalData(@Nullable String externalSourceUrl) {}
}
