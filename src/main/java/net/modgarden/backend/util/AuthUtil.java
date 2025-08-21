package net.modgarden.backend.util;

import io.javalin.http.Context;
import io.seruco.encoding.base62.Base62;
import net.modgarden.backend.ModGardenBackend;
import net.modgarden.backend.data.LinkCode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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

    public static String insertTokenIntoDatabase(Context ctx, String accountId, LinkCode.Service service) {
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             var checkAccountIdStatement = connection.prepareStatement("SELECT code FROM link_codes WHERE account_id = ?");
             var checkCodeStatement = connection.prepareStatement("SELECT 1 FROM link_codes WHERE code = ?");
             var insertStatement = connection.prepareStatement("INSERT INTO link_codes(code, account_id, service, expires) VALUES (?, ?, ?, ?)")) {
            checkAccountIdStatement.setString(1, accountId);
            ResultSet existing = checkAccountIdStatement.executeQuery();
            String token = existing.getString(1);
            if (token != null)
                return token;
            while (token == null) {
                checkCodeStatement.clearParameters();
                String potential = generateRandomToken();
                checkCodeStatement.setString(1, potential);
                ResultSet result = checkCodeStatement.executeQuery();
                if (!result.getBoolean(1))
                    token = potential;
            }
            insertStatement.setString(1, token);
            insertStatement.setString(2, accountId);
            insertStatement.setString(3, service.serializedName());
            insertStatement.setLong(4, AuthUtil.getTokenExpirationTime());
            insertStatement.execute();
            return token;
        } catch (SQLException ex) {
            ModGardenBackend.LOG.error("Exception in SQL query.", ex);
            ctx.result("Internal Error.");
            ctx.status(500);
        }
        return null;
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
            }
        }).start();
    }

    private static void clearTokens() {
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM link_codes WHERE expires <= ?")) {
            statement.setLong(1, System.currentTimeMillis());
            int total = statement.executeUpdate();
			ModGardenBackend.LOG.debug("Cleared {} link codes.", total);
        } catch (SQLException ex) {
            ModGardenBackend.LOG.error("Failed to clear link codes from database.");
        }
    }
}
