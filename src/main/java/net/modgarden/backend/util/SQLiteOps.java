package net.modgarden.backend.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.modgarden.backend.ModGardenBackend;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetProvider;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An ops for SQLite.
 * <br>
 * Make sure to close any values returned by this class.
 */
public class SQLiteOps implements DynamicOps<ResultSet> {
    private static final ResultSet EMPTY = new DummyResultSet();
    public static final SQLiteOps INSTANCE = new SQLiteOps();

    protected SQLiteOps() {
    }

    @Override
    public ResultSet empty() {
        return EMPTY;
    }

    @Override
    public <U> U convertTo(DynamicOps<U> outOps, ResultSet input) {
        try {
            int columnCount = input.getMetaData().getColumnCount();
            if (columnCount > 1) {
                return convertMap(outOps, input);
            }

            if (input.getMetaData().getColumnTypeName(1).equals("TEXT")) {
                try {
                    JsonElement element = JsonParser.parseString(input.getString(1));
                    if (element.isJsonArray())
                        return convertList(outOps, input);
                } catch (JsonParseException ignored) {}
                return outOps.createString(input.getString(1));
            }

            if (input.getMetaData().getColumnTypeName(1).equals("REAL"))
                return outOps.createNumeric(input.getDouble(1));

            return outOps.createNumeric(input.getLong(1));
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public DataResult<Number> getNumberValue(ResultSet input) {
        return getValue(input);
    }

    @Override
    public ResultSet createNumeric(Number i) {
        boolean isInt = i.doubleValue() % 1 == 0;
        return createValue(i, isInt ? "INTEGER" : "REAL", false);
    }

    @Override
    public DataResult<String> getStringValue(ResultSet input) {
        return getValue(input);
    }

    @Override
    public ResultSet createString(String value) {
        return createValue(value, "TEXT", true);
    }

    private <T> DataResult<T> getValue(ResultSet input) {
        try {
            input.next();
            T value = input.getMetaData().getColumnTypeName(1).equals("INTEGER") ? (T) (Object) input.getLong(1) :  (T) input.getObject(1);
            if (value == null)
                return DataResult.error(() -> "Value not found.");
            return DataResult.success(value);
        } catch (SQLException ex) {
            return DataResult.error(() -> "Exception when obtaining value. " + ex.getMessage());
        }
    }

    private <T> ResultSet createValue(T value, String dataType, boolean key) {
        ResultSet resultSet;
        createTempTable();
        try (Connection connection = createTempDatabaseConnection();
             Statement statement = connection.createStatement();) {
            statement.execute("ALTER TABLE temp ADD COLUMN value " + dataType);
            try (PreparedStatement replacePrepared = connection.prepareStatement("REPLACE INTO temp (value) VALUES (?)")) {
                if (dataType.equals("TEXT") && value instanceof JsonArray array)
                    replacePrepared.setObject(1, array.toString());
                else if (value instanceof Number number) {
                    if (dataType.equals("INTEGER"))
                        replacePrepared.setLong(1, number.longValue());
                    else
                        replacePrepared.setObject(1, number.doubleValue());
                } else
                    replacePrepared.setObject(1, value);
                replacePrepared.execute();
            }
            ResultSet result = statement.executeQuery("SELECT value FROM temp");
            CachedRowSet rowSet = RowSetProvider.newFactory().createCachedRowSet();
            rowSet.populate(result);
            result.close();
            if (dataType.equals("TEXT") && key) {
                // We need to do this because there is no way to detect whether
                // a created value is a key or not.
                resultSet = new ResultSetComparableStringWrapper(rowSet);
            } else
                resultSet = rowSet;
            statement.execute("ALTER TABLE temp DROP COLUMN value");
        }
        catch (SQLException ex) {
            dropTempTable();
            throw new RuntimeException(ex);
        }
        dropTempTable();
        return resultSet;
    }

    @Override
    public DataResult<ResultSet> mergeToList(ResultSet list, ResultSet value) {
        DataResult<ResultSet> dataResult;
        try(list; value) {
            if ((list == empty() || list.next()) && value.next()) {
                JsonElement element;
                if (list == empty()) {
                    element = new JsonArray();
                } else
                    element = JsonParser.parseString(list.getString(1));
                if (!element.isJsonArray())
                    throw new SQLException("Initial value is not a list.");
                JsonArray array = element.getAsJsonArray();
                array.add(JsonParser.parseString(value.getString(1)));
                dataResult = DataResult.success(createValue(array, "TEXT", false));
            } else
                dataResult = DataResult.error(() -> "Attempted to insert empty value into list.");
        } catch (SQLException ex) {
            ModGardenBackend.LOG.error("Failed merging results to database list. ", ex);
            dataResult = DataResult.error(() -> "Failed merging results to database list.");
        }
        return dataResult;
    }

    @Override
    public DataResult<ResultSet> mergeToMap(ResultSet map, ResultSet key, ResultSet value) {
        DataResult<ResultSet> dataResult;
        createTempTable();
        try (Connection connection = createTempDatabaseConnection();
             Statement statement = connection.createStatement();
             map; key; value) {

            CachedRowSet returnSet = RowSetProvider.newFactory().createCachedRowSet();

            List<String> keyList = new ArrayList<>();
            List<Object> valueList = new ArrayList<>();

            if (map != empty()) {
                map.beforeFirst();
                while (map.next()) {
                    for (int i = 1; i <= map.getMetaData().getColumnCount(); ++i) {
                        String keyString = map.getMetaData().getColumnName(i);
                        keyList.add(keyString);
                        valueList.add(map.getObject(i));

                        statement.execute("ALTER TABLE temp ADD COLUMN " + keyString + " " + map.getMetaData().getColumnTypeName(i));
                        try (PreparedStatement replacePrepared = connection.prepareStatement("REPLACE INTO temp (_temp_primary," + String.join(",", keyList) + ") VALUES (0," + valueList.stream().map(object -> "?").collect(Collectors.joining(",")) + ")")) {
                            for (int j = 1; j <= valueList.size(); ++j)
                                replacePrepared.setObject(j, valueList.get(j - 1));
                            replacePrepared.execute();
                        }
                    }
                }
            }
            key.beforeFirst();
            value.beforeFirst();
            while (key.next() && value.next()) {
                String keyString = key.getString(1);
                keyList.add(keyString);
                valueList.add(value.getObject(1));

                statement.execute("ALTER TABLE temp ADD COLUMN " + keyString + " " + value.getMetaData().getColumnTypeName(1));
                    try (PreparedStatement replacePrepared = connection.prepareStatement("REPLACE INTO temp (_temp_primary," + String.join(",", keyList) + ") VALUES (0," + valueList.stream().map(object -> "?").collect(Collectors.joining(",")) + ")")) {
                        for (int j = 1; j <= valueList.size(); ++j)
                            replacePrepared.setObject(j, valueList.get(j - 1));
                        replacePrepared.execute();
                    }
            }

            ResultSet tempSet = statement.executeQuery("SELECT " + String.join(",", keyList) + " FROM temp");
            returnSet.populate(tempSet);
            tempSet.close();
            dataResult = DataResult.success(returnSet);
        } catch (SQLException ex) {
            ModGardenBackend.LOG.error("Exception merging results to database map.", ex);
            dataResult = DataResult.error(() -> "Exception merging results to database map.");
        }
        dropTempTable();
        return dataResult;
    }

    @Override
    public DataResult<Stream<Pair<ResultSet, ResultSet>>> getMapValues(ResultSet input) {
        List<Pair<ResultSet, ResultSet>> pairs = new ArrayList<>();
        try (input) {
            if (input.next()) {
                for (int i = 1; i <= input.getMetaData().getColumnCount(); ++i) {
                    String columnName = input.getMetaData().getColumnName(i);
                    ResultSet value = mapToResultSet(input, i);
                    pairs.add(Pair.of(createString(columnName), value));
                }
            }
            return DataResult.success(pairs.stream());
        } catch (SQLException ex) {
            ModGardenBackend.LOG.error("Exception collecting results from database map.", ex);
            return DataResult.error(() -> "Exception collecting results from database map.");
        }
    }

    private ResultSet mapToResultSet(ResultSet input, int columnIndex) throws SQLException {
        String columnTypeName = input.getMetaData().getColumnTypeName(columnIndex);

        if (input.getObject(columnIndex) == null)
            return createValue(null, "NULL", false);

        try {
            if (columnTypeName.equals("TEXT")) {
                String columnString = input.getString(columnIndex);
                JsonElement json = JsonParser.parseString(columnString);
                if (json.isJsonArray())
                    return createValue(json, "TEXT", false);
            }
        } catch (JsonParseException ignored) {}

        if (columnTypeName.equals("TEXT"))
            return createValue(input.getString(columnIndex), "TEXT", false);

        return createNumeric(columnTypeName.equals("INTEGER") ? input.getLong(columnIndex) : input.getDouble(columnIndex));
    }

    @Override
    public DataResult<Stream<ResultSet>> getStream(ResultSet input) {
        try(input) {
            if (input == empty() || input.next()) {
                if (input == empty() || !(input.getObject(1) instanceof String))
                    return DataResult.success(Stream.of());
                JsonElement element = JsonParser.parseString(input.getString(1));
                DataResult<Stream<JsonElement>> jsonElement = JsonOps.INSTANCE.getStream(element);
                if (jsonElement.isError())
                    return jsonElement.map(json -> null);
                List<ResultSet> resultSets = new ArrayList<>();
                for (JsonElement element1 : jsonElement.getOrThrow().toList()) {
                    ResultSet converted = JsonOps.INSTANCE.convertTo(this, element1);
                    resultSets.add(converted);
                }
                return DataResult.success(resultSets.stream());
            }
            return DataResult.error(() -> "Input did not have next.");
        } catch (SQLException ex) {
            ModGardenBackend.LOG.error("Exception collecting results from database list.", ex);
            return DataResult.error(() -> "Exception collecting results from database list.");
        }
    }

    @Override
    public ResultSet createMap(Stream<Pair<ResultSet, ResultSet>> map) {
        try {
            List<Pair<ResultSet, ResultSet>> list = map.toList();

            ResultSet rowSet = empty();

            if (list.isEmpty())
                return empty();

            for (Pair<ResultSet, ResultSet> result : list) {
                try (ResultSet first = result.getFirst();
                     ResultSet second = result.getSecond()) {
                    rowSet = mergeToMap(rowSet, first, second).getOrThrow();
                }
            }

            return rowSet;
        } catch (SQLException ex) {
            ModGardenBackend.LOG.error("Exception creating database map. ", ex);
            return null;
        }
    }

    @Override
    public ResultSet createList(Stream<ResultSet> input) {
        List<ResultSet> list = input.toList();

        ResultSet rowSet = empty();

        if (list.isEmpty())
            return rowSet;

        for (ResultSet result : list) {
            rowSet = mergeToList(rowSet, result).getOrThrow();
        }

        return rowSet;
    }

    @Override
    public ResultSet remove(ResultSet input, String key) {
        // FIXME: This.
        try (input) {
            CachedRowSet rowSet = RowSetProvider.newFactory().createCachedRowSet();
            rowSet.populate(input);
            return rowSet;
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static Connection createTempDatabaseConnection() throws SQLException {
        try {
            new File("./temp.db").createNewFile();
        } catch (IOException ex) {
            ModGardenBackend.LOG.error("Failed to create temporary database file.", ex);
        }
        String url = "jdbc:sqlite:temp.db";
        return DriverManager.getConnection(url);
    }

    private static void dropTempTable() {
        try {
            Files.deleteIfExists(new File("./temp.db").toPath());
        } catch (IOException ex) {
            ModGardenBackend.LOG.error("Failed to delete temporary database file.", ex);
        }
    }

    private void createTempTable() {
        String createStatement = "CREATE TABLE IF NOT EXISTS temp (_temp_primary INTEGER PRIMARY KEY)";
        try(Connection connection = createTempDatabaseConnection();
            Statement statement = connection.createStatement()) {
            statement.execute(createStatement);
        } catch (SQLException ex) {
            ModGardenBackend.LOG.error("Failed to create database connection.", ex);
        }
    }
}
