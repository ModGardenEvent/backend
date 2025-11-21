package net.modgarden.backend.database.function;

import net.modgarden.backend.data.NaturalId;
import net.modgarden.backend.database.DatabaseFunction;

import java.sql.SQLException;

public class GenerateNaturalIdFunction extends DatabaseFunction {
	public static final GenerateNaturalIdFunction INSTANCE = new GenerateNaturalIdFunction();

	protected GenerateNaturalIdFunction() {}

	@Override
	protected void xFunc() throws SQLException {
		String table = this.value_text(0);
		String key = this.value_text(1);
		String key2 = this.value_text(2);
		int length = this.value_int(3);
		this.result(NaturalId.generate(table, key, key2, length, null));
	}

	@Override
	protected String getName() {
		return "generate_natural_id";
	}
}
