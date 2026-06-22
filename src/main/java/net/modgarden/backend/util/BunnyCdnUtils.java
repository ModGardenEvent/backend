package net.modgarden.backend.util;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Supplier;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.NaturalId;
import net.modgarden.backend.oauth.OAuthService;
import net.modgarden.backend.oauth.client.BunnyCdnOAuthClient;
import org.jetbrains.annotations.Nullable;

// TODO: Implement CDN when we get to Website V2.
public class BunnyCdnUtils {
	@Nullable
	private static String uploadToCdn(String baseUrl, @Nullable InputStream imageStream) throws Exception {
		if (imageStream == null) {
			return null;
		}
		BunnyCdnOAuthClient client = OAuthService.BUNNY_CDN.authenticate();
		String uploadUrl = NaturalId.generateCdnLink(baseUrl, 5);
		HttpResponse<InputStream> uploadResponse = client.put(
				uploadUrl,
				HttpRequest.BodyPublishers.ofInputStream(() -> imageStream),
				HttpResponse.BodyHandlers.ofInputStream(),
				"Content-Type", "application/octet-stream",
				"Accept", "image/gif, image/png, image/webp"
		);
		try (InputStreamReader reader = new InputStreamReader(uploadResponse.body())) {
			if (uploadResponse.statusCode() != 201) {
				JsonElement json = JsonParser.parseReader(reader);
				String errorMessage = json.isJsonObject() && json.getAsJsonObject().has("Message") ?
						json.getAsJsonObject().getAsJsonPrimitive("Message").getAsString() :
						"Undefined Error.";
				throw new InternalError(errorMessage);
			}
		}
		return uploadUrl;
	}

	private static InputStream getImageAsStream(@Nullable String externalDataUrl, @Nullable Supplier<InputStream> fmjImage) throws Exception {
		if (externalDataUrl != null) {
			HttpRequest httpRequest = HttpRequest.newBuilder(
					URI.create(externalDataUrl)
			).build();
			HttpResponse<InputStream> response = ModGardenBackend.HTTP_CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
			if (response.statusCode() == 200) {
				return response.body();
			}
		}
		if (fmjImage != null) {
			return fmjImage.get();
		}
		return null;
	}

	private static InputStream getIconAsStream(JsonObject fmj, JarFile file) throws Exception {
		if (!fmj.has("icon")) {
			return null;
		}
		String path = fmj.getAsJsonPrimitive("icon").getAsString();
		ZipEntry entry = file.getEntry(path);
		if (entry != null) {
			return file.getInputStream(entry);
		}
		return null;
	}
}
