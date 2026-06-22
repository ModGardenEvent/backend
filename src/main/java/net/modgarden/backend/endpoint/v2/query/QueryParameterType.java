package net.modgarden.backend.endpoint.v2.query;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public enum QueryParameterType {
	BY_KEY("by", QueryKey.class),
	WITH_VALUE("with", QueryValue.class);

	private final String queryName;
	private final Class<?> clazz;

	private static final Map<Class<?>, QueryParameterType> KEY_2_PARAM_TYPE = new HashMap<>();

	QueryParameterType(String queryName, Class<?> clazz) {
		this.queryName = queryName;
		this.clazz = clazz;
	}

	static {
		for (QueryParameterType value : values()) {
			KEY_2_PARAM_TYPE.put(value.clazz, value);
		}
	}

	public static QueryParameterType get(Class<?> clazz) {
		return Objects.requireNonNull(KEY_2_PARAM_TYPE.get(clazz));
	}

	@Override
	public String toString() {
		return this.queryName.toLowerCase(Locale.ROOT);
	}
}
