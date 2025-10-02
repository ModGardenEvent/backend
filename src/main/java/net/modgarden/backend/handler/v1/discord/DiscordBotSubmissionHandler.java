package net.modgarden.backend.handler.v1.discord;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.javalin.http.Context;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.NaturalId;
import net.modgarden.backend.data.event.Event;
import net.modgarden.backend.data.profile.User;
import net.modgarden.backend.oauth.OAuthService;
import net.modgarden.backend.oauth.client.OAuthClient;
import net.modgarden.backend.util.ExtraCodecs;
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
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.stream.Collectors;

public class DiscordBotSubmissionHandler {
	public static final String REGEX = "^[a-z0-9!@$()`.+,_\"-]*$";

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
			Body body = Body.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(bodyReader)).getOrThrow();
			String slug = body.slug.toLowerCase(Locale.ROOT);

			if (!slug.matches(REGEX)) {
				ctx.status(422);
				ctx.result("Invalid Modrinth slug.");
				return;
			}

			User user = User.query(body.discordId, "discord");
			if (user == null || user.modrinthId().isEmpty()) {
				ctx.status(422);
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

				Event event = getCurrentEvent(connection);

				if (event == null) {
					ctx.status(422);
					ctx.result("A Mod Garden event is not currently open for submissions.");
					return;
				}

				var projectStream = modrinthClient.get("v2/project/" + slug, HttpResponse.BodyHandlers.ofInputStream());
				if (projectStream.statusCode() != 200) {
					ctx.status(422);
					ctx.result("Could not find Modrinth project.");
					return;
				}

				try (InputStreamReader projectReader = new InputStreamReader(projectStream.body())) {
					ModrinthProject modrinthProject = ModrinthProject.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(projectReader)).getOrThrow();

					projectCheckStatement.setString(1, modrinthProject.id);
					ResultSet projectCheck = projectCheckStatement.executeQuery();
					String projectId = projectCheck.getString(1);

					if (projectId != null) {
						projectAuthorsCheckStatement.setString(1, projectId);
						projectAuthorsCheckStatement.setString(2, user.id());
						ResultSet authorsCheck = projectAuthorsCheckStatement.executeQuery();
						if (!authorsCheck.getBoolean(1)) {
							ctx.status(401);
							ctx.result("Unauthorized to submit Modrinth project '" + modrinthProject.title + "' to event '" + event.displayName() + "'.");
							return;
						}
					} else if (!hasModrinthAttribution(ctx,
							modrinthClient,
							modrinthProject,
							user.modrinthId().get(),
							event.displayName()
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
						result.addProperty("description", "Modrinth project '" + modrinthProject.title + "' has already been submitted to event '" + event.displayName() + "'.");
						ctx.json(result);
						return;
					}

					ModrinthVersion modrinthVersion = getModrinthVersion(modrinthClient, modrinthProject, event.minecraftVersion(), event.loader(), null);
					if (modrinthVersion == null) {
						ctx.status(422);
						ctx.result("Could not find a valid Modrinth version for " + toFriendlyLoaderString(event.loader()) + " on Minecraft " + event.minecraftVersion() + ".");
						return;
					}

					if (projectId == null) {
						projectId = NaturalId.generate("projects", "id", 5);
						projectInsertStatement.setString(1, projectId);
						projectInsertStatement.setString(2, slug);
						projectInsertStatement.setString(3, modrinthProject.id);
						projectInsertStatement.setString(4, user.id());
						projectInsertStatement.execute();

						// TODO: Add added project authors with valid Mod Garden accounts (outside of just being part of the org) to the project.
						projectAuthorsStatement.setString(1, projectId);
						projectAuthorsStatement.setString(2, user.id());
						projectAuthorsStatement.execute();
					}

					String submissionId = NaturalId.generate("submissions", "id",
							5
					);
					submissionStatement.setString(1, submissionId);
					submissionStatement.setString(2, projectId);
					submissionStatement.setString(3, event.id());
					submissionStatement.setString(4, modrinthVersion.id());
					submissionStatement.setLong(5, System.currentTimeMillis());
					submissionStatement.execute();

					ctx.status(201);

					JsonObject result = new JsonObject();
					result.addProperty("success", ctx.status().getMessage());
					result.addProperty("description", "Submitted Modrinth project '" + modrinthProject.title + "' to event '" + event.displayName() + "'.");
					ctx.json(result);
				}
			}
		} catch (SQLException | IOException | InterruptedException ex) {
			ModGardenBackend.LOG.error("Failed to submit project.", ex);
			ctx.status(500);
			ctx.result("Internal error.");
		}
	}


	public static void setVersionModrinth(Context ctx) {
		if (!("Basic " + ModGardenBackend.DOTENV.get("DISCORD_OAUTH_SECRET")).equals(ctx.header("Authorization"))) {
			ctx.result("Unauthorized.");
			ctx.status(401);
			return;
		}

		try (InputStream bodyStream = ctx.bodyInputStream();
			 InputStreamReader bodyReader = new InputStreamReader(bodyStream)) {
			Body body = Body.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(bodyReader)).getOrThrow();
			String slug = body.slug.toLowerCase(Locale.ROOT);

			if (!slug.matches(REGEX)) {
				ctx.status(422);
				ctx.result("Invalid Modrinth slug.");
				return;
			}

			User user = User.query(body.discordId, "discord");
			if (user == null) {
				ctx.status(422);
				ctx.result("Could not find a Mod Garden or Modrinth account linked to the specified Discord user.");
				return;
			}

			try (Connection connection = ModGardenBackend.createDatabaseConnection();
				 PreparedStatement selectProjectDataStatement = connection.prepareStatement("SELECT id, modrinth_id FROM projects WHERE slug = ?");
				 PreparedStatement projectAuthorCheckStatement = connection.prepareStatement("SELECT 1 FROM project_authors WHERE project_id = ? AND user_id = ?");
				 PreparedStatement updateSubmissionVersionStatement = connection.prepareStatement("UPDATE submissions SET modrinth_version_id = ? WHERE event = ? AND project_id = ?")) {
				Event event = getNonFrozenEvent(connection);
				if (event == null) {
					ctx.status(422);
					ctx.result("A Mod Garden event is not currently open for updating.");
					return;
				}

				selectProjectDataStatement.setString(1, body.slug);
				ResultSet projectDataQuery = selectProjectDataStatement.executeQuery();

				String projectId = projectDataQuery.getString("id");
				String modrinthId = projectDataQuery.getString("modrinth_id");

				var modrinthClient = OAuthService.MODRINTH.authenticate();
				var modrinthStream = modrinthClient.get("v2/project/" + modrinthId, HttpResponse.BodyHandlers.ofInputStream());
				if (modrinthStream.statusCode() != 200) {
					ctx.status(422);
					ctx.result("Could not find the specified Modrinth project.");
					return;
				}
				InputStreamReader modrinthProjectReader = new InputStreamReader(modrinthStream.body());
				ModrinthProject modrinthProject = ModrinthProject.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(modrinthProjectReader)).getOrThrow();

				projectAuthorCheckStatement.setString(1, projectId);
				projectAuthorCheckStatement.setString(2, user.id());
				ResultSet projectAuthorQuery = projectAuthorCheckStatement.executeQuery();

				if (!projectAuthorQuery.getBoolean(1)) {
					ctx.status(401);
					ctx.result("Only an author of a project is authorized to change the version of the project '" + modrinthProject.title + "' from event '" + event.displayName() + "'.");
					return;
				}

				ModrinthVersion modrinthVersion = getModrinthVersion(modrinthClient, modrinthProject, event.minecraftVersion(), event.loader(), body.version);
				if (modrinthVersion == null) {
					ctx.status(422);
					if (body.version != null) {
						ctx.result("Could not find a valid Modrinth version '" + body.version + "' for " + toFriendlyLoaderString(event.loader()) + " on Minecraft " + event.minecraftVersion() + ".");
					} else {
						ctx.result("Could not find a valid Modrinth version for " + toFriendlyLoaderString(event.loader()) + " on Minecraft " + event.minecraftVersion() + ".");
					}
					return;
				}
				updateSubmissionVersionStatement.setString(1, modrinthVersion.id());
				updateSubmissionVersionStatement.setString(2, event.id());
				updateSubmissionVersionStatement.setString(3, projectId);
				if (updateSubmissionVersionStatement.executeUpdate() == 0) {
					ctx.status(200);
					JsonObject result = new JsonObject();
					result.addProperty("success", ctx.status().getMessage());
					result.addProperty("description", "Modrinth project '" + modrinthProject.title + "' is already set to version '" + modrinthVersion.name + "'.");
					ctx.json(result);
					return;
				}
				ctx.status(201);
				JsonObject result = new JsonObject();
				result.addProperty("success", ctx.status().getMessage());
				result.addProperty("description", "Successfully updated Modrinth project '" + modrinthProject.title + "' to '" + modrinthVersion.name + "' within the Mod Garden database.");
				ctx.json(result);
			}
		} catch (Exception ex) {
			ModGardenBackend.LOG.error("Failed to unsubmit project.", ex);
			ctx.status(500);
			ctx.result("Internal error.");
		}
	}

	public static void unsubmit(Context ctx) {
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
			Body body = Body.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(bodyReader)).getOrThrow();
			String slug = body.slug.toLowerCase(Locale.ROOT);

			if (!slug.matches(REGEX)) {
				ctx.status(422);
				ctx.result("Invalid Mod Garden slug.");
				return;
			}

			User user = User.query(body.discordId, "discord");
			if (user == null) {
				ctx.status(422);
				ctx.result("Could not find a Mod Garden or Modrinth account linked to the specified Discord user.");
				return;
			}

			try (Connection connection = ModGardenBackend.createDatabaseConnection();
				 PreparedStatement getModrinthIdStatement = connection.prepareStatement("SELECT id, modrinth_id FROM projects WHERE slug = ?");
				 PreparedStatement checkSubmissionStatement = connection.prepareStatement("SELECT 1 FROM submissions WHERE project_id = ? AND event = ?");
				 PreparedStatement projectAttributionCheckStatement = connection.prepareStatement("SELECT 1 FROM projects WHERE slug = ? AND attributed_to = ?");
				 PreparedStatement deleteSubmissionStatement = connection.prepareStatement("DELETE FROM submissions WHERE project_id = ? AND event = ?");
				 PreparedStatement checkSubmissionPreDeletionStatement = connection.prepareStatement("SELECT 1 FROM submissions WHERE project_id = ?");
				 PreparedStatement projectDeleteStatement = connection.prepareStatement("DELETE FROM projects WHERE id = ?");
				 PreparedStatement projectAuthorsDeleteStatement = connection.prepareStatement("DELETE FROM project_authors WHERE project_id = ?")) {
				Event event = getCurrentEvent(connection);

				if (event == null) {
					ctx.status(422);
					ctx.result("A Mod Garden event is not currently open for unsubmitting.");
					return;
				}

				getModrinthIdStatement.setString(1, slug);
				ResultSet modrinthResult = getModrinthIdStatement.executeQuery();
				String potentialModrinthId = modrinthResult.getString("modrinth_id");
				String modrinthId = slug;
				if (potentialModrinthId != null) {
					modrinthId = potentialModrinthId;
				}

				String projectId = modrinthResult.getString("id");

				var modrinthStream = OAuthService.MODRINTH.authenticate().get("v2/project/" + modrinthId, HttpResponse.BodyHandlers.ofInputStream());
				String title = slug;
				if (modrinthStream.statusCode() == 200) {
					InputStreamReader modrinthProjectReader = new InputStreamReader(modrinthStream.body());
					ModrinthProject modrinthProject = ModrinthProject.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(modrinthProjectReader)).getOrThrow();
					title = modrinthProject.title;
				}

				if (projectId == null) {
					ctx.status(200);
					JsonObject result = new JsonObject();
					result.addProperty("success", ctx.status().getMessage());
					result.addProperty("description", "Project '" + title + "' was never submitted to a Mod Garden event.");
					ctx.json(result);
					return;
				}

				checkSubmissionStatement.setString(1, projectId);
				checkSubmissionStatement.setString(2, event.id());
				ResultSet submissionResult = checkSubmissionStatement.executeQuery();
				if (!submissionResult.getBoolean(1)) {
					ctx.status(200);
					JsonObject result = new JsonObject();
					result.addProperty("success", ctx.status().getMessage());
					result.addProperty("description", "Project '" + title + "' was never submitted to event '" + event.displayName() + "'.");
					ctx.json(result);
					return;
				}

				projectAttributionCheckStatement.setString(1, slug);
				projectAttributionCheckStatement.setString(2, user.id());
				ResultSet projectAttributionResult = projectAttributionCheckStatement.executeQuery();
				if (!projectAttributionResult.getBoolean(1)) {
					ctx.status(401);
					ctx.result("Only the original submitter is authorized to unsubmit '" + title + "' from event '" + event.displayName() + "'.");
					return;
				}

				deleteSubmissionStatement.setString(1, projectId);
				deleteSubmissionStatement.setString(2, event.id());
				deleteSubmissionStatement.execute();

				checkSubmissionPreDeletionStatement.setString(1, projectId);
				ResultSet submissionPreDeletionResult = checkSubmissionPreDeletionStatement.executeQuery();
				if (!submissionPreDeletionResult.getBoolean(1)) {
					projectDeleteStatement.setString(1, projectId);
					projectDeleteStatement.execute();

					projectAuthorsDeleteStatement.setString(1, projectId);
					projectAuthorsDeleteStatement.execute();
				}

				ctx.status(201);

				JsonObject result = new JsonObject();
				result.addProperty("success", ctx.status().getMessage());
				result.addProperty("description", "Unsubmitted project '" + title + "' from event '" + event.displayName() + "'.");
				ctx.json(result);
			}
		} catch (Exception ex) {
			ModGardenBackend.LOG.error("Failed to unsubmit project.", ex);
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
												  ModrinthProject project,
												  String userId,
												  String eventDisplayName) throws IOException, InterruptedException {
		var membersStream = modrinthClient.get("v2/project/" + project.id + "/members", HttpResponse.BodyHandlers.ofInputStream());
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

		if (project.organization != null) {
			var organizationStream = modrinthClient.get("v3/organization/" + project.organization, HttpResponse.BodyHandlers.ofInputStream());
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
		ctx.result("Unauthorized to submit Modrinth project '" + project.title + "' to Mod Garden event '" + eventDisplayName + "'.");
		return false;
	}

	@Nullable
	private static ModrinthVersion getModrinthVersion(OAuthClient modrinthClient, ModrinthProject modrinthProject, String minecraftVersion, String loader, @Nullable String versionString) throws IOException, InterruptedException {
		if (versionString != null) {
			var versionStream = modrinthClient.get("v2/project/" + modrinthProject.id + "/version/" + versionString, HttpResponse.BodyHandlers.ofInputStream());
			if (versionStream.statusCode() == 200) {
				try (InputStreamReader versionReader = new InputStreamReader(versionStream.body())) {
					JsonElement versionJson = JsonParser.parseReader(versionReader);
					ModrinthVersion potentialVersion = ModrinthVersion.CODEC.parse(JsonOps.INSTANCE, versionJson).getOrThrow();

					if (versionString.equals(potentialVersion.id) || versionString.equals(potentialVersion.versionNumber)) {
						if (potentialVersion.loaders.contains(loader) || loader.equals("neoforge") && potentialVersion.loaders.contains("fabric")) {
							return potentialVersion;
						}
					}
				}
			}
			return null;
		}

		List<ModrinthVersion> modrinthVersions = modrinthProject.versions.parallelStream().map(versionId -> {
			try {
				var versionStream = modrinthClient.get("v2/version/" + versionId, HttpResponse.BodyHandlers.ofInputStream());
				if (versionStream.statusCode() != 200)
					return null;

				try (InputStreamReader versionReader = new InputStreamReader(versionStream.body())) {
					JsonElement versionJson = JsonParser.parseReader(versionReader);
					ModrinthVersion potentialVersion = ModrinthVersion.CODEC.parse(JsonOps.INSTANCE, versionJson).getOrThrow();

					if (!potentialVersion.gameVersions.contains(minecraftVersion))
						return null;

					// Handle natively supported mods for the event's loader.
					if (potentialVersion.loaders.contains(loader)) {
						return potentialVersion;
					// Handle Fabric mods loaded via Connector on NeoForge.
					} else if (loader.equals("neoforge") && potentialVersion.loaders.contains("fabric")) {
						return potentialVersion;
					}
				}
			} catch (Exception ex) {
				ModGardenBackend.LOG.error("Failed to read Modrinth version.", ex);
			}
			return null;
		}).filter(Objects::nonNull).collect(Collectors.toCollection(ArrayList::new));

		// Filter out non-native options if the mod has a native version.
		// Handles cases like Sinytra Connector.
		if (modrinthVersions.stream().anyMatch(v -> v.loaders.contains(loader))) {
			modrinthVersions.removeIf(v -> !v.loaders.contains(loader));
		}

		return modrinthVersions.stream()
				.max(Comparator.comparingLong(value -> value.datePublished.getLong(ChronoField.INSTANT_SECONDS)))
				.orElse(null);
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


	private static Event getNonFrozenEvent(Connection connection) throws SQLException {
		PreparedStatement slugStatement = connection.prepareStatement("SELECT slug FROM events WHERE start_time <= ? AND freeze_time > ? LIMIT 1");

		long currentMillis = System.currentTimeMillis();
		slugStatement.setLong(1, currentMillis);
		slugStatement.setLong(2, currentMillis);
		ResultSet query = slugStatement.executeQuery();

		@Nullable String slug = query.getString(1);

		if (slug != null)
			return Event.queryFromSlug(slug);

		return null;
	}

	private record Body(String discordId, String slug, @Nullable String version) {
			private static final Codec<Body> CODEC = RecordCodecBuilder.create(inst -> inst.group(
					Codec.STRING
							.fieldOf("discord_id")
							.forGetter(b -> b.discordId),
					Codec.STRING
							.fieldOf("slug")
							.forGetter(b -> b.slug),
					Codec.STRING
							.optionalFieldOf("version")
							.forGetter(b -> Optional.ofNullable(b.version))
			).apply(inst, (discordId, slug, version) ->
					new Body(discordId, slug, version.orElse(null))));

	}

	private static class ModrinthProject {
		protected static final Codec<ModrinthProject> CODEC = RecordCodecBuilder.create(inst -> inst.group(
				Codec.STRING.fieldOf("title").forGetter(p -> p.title),
				Codec.STRING.fieldOf("id").forGetter(p -> p.id),
				Codec.STRING.listOf().xmap(Set::copyOf, List::copyOf).fieldOf("versions").forGetter(p -> p.versions)
		).apply(inst, ModrinthProject::new));

		protected final String title;
		protected final String id;
		protected final Set<String> versions;

		@Nullable
		protected String organization;

		private ModrinthProject(String title, String id, Set<String> versions) {
			this.title = title;
			this.id = id;
			this.versions = versions;
		}
	}

	private record ModrinthVersion(String id, String name, String versionNumber, ZonedDateTime datePublished,
								   Set<String> gameVersions, Set<String> loaders) {
			private static final Codec<ModrinthVersion> CODEC = RecordCodecBuilder.create(inst -> inst.group(
					Codec.STRING.fieldOf("id").forGetter(v -> v.id),
					Codec.STRING.fieldOf("name").forGetter(v -> v.name),
					Codec.STRING.fieldOf("version_number").forGetter(v -> v.versionNumber),
					ExtraCodecs.ISO_DATE_TIME.fieldOf("date_published").forGetter(v -> v.datePublished),
					Codec.STRING.listOf().xmap(Set::copyOf, List::copyOf).fieldOf("game_versions").forGetter(v -> v.gameVersions),
					Codec.STRING.listOf().xmap(Set::copyOf, List::copyOf).fieldOf("loaders").forGetter(v -> v.loaders)
			).apply(inst, ModrinthVersion::new));
	}
}
