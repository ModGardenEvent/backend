package net.modgarden.backend.endpoint.exception;

/// Thrown when a user is forbidden from accessing a resource.
public class ForbiddenException extends HypertextException {
	public ForbiddenException(String message) {
		super(403, message);
	}

	public ForbiddenException() {
		this("Forbidden.");
	}
}
