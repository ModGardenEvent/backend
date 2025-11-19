package net.modgarden.backend.database.function;

import net.modgarden.backend.data.NaturalId;
import net.modgarden.backend.database.DatabaseFunction;

import java.sql.SQLException;

public class GenerateNaturalIdFromNumberFunction extends DatabaseFunction {
	public static final GenerateNaturalIdFromNumberFunction INSTANCE = new GenerateNaturalIdFromNumberFunction();

	protected GenerateNaturalIdFromNumberFunction() {}

	@Override
	protected void xFunc() throws SQLException {
		int number = this.value_int(0);
		int length = this.value_int(1);
		this.result(NaturalId.generateFromNumber(number, length));
	}

	@Override
	protected String getName() {
		return "generate_natural_id_from_number";
	}
}
