package net.modgarden.backend.endpoint.exception;

/// A syntactically or semantically malformed request.
public class BadRequestException extends HypertextException {
	public BadRequestException(String message) {
		super(400, message);
	}

	public BadRequestException(String message, Exception cause) {
		super(400, message, cause);
	}
}
