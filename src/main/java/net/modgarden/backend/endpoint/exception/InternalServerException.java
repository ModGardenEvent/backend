package net.modgarden.backend.endpoint.exception;

/// An error caused by the server.
public class InternalServerException extends HypertextException {
	public InternalServerException(String message) {
		super(500, message);
	}
}
