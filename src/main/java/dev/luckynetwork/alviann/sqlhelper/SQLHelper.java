package dev.luckynetwork.alviann.sqlhelper;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.Properties;

@SuppressWarnings({"unused", "WeakerAccess"})
public class SQLHelper {

    private final String host, port, database, username, password;
    private final Type type;
    private final boolean hikari;

    private HikariDataSource dataSource;
    private Connection connection;

    // ---------------------------- Constructor ---------------------------- //

    /**
     * constructs the SQL instance
     *
     * @param host     the host
     * @param port     the port
     * @param database the database
     * @param username the username
     * @param password the password
     * @param type     the SQL type
     * @param hikari   true if hikari is being used, otherwise false
     */
    SQLHelper(String host, String port, String database, String username, String password, Type type, boolean hikari) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.type = type;
        this.hikari = hikari;
    }

    // ---------------------------- Static Builder ---------------------------- //

    public static SQLBuilder newBuilder(Type type) {
        return new SQLBuilder("", "", "", "", "", false, type);
    }

    // ---------------------------- Getter ---------------------------- //

    /**
     * @return the host
     */
    public String getHost() {
        return host;
    }

    /**
     * @return the port
     */
    public String getPort() {
        return port;
    }

    /**
     * @return the database
     */
    public String getDatabase() {
        return database;
    }

    /**
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * @return the password
     */
    public String getPassword() {
        return password;
    }

    /**
     * @return the SQL type
     */
    public Type getType() {
        return type;
    }

    /**
     * @return true if SQL is using hikari
     */
    public boolean isHikari() {
        return hikari;
    }

    // ---------------------------- Enum ---------------------------- //

    /**
     * the SQL types
     */
    public enum Type {
        MYSQL("jdbc:mysql://%s:%s%s?useSSL=false&seLegacyDatetimeCode=false&serverTimezone=UTC", "com.mysql.cj.jdbc.Driver"),
        SQLITE("jdbc:sqlite:%s", "org.sqlite.JDBC"),
        H2("jdbc:h2:./%s", "org.h2.Driver");

        private final String jdbcUrl;
        private final String classPath;

        Type(String jdbcUrl, String classPath) {
            this.jdbcUrl = jdbcUrl;
            this.classPath = classPath;
        }

        /**
         * @return the jdbc url
         */
        String getJdbcUrl() {
            return jdbcUrl;
        }

        /**
         * @return the class path
         */
        String getClassPath() {
            return classPath;
        }
    }

    // ---------------------------- Manager ---------------------------- //

    /**
     * @return the SQL connection
     */
    public Connection getConnection() {
        if (hikari) {
            try {
                return dataSource.getConnection();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return connection;
    }

    /**
     * executes SQL query using PreparedStatement
     *
     * @param sql the SQL query
     * @throws SQLException if the query failed to be executed
     */
    public void executeQuery(String sql) throws SQLException {
        Connection retrievedConnection = this.getConnection();

        if (hikari) {
            try (Connection connection = retrievedConnection; PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.execute();
            } catch (SQLException e) {
                throw new SQLException("Failed to execute query (Query: " + sql + ")");
            }
        }
        else {
            try (PreparedStatement statement = retrievedConnection.prepareStatement(sql)) {
                statement.execute();
            } catch (SQLException e) {
                throw new SQLException("Failed to execute query (Query: " + sql + ")");
            }
        }
    }

    /**
     * fetches the SQL results, this can be used to fetch ResultSet too
     *
     * @param sql the SQL query
     * @return the SQL results
     * @throws SQLException if the query failed to be executed or failed to fetch the SQL results
     */
    public Results getResults(String sql) throws SQLException {
        try {
            Connection connection = this.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql);
            ResultSet set = statement.executeQuery();

            return new Results(connection, statement, set, hikari);
        } catch (SQLException e) {
            throw new SQLException("Failed to execute query (Query: " + sql + ")");
        }
    }

    // ---------------------------- Handler ---------------------------- //

    /**
     * connects the SQL
     *
     * @throws SQLException if the SQL failed to connect
     */
    public void connect() throws SQLException {
        String url = this.formatUrl(host, port, database);

        if (url == null) {
            throw new NullPointerException("Failed to format the JDBC URL!");
        }

        if (hikari) {
            HikariConfig config = new HikariConfig();

            config.setDriverClassName(type.getClassPath());
            config.setJdbcUrl(String.format(url, host, port, database));
            config.setMaximumPoolSize(20);

            config.setUsername(username);
            config.setPassword(password);

            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");

            dataSource = new HikariDataSource(config);
        }
        else {
            try {
                Class.forName(type.classPath);
            } catch (Exception ignored) {
            }

            if (type == Type.MYSQL) {
                Properties config = new Properties();

                config.setProperty("user", username);
                config.setProperty("password", password);

                config.setProperty("cachePrepStmts", "true");
                config.setProperty("prepStmtCacheSize", "250");
                config.setProperty("prepStmtCacheSqlLimit", "2048");
                config.setProperty("useServerPrepStmts", "true");

                connection = DriverManager.getConnection(url, config);
            }
            else {
                connection = DriverManager.getConnection(url, username, password);
            }
        }
    }

    /**
     * disconnects the SQL
     *
     * @throws SQLException if the SQL failed to disconnect
     */
    public void disconnect() throws SQLException {
        if (hikari) {
            dataSource.close();
        }
        else {
            connection.close();
        }

        dataSource = null;
        connection = null;
    }

    /**
     * handles JDBC URL formatting
     *
     * @param host     the host
     * @param port     the port
     * @param database the database
     * @return the formatted JDBC URL
     */
    private String formatUrl(String host, String port, String database) {
        switch (type) {
            case MYSQL: {
                if (database != null && !database.isEmpty() && !database.startsWith("/")) {
                    database = "/" + database;
                }
                return String.format(type.jdbcUrl, host, port, database);
            }
            case SQLITE:
            case H2: {
                return String.format(type.jdbcUrl, database);
            }
            default: {
                return null;
            }
        }
    }

}
