package net.modgarden.backend.endpoint.exception;

/// Thrown when a user is forbidden from accessing a resources.
public class ForbiddenException extends HypertextException {
	public ForbiddenException(String message) {
		super(403, message);
	}
}
