package net.modgarden.backend.handler;

import io.javalin.http.Context;

public class RegistrationHandler {
    public static void registerThroughDiscord(Context ctx) {
        String id = ctx.pathParam("id");

        ctx.result("Successfully registered Mod Garden account.");
        ctx.status(201);
    }
}
