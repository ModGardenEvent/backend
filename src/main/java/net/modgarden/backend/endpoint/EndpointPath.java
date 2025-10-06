package net.modgarden.backend.endpoint;

import java.lang.annotation.*;

/// An annotation that quickly documents what path an
/// endpoint resides at.
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface EndpointPath {
	String value();
}
