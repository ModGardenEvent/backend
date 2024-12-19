package net.modgarden.backend.util;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.modgarden.backend.ModGardenBackend;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetProvider;
import java.sql.Connection;
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
        return convertMap(outOps, input);
    }

    @Override
    public DataResult<Number> getNumberValue(ResultSet input) {
        return getValue(input);
    }

    @Override
    public ResultSet createNumeric(Number i) {
        boolean isInt = i.doubleValue() % 1 == 0;
        return createValue(isInt ? i.longValue() : i.doubleValue(), isInt ? "INTEGER" : "REAL", true);
    }

    @Override
    public DataResult<String> getStringValue(ResultSet input) {
        return getValue(input);
    }

    @Override
    public ResultSet createString(String value) {
        return createValue("'" + value + "'", "TEXT", true);
    }

    private <T> DataResult<T> getValue(ResultSet input) {
        try {
            T value = null;
            while (input.next()) {
                for (int i = 1; i <= input.getMetaData().getColumnCount(); ++i) {
                    if (input.getMetaData().getColumnType(i) != 0)
                        value = (T) input.getObject(i);
                }
            }
            if (value == null)
                return DataResult.error(() -> "Value not found.");
            return DataResult.success(value);
        } catch (SQLException ex) {
            return DataResult.error(() -> "Exception when obtaining value. " + ex.getMessage());
        }
    }

    private <T> DataResult<T> getValueAtIndex(ResultSet input, int index) {
        try {
            T obj = (T) input.getObject(index);
            return DataResult.success(obj);
        } catch (SQLException ex) {
            return DataResult.error(() -> "Exception when obtaining value from index. " + ex.getMessage());
        }
    }

    private <T> ResultSet createValue(T value, String dataType, boolean key) {
        createTempTable();
        try (Connection connection = ModGardenBackend.createTempDatabaseConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE temp ADD COLUMN value " + dataType);
            statement.execute("REPLACE INTO temp (value) VALUES (" + value + ")");
            ResultSet result = statement.executeQuery("SELECT value FROM temp");
            CachedRowSet rowSet = RowSetProvider.newFactory().createCachedRowSet();
            rowSet.populate(result);
            result.close();

            dropTempTable();
            if (dataType.equals("TEXT") && key) {
                // We need to do this because there is no way to detect whether
                // a created value is a key or not.
                return new ResultSetComparableStringWrapper(rowSet, (String) value);
            } else
                return rowSet;
        }
        catch (SQLException ex) {
            dropTempTable();
            throw new RuntimeException(ex);
        }
    }

    @Override
    public DataResult<ResultSet> mergeToList(ResultSet list, ResultSet value) {
        // TODO
        return DataResult.success(EMPTY);
    }

    @Override
    public DataResult<ResultSet> mergeToMap(ResultSet map, ResultSet key, ResultSet value) {
        try (Connection connection = ModGardenBackend.createTempDatabaseConnection();
             Statement statement = connection.createStatement();
             map; key; value) {
            createTempTable();

            CachedRowSet returnSet = RowSetProvider.newFactory().createCachedRowSet();

            List<String> keyList = new ArrayList<>();
            List<Object> valueList = new ArrayList<>();

            if (map != empty()) {
                map.beforeFirst();
                // This method should only ever be used on one row.
                // As such, we can ignore making the map do more than it should.
                while (map.next()) {
                    for (int i = 1; i <= map.getMetaData().getColumnCount(); ++i) {
                        String keyString = map.getMetaData().getColumnName(i);
                        keyList.add(keyString);
                        valueList.add(map.getObject(i));

                        statement.executeUpdate("ALTER TABLE temp ADD COLUMN " + keyString + " " + map.getMetaData().getColumnTypeName(i));
                        PreparedStatement preparedStatement = connection.prepareStatement("REPLACE INTO temp (_temp_primary," + String.join(",", keyList) + ") VALUES (0," + valueList.stream().map(object -> "?").collect(Collectors.joining(",")) + ")");
                        for (int j = 1; j <= valueList.size(); ++j)
                            preparedStatement.setObject(j, valueList.get(j - 1));
                        preparedStatement.execute();
                    }
                }
            }
            key.beforeFirst();
            value.beforeFirst();
            while (key.next() && value.next()) {
                String keyString = key.getString(1);
                keyList.add(keyString);
                valueList.add(value.getObject(1));

                statement.executeUpdate("ALTER TABLE temp ADD COLUMN " + keyString + " " + value.getMetaData().getColumnTypeName(1));
                PreparedStatement preparedStatement = connection.prepareStatement("REPLACE INTO temp (_temp_primary," + String.join(",", keyList) + ") VALUES (0," + valueList.stream().map(object -> "?").collect(Collectors.joining(",")) + ")");
                for (int i = 1; i <= valueList.size(); ++i)
                    preparedStatement.setObject(i, valueList.get(i - 1));
                preparedStatement.execute();
            }

            ResultSet tempSet = statement.executeQuery("SELECT " + String.join(",", keyList) + " from temp");
            returnSet.populate(tempSet);
            tempSet.close();
            dropTempTable();
            return DataResult.success(returnSet);
        } catch (SQLException ex) {
            dropTempTable();
            ModGardenBackend.LOG.error("Exception merging results to database map.", ex);
            return DataResult.error(() -> "Exception merging results to database map.");
        }
    }

    @Override
    public DataResult<Stream<Pair<ResultSet, ResultSet>>> getMapValues(ResultSet input) {
        List<Pair<ResultSet, ResultSet>> pairs = new ArrayList<>();
        try (input) {
            while (input.next()) {
                for (int i = 1; i <= input.getMetaData().getColumnCount(); ++i) {
                    String columnName = input.getMetaData().getColumnName(i);
                    String columnTypeName = input.getMetaData().getColumnTypeName(i);
                    ResultSet value = columnTypeName.equals("TEXT") ? createValue("'" + input.getString(i) + "'", "TEXT", false) : createNumeric(columnTypeName.equals("INTEGER") ? input.getInt(i) : input.getDouble(i));
                    pairs.add(Pair.of(createString(columnName), value));
                }
            }
            return DataResult.success(pairs.stream());
        } catch (SQLException ex) {
            ModGardenBackend.LOG.error("Exception collecting results from database map.", ex);
            return DataResult.error(() -> "Exception collecting results from database map.");
        }
    }

    @Override
    public DataResult<Stream<ResultSet>> getStream(ResultSet input) {
        return DataResult.success(Stream.of());
    }

    @Override
    public ResultSet createMap(Stream<Pair<ResultSet, ResultSet>> map) {
        try {
            List<Pair<ResultSet, ResultSet>> list = map.toList();

            CachedRowSet rowSet = RowSetProvider.newFactory().createCachedRowSet();

            for (Pair<ResultSet, ResultSet> result : list) {
                try (ResultSet first = result.getFirst();
                     ResultSet second = result.getSecond()) {
                    mergeToMap(rowSet, first, second);
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
        // TODO
        return EMPTY;
    }

    @Override
    public ResultSet remove(ResultSet input, String key) {
        try (input) {
            CachedRowSet rowSet = RowSetProvider.newFactory().createCachedRowSet();
            rowSet.populate(input);
            return rowSet;
        } catch (SQLException ex) {
            ModGardenBackend.LOG.error("Failed to remove from database. ", ex);
            throw new RuntimeException("Failed to remove from database.");
        }
    }

    private void createTempTable() {
        String createStatement = "CREATE TABLE IF NOT EXISTS temp (_temp_primary INTEGER PRIMARY KEY)";
        try(Connection connection = ModGardenBackend.createTempDatabaseConnection();
            Statement statement = connection.createStatement()) {
            statement.execute(createStatement);
        } catch (SQLException ex) {
            ModGardenBackend.LOG.error("Failed to create database connection.", ex);
        }
    }

    private void dropTempTable() {
        String createStatement = "DROP TABLE IF EXISTS temp";
        try(Connection connection = ModGardenBackend.createTempDatabaseConnection();
            Statement statement = connection.createStatement()) {
            statement.execute(createStatement);
        } catch (SQLException ex) {
            ModGardenBackend.LOG.error("Failed to drop database connection.", ex);
        }
        ModGardenBackend.dropTempFile();
    }
}
