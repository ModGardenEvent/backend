package net.modgarden.backend.endpoint.exception;

public class HypertextException extends Exception {
	private final int status;

	public HypertextException(int status, String message) {
		super(message);
		this.status = status;
	}

	public int getStatus() {
		return status;
	}
}
