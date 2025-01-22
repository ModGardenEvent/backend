package net.modgarden.backend.util;

import io.seruco.encoding.base62.Base62;
import net.modgarden.backend.ModGardenBackend;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AuthUtil {
    public static String createBody(Map<String, String> params) {
        return params.entrySet()
                .stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    public static String generateRandomToken() {
        byte[] bytes = new byte[10];
        new SecureRandom().nextBytes(bytes);
        var token = new String(Base62.createInstance().encode(bytes), StandardCharsets.UTF_8);
        return token.substring(0, 6);
    }

    public static long getTokenExpirationTime() {
        return (long) (Math.floor((double) (System.currentTimeMillis() + 900000) / 900000) * 900000); // 15 minutes later
    }

    public static void clearTokensEachFifteenMinutes() {
        new Thread(() -> {
            try (ScheduledExecutorService executor = Executors.newScheduledThreadPool(1)) {
                long scheduleTime = (long) (Math.floor((double) (System.currentTimeMillis() + 900000) / 900000) * 900000) - System.currentTimeMillis();
                executor.schedule(() -> {
                    clearTokens();
                    executor.schedule(AuthUtil::clearTokens, 900000, TimeUnit.MILLISECONDS);
                }, scheduleTime, TimeUnit.MILLISECONDS);
                ModGardenBackend.LOG.info("Cleared tokens.");
            }
        }).start();
    }

    private static void clearTokens() {
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM link_codes WHERE expiration_time <= ?")) {
            statement.setLong(1, System.currentTimeMillis());
            statement.execute();
        } catch (SQLException ex) {
            ModGardenBackend.LOG.error("Failed to clear tokens from database.");
        }
    }
}
