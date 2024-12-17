package net.modgarden.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Map;

public class Main {
	public static final Logger LOG = LoggerFactory.getLogger("Mod Garden Backend");


    public static void main(String[] args) {
		Javalin app = Javalin.create();
		app.get("", Main::getLandingJson);
		app.error(404, Main::handleError);
		app.start(7070);
		LOG.info("Mod Garden Backend Started!");
	}

	private static void getLandingJson(Context ctx) {
		InputStream landingFile = Main.class.getResourceAsStream("/landing.json");
		if (landingFile == null) {
			LOG.error("Could not find 'landing.json' resource file.");
			ctx.result("Could not find landing file.");
			ctx.status(404);
			return;
		}

		ctx.json(ctx.jsonMapper().fromJsonStream(landingFile, Map.class));
	}

	private static void handleError(Context ctx) {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode node = mapper.createObjectNode();
		node.put("error", ctx.status().toString());
		node.put("description", ctx.result());
		ctx.json(node);
	}
}
