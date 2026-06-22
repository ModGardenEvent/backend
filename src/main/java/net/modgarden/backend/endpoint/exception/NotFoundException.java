package net.modgarden.backend.endpoint.exception;

public class NotFoundException extends HypertextException {
	public NotFoundException(String message) {
		super(404, message);
	}
}
