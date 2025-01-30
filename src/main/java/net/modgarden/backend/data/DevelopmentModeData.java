package net.modgarden.backend.data;

import net.modgarden.backend.ModGardenBackend;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Random;

public class DevelopmentModeData {
	private static final Random RANDOM = new Random(123);
	public static void insertDevelopmentModeData() {
		try {
			Connection connection = ModGardenBackend.createDatabaseConnection();
			var userStatement = connection.prepareStatement("INSERT OR IGNORE INTO users(id, username, display_name, discord_id, created, modrinth_id) VALUES (?, ?, ?, ?, ?, ?)");
			long ultrusId = RANDOM.nextLong(Long.MAX_VALUE);
			userStatement.setString(1, Long.toString(ultrusId));
			userStatement.setString(2, "ultrusbot");
			userStatement.setString(3, "UltrusBot");
			userStatement.setString(4, "852948197356863528");
			userStatement.setLong(5, System.currentTimeMillis());
			userStatement.setString(6, "RlpLaNSn");
			userStatement.execute();

            long pugId = RANDOM.nextLong(Long.MAX_VALUE);
			userStatement.setString(1, Long.toString(pugId));
			userStatement.setString(2, "pug");
			userStatement.setString(3, "Pug");
			userStatement.setString(4, "680986902240690176");
			userStatement.setLong(5, System.currentTimeMillis());
			userStatement.setString(6, "84zsGbft");
			userStatement.execute();

			long greencowId = RANDOM.nextLong(Long.MAX_VALUE);
			userStatement.setString(1, Long.toString(greencowId));
			userStatement.setString(2, "greenbot");
			userStatement.setString(3, "GreenBot");
			userStatement.setString(4, "876135519526977587");
			userStatement.setLong(5, System.currentTimeMillis());
			userStatement.setString(6, "");
			userStatement.execute();


			var eventStatement = connection.prepareStatement("INSERT OR IGNORE INTO events(id, slug, display_name, started, minecraft_version, loader, loader_version) VALUES (?, ?, ?, ?, '1.21.1', 'fabric', '0.16.10')");
			long mojankId = RANDOM.nextLong(Long.MAX_VALUE);
			eventStatement.setString(1, Long.toString(mojankId));
			eventStatement.setString(2, "mojank-fest");
			eventStatement.setString(3, "MoJank Fest");
			eventStatement.setLong(4, System.currentTimeMillis() - (86400000 * 5));
			eventStatement.execute();

            long festivalId = RANDOM.nextLong(Long.MAX_VALUE);
            eventStatement.setString(1, Long.toString(festivalId));
            eventStatement.setString(2, "festival");
            eventStatement.setString(3, "Mod Garden: Festival");
            eventStatement.setLong(4, System.currentTimeMillis() - (86400000 * 3));
            eventStatement.execute();

			long exampleGardenId = RANDOM.nextLong(Long.MAX_VALUE);
			eventStatement.setString(1, Long.toString(exampleGardenId));
			eventStatement.setString(2, "example-garden");
			eventStatement.setString(3, "Example Garden");
			eventStatement.setLong(4, System.currentTimeMillis() - (86400000 * 2));
			eventStatement.execute();

			long currentEventId = RANDOM.nextLong(Long.MAX_VALUE);
			eventStatement.setString(1, Long.toString(currentEventId));
			eventStatement.setString(2, "current-event");
			eventStatement.setString(3, "Current Event");
			eventStatement.setLong(4, System.currentTimeMillis());
			eventStatement.execute();

			var projectStatement = connection.prepareStatement("INSERT OR IGNORE INTO projects(id, modrinth_id, attributed_to, slug) VALUES (?, ?, ?, ?)");
			long glowBannersId = RANDOM.nextLong(Long.MAX_VALUE);
			projectStatement.setString(1, Long.toString(glowBannersId));
			projectStatement.setString(2, "r7G43arb");
			projectStatement.setString(3, Long.toString(ultrusId));
			projectStatement.setString(4, "glow-banners");
			projectStatement.execute();

			long smeltingTouchId = RANDOM.nextLong(Long.MAX_VALUE);
			projectStatement.setString(1, Long.toString(smeltingTouchId));
			projectStatement.setString(2, "otiSEfKe");
			projectStatement.setString(3, Long.toString(ultrusId));
			projectStatement.setString(4, "smelting-touch");
			projectStatement.execute();

			long bovinesId = RANDOM.nextLong(Long.MAX_VALUE);
			projectStatement.setString(1, Long.toString(bovinesId));
			projectStatement.setString(2, "BDg6nMn3");
			projectStatement.setString(3, Long.toString(pugId));
			projectStatement.setString(4, "bovines-and-buttercups");
            projectStatement.execute();

			long rapscallionsId = RANDOM.nextLong(Long.MAX_VALUE);
			projectStatement.setString(1, Long.toString(rapscallionsId));
			projectStatement.setString(2, "9pGITjpO");
			projectStatement.setString(3, Long.toString(pugId));
			projectStatement.setString(4, "rapscallions-and-rockhoppers");
            projectStatement.execute();

			var submissionStatement = connection.prepareStatement("INSERT OR IGNORE INTO submissions(id, project_id, event, modrinth_version_id, submitted_at) VALUES (?, ?, ?, ?, ?)");

			long glowBannersSubmissionId = RANDOM.nextLong(Long.MAX_VALUE);
			submissionStatement.setString(1, Long.toString(glowBannersSubmissionId));
			submissionStatement.setString(2, Long.toString(glowBannersId));
			submissionStatement.setString(3, Long.toString(mojankId));
			submissionStatement.setString(4, "c2VxpX2M");
			submissionStatement.setLong(5, System.currentTimeMillis() - (86400000 * 3));
			submissionStatement.execute();

			long smeltingTouchSubmissionId = RANDOM.nextLong(Long.MAX_VALUE);
			submissionStatement.setString(1, Long.toString(smeltingTouchSubmissionId));
			submissionStatement.setString(2, Long.toString(smeltingTouchId));
			submissionStatement.setString(3, Long.toString(exampleGardenId));
			submissionStatement.setString(4, "ubrXE4aR");
			submissionStatement.setLong(5, System.currentTimeMillis() - (86400000));
			submissionStatement.execute();

			long bovinesMojankSubmissionId = RANDOM.nextLong(Long.MAX_VALUE);
			submissionStatement.setString(1, Long.toString(bovinesMojankSubmissionId));
			submissionStatement.setString(2, Long.toString(bovinesId));
			submissionStatement.setString(3, Long.toString(mojankId));
			submissionStatement.setString(4, "j7WIi30J");
			submissionStatement.setLong(5, System.currentTimeMillis() - (86400000 * 4));
			submissionStatement.execute();

            long bovinesFestivalSubmissionId = RANDOM.nextLong(Long.MAX_VALUE);
            submissionStatement.setString(1, Long.toString(bovinesFestivalSubmissionId));
            submissionStatement.setString(2, Long.toString(bovinesId));
            submissionStatement.setString(3, Long.toString(festivalId));
            submissionStatement.setString(4, "j7WIi30J");
            submissionStatement.setLong(5, System.currentTimeMillis() - (86400000 * 4));
            submissionStatement.execute();

			long rapscallionsSubmissionId = RANDOM.nextLong(Long.MAX_VALUE);
			submissionStatement.setString(1, Long.toString(rapscallionsSubmissionId));
			submissionStatement.setString(2, Long.toString(rapscallionsId));
			submissionStatement.setString(3, Long.toString(exampleGardenId));
			submissionStatement.setString(4, "HOekJDf0");
			submissionStatement.setLong(5, System.currentTimeMillis() - (86400000 * 2));
			submissionStatement.execute();

			var projectAuthorsStatement = connection.prepareStatement("INSERT OR IGNORE INTO project_authors(project_id, user_id) VALUES (?, ?)");

            // Glow Banners Data
            projectAuthorsStatement.setString(1, Long.toString(glowBannersId));
            projectAuthorsStatement.setString(2, Long.toString(ultrusId));
            projectAuthorsStatement.execute();

            // Smelting Touch Data
            projectAuthorsStatement.setString(1, Long.toString(smeltingTouchId));
            projectAuthorsStatement.setString(2, Long.toString(ultrusId));
            projectAuthorsStatement.execute();

            // Bovines and Buttercups Data
            projectAuthorsStatement.setString(1, Long.toString(bovinesId));
            projectAuthorsStatement.setString(2, Long.toString(pugId));
            projectAuthorsStatement.execute();

            // Rapscallions and Rockhoppers Data
            projectAuthorsStatement.setString(1, Long.toString(rapscallionsId));
            projectAuthorsStatement.setString(2, Long.toString(pugId));
            projectAuthorsStatement.execute();

			projectAuthorsStatement.setString(1, Long.toString(rapscallionsId));
			projectAuthorsStatement.setString(2, Long.toString(ultrusId));
			projectAuthorsStatement.execute();

			var awardsStatement = connection.prepareStatement("INSERT OR IGNORE INTO awards(id, slug, display_name, sprite, discord_emote, tooltip) VALUES (?, ?, ?, ?, ?, ?)");
			long cowAward = RANDOM.nextLong(Long.MAX_VALUE);
			awardsStatement.setString(1, Long.toString(cowAward));
			awardsStatement.setString(2, "flower-cow");
			awardsStatement.setString(3, "Flower Cow");
			awardsStatement.setString(4, "cow_award");
			awardsStatement.setString(5, "1065689127774867487");
			awardsStatement.setString(6, "Flower Cow award");
			awardsStatement.execute();

			long glowAward = RANDOM.nextLong(Long.MAX_VALUE);
			awardsStatement.setString(1, Long.toString(glowAward));
			awardsStatement.setString(2, "glowing-award");
			awardsStatement.setString(3, "Glowing Award");
			awardsStatement.setString(4, "glow_award");
			awardsStatement.setString(5, "1205742638884462592");
			awardsStatement.setString(6, "Glowing Award, for mods that have glowing in them");
			awardsStatement.execute();

			var awardInstancesStatement = connection.prepareStatement("INSERT OR IGNORE INTO award_instances(award_id, awarded_to, custom_data) VALUES (?, ?, ?)");
			awardInstancesStatement.setString(1, Long.toString(cowAward));
			awardInstancesStatement.setString(2, Long.toString(pugId));
			awardInstancesStatement.setString(3, "");
			awardInstancesStatement.execute();

			awardInstancesStatement.setString(1, Long.toString(glowAward));
			awardInstancesStatement.setString(2, Long.toString(ultrusId));
			awardInstancesStatement.setString(3, "");
			awardInstancesStatement.execute();

			var minecraftAccountStatement = connection.prepareStatement("INSERT OR IGNORE INTO minecraft_accounts(uuid, user_id) VALUES (?, ?)");
			minecraftAccountStatement.setString(1, "cd21c753fc8d493aa65c25184613402e");
			minecraftAccountStatement.setString(2, Long.toString(pugId));
			minecraftAccountStatement.execute();

			minecraftAccountStatement.setString(1, "6092cacbdd2a41c29f90e5b4680889cb");
			minecraftAccountStatement.setString(2, Long.toString(ultrusId));
			minecraftAccountStatement.execute();


		} catch (SQLException e) {
			ModGardenBackend.LOG.error("Failed to create database connection in insertDevelopmentModeData.", e);
			return;
		}
		ModGardenBackend.LOG.info("Inserted development mode data");
	}
}
