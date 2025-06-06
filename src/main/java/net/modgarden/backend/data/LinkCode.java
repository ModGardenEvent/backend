package net.modgarden.backend.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.modgarden.backend.ModGardenBackend;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public record LinkCode(String code, String accountId, Service service, long expires) {
    public static final Codec<LinkCode> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("code").forGetter(LinkCode::code),
            Codec.STRING.fieldOf("account_id").forGetter(LinkCode::accountId),
            Service.CODEC.fieldOf("service").forGetter(LinkCode::service),
            Codec.LONG.fieldOf("expires").forGetter(LinkCode::expires)
    ).apply(inst, LinkCode::new));

    @Nullable
    private static LinkCode query(String code) {
        try (Connection connection = ModGardenBackend.createDatabaseConnection();
             PreparedStatement prepared = connection.prepareStatement("SELECT * FROM link_codes WHERE code=?")) {
            prepared.setString(1, code);
            ResultSet result = prepared.executeQuery();
            if (!result.isBeforeFirst())
                return null;
			var serviceString = result.getString("service");
			var service = Service.valueOf(serviceString.toUpperCase(Locale.ROOT));
			return new LinkCode(
					result.getString("code"),
					result.getString("account_id"),
					service,
					result.getLong("expires")
			);
		} catch (SQLException ex) {
            ModGardenBackend.LOG.error("Exception in SQL query.", ex);
        }
        return null;
    }

    public enum Service {
        MODRINTH,
        MINECRAFT;

        public String serializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }

        public static final Codec<Service> CODEC = Codec.STRING.flatXmap(string -> {
            for (Service service : Service.values()) {
                if (service.serializedName().equals(string))
                    return DataResult.success(service);
            }
            return DataResult.error(() -> "Invalid service provided. Must be one of [" + Arrays.stream(Service.values()).map(Service::serializedName).collect(Collectors.joining(", ")) + "].");
        }, service -> DataResult.success(service.serializedName()));
    }
}
