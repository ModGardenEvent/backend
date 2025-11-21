package net.modgarden.backend.data.fixer.fix;

import net.modgarden.backend.data.fixer.DatabaseFix;
import net.modgarden.backend.util.MetadataUtils;
import org.jetbrains.annotations.Nullable;
import org.sqlite.Function;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Consumer;

public class V5ToV6 extends DatabaseFix {
	public V5ToV6() {
		super(5);
	}

	@Override
	public @Nullable Consumer<Connection> fix(Connection connection) throws SQLException {
		var statement = connection.createStatement();

		// temp functions for the datafixer
		Function.create(
				connection, "clean_slug_mg", new Function() {
					@Override
					protected void xFunc() throws SQLException {
						String slug = this.value_text(0);
						this.result(slug.replace("mod-garden-", ""));
					}
				}
		);

		statement.addBatch("PRAGMA foreign_keys = ON");

		statement.addBatch("ALTER TABLE users RENAME TO users_old");
		statement.addBatch("""
		CREATE TABLE IF NOT EXISTS users (
			id TEXT UNIQUE NOT NULL,
			username TEXT UNIQUE NOT NULL,
			created INTEGER NOT NULL,
			permissions INTEGER NOT NULL,
			PRIMARY KEY(id)
		)
		""");
		statement.addBatch("""
		INSERT INTO users (id, username, created, permissions)
		SELECT id, username, created, permissions from users_old
		""");


		statement.addBatch("""
		CREATE TABLE IF NOT EXISTS user_bios (
			user_id TEXT UNIQUE NOT NULL,
			display_name TEXT NOT NULL,
			pronouns TEXT,
			description TEXT,
			avatar_url TEXT,
			FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE,
			PRIMARY KEY (user_id)
		)
		""");
		statement.addBatch("""
		CREATE TABLE IF NOT EXISTS user_bio_fields (
			user_id TEXT NOT NULL,
			field_name TEXT NOT NULL,
			field_value TEXT NOT NULL,
			FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE
		)
		""");
		statement.addBatch("""
		CREATE UNIQUE INDEX idx_user_id_field_name ON user_bio_fields(field_name, field_value)
		""");

		statement.addBatch("""
		INSERT INTO user_bios (user_id, display_name, pronouns, avatar_url)
		SELECT id, display_name, pronouns, avatar_url FROM users_old
		""");

		// Events modification is above all event related operations because order matters when executing SQL actions.
		statement.addBatch("""
		ALTER TABLE events ADD event_type_slug TEXT NOT NULL DEFAULT 'mod-garden'
		""");
		statement.addBatch("""
		ALTER TABLE events RENAME COLUMN registration_time TO registration_open_time
		""");
		statement.addBatch("""
		ALTER TABLE events ADD registration_close_time INTEGER NOT NULL DEFAULT 1748131200000
		""");
		statement.addBatch("""
		ALTER TABLE events RENAME TO events_old
		""");
		statement.addBatch("""
		CREATE TABLE IF NOT EXISTS events (
			id TEXT UNIQUE NOT NULL,
			slug TEXT UNIQUE NOT NULL,
			event_type_slug TEXT NOT NULL,
			display_name TEXT NOT NULL,
			minecraft_version TEXT NOT NULL,
			loader TEXT NOT NULL,
			registration_open_time INTEGER NOT NULL,
			registration_close_time INTEGER NOT NULL,
			start_time INTEGER NOT NULL,
			end_time INTEGER NOT NULL,
			freeze_time INTEGER NOT NULL,
			PRIMARY KEY (id)
		)
		""");
		statement.addBatch("""
		INSERT INTO events (id, slug, event_type_slug, display_name, minecraft_version, loader, registration_open_time, registration_close_time, start_time, end_time, freeze_time)
		SELECT id, slug, event_type_slug, display_name, minecraft_version, loader, registration_open_time, registration_close_time, start_time, end_time, freeze_time from events_old
		""");

		statement.addBatch("ALTER TABLE projects RENAME TO projects_old");
		statement.addBatch("""
		CREATE TABLE IF NOT EXISTS projects (
			id TEXT UNIQUE NOT NULL,
			PRIMARY KEY (id)
		)
		""");
		statement.addBatch("""
		INSERT INTO projects (id)
		SELECT id FROM projects_old
		""");

		statement.addBatch("""
		CREATE TABLE IF NOT EXISTS project_metadata (
			project_id TEXT UNIQUE NOT NULL,
			mod_id TEXT NOT NULL,
			name TEXT NOT NULL,
			description TEXT,
			source_url TEXT NOT NULL,
			icon_url TEXT NOT NULL,
			banner_url TEXT,
			FOREIGN KEY (project_id) REFERENCES projects(id) ON UPDATE CASCADE ON DELETE CASCADE,
			PRIMARY KEY (project_id)
		)
		""");

		// For similar reasons to the below, handle projects and submissions above other content too.
		statement.addBatch("ALTER TABLE submissions RENAME TO submissions_old");
		statement.addBatch("""
		CREATE TABLE IF NOT EXISTS submissions (
			id TEXT UNIQUE NOT NULL,
			event TEXT NOT NULL,
			project_id TEXT NOT NULL,
			submitted INTEGER NOT NULL,
			FOREIGN KEY (project_id) REFERENCES projects(id) ON UPDATE CASCADE ON DELETE CASCADE,
			FOREIGN KEY (event) REFERENCES events(id) ON UPDATE CASCADE ON DELETE CASCADE,
			PRIMARY KEY(id)
		)
		""");
		statement.addBatch("""
		INSERT INTO submissions (id, event, project_id, submitted)
		SELECT id, event, project_id, submitted from submissions_old
		""");
		statement.addBatch("""
		UPDATE submissions
		SET id = generate_natural_id_from_snowflake_id('submissions', id)
		""");

		// Use submissions_old since it has not yet been deleted.
		statement.addBatch("CREATE TABLE submissions_mr AS SELECT * FROM submissions_old");
		statement.addBatch("ALTER TABLE submissions_mr ADD COLUMN modrinth_id TEXT");

		statement.addBatch("""
		UPDATE submissions_mr
		SET modrinth_id = (
			SELECT modrinth_id FROM projects_old WHERE submissions_mr.project_id = projects_old.id
		)
		""");

		statement.addBatch("""
		UPDATE submissions_mr
		SET id = generate_natural_id_from_snowflake_id('submissions_mr', id)
		""");

		statement.addBatch("""
		CREATE TABLE IF NOT EXISTS submission_type_modrinth (
			submission_id TEXT NOT NULL,
			modrinth_id TEXT NOT NULL,
			version_id TEXT NOT NULL,
			FOREIGN KEY (submission_id) REFERENCES submissions(id) ON UPDATE CASCADE ON DELETE CASCADE,
			PRIMARY KEY (submission_id)
		)
		""");
		statement.addBatch("""
		INSERT INTO submission_type_modrinth (submission_id, modrinth_id, version_id)
		SELECT id, modrinth_id, modrinth_version_id FROM submissions_mr
		WHERE modrinth_id NOT NULL
		""");

		statement.addBatch("""
		CREATE TABLE IF NOT EXISTS api_keys (
			uuid BLOB NOT NULL,
			user_id TEXT NOT NULL,
			hash TEXT NOT NULL,
			expires INTEGER NOT NULL,
			name TEXT NOT NULL,
			FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE,
			PRIMARY KEY (uuid)
		)
		""");
		statement.addBatch("""
		CREATE TABLE IF NOT EXISTS api_key_scopes (
			uuid BLOB NOT NULL,
			scope TEXT NOT NULL CHECK (scope in ('project', 'user')),
			project_id TEXT,
			permissions INTEGER NOT NULL,
			FOREIGN KEY (project_id) REFERENCES projects(id) ON UPDATE CASCADE ON DELETE CASCADE,
			FOREIGN KEY (uuid) REFERENCES api_keys(uuid) ON UPDATE CASCADE ON DELETE CASCADE,
			PRIMARY KEY (uuid)
		)
		""");
		statement.addBatch("""
		CREATE TABLE IF NOT EXISTS passwords (
			user_id TEXT NOT NULL,
			hash TEXT NOT NULL,
			last_updated INTEGER NOT NULL,
			FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE,
			PRIMARY KEY (user_id)
		)
		""");
		statement.addBatch("""
		CREATE TABLE IF NOT EXISTS user_integration_modrinth (
			user_id TEXT NOT NULL,
			modrinth_id TEXT NOT NULL,
			FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE,
			PRIMARY KEY (user_id)
		)
		""");
		statement.addBatch("""
		CREATE TABLE IF NOT EXISTS user_integration_discord (
			user_id TEXT NOT NULL,
			discord_id TEXT NOT NULL,
			FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE,
			PRIMARY KEY (user_id)
		)
		""");

		statement.addBatch("""
		CREATE TABLE IF NOT EXISTS project_roles (
			project_id TEXT NOT NULL,
			user_id TEXT NOT NULL,
			permissions INTEGER NOT NULL DEFAULT 0,
			role_name TEXT NOT NULL DEFAULT 'Member',
			FOREIGN KEY (project_id) REFERENCES projects(id) ON UPDATE CASCADE ON DELETE CASCADE,
			FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE
		)
		""");

		statement.addBatch("""
		CREATE UNIQUE INDEX idx_project_roles_two_ids ON project_roles(project_id, user_id)
		""");

		statement.addBatch("""
		CREATE TABLE IF NOT EXISTS event_integration_discord (
			id TEXT UNIQUE NOT NULL,
			role_id TEXT NOT NULL,
			FOREIGN KEY (id) REFERENCES events(id) ON UPDATE CASCADE ON DELETE CASCADE,
			PRIMARY KEY (id)
		)
		""");
		statement.addBatch("""
		INSERT INTO event_integration_discord (id, role_id)
		SELECT id, discord_role_id FROM events_old
		""");

		statement.addBatch("""
		INSERT INTO user_integration_modrinth (user_id, modrinth_id)
		SELECT id, modrinth_id FROM users_old
		WHERE modrinth_id NOT NULL
		""");

		statement.addBatch("""
		INSERT INTO user_integration_discord (user_id, discord_id)
		SELECT id, discord_id FROM users_old
		""");


		statement.addBatch("""
		CREATE TABLE IF NOT EXISTS project_roles_temp (
			project_id TEXT NOT NULL,
			user_id TEXT NOT NULL,
			permissions INTEGER NOT NULL DEFAULT 1,
			role_name TEXT NOT NULL DEFAULT 'Member',
			FOREIGN KEY (project_id) REFERENCES projects(id) ON UPDATE CASCADE ON DELETE CASCADE,
			FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE
		)
		""");

		statement.addBatch("""
		INSERT OR REPLACE INTO project_roles_temp (project_id, user_id)
		SELECT project_id, user_id FROM project_authors
		""");

		statement.addBatch("""
		INSERT INTO project_roles (project_id, user_id, permissions, role_name)
		SELECT project_id, user_id, permissions, role_name FROM project_roles_temp
		""");

		statement.addBatch("ALTER TABLE minecraft_accounts RENAME TO minecraft_accounts_old");
		statement.addBatch("""
		CREATE TABLE IF NOT EXISTS user_integration_minecraft (
			uuid TEXT UNIQUE NOT NULL,
			user_id TEXT UNIQUE NOT NULL,
			FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE,
			PRIMARY KEY (uuid)
		)
		""");
		statement.addBatch("""
		INSERT INTO user_integration_minecraft (uuid, user_id)
		SELECT uuid, user_id FROM minecraft_accounts_old
		""");

		statement.addBatch("ALTER TABLE award_instances RENAME TO award_instances_old");
		statement.addBatch("""
		CREATE TABLE IF NOT EXISTS award_instances (
			award_id TEXT UNIQUE NOT NULL,
			awarded_to TEXT NOT NULL,
			custom_data TEXT,
			submission_id TEXT,
			tier_override TEXT CHECK (tier_override in ('COMMON', 'UNCOMMON', 'RARE', 'LEGENDARY')),
			FOREIGN KEY (award_id) REFERENCES awards(id) ON UPDATE CASCADE ON DELETE CASCADE,
			FOREIGN KEY (awarded_to) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE,
			FOREIGN KEY (submission_id) REFERENCES submissions(id) ON UPDATE CASCADE ON DELETE CASCADE,
			PRIMARY KEY (award_id, awarded_to)
		)
		""");
		statement.addBatch("""
		INSERT INTO award_instances (award_id, awarded_to, custom_data, submission_id, tier_override)
		SELECT award_id, awarded_to, custom_data, submission_id, tier_override FROM award_instances_old
		""");

		statement.addBatch("ALTER TABLE team_invites RENAME TO team_invites_old");
		statement.addBatch("""
		CREATE TABLE IF NOT EXISTS team_invites (
			code TEXT NOT NULL,
			project_id TEXT NOT NULL,
			user_id TEXT NOT NULL,
			expires INTEGER NOT NULL,
			role TEXT NOT NULL DEFAULT 'Member',
			permissions INTEGER NOT NULL DEFAULT 0,
			FOREIGN KEY (project_id) REFERENCES projects(id) ON UPDATE CASCADE ON DELETE CASCADE,
			FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE,
			PRIMARY KEY (code)
		)
		""");
		statement.addBatch("""
		INSERT INTO team_invites (code, project_id, user_id, expires, role)
		SELECT code, project_id, user_id, expires, role FROM team_invites_old
		""");

		statement.addBatch("""
		UPDATE users
		SET id = generate_natural_id_from_snowflake_id('users', id)
		""");

		statement.addBatch("""
		UPDATE users
		SET id = 'mgacc', permissions = 1
		WHERE username == 'mod_garden'
		""");
		statement.addBatch("""
		UPDATE user_bios
		SET pronouns = 'they/it'
		WHERE user_id = 'mgacc'
		""");

		statement.addBatch("""
		INSERT INTO users VALUES ('grbot', 'gardenbot', unix_millis(), 1)
		""");
		statement.addBatch("""
		UPDATE user_bios
		SET display_name = 'GardenBot', pronouns = 'it/its'
		WHERE user_id = 'grbot'
		""");

		statement.addBatch("""
		INSERT INTO users VALUES ('abcde', 'tiny_pineapple', unix_millis(), 0)
		""");
		statement.addBatch("""
		UPDATE user_bios
		SET display_name = 'Tiny Pineapple', pronouns = 'it/its'
		WHERE user_id = 'abcde'
		""");

		statement.addBatch("""
		UPDATE projects
		SET id = generate_natural_id_from_snowflake_id('projects', id)
		""");

		statement.addBatch("""
		UPDATE events
		SET id = generate_natural_id_from_snowflake_id('events', id)
		""");
		statement.addBatch("""
		UPDATE events
		SET slug = clean_slug_mg(slug)
		""");

		statement.executeBatch();

		var modrinthSubmissionsStatement = connection.prepareStatement("""
			SELECT s.project_id, mr.modrinth_id, mr.version_id
			FROM submission_type_modrinth mr
			INNER JOIN submissions s ON s.id = mr.submission_id
		""");
		var projectMetadataInsertStatement = connection.prepareStatement("""
			INSERT INTO project_metadata (project_id, mod_id, name, description, source_url, icon_url, banner_url)
			VALUES (?, ?, ?, ?, ?, ?, ?)
		""");
		var modrinthSubmissionsResult = modrinthSubmissionsStatement.executeQuery();

		if (modrinthSubmissionsResult.isBeforeFirst()) {
			while (modrinthSubmissionsResult.next()) {
				String projectId = modrinthSubmissionsResult.getString("project_id");
				String modrinthId = modrinthSubmissionsResult.getString("modrinth_id");
				String modrinthVersionId = modrinthSubmissionsResult.getString("version_id");

				try {
					var modrinthData = MetadataUtils.getMetadataFromModrinth(modrinthId, modrinthVersionId);
					projectMetadataInsertStatement.setString(1, projectId);
					projectMetadataInsertStatement.setString(2, modrinthData.modId());
					projectMetadataInsertStatement.setString(3, modrinthData.name());
					projectMetadataInsertStatement.setString(4, modrinthData.description());
					projectMetadataInsertStatement.setString(5, modrinthData.sourceUrl());
					projectMetadataInsertStatement.setString(6, modrinthData.iconUrl());
					projectMetadataInsertStatement.setString(7, modrinthData.bannerUrl());
					projectMetadataInsertStatement.executeUpdate();
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}

		return postConnections -> {
			try {
				var postStatements = postConnections.createStatement();
				postStatements.addBatch("PRAGMA foreign_keys = ON");

				postStatements.addBatch("DROP TABLE submissions_old");
				postStatements.addBatch("DROP TABLE submissions_mr");
				postStatements.addBatch("DROP TABLE project_builders");
				postStatements.addBatch("DROP TABLE project_authors");
				postStatements.addBatch("DROP TABLE project_roles_temp");
				postStatements.addBatch("DROP TABLE minecraft_accounts_old");
				postStatements.addBatch("DROP TABLE award_instances_old");
				postStatements.addBatch("DROP TABLE team_invites_old");
				postStatements.addBatch("DROP TABLE projects_old");
				postStatements.addBatch("DROP TABLE users_old");
				postStatements.addBatch("DROP TABLE events_old");
				postStatements.executeBatch();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		};
	}
}
