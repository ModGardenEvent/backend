package net.modgarden.backend.data.fixer;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.fixer.fix.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

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

	public static int getSchemaVersion() {
		if (FIXES.isEmpty()) {
			return -1;
		}
		return FIXES.getLast().getVersionToFixFrom() + 1;
	}

	public static void fixDatabase() {
		int version = -1;
		try (Connection connection = ModGardenBackend.createDatabaseConnection();
			 PreparedStatement schemaVersion = connection.prepareStatement("SELECT version FROM schema")) {
			ResultSet query = schemaVersion.executeQuery();

			version = query.getInt(1);
			int lastVersion = getSchemaVersion();
			if (lastVersion == -1 || version > lastVersion) {
				throw new IllegalStateException("Schema version is invalid! Got " + lastVersion + ", " + version + " in the database");
			}
			if (version == lastVersion)
				return;

		} catch (Exception ex) {
			ModGardenBackend.LOG.error("Failed to fix data: ", ex);
		}

		for (DatabaseFix fix : FIXES) {
			Consumer<Connection> dropper = null;
			try (Connection connection = ModGardenBackend.createDatabaseConnection()) {
				dropper = fix.fixInternal(connection, version);
			} catch (SQLException ex) {
				ModGardenBackend.LOG.error("Failed to fix data: ", ex);
			}

			try (Connection connection = ModGardenBackend.createDatabaseConnection()) {
				if (dropper != null) {
					dropper.accept(connection);
				}
			} catch (SQLException ex) {
				ModGardenBackend.LOG.error("Failed to fix data: ", ex);
			}
		}
	}
}
