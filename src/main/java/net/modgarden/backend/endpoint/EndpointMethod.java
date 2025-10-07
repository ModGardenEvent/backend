package net.modgarden.backend.endpoint;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface EndpointMethod {
	Method value();

	enum Method {
		GET,
		POST,
		PUT,
		DELETE,
	}
}
