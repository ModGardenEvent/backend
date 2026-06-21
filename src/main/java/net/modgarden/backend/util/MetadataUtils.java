package net.modgarden.backend.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.LandingPage;
import net.modgarden.backend.data.project.ProjectMetadata;
import net.modgarden.backend.data.project.metadata.ModProjectMetadata;
import net.modgarden.backend.endpoint.exception.*;
import net.modgarden.backend.oauth.OAuthService;
import net.modgarden.backend.oauth.client.ModrinthOAuthClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// Imo, it's okay to hardcode this to Fabric for now.
/// We can definitely implement resource/data-packs later.
/// @see ProjectMetadata
/// @see ModProjectMetadata
public class MetadataUtils {
	private static final String USER_AGENT = "ModGardenEvent/backend/" + LandingPage.getInstance().version() + " (modgarden.net)";

	public static ProjectMetadata getMetadataFromModrinth(String modrinthProjectId,
	                                                      String modrinthVersionId) throws HypertextException {
		ModrinthOAuthClient authClient = OAuthService.MODRINTH.authenticate();

		HttpResponse<InputStream> versionResponse;
		try {
			versionResponse = authClient
					.get(
							"v3/version/" + modrinthVersionId,
							HttpResponse.BodyHandlers.ofInputStream()
					);
		} catch (IOException | InterruptedException e) {
			throw new BadRequestException("Failed to get version from Modrinth", e);
		}

		File tempFile = null;
		try (
				InputStream versionStream = versionResponse.body();
				InputStreamReader versionStreamReader = new InputStreamReader(versionStream)
		) {
			ExternalData externalData = ModrinthUtils.getModrinthExternalData(authClient, modrinthProjectId);

			JsonElement potentialVersion = JsonParser.parseReader(versionStreamReader);
			if (!potentialVersion.isJsonObject()) {
				throw new InternalServerException("Attempted to get a non-JSON Object Modrinth version whilst getting project metadata.");
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
				throw new UnprocessableEntityException("Could not find valid primary download URL from Modrinth version whilst getting project metadata.");
			}

			for (JsonElement element : version.getAsJsonArray("loaders")) {
				String loader = element.getAsJsonPrimitive().getAsString();
				if (loader.equals("fabric")) {
					tempFile = download(primaryUri);
					ProjectMetadata metadata = getMetadataFromFabricModJson(tempFile, externalData);
					cleanupTmpFolder(tempFile.toPath());
					return metadata;
				}
			}
			throw new UnprocessableEntityException("All mod-loaders associated with the specified version are unsupported.");
		} catch (IOException | InterruptedException e) {
			if (tempFile != null) {
				cleanupTmpFolder(tempFile.toPath());
			}
			throw new InternalServerException("Failed to parse Modrinth version data.");
		}
	}

	public static ProjectMetadata getMetadataFromDownloadUrl(@NotNull String downloadUrl) throws HypertextException {
		URI uri;
		try {
			uri = new URI(downloadUrl);
		} catch (URISyntaxException e) {
			throw new UnprocessableEntityException("Could not parse download URL '" + downloadUrl + "'.");
		}

		File tempFile = null;
		try {
			tempFile = download(uri);
			ProjectMetadata metadata = null;

			if (isJar(tempFile)) {
				if (isFabricMod(tempFile)) {
					metadata = getMetadataFromFabricModJson(tempFile, null);
				}
			}

			cleanupTmpFolder(tempFile.toPath());

			if (metadata == null) {
				throw new UnprocessableEntityException("The downloaded file is not a mod for a supported mod-loader.");
			}

			return metadata;
		} catch (IOException | InterruptedException e) {
			if (tempFile != null) {
				cleanupTmpFolder(tempFile.toPath());
			}
			throw new InternalServerException("Failed to parse downloaded file.");
		}
	}

	public static ProjectMetadata getMetadataFromFabricModJson(@NotNull File file,
	                                                           @Nullable ExternalData externalData) throws HypertextException {

		if (!isJar(file)) {
			throw new UnprocessableEntityException("Submitted a non-JAR file.");
		}

		ProjectMetadata metadata;

		try {
			try (
					JarFile jarFile = new JarFile(file);
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

				metadata = new ModProjectMetadata(
						modId,
						name,
						description,
						sourceUrl
				);
			}

			return metadata;
		} catch (IOException e) {
			throw new InternalServerException("Failed to read metadata from fabric.mod.json.");
		}
	}

	private static File download(URI uri) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder()
				.header("User-Agent", USER_AGENT)
				.uri(uri)
				.build();
		String fileName = getFileName(uri);

		Path temporaryFolder = Path.of("./.tmp/")
				.resolve(fileName);

		Files.createDirectories(temporaryFolder.getParent());

		HttpResponse<Path> response = ModGardenBackend.HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofFile(temporaryFolder));

		return response.body().toFile();
	}

	private static boolean isJar(File file) {
		String fileName = file.getName();

		int i = fileName.lastIndexOf('.');

		if (i > 0) {
			String fileExtension = fileName.substring(i + 1);
			return fileExtension.equals("jar");
		}

		return false;
	}

	private static boolean isFabricMod(File file) throws IOException {
		try (JarFile jarFile = new JarFile(file)) {
			return jarFile.getEntry("fabric.mod.json") != null;
		}
	}

	private static InputStream getFmjAsStream(JarFile file) throws IOException, HypertextException {
		ZipEntry entry = file.getEntry("fabric.mod.json");
		if (entry != null) {
			return file.getInputStream(entry);
		}
		throw new BadRequestException("The specified JAR is not a Fabric mod.");
	}

	private static String getFmjSourceUrl(JsonObject fmj, @Nullable ExternalData data) throws NotFoundException {
		if (data != null && data.externalSourceUrl() != null) {
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

	private static void cleanupTmpFolder(Path temporaryFilePath) throws HypertextException {
		try {
			if (Files.deleteIfExists(temporaryFilePath)) {
				Path temporaryFolder = temporaryFilePath.getParent();
				if (Files.isDirectory(temporaryFilePath)) {
					try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(temporaryFolder)) {
						if (!directoryStream.iterator().hasNext()) {
							Files.deleteIfExists(temporaryFolder);
						}
					}
				}
			}
		} catch (IOException e) {
			throw new InternalServerException("Failed to clean-up temporary folder for checking the submitted JAR.");
		}
	}

	private static String getFileName(URI uri) {
		return Paths.get(uri.getPath()).getFileName().toString();
	}

	public record ExternalData(@Nullable String externalSourceUrl) {}
}
