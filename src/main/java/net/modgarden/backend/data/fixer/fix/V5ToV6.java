package net.modgarden.backend.data.fixer.fix;

import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.NaturalId;
import net.modgarden.backend.data.fixer.DatabaseFix;
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

		statement.addBatch("PRAGMA foreign_keys = ON");


		statement.addBatch("ALTER TABLE users RENAME TO users_old");
		statement.addBatch("""
		CREATE TABLE IF NOT EXISTS users (
			id TEXT UNIQUE NOT NULL,
			username TEXT UNIQUE NOT NULL,
			display_name TEXT NOT NULL,
			pronouns TEXT,
			avatar_url TEXT,
			created INTEGER NOT NULL,
			permissions INTEGER NOT NULL,
			PRIMARY KEY(id)
		)
		""");
		statement.addBatch("""
		INSERT INTO users (id, username, display_name, pronouns, avatar_url, created, permissions)
		SELECT id, username, display_name, pronouns, avatar_url, created, permissions from users_old
		""");


		statement.addBatch("CREATE TABLE submissions_mr AS SELECT * FROM submissions");
		statement.addBatch("ALTER TABLE submissions_mr ADD COLUMN modrinth_id TEXT");

		statement.addBatch("""
		UPDATE submissions_mr
		SET modrinth_id = (
			SELECT modrinth_id FROM projects WHERE submissions_mr.project_id = projects.id
		)
		""");

		statement.addBatch("ALTER TABLE projects RENAME TO projects_old");
		statement.addBatch("""
		CREATE TABLE IF NOT EXISTS projects (
			id TEXT UNIQUE NOT NULL,
			slug TEXT UNIQUE NOT NULL,
			PRIMARY KEY (id)
		)
		""");
		statement.addBatch("""
		INSERT INTO projects (id, slug)
		SELECT id, slug FROM projects_old
		""");


		statement.addBatch("""
		CREATE TABLE IF NOT EXISTS api_keys (
			uuid BLOB NOT NULL,
			user_id TEXT NOT NULL,
			salt BLOB NOT NULL,
			hash BLOB UNIQUE NOT NULL,
			expires INTEGER NOT NULL,
			FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE,
			PRIMARY KEY (uuid)
		)
		""");
		statement.addBatch("""
		CREATE TABLE IF NOT EXISTS api_key_scopes (
			uuid BLOB NOT NULL,
			scope TEXT CHECK (scope in ('project', 'user')),
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
			salt BLOB NOT NULL,
			hash BLOB NOT NULL,
			last_updated INTEGER NOT NULL,
			FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE,
			PRIMARY KEY (user_id)
		)
		""");
		statement.addBatch("""
		CREATE TABLE IF NOT EXISTS integration_modrinth (
			user_id TEXT NOT NULL,
			modrinth_id TEXT NOT NULL,
			FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE,
			PRIMARY KEY (user_id)
		)
		""");
		statement.addBatch("""
		CREATE TABLE IF NOT EXISTS integration_discord (
			user_id TEXT NOT NULL,
			discord_id TEXT NOT NULL,
			FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE,
			PRIMARY KEY (user_id)
		)
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
		INSERT INTO submission_type_modrinth (submission_id, modrinth_id, version_id)
		SELECT id, modrinth_id, modrinth_version_id FROM submissions_mr
		WHERE modrinth_id NOT NULL
		""");
		statement.addBatch("""
		INSERT INTO integration_modrinth (user_id, modrinth_id)
		SELECT id, modrinth_id FROM users_old
		WHERE modrinth_id NOT NULL
		""");

		statement.addBatch("""
		INSERT INTO integration_discord (user_id, discord_id)
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
		CREATE TABLE IF NOT EXISTS minecraft_accounts (
			uuid TEXT UNIQUE NOT NULL,
			user_id TEXT UNIQUE NOT NULL,
			FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE,
			PRIMARY KEY (uuid)
		)
		""");
		statement.addBatch("""
		INSERT INTO minecraft_accounts (uuid, user_id)
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
			role TEXT NOT NULL CHECK (role IN ('author', 'builder')),
			FOREIGN KEY (project_id) REFERENCES projects(id) ON UPDATE CASCADE ON DELETE CASCADE,
			FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE,
			PRIMARY KEY (code)
		)
		""");
		statement.addBatch("""
		INSERT INTO team_invites (code, project_id, user_id, expires, role)
		SELECT code, project_id, user_id, expires, role FROM team_invites
		""");

		Function.create(
				connection, "generate_natural_id", new Function() {
					@Override
					protected void xFunc() throws SQLException {
						String table = this.value_text(0);
						String key = this .value_text(1);
						int length = this.value_int(2);
						this.result(NaturalId.generate(table, key, length));
					}
				}
		);
		Function.create(
				connection, "generate_natural_id_from_number", new Function() {
					@Override
					protected void xFunc() throws SQLException {
						int number = this.value_int(0);
						int length = this.value_int(1);
						this.result(NaturalId.generateFromNumber(number, length));
					}
				}
		);

		statement.addBatch("""
		WITH cnt(i) AS (
			SELECT 1 UNION SELECT i+1 FROM cnt
		)
		UPDATE users
		SET id = concat('zzz', generate_natural_id_from_number(ROWID - 1, 2))
		""");

		statement.addBatch("""
		UPDATE users
		SET id = 'mgacc', permissions = 1, pronouns = 'they/it'
		WHERE username == 'mod_garden'
		""");

		statement.addBatch("""
		INSERT INTO users VALUES ('grbot', 'gardenbot', 'GardenBot', 'it/its', NULL, unixepoch('subsec') * 1000, 1)
		""");

		statement.addBatch("""
		INSERT INTO users VALUES ('abcde', 'tiny_pineapple', 'Tiny Pineapple', 'it/its', NULL, unixepoch('subsec') * 1000, 0)
		""");

		statement.executeBatch();

		return dropConnection -> {
			try {
				var dropStatement = dropConnection.createStatement();
				dropStatement.addBatch("PRAGMA foreign_keys = ON");
				dropStatement.addBatch("DROP TABLE submissions_old");
				dropStatement.addBatch("DROP TABLE submissions_mr");
				dropStatement.addBatch("DROP TABLE project_builders");
				dropStatement.addBatch("DROP TABLE project_authors");
				dropStatement.addBatch("DROP TABLE project_roles_temp");
				dropStatement.addBatch("DROP TABLE minecraft_accounts_old");
				dropStatement.addBatch("DROP TABLE award_instances_old");
				dropStatement.addBatch("DROP TABLE team_invites_old");
				dropStatement.addBatch("DROP TABLE projects_old");
				dropStatement.addBatch("DROP TABLE users_old");
				dropStatement.executeBatch();
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
		};
	}
}
