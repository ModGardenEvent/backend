package net.modgarden.backend.endpoint.exception;

public class UnauthorizedException extends HypertextException {
	public UnauthorizedException(String message) {
		super(401, message);
	}

	public UnauthorizedException() {
		this("Unauthorized.");
	}
}
