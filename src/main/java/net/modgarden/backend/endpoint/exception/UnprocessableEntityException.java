package net.modgarden.backend.endpoint.exception;

/// Thrown when the client sends data that can't be processed.
public class UnprocessableEntityException extends HypertextException {
	public UnprocessableEntityException(String message) {
		super(422, message);
	}
}
