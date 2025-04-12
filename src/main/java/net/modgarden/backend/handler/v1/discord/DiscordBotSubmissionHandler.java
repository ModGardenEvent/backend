package net.modgarden.backend.handler.v1.discord;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.event.Event;
import net.modgarden.backend.data.event.Project;
import net.modgarden.backend.data.event.Submission;
import net.modgarden.backend.data.profile.User;
import net.modgarden.backend.oauth.OAuthService;
import net.modgarden.backend.oauth.client.OAuthClient;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class DiscordBotSubmissionHandler {
	public static void submitModrinth(Context ctx) {
		if (!("Basic " + ModGardenBackend.DOTENV.get("DISCORD_OAUTH_SECRET")).equals(ctx.header("Authorization"))) {
			ctx.result("Unauthorized.");
			ctx.status(401);
			return;
		}

		if (!("application/json").equals(ctx.header("Content-Type"))) {
			ctx.result("Invalid Content-Type.");
			ctx.status(415);
			return;
		}

		try (InputStream bodyStream = ctx.bodyInputStream();
			 InputStreamReader bodyReader = new InputStreamReader(bodyStream)) {
			JsonElement bodyJson = JsonParser.parseReader(bodyReader);
			var bodyResult = Body.CODEC.decode(JsonOps.INSTANCE, bodyJson);
			if (bodyResult.isError()) {
				ctx.status(422);
				ctx.result(bodyResult.error().orElseThrow().message());
				return;
			}
			Body body = bodyResult.getOrThrow().getFirst();

			User user = User.query(body.discordId, "discord");
			if (user == null || user.modrinthId().isEmpty()) {
				ctx.status(404);
				ctx.result("Could not find a Mod Garden or Modrinth account linked to the specified Discord user.");
				return;
			}

			OAuthClient modrinthClient = OAuthService.MODRINTH.authenticate();
			try (Connection connection = ModGardenBackend.createDatabaseConnection();
				 PreparedStatement projectCheckStatement = connection.prepareStatement("SELECT id FROM projects WHERE modrinth_id = ?");
				 PreparedStatement projectAuthorsCheckStatement = connection.prepareStatement("SELECT 1 FROM project_authors WHERE project_id = ? AND user_id = ?");
				 PreparedStatement projectInsertStatement = connection.prepareStatement("INSERT INTO projects(id, slug, modrinth_id, attributed_to) VALUES (?, ?, ?, ?)");
				 PreparedStatement projectAuthorsStatement = connection.prepareStatement("INSERT INTO project_authors(project_id, user_id) VALUES (?, ?)");
				 PreparedStatement submissionCheckStatement = connection.prepareStatement("SELECT 1 FROM submissions WHERE project_id = ? AND event = ?");
				 PreparedStatement submissionStatement = connection.prepareStatement("INSERT INTO submissions(id, project_id, event, modrinth_version_id, submitted) VALUES (?, ?, ?, ?, ?)")) {

				Event event;

				if (body.event().isPresent()) {
					event = body.event().get();
				} else {
					event = getCurrentEvent(connection);
				}

				if (event == null) {
					ctx.status(422);
					ctx.result("Could not find an event to submit to.");
					return;
				}

				var projectStream = modrinthClient.get("v2/project/" + body.modrinthSlug(), HttpResponse.BodyHandlers.ofInputStream());
				if (projectStream.statusCode() != 200) {
					ctx.status(422);
					ctx.result("Could not find Modrinth project.");
					return;
				}

				try (InputStreamReader projectReader = new InputStreamReader(projectStream.body())) {
					JsonElement projectJson = JsonParser.parseReader(projectReader);
					if (!projectJson.isJsonObject() || !projectJson.getAsJsonObject().has("id") || !projectJson.getAsJsonObject().has("versions") || !projectJson.getAsJsonObject().has("title")) {
						ctx.status(422);
						ctx.result("Invalid Modrinth project.");
						return;
					}

					String title = projectJson.getAsJsonObject().get("title").getAsString();
					String modrinthId = projectJson.getAsJsonObject().get("id").getAsString();

					projectCheckStatement.setString(1, modrinthId);
					ResultSet projectCheck = projectCheckStatement.executeQuery();
					String projectId = projectCheck.getString(1);

					if (projectId != null) {
						projectAuthorsCheckStatement.setString(1, projectId);
						projectAuthorsCheckStatement.setString(2, user.id());
						ResultSet authorsCheck = projectAuthorsCheckStatement.executeQuery();
						if (!authorsCheck.getBoolean(1)) {
							ctx.status(401);
							;
							ctx.result("Unauthorized to submit Modrinth project '" + title + "' to event '" + event.displayName() + "'.");
							return;
						}
					} else if (!hasModrinthAttribution(ctx,
							modrinthClient,
							body.modrinthSlug(),
							user.modrinthId().get(),
							title,
							event.displayName(),
							projectJson.getAsJsonObject().get("organization").isJsonNull() ? null : projectJson.getAsJsonObject().get("organization").getAsString()
					)) {
						return;
					}

					submissionCheckStatement.setString(1, projectId);
					submissionCheckStatement.setString(2, event.id());
					var submissionCheck = submissionCheckStatement.executeQuery();

					if (submissionCheck.getBoolean(1)) {
						ctx.status(200);
						JsonObject result = new JsonObject();
						result.addProperty("success", ctx.status().getMessage());
						result.addProperty("description", "Modrinth project '" + title + "' has already been submitted to event '" + event.displayName() + "'.");
						ctx.json(result);
						return;
					}

					String modrinthVersion = getModrinthVersion(projectJson.getAsJsonObject(), modrinthClient, event.minecraftVersion(), event.loader());

					if (modrinthVersion == null) {
						ctx.status(422);
						ctx.result("Could not find a valid Modrinth version for " + toFriendlyLoaderString(event.loader()) + " on Minecraft " + event.minecraftVersion() + ".");
						return;
					}

					if (projectId == null) {
						long generatedProjectId = Project.ID_GENERATOR.next();
						projectId = Long.toString(generatedProjectId);
						projectInsertStatement.setString(1, projectId);
						projectInsertStatement.setString(2, body.modrinthSlug());
						projectInsertStatement.setString(3, modrinthId);
						projectInsertStatement.setString(4, user.id());
						projectInsertStatement.execute();

						// TODO: Add added project authors with valid Mod Garden accounts (outside of just being part of the org) to the project.
						projectAuthorsStatement.setString(1, projectId);
						projectAuthorsStatement.setString(2, user.id());
						projectAuthorsStatement.execute();
					}

					long generatedSubmissionId = Submission.ID_GENERATOR.next();
					String submissionId = Long.toString(generatedSubmissionId);
					submissionStatement.setString(1, submissionId);
					submissionStatement.setString(2, projectId);
					submissionStatement.setString(3, event.id());
					submissionStatement.setString(4, modrinthVersion);
					submissionStatement.setLong(5, System.currentTimeMillis());
					submissionStatement.execute();

					ctx.status(201);

					JsonObject result = new JsonObject();
					result.addProperty("success", ctx.status().getMessage());
					result.addProperty("description", "Submitted Modrinth project '" + title + "' to event '" + event.displayName() + "'.");
					ctx.json(result);
				}
			}
		} catch (SQLException | IOException | InterruptedException ex) {
			ModGardenBackend.LOG.error("Failed to submit project.", ex);
			ctx.status(500);
			ctx.result("Internal error.");
		}
	}

	private static String toFriendlyLoaderString(String value) {
		if (value.equals("neoforge")) {
			return "NeoForge";
		}
		return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
	}

	private static boolean hasModrinthAttribution(Context ctx,
												  OAuthClient modrinthClient,
												  String projectSlug,
												  String userId,
												  String projectDisplayName,
												  String eventDisplayName,
												  @Nullable String organisationId) throws IOException, InterruptedException {
		var membersStream = modrinthClient.get("v2/project/" + projectSlug + "/members", HttpResponse.BodyHandlers.ofInputStream());
		if (membersStream.statusCode() == 200) {
			try (InputStreamReader membersReader = new InputStreamReader(membersStream.body())) {
				JsonElement membersJson = JsonParser.parseReader(membersReader);
				if (!membersJson.isJsonArray()) {
					ctx.status(500);
					ctx.result("Could not parse project member data.");
					return false;
				}

				for (JsonElement member : membersJson.getAsJsonArray()) {
					if (!member.isJsonObject())
						continue;
					JsonObject memberObj = member.getAsJsonObject();
					JsonObject userObj = memberObj.getAsJsonObject("user");
					if (userObj.has("id")) {
						if (userId.equals(userObj.get("id").getAsString())) {
							return true;
						}
					}
				}
			}
		}

		if (organisationId != null) {
			var organizationStream = modrinthClient.get("v3/organization/" + organisationId, HttpResponse.BodyHandlers.ofInputStream());
			try (InputStreamReader organizationReader = new InputStreamReader(organizationStream.body())) {
				JsonElement organizationJson = JsonParser.parseReader(organizationReader);
				if (!organizationJson.isJsonObject()) {
					ctx.status(500);
					ctx.result("Could not parse organization data.");
					return false;
				}

				JsonObject organizationObj = organizationJson.getAsJsonObject();
				for (JsonElement member : organizationObj.getAsJsonArray("members")) {
					if (!member.isJsonObject())
						continue;
					JsonObject memberObj = member.getAsJsonObject();
					JsonObject userObj = memberObj.getAsJsonObject("user");
					if (userObj.has("id")) {
						if (userId.equals(userObj.get("id").getAsString())) {
							return true;
						}
					}
				}
			}
		}

		ctx.status(401);
		ctx.result("Unauthorized to submit Modrinth project '" + projectDisplayName + "' to Mod Garden event '" + eventDisplayName + "'.");
		return false;
	}

	@Nullable
	private static String getModrinthVersion(JsonObject json, OAuthClient modrinthClient, String minecraftVersion, String loader) throws IOException, InterruptedException {
		String modrinthVersion = null;
		ZonedDateTime latestVersionTime = null;
		boolean isNative = false; // Used within NeoForge events to make sure that Modrinth will prioritise NeoForge projects over Connector-ran Fabric projects.

		for (JsonElement element : json.getAsJsonArray("versions")) {
			String versionSlug = element.getAsString();
			var versionStream = modrinthClient.get("v2/version/" + versionSlug, HttpResponse.BodyHandlers.ofInputStream());
			if (versionStream.statusCode() != 200)
				continue;

			try (InputStreamReader versionReader = new InputStreamReader(versionStream.body())) {
				JsonElement versionJson = JsonParser.parseReader(versionReader);

				if (!versionJson.isJsonObject())
					continue;

				JsonObject versionJsonObject = versionJson.getAsJsonObject();
				if (versionJsonObject.has("date_published") && versionJsonObject.has("game_versions") && versionJsonObject.has("loaders")) {
					List<String> gameVersions = Codec.STRING.listOf().decode(JsonOps.INSTANCE, versionJsonObject.getAsJsonArray("game_versions")).getOrThrow().getFirst();
					List<String> loaders = Codec.STRING.listOf().decode(JsonOps.INSTANCE, versionJsonObject.getAsJsonArray("loaders")).getOrThrow().getFirst();
					ZonedDateTime datePublished = ZonedDateTime.parse(versionJsonObject.get("date_published").getAsString(), DateTimeFormatter.ISO_DATE_TIME);

					if (!gameVersions.contains(minecraftVersion))
						continue;

					// Handle natively supported mods for the event's loader.
					if (loaders.contains(loader) && (!isNative || datePublished.isAfter(latestVersionTime))) {
						modrinthVersion = versionSlug;
						latestVersionTime = datePublished;
						isNative = true;
						// Handle Fabric mods loaded via Connector on NeoForge.
					} else if (loader.equals("neoforge") && loaders.contains("fabric") && (latestVersionTime == null || datePublished.isAfter(latestVersionTime))) {
						modrinthVersion = versionSlug;
						latestVersionTime = datePublished;
						isNative = false;
					}
				}
			}
		}

		return modrinthVersion;
	}

	private static Event getCurrentEvent(Connection connection) throws SQLException {
		PreparedStatement slugStatement = connection.prepareStatement("SELECT slug FROM events WHERE start_time <= ? AND end_time > ? LIMIT 1");

		long currentMillis = System.currentTimeMillis();
		slugStatement.setLong(1, currentMillis);
		slugStatement.setLong(2, currentMillis);
		ResultSet query = slugStatement.executeQuery();

		@Nullable String slug = query.getString(1);

		if (slug != null)
			return Event.queryFromSlug(slug);

		return null;
	}

	public record Body(String discordId, Optional<Event> event, String modrinthSlug) {
		public static final Codec<Body> CODEC = RecordCodecBuilder.create(inst -> inst.group(
				Codec.STRING.fieldOf("discord_id").forGetter(Body::discordId),
				Event.FROM_SLUG_CODEC.optionalFieldOf("event").forGetter(Body::event),
				Codec.STRING.fieldOf("modrinth_slug").forGetter(Body::modrinthSlug)
		).apply(inst, Body::new));
	}
}
