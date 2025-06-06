package net.modgarden.backend.data;

import net.modgarden.backend.ModGardenBackend;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Random;

public class DevelopmentModeData {
	private static final Random RANDOM = new Random(123);
	private static final long DAY_MILLISECONDS = 86400000L;

	public static void insertDevelopmentModeData() {
		try {
			Connection connection = ModGardenBackend.createDatabaseConnection();
			var userStatement = connection.prepareStatement("INSERT OR IGNORE INTO users(id, username, display_name, discord_id, created, permissions, modrinth_id) VALUES (?, ?, ?, ?, ?, ?, ?)");
			long ultrusId = RANDOM.nextLong(Long.MAX_VALUE);
			userStatement.setString(1, Long.toString(ultrusId));
			userStatement.setString(2, "ultrusbot");
			userStatement.setString(3, "UltrusBot");
			userStatement.setString(4, "852948197356863528");
			userStatement.setLong(5, System.currentTimeMillis());
			userStatement.setLong(6, 1);
			userStatement.setString(7, "RlpLaNSn");
			userStatement.execute();

            long calicoId = RANDOM.nextLong(Long.MAX_VALUE);
			userStatement.setString(1, Long.toString(calicoId));
			userStatement.setString(2, "calico");
			userStatement.setString(3, "Calico");
			userStatement.setString(4, "680986902240690176");
			userStatement.setLong(5, System.currentTimeMillis());
			userStatement.setLong(6, 1);
			userStatement.setString(7, "84zsGbft");
			userStatement.execute();

			long greencowId = RANDOM.nextLong(Long.MAX_VALUE);
			userStatement.setString(1, Long.toString(greencowId));
			userStatement.setString(2, "greenbot");
			userStatement.setString(3, "GreenBot");
			userStatement.setString(4, "876135519526977587");
			userStatement.setLong(5, System.currentTimeMillis());
			userStatement.setLong(6, 0);
			userStatement.setNull(7, Types.VARCHAR);
			userStatement.execute();


			var eventStatement = connection.prepareStatement("INSERT OR IGNORE INTO events(id, slug, display_name, discord_role_id, registration_time, start_time, end_time, minecraft_version, loader) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
			long mojankId = RANDOM.nextLong(Long.MAX_VALUE);
			eventStatement.setString(1, Long.toString(mojankId));
			eventStatement.setString(2, "mojank-fest");
			eventStatement.setString(3, "MoJank Fest");
			eventStatement.setNull(4, Types.VARCHAR);
			eventStatement.setLong(5, System.currentTimeMillis() - (DAY_MILLISECONDS * 365));
			eventStatement.setLong(6, System.currentTimeMillis() - (DAY_MILLISECONDS * 365));
			eventStatement.setLong(7, System.currentTimeMillis() - (DAY_MILLISECONDS * 344));
			eventStatement.setString(8, "1.20.1");
			eventStatement.setString(9, "fabric");
			eventStatement.execute();

            long festivalId = RANDOM.nextLong(Long.MAX_VALUE);
            eventStatement.setString(1, Long.toString(festivalId));
            eventStatement.setString(2, "festival");
            eventStatement.setString(3, "Mod Garden: Festival");
			eventStatement.setNull(4, Types.VARCHAR);
			eventStatement.setLong(5, System.currentTimeMillis() - (DAY_MILLISECONDS * 229));
            eventStatement.setLong(6, System.currentTimeMillis() - (DAY_MILLISECONDS * 222));
			eventStatement.setLong(7, System.currentTimeMillis() - (DAY_MILLISECONDS * 162));
			eventStatement.setString(8, "1.21.1");
			eventStatement.setString(9, "fabric");
            eventStatement.execute();

			long exampleGardenId = RANDOM.nextLong(Long.MAX_VALUE);
			eventStatement.setString(1, Long.toString(exampleGardenId));
			eventStatement.setString(2, "mod-garden-example");
			eventStatement.setString(3, "Mod Garden: Example");
			eventStatement.setString(4, ModGardenBackend.DOTENV.get("", ""));
			eventStatement.setLong(5, System.currentTimeMillis() - (DAY_MILLISECONDS * 7));
			eventStatement.setLong(6, System.currentTimeMillis());
			eventStatement.setLong(7, System.currentTimeMillis() + (DAY_MILLISECONDS * 60));
			eventStatement.setString(8, "1.21.5");
			eventStatement.setString(9, "fabric");
			eventStatement.execute();

			long otherEvent = RANDOM.nextLong(Long.MAX_VALUE);
			eventStatement.setString(1, Long.toString(otherEvent));
			eventStatement.setString(2, "other-event");
			eventStatement.setString(3, "Other Event");
			eventStatement.setString(4, ModGardenBackend.DOTENV.get("OTHER_EVENT_ROLE_ID", ""));
			eventStatement.setLong(5, System.currentTimeMillis() + (DAY_MILLISECONDS * 7));
			eventStatement.setLong(6, System.currentTimeMillis() + (DAY_MILLISECONDS * 14));
			eventStatement.setLong(7, System.currentTimeMillis() + (DAY_MILLISECONDS * 35));
			eventStatement.setString(8, "1.21.1");
			eventStatement.setString(9, "neoforge");

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
			projectStatement.setString(3, Long.toString(calicoId));
			projectStatement.setString(4, "bovines-and-buttercups");
            projectStatement.execute();

			long rapscallionsId = RANDOM.nextLong(Long.MAX_VALUE);
			projectStatement.setString(1, Long.toString(rapscallionsId));
			projectStatement.setString(2, "9pGITjpO");
			projectStatement.setString(3, Long.toString(calicoId));
			projectStatement.setString(4, "rapscallions-and-rockhoppers");
            projectStatement.execute();

			var submissionStatement = connection.prepareStatement("INSERT OR IGNORE INTO submissions(id, event, project_id, modrinth_version_id, submitted) VALUES (?, ?, ?, ?, ?)");

			long glowBannersSubmissionId = RANDOM.nextLong(Long.MAX_VALUE);
			submissionStatement.setString(1, Long.toString(glowBannersSubmissionId));
			submissionStatement.setString(2, Long.toString(mojankId));
			submissionStatement.setString(3, Long.toString(glowBannersId));
			submissionStatement.setString(4, "c2VxpX2M");
			submissionStatement.setLong(5, System.currentTimeMillis() - (86400000 * 3));
			submissionStatement.execute();

			long smeltingTouchSubmissionId = RANDOM.nextLong(Long.MAX_VALUE);
			submissionStatement.setString(1, Long.toString(smeltingTouchSubmissionId));
			submissionStatement.setString(2, Long.toString(exampleGardenId));
			submissionStatement.setString(3, Long.toString(smeltingTouchId));
			submissionStatement.setString(4, "ubrXE4aR");
			submissionStatement.setLong(5, System.currentTimeMillis() - (86400000));
			submissionStatement.execute();

			long bovinesMojankSubmissionId = RANDOM.nextLong(Long.MAX_VALUE);
			submissionStatement.setString(1, Long.toString(bovinesMojankSubmissionId));
			submissionStatement.setString(2, Long.toString(mojankId));
			submissionStatement.setString(3, Long.toString(bovinesId));
			submissionStatement.setString(4, "j7WIi30J");
			submissionStatement.setLong(5, System.currentTimeMillis() - (86400000 * 4));
			submissionStatement.execute();

            long bovinesFestivalSubmissionId = RANDOM.nextLong(Long.MAX_VALUE);
            submissionStatement.setString(1, Long.toString(bovinesFestivalSubmissionId));
			submissionStatement.setString(2, Long.toString(festivalId));
            submissionStatement.setString(3, Long.toString(bovinesId));
            submissionStatement.setString(4, "j7WIi30J");
            submissionStatement.setLong(5, System.currentTimeMillis() - (86400000 * 4));
            submissionStatement.execute();

			long rapscallionsSubmissionId = RANDOM.nextLong(Long.MAX_VALUE);
			submissionStatement.setString(1, Long.toString(rapscallionsSubmissionId));
			submissionStatement.setString(2, Long.toString(exampleGardenId));
			submissionStatement.setString(3, Long.toString(rapscallionsId));
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
            projectAuthorsStatement.setString(2, Long.toString(calicoId));
            projectAuthorsStatement.execute();

            // Rapscallions and Rockhoppers Data
            projectAuthorsStatement.setString(1, Long.toString(rapscallionsId));
            projectAuthorsStatement.setString(2, Long.toString(calicoId));
            projectAuthorsStatement.execute();

			projectAuthorsStatement.setString(1, Long.toString(rapscallionsId));
			projectAuthorsStatement.setString(2, Long.toString(ultrusId));
			projectAuthorsStatement.execute();

			var awardsStatement = connection.prepareStatement("INSERT OR IGNORE INTO awards(id, slug, display_name, sprite, discord_emote, tooltip, tier) VALUES (?, ?, ?, ?, ?, ?, ?)");
			long cowAward = RANDOM.nextLong(Long.MAX_VALUE);
			awardsStatement.setString(1, Long.toString(cowAward));
			awardsStatement.setString(2, "flower-cow");
			awardsStatement.setString(3, "Flower Cow");
			awardsStatement.setString(4, "cow_award");
			awardsStatement.setString(5, "1065689127774867487");
			awardsStatement.setString(6, "Flower Cow Award: Flower Cow");
			awardsStatement.setString(7, "RARE");
			awardsStatement.execute();

			long glowAward = RANDOM.nextLong(Long.MAX_VALUE);
			awardsStatement.setString(1, Long.toString(glowAward));
			awardsStatement.setString(2, "glowing-award");
			awardsStatement.setString(3, "Glowing Award");
			awardsStatement.setString(4, "glow_award");
			awardsStatement.setString(5, "1205742638884462592");
			awardsStatement.setString(6, "Glowing Award, for mods that have glowing in them");
			awardsStatement.setString(7, "UNCOMMON");
			awardsStatement.execute();

			long mojankShardsAward = RANDOM.nextLong(Long.MAX_VALUE);
			awardsStatement.setString(1, Long.toString(mojankShardsAward));
			awardsStatement.setString(2, "mojank-petals");
			awardsStatement.setString(3, "Mojank Petals");
			awardsStatement.setString(4, "mojank_shards");
			awardsStatement.setString(5, "1333278359874179113");
			awardsStatement.setString(6, "You have collected %custom_data% out of 50 petals in the MoJank Fest event");
			awardsStatement.setString(7, "COMMON");
			awardsStatement.execute();

			long commonAward = RANDOM.nextLong(Long.MAX_VALUE);
			awardsStatement.setString(1, Long.toString(commonAward));
			awardsStatement.setString(2, "common-award");
			awardsStatement.setString(3, "Common Award");
			awardsStatement.setString(4, "common_award");
			awardsStatement.setString(5, "1333278359874179113");
			awardsStatement.setString(6, "Award Tier: Common");
			awardsStatement.setString(7, "COMMON");
			awardsStatement.execute();

			long uncommonAward = RANDOM.nextLong(Long.MAX_VALUE);
			awardsStatement.setString(1, Long.toString(uncommonAward));
			awardsStatement.setString(2, "uncommon-award");
			awardsStatement.setString(3, "Uncommon Award");
			awardsStatement.setString(4, "uncommon_award");
			awardsStatement.setString(5, "1333278359874179113");
			awardsStatement.setString(6, "Award Tier: Uncommon");
			awardsStatement.setString(7, "UNCOMMON");
			awardsStatement.execute();

			long rareAward = RANDOM.nextLong(Long.MAX_VALUE);
			awardsStatement.setString(1, Long.toString(rareAward));
			awardsStatement.setString(2, "rare-award");
			awardsStatement.setString(3, "Rare Award");
			awardsStatement.setString(4, "rare_award");
			awardsStatement.setString(5, "1333278359874179113");
			awardsStatement.setString(6, "Award Tier: Rare");
			awardsStatement.setString(7, "RARE");
			awardsStatement.execute();

			long legendaryAward = RANDOM.nextLong(Long.MAX_VALUE);
			awardsStatement.setString(1, Long.toString(legendaryAward));
			awardsStatement.setString(2, "legendary-award");
			awardsStatement.setString(3, "Legendary Award");
			awardsStatement.setString(4, "legendary_award");
			awardsStatement.setString(5, "1333278359874179113");
			awardsStatement.setString(6, "Award Tier: Legendary");
			awardsStatement.setString(7, "LEGENDARY");
			awardsStatement.execute();

			var awardInstancesStatement = connection.prepareStatement("INSERT OR IGNORE INTO award_instances(award_id, awarded_to, custom_data, submission_id, tier_override) VALUES (?, ?, ?, ?, ?)");
			awardInstancesStatement.setString(1, Long.toString(cowAward));
			awardInstancesStatement.setString(2, Long.toString(calicoId));
			awardInstancesStatement.setString(3, "");
			awardInstancesStatement.setString(4, Long.toString(bovinesFestivalSubmissionId));
			awardInstancesStatement.setNull(5, Types.VARCHAR);
			awardInstancesStatement.execute();

			awardInstancesStatement.setString(1, Long.toString(glowAward));
			awardInstancesStatement.setString(2, Long.toString(ultrusId));
			awardInstancesStatement.setString(3, "");
			awardInstancesStatement.setString(4, Long.toString(glowBannersSubmissionId));
			awardInstancesStatement.setNull(5, Types.VARCHAR);
			awardInstancesStatement.execute();

			awardInstancesStatement.setString(1, Long.toString(mojankShardsAward));
			awardInstancesStatement.setString(2, Long.toString(ultrusId));
			awardInstancesStatement.setString(3, "25");
			awardInstancesStatement.setNull(4, Types.VARCHAR);
			awardInstancesStatement.setString(5, "UNCOMMON");
			awardInstancesStatement.execute();

			awardInstancesStatement.setString(1, Long.toString(mojankShardsAward));
			awardInstancesStatement.setString(2, Long.toString(calicoId));
			awardInstancesStatement.setString(3, "50");
			awardInstancesStatement.setNull(4, Types.VARCHAR);
			awardInstancesStatement.setString(5, "LEGENDARY");
			awardInstancesStatement.execute();

			awardInstancesStatement.setString(1, Long.toString(mojankShardsAward));
			awardInstancesStatement.setString(2, Long.toString(greencowId));
			awardInstancesStatement.setString(3, "2");
			awardInstancesStatement.setNull(4, Types.VARCHAR);
			awardInstancesStatement.setNull(5, Types.VARCHAR);
			awardInstancesStatement.execute();

			awardInstancesStatement.setString(1, Long.toString(commonAward));
			awardInstancesStatement.setString(2, Long.toString(greencowId));
			awardInstancesStatement.setString(3, "");
			awardInstancesStatement.setNull(4, Types.VARCHAR);
			awardInstancesStatement.setNull(5, Types.VARCHAR);
			awardInstancesStatement.execute();

			awardInstancesStatement.setString(1, Long.toString(uncommonAward));
			awardInstancesStatement.setString(2, Long.toString(greencowId));
			awardInstancesStatement.setString(3, "");
			awardInstancesStatement.setNull(4, Types.VARCHAR);
			awardInstancesStatement.setNull(5, Types.VARCHAR);
			awardInstancesStatement.execute();

			awardInstancesStatement.setString(1, Long.toString(rareAward));
			awardInstancesStatement.setString(2, Long.toString(greencowId));
			awardInstancesStatement.setString(3, "");
			awardInstancesStatement.setNull(4, Types.VARCHAR);
			awardInstancesStatement.setNull(5, Types.VARCHAR);
			awardInstancesStatement.execute();

			awardInstancesStatement.setString(1, Long.toString(legendaryAward));
			awardInstancesStatement.setString(2, Long.toString(greencowId));
			awardInstancesStatement.setString(3, "");
			awardInstancesStatement.setNull(4, Types.VARCHAR);
			awardInstancesStatement.setNull(5, Types.VARCHAR);
			awardInstancesStatement.execute();

			var minecraftAccountStatement = connection.prepareStatement("INSERT OR IGNORE INTO minecraft_accounts(uuid, user_id) VALUES (?, ?)");
			minecraftAccountStatement.setString(1, "cd21c753fc8d493aa65c25184613402e");
			minecraftAccountStatement.setString(2, Long.toString(calicoId));
			minecraftAccountStatement.execute();

			minecraftAccountStatement.setString(1, "6092cacbdd2a41c29f90e5b4680889cb");
			minecraftAccountStatement.setString(2, Long.toString(ultrusId));
			minecraftAccountStatement.execute();

		} catch (SQLException e) {
			ModGardenBackend.LOG.error("Failed to insert development mode data.", e);
			return;
		}
		ModGardenBackend.LOG.debug("Inserted development mode data");
	}
}
