package net.modgarden.backend.data.fixer;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.fixer.fix.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.List;

public class DatabaseFixer {
	private static final List<DatabaseFix> FIXES = new ObjectArrayList<>();

	public static void createFixers() {
		Collections.addAll(
				FIXES,
				new V1ToV2(),
				new V2ToV3(),
				new V3ToV4(),
				new V4ToV5(),
				new V5ToV6()
		);
	}

	public static void fixDatabase() {
		try (Connection connection = ModGardenBackend.createDatabaseConnection();
			 PreparedStatement schemaVersion = connection.prepareStatement("SELECT version FROM schema")) {
			ResultSet query = schemaVersion.executeQuery();

			int version = query.getInt(1);
			if (version >= ModGardenBackend.DATABASE_SCHEMA_VERSION)
				return;

			for (DatabaseFix fix : FIXES) {
				fix.fixInternal(connection, version);
			}
		} catch (Exception ex) {
			ModGardenBackend.LOG.error("Failed to fix data: ", ex);
		}
	}
}
