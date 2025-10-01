package net.modgarden.backend.data.fixer.fix;

import net.modgarden.backend.data.fixer.DatabaseFix;
import org.jetbrains.annotations.Nullable;

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
		statement.addBatch("""
		CREATE TABLE IF NOT EXISTS api_keys (
			uuid BLOB NOT NULL,
			user_id TEXT NOT NULL,
			salt BLOB NOT NULL,
			hash BLOB UNIQUE NOT NULL,
			expires INTEGER NOT NULL,
			FOREIGN KEY (user_id) REFERENCES users(id),
			PRIMARY KEY (uuid)
		)
		""");
		statement.addBatch("""
		CREATE TABLE IF NOT EXISTS api_key_scopes (
			uuid BLOB NOT NULL,
			scope TEXT CHECK (scope in ('project', 'user')),
			project_id TEXT,
			permissions INTEGER NOT NULL,
			FOREIGN KEY (project_id) REFERENCES projects(id),
			FOREIGN KEY (uuid) REFERENCES api_keys(uuid),
			PRIMARY KEY (uuid)
		)
		""");
		statement.addBatch("""
		CREATE TABLE IF NOT EXISTS passwords (
			user_id TEXT NOT NULL,
			salt BLOB NOT NULL,
			hash BLOB NOT NULL,
			last_updated INTEGER NOT NULL,
			FOREIGN KEY (user_id) REFERENCES users(id),
			PRIMARY KEY (user_id)
		)
		""");
		statement.addBatch("""
		CREATE TABLE IF NOT EXISTS integration_modrinth (
			user_id TEXT NOT NULL,
			modrinth_id TEXT NOT NULL,
			FOREIGN KEY (user_id) REFERENCES users(id),
			PRIMARY KEY (user_id)
		)
		""");
		statement.addBatch("""
		CREATE TABLE IF NOT EXISTS integration_discord (
			user_id TEXT NOT NULL,
			discord_id TEXT NOT NULL,
			FOREIGN KEY (user_id) REFERENCES users(id),
			PRIMARY KEY (user_id)
		)
		""");
		statement.addBatch("""
		CREATE TABLE IF NOT EXISTS submission_type_modrinth (
			submission_id TEXT NOT NULL,
			modrinth_id TEXT NOT NULL,
			version_id TEXT NOT NULL,
			FOREIGN KEY (submission_id) REFERENCES submissions(id),
			PRIMARY KEY (submission_id)
		)
		""");

		statement.addBatch("""
		CREATE TABLE IF NOT EXISTS project_roles (
			project_id TEXT NOT NULL,
			user_id TEXT NOT NULL,
			permissions INTEGER NOT NULL DEFAULT 0,
			role_name TEXT NOT NULL DEFAULT 'Member',
			FOREIGN KEY (project_id) REFERENCES projects(id),
			FOREIGN KEY (user_id) REFERENCES users(id)
		)
		""");

		statement.addBatch("""
		CREATE UNIQUE INDEX idx_project_roles_two_ids ON project_roles(project_id, user_id)
		""");

		statement.executeBatch();

		statement.execute("""
		INSERT INTO integration_modrinth (user_id, modrinth_id)
		SELECT id, modrinth_id FROM users
		WHERE modrinth_id NOT NULL
		""");

		statement.execute("""
		INSERT INTO integration_discord (user_id, discord_id)
		SELECT id, discord_id FROM users
		""");

		statement.execute("ALTER TABLE users RENAME TO users_old");
		statement.execute("""
		CREATE TABLE users AS SELECT id, id, username, display_name, pronouns, avatar_url, created, permissions FROM users_old
		""");


		statement.execute("CREATE TABLE submissions_mr AS SELECT * FROM submissions");
		statement.execute("ALTER TABLE submissions_mr ADD COLUMN modrinth_id TEXT");

		statement.execute("""
		UPDATE submissions_mr
		SET modrinth_id = (
			SELECT modrinth_id FROM projects WHERE submissions_mr.project_id = projects.id
		)
		""");

		statement.execute("ALTER TABLE submissions RENAME TO submissions_old");
		statement.execute("""
		CREATE TABLE submissions AS SELECT * FROM submissions_old
		WHERE NOT modrinth_version_id
		""");

		statement.execute("ALTER TABLE projects RENAME TO projects_old");
		statement.execute("""
		CREATE TABLE projects AS SELECT * FROM projects_old
		WHERE NOT modrinth_id
		""");

		statement.execute("""
		INSERT INTO submission_type_modrinth (submission_id, modrinth_id, version_id)
		SELECT id, modrinth_id, modrinth_version_id FROM submissions_mr
		WHERE modrinth_id NOT NULL
		""");

		statement.execute("""
		CREATE TABLE IF NOT EXISTS project_roles_temp (
			project_id TEXT NOT NULL,
			user_id TEXT NOT NULL,
			permissions INTEGER NOT NULL DEFAULT 1,
			role_name TEXT NOT NULL DEFAULT 'Member',
			FOREIGN KEY (project_id) REFERENCES projects(id),
			FOREIGN KEY (user_id) REFERENCES users(id)
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

		return dropConnection -> {
			try {
				var dropStatement = dropConnection.createStatement();
				dropStatement.execute("DROP TABLE users_old");
				dropStatement.execute("DROP TABLE submissions_old");
				dropStatement.execute("DROP TABLE submissions_mr");
				dropStatement.execute("ALTER TABLE submissions DROP COLUMN modrinth_version_id");
				dropStatement.execute("DROP TABLE projects_old");
				dropStatement.execute("DROP TABLE project_builders");
				dropStatement.execute("DROP TABLE project_authors");
				dropStatement.execute("DROP TABLE project_roles_temp");
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
		};
	}
}
