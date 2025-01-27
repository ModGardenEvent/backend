package net.modgarden.backend.data;

import de.mkammerer.snowflakeid.SnowflakeIdGenerator;
import net.modgarden.backend.ModGardenBackend;

import java.sql.Connection;
import java.sql.SQLException;

public class DevelopmentModeData {
	public static void insertDevelopmentModeData() {
		try {
			Connection connection = ModGardenBackend.createDatabaseConnection();
			var userStatement = connection.prepareStatement("INSERT OR IGNORE INTO users(id, username, display_name, discord_id, created) VALUES (?, ?, ?, ?, ?)");
			var generator = SnowflakeIdGenerator.createDefault(0);
			long ultrusId = generator.next();
			userStatement.setString(1, Long.toString(ultrusId));
			userStatement.setString(2, "ultrusbot");
			userStatement.setString(3, "UltrusBot");
			userStatement.setString(4, "852948197356863528");
			userStatement.setLong(5, System.currentTimeMillis());
			userStatement.execute();

            long pugId = generator.next();
			userStatement.setString(1, Long.toString(pugId));
			userStatement.setString(2, "pug");
			userStatement.setString(3, "Pug");
			userStatement.setString(4, "680986902240690176");
			userStatement.setLong(5, System.currentTimeMillis());
			userStatement.execute();


			var eventStatement = connection.prepareStatement("INSERT OR IGNORE INTO events(id, slug, display_name, started, minecraft_version, loader, loader_version) VALUES (?, ?, ?, ?, '1.21.1', 'fabric', '0.16.10')");
			long mojankId = generator.next();
			eventStatement.setString(1, Long.toString(mojankId));
			eventStatement.setString(2, "mojank-fest");
			eventStatement.setString(3, "MoJank Fest");
			eventStatement.setLong(4, System.currentTimeMillis() - (86400000 * 5));
			eventStatement.execute();

            long festivalId = generator.next();
            eventStatement.setString(1, Long.toString(festivalId));
            eventStatement.setString(2, "festival");
            eventStatement.setString(3, "Mod Garden: Festival");
            eventStatement.setLong(4, System.currentTimeMillis() - (86400000 * 3));
            eventStatement.execute();

			long exampleGardenId = generator.next();
			eventStatement.setString(1, Long.toString(exampleGardenId));
			eventStatement.setString(2, "example-garden");
			eventStatement.setString(3, "Example Garden");
			eventStatement.setLong(4, System.currentTimeMillis() - (86400000 * 2));
			eventStatement.execute();

			var projectStatement = connection.prepareStatement("INSERT OR IGNORE INTO projects(id, modrinth_id, attributed_to, slug) VALUES (?, ?, ?, ?)");
			long glowBannersId = generator.next();
			projectStatement.setString(1, Long.toString(glowBannersId));
			projectStatement.setString(2, "r7G43arb");
			projectStatement.setString(3, Long.toString(ultrusId));
			projectStatement.setString(4, "glow-banners");
			projectStatement.execute();

			long smeltingTouchId = generator.next();
			projectStatement.setString(1, Long.toString(smeltingTouchId));
			projectStatement.setString(2, "otiSEfKe");
			projectStatement.setString(3, Long.toString(ultrusId));
			projectStatement.setString(4, "smelting-touch");
			projectStatement.execute();

			long bovinesId = generator.next();
			projectStatement.setString(1, Long.toString(bovinesId));
			projectStatement.setString(2, "BDg6nMn3");
			projectStatement.setString(3, Long.toString(pugId));
			projectStatement.setString(4, "bovines-and-buttercups");
            projectStatement.execute();

			long rapscallionsId = generator.next();
			projectStatement.setString(1, Long.toString(rapscallionsId));
			projectStatement.setString(2, "9pGITjpO");
			projectStatement.setString(3, Long.toString(pugId));
			projectStatement.setString(4, "rapscallions-and-rockhoppers");
            projectStatement.execute();

			var submissionStatement = connection.prepareStatement("INSERT OR IGNORE INTO submissions(id, project_id, event, modrinth_version_id, submitted_at) VALUES (?, ?, ?, ?, ?)");

			long glowBannersSubmissionId = generator.next();
			submissionStatement.setString(1, Long.toString(glowBannersSubmissionId));
			submissionStatement.setString(2, Long.toString(glowBannersId));
			submissionStatement.setString(3, Long.toString(mojankId));
			submissionStatement.setString(4, "c2VxpX2M");
			submissionStatement.setLong(5, System.currentTimeMillis() - (86400000 * 3));
			submissionStatement.execute();

			long smeltingTouchSubmissionId = generator.next();
			submissionStatement.setString(1, Long.toString(smeltingTouchSubmissionId));
			submissionStatement.setString(2, Long.toString(smeltingTouchId));
			submissionStatement.setString(3, Long.toString(exampleGardenId));
			submissionStatement.setString(4, "ubrXE4aR");
			submissionStatement.setLong(5, System.currentTimeMillis() - (86400000));
			submissionStatement.execute();

			long bovinesMojankSubmissionId = generator.next();
			submissionStatement.setString(1, Long.toString(bovinesMojankSubmissionId));
			submissionStatement.setString(2, Long.toString(bovinesId));
			submissionStatement.setString(3, Long.toString(mojankId));
			submissionStatement.setString(4, "j7WIi30J");
			submissionStatement.setLong(5, System.currentTimeMillis() - (86400000 * 4));
			submissionStatement.execute();

            long bovinesFestivalSubmissionId = generator.next();
            submissionStatement.setString(1, Long.toString(bovinesFestivalSubmissionId));
            submissionStatement.setString(2, Long.toString(bovinesId));
            submissionStatement.setString(3, Long.toString(festivalId));
            submissionStatement.setString(4, "j7WIi30J");
            submissionStatement.setLong(5, System.currentTimeMillis() - (86400000 * 4));
            submissionStatement.execute();

			long rapscallionsSubmissionId = generator.next();
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

			// TODO: Add more example data for the rest of the tables

		} catch (SQLException e) {
			ModGardenBackend.LOG.error("Failed to create database connection in insertDevelopmentModeData.", e);
			return;
		}
		ModGardenBackend.LOG.info("Inserted development mode data");
	}
}
