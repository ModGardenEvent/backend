package net.modgarden.backend.data.fixer.fix;

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

		statement.execute("PRAGMA foreign_keys = ON");


		statement.execute("ALTER TABLE users RENAME TO users_old");
		statement.execute("""
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
		statement.execute("""
		INSERT INTO users (id, username, display_name, pronouns, avatar_url, created, permissions)
		SELECT id, username, display_name, pronouns, avatar_url, created, permissions from users_old
		""");


		statement.execute("CREATE TABLE submissions_mr AS SELECT * FROM submissions");
		statement.execute("ALTER TABLE submissions_mr ADD COLUMN modrinth_id TEXT");

		statement.execute("""
		UPDATE submissions_mr
		SET modrinth_id = (
			SELECT modrinth_id FROM projects WHERE submissions_mr.project_id = projects.id
		)
		""");

		statement.execute("ALTER TABLE projects RENAME TO projects_old");
		statement.execute("""
		CREATE TABLE IF NOT EXISTS projects (
			id TEXT UNIQUE NOT NULL,
			slug TEXT UNIQUE NOT NULL,
			PRIMARY KEY (id)
		)
		""");
		statement.execute("""
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

		statement.executeBatch();


		statement.execute("ALTER TABLE submissions RENAME TO submissions_old");
		statement.execute("""
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
		statement.execute("""
		INSERT INTO submissions (id, event, project_id, submitted)
		SELECT id, event, project_id, submitted from submissions_old
		""");

		statement.execute("""
		INSERT INTO submission_type_modrinth (submission_id, modrinth_id, version_id)
		SELECT id, modrinth_id, modrinth_version_id FROM submissions_mr
		WHERE modrinth_id NOT NULL
		""");
		statement.execute("""
		INSERT INTO integration_modrinth (user_id, modrinth_id)
		SELECT id, modrinth_id FROM users_old
		WHERE modrinth_id NOT NULL
		""");

		statement.execute("""
		INSERT INTO integration_discord (user_id, discord_id)
		SELECT id, discord_id FROM users_old
		""");

		statement.execute("""
		CREATE TABLE IF NOT EXISTS project_roles_temp (
			project_id TEXT NOT NULL,
			user_id TEXT NOT NULL,
			permissions INTEGER NOT NULL DEFAULT 1,
			role_name TEXT NOT NULL DEFAULT 'Member',
			FOREIGN KEY (project_id) REFERENCES projects(id) ON UPDATE CASCADE ON DELETE CASCADE,
			FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE
		)
		""");

		statement.execute("""
		INSERT OR REPLACE INTO project_roles_temp (project_id, user_id)
		SELECT project_id, user_id FROM project_authors
		""");

		statement.execute("""
		INSERT INTO project_roles (project_id, user_id, permissions, role_name)
		SELECT project_id, user_id, permissions, role_name FROM project_roles_temp
		""");

		statement.execute("ALTER TABLE minecraft_accounts RENAME TO minecraft_accounts_old");
		statement.execute("""
		CREATE TABLE IF NOT EXISTS minecraft_accounts (
			uuid TEXT UNIQUE NOT NULL,
			user_id TEXT UNIQUE NOT NULL,
			FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE,
			PRIMARY KEY (uuid)
		)
		""");
		statement.execute("""
		INSERT INTO minecraft_accounts (uuid, user_id)
		SELECT uuid, user_id FROM minecraft_accounts_old
		""");

		statement.execute("ALTER TABLE award_instances RENAME TO award_instances_old");
		statement.execute("""
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
		statement.execute("""
		INSERT INTO award_instances (award_id, awarded_to, custom_data, submission_id, tier_override)
		SELECT award_id, awarded_to, custom_data, submission_id, tier_override FROM award_instances_old
		""");

		statement.execute("ALTER TABLE team_invites RENAME TO team_invites_old");
		statement.execute("""
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
		statement.execute("""
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

		statement.execute("""
		WITH cnt(i) AS (
			SELECT 1 UNION SELECT i+1 FROM cnt
		)
		UPDATE users
		SET id = concat('zzz', generate_natural_id_from_number(ROWID - 1, 2))
		""");

		return dropConnection -> {
			try {
				var dropStatement = dropConnection.createStatement();
				dropStatement.execute("PRAGMA foreign_keys = ON");
				dropStatement.execute("DROP TABLE submissions_old");
				dropStatement.execute("DROP TABLE submissions_mr");
				dropStatement.execute("DROP TABLE project_builders");
				dropStatement.execute("DROP TABLE project_authors");
				dropStatement.execute("DROP TABLE project_roles_temp");
				dropStatement.execute("DROP TABLE minecraft_accounts_old");
				dropStatement.execute("DROP TABLE award_instances_old");
				dropStatement.execute("DROP TABLE team_invites_old");
				dropStatement.execute("DROP TABLE projects_old");
				dropStatement.execute("DROP TABLE users_old");
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
		};
	}
}
