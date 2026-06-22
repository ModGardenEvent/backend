package net.modgarden.backend.endpoint.exception;

/// Thrown when a resource already exists or when proposed content may conflict with existing resources.
public class AlreadyExistsException extends HypertextException {
	public AlreadyExistsException(String message) {
		super(409, message);
	}
}
