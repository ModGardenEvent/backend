package net.modgarden.backend.data.fixer;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.fixer.fix.V1ToV2;
import net.modgarden.backend.data.fixer.fix.V2ToV3;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

public class DatabaseFixer {
	private static final List<DatabaseFix> FIXES = new ObjectArrayList<>();

	public static void createFixers() {
		FIXES.add(new V1ToV2());
		FIXES.add(new V2ToV3());
	}

	public static void fixDatabase() {
		try (Connection connection = ModGardenBackend.createDatabaseConnection();
			 PreparedStatement prepared = connection.prepareStatement("SELECT 1 FROM schema WHERE version = ?")) {
			prepared.setInt(1, ModGardenBackend.DATABASE_SCHEMA_VERSION);
			ResultSet query = prepared.executeQuery();
			if (query.getBoolean(1))
				return;

			for (DatabaseFix fix : FIXES) {
				fix.fixInternal(connection);
			}
		} catch (Exception ex) {
			ModGardenBackend.LOG.error("Failed to fix data: ", ex);
		}
	}
}
