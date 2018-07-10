/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.server.ordermatch.store;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bigtangle.core.Address;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.Exchange;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderMatch;
import net.bigtangle.core.OrderPublish;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.StoredBlock;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.UTXOProviderException;
import net.bigtangle.utils.OrderState;

/**
 * <p>
 * A generic full pruned block store for a relational database. This generic
 * class requires certain table structures for the block store.
 * </p>
 * 
 */
public abstract class DatabaseFullPrunedBlockStore implements FullPrunedBlockStore {
    private static final Logger log = LoggerFactory.getLogger(DatabaseFullPrunedBlockStore.class);

    protected String VERSION_SETTING = "version";

    // Drop table SQL.
    public static String DROP_ORDERPUBLISH_TABLE = "DROP TABLE orderpublish";
    public static String DROP_ORDERMATCH_TABLE = "DROP TABLE ordermatch";
    public static String DROP_EXCHANGE_TABLE = "DROP TABLE exchange";

    protected String INSERT_ORDERPUBLISH_SQL = getInsert()
            + "  INTO orderpublish (orderid, address, tokenid, type, validateto, validatefrom,"
            + " price, amount, state, market, submitDate) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    protected String SELECT_ORDERPUBLISH_SQL = "SELECT orderid, address, tokenid, type,"
            + " validateto, validatefrom, price, amount, state, market FROM orderpublish";

    protected String INSERT_ORDERMATCH_SQL = getInsert() + " INTO ordermatch (matchid, "
            + "restingOrderId, incomingOrderId, type, price, executedQuantity, remainingQuantity) VALUES (?, ?, ?, ?, ?, ?, ?)";

    protected String INSERT_EXCHANGE_SQL = getInsert()
            + "  INTO exchange (orderid, fromAddress, fromTokenHex, fromAmount,"
            + " toAddress, toTokenHex, toAmount, data, toSign, fromSign, toOrderId, fromOrderId, market) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    protected String SELECT_EXCHANGE_SQL = "SELECT orderid, fromAddress, "
            + "fromTokenHex, fromAmount, toAddress, toTokenHex, toAmount, "
            + "data, toSign, fromSign, toOrderId, fromOrderId, market "
            + "FROM exchange WHERE (fromAddress = ? OR toAddress = ?) AND (toSign = false OR fromSign = false)"
            + afterSelect();
    protected String SELECT_EXCHANGE_ORDERID_SQL = "SELECT orderid,"
            + " fromAddress, fromTokenHex, fromAmount, toAddress, toTokenHex,"
            + " toAmount, data, toSign, fromSign, toOrderId, fromOrderId, market FROM exchange WHERE orderid = ?";
    
    protected String DELETE_EXCHANGE_SQL = "DELETE FROM exchange WHERE toOrderId = ? OR fromOrderId = ?";
    protected String DELETE_ORDERPUBLISH_SQL = "DELETE FROM orderpublish WHERE orderid = ?";
    protected String DELETE_ORDERMATCH_SQL = "DELETE FROM ordermatch WHERE restingOrderId = ? OR incomingOrderId = ?";
    
    // Tables exist SQL.
    protected String SELECT_CHECK_TABLES_EXIST_SQL = "SELECT * FROM orderpublish WHERE 1 = 2";

    protected NetworkParameters params;
    protected ThreadLocal<Connection> conn;
    protected List<Connection> allConnections;
    protected String connectionURL;
    protected int fullStoreDepth;
    protected String username;
    protected String password;
    protected String schemaName;

    public ThreadLocal<Connection> getConnection() {
        return this.conn;
    }

    /**
     * <p>
     * Create a new DatabaseFullPrunedBlockStore, using the full connection URL
     * instead of a hostname and password, and optionally allowing a schema to
     * be specified.
     * </p>
     *
     * @param params
     *            A copy of the NetworkParameters used.
     * @param connectionURL
     *            The jdbc url to connect to the database.
     * @param fullStoreDepth
     *            The number of blocks of history stored in full (something like
     *            1000 is pretty safe).
     * @param username
     *            The database username.
     * @param password
     *            The password to the database.
     * @param schemaName
     *            The name of the schema to put the tables in. May be null if no
     *            schema is being used.
     * @throws BlockStoreException
     *             If there is a failure to connect and/or initialise the
     *             database.
     */
    public DatabaseFullPrunedBlockStore(NetworkParameters params, String connectionURL, int fullStoreDepth,
            @Nullable String username, @Nullable String password, @Nullable String schemaName)
            throws BlockStoreException {
        this.params = params;
        this.fullStoreDepth = fullStoreDepth;
        this.connectionURL = connectionURL;
        this.schemaName = schemaName;
        this.username = username;
        this.password = password;
        this.conn = new ThreadLocal<Connection>();
        this.allConnections = new LinkedList<Connection>();
        create();
    }

    public void create() throws BlockStoreException {
        try {
            Class.forName(getDatabaseDriverClass());
            log.info(getDatabaseDriverClass() + " loaded. ");
        } catch (ClassNotFoundException e) {
            log.error("check CLASSPATH for database driver jar ", e);
        }
        maybeConnect();

        try {
            // Create tables if needed
            if (!tablesExists()) {
                createTables();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new BlockStoreException(e);
        }
    }

    protected String afterSelect() {
        return "";
    }

    protected String getInsert() {
        return "insert ";
    }

    protected String getUpdate() {
        return "update ";
    }

    /**
     * Get the database driver class,
     * <p>
     * i.e org.postgresql.Driver.
     * </p>
     * 
     * @return The fully qualified database driver class.
     */
    protected abstract String getDatabaseDriverClass();

    /**
     * Get the SQL statements that create the schema (DDL).
     * 
     * @return The list of SQL statements.
     */
    protected abstract List<String> getCreateSchemeSQL();

    /**
     * Get the SQL statements that create the tables (DDL).
     * 
     * @return The list of SQL statements.
     */
    protected abstract List<String> getCreateTablesSQL();

    /**
     * Get the SQL statements that create the indexes (DDL).
     * 
     * @return The list of SQL statements.
     */
    protected abstract List<String> getCreateIndexesSQL();

    /**
     * Get the database specific error code that indicated a duplicate key error
     * when inserting a record.
     * <p>
     * This is the code returned by {@link java.sql.SQLException#getSQLState()}
     * </p>
     * 
     * @return The database duplicate error code.
     */
    protected abstract String getDuplicateKeyErrorCode();

    /**
     * Get the SQL to drop all the tables (DDL).
     * 
     * @return The SQL drop statements.
     */
    protected List<String> getDropTablesSQL() {
        List<String> sqlStatements = new ArrayList<String>();
        sqlStatements.add(DROP_ORDERPUBLISH_TABLE);
        sqlStatements.add(DROP_ORDERMATCH_TABLE);
        sqlStatements.add(DROP_EXCHANGE_TABLE);
        return sqlStatements;
    }

    /**
     * <p>
     * If there isn't a connection on the {@link ThreadLocal} then create and
     * store it.
     * </p>
     * <p>
     * This will also automatically set up the schema if it does not exist
     * within the DB.
     * </p>
     * 
     * @throws BlockStoreException
     *             if successful connection to the DB couldn't be made.
     */
    protected synchronized void maybeConnect() throws BlockStoreException {
        try {
            if (conn.get() != null && !conn.get().isClosed())
                return;

            if (username == null || password == null) {
                conn.set(DriverManager.getConnection(connectionURL));
            } else {
                Properties props = new Properties();
                props.setProperty("user", this.username);
                props.setProperty("password", this.password);
                conn.set(DriverManager.getConnection(connectionURL, props));
            }
            allConnections.add(conn.get());
            Connection connection = conn.get();
            // set the schema if one is needed
            if (schemaName != null) {
                Statement s = connection.createStatement();
                for (String sql : getCreateSchemeSQL()) {
                    s.execute(sql);
                }
            }
            log.info("Made a new connection to database " + connectionURL);
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        }
    }

    @Override
    public synchronized void close() {
        for (Connection conn : allConnections) {
            try {
                if (!conn.getAutoCommit()) {
                    conn.rollback();
                }
                conn.close();
                if (conn == this.conn.get()) {
                    this.conn.set(null);
                }
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        }
        allConnections.clear();
    }

    /**
     * <p>
     * Check if a tables exists within the database.
     * </p>
     *
     * <p>
     * This specifically checks for the 'settings' table and if it exists makes
     * an assumption that the rest of the data structures are present.
     * </p>
     *
     * @return If the tables exists.
     * @throws java.sql.SQLException
     */
    private boolean tablesExists() throws SQLException {
        PreparedStatement ps = null;
        try {
            ps = conn.get().prepareStatement(getTablesExistSQL());
            ResultSet results = ps.executeQuery();
            results.close();
            return true;
        } catch (SQLException ex) {
            return false;
        } finally {
            if (ps != null && !ps.isClosed()) {
                ps.close();
            }
        }
    }
    
    protected String getTablesExistSQL() {
        return SELECT_CHECK_TABLES_EXIST_SQL;
    }

    /**
     * Create the tables/block store in the database and
     * 
     * @throws java.sql.SQLException
     *             If there is a database error.
     * @throws BlockStoreException
     *             If the block store could not be created.
     */
    private void createTables() throws SQLException, BlockStoreException {
        // create all the database tables
        for (String sql : getCreateTablesSQL()) {
            if (log.isDebugEnabled()) {
                log.debug("DatabaseFullPrunedBlockStore : CREATE table " + sql);
            }
            Statement s = conn.get().createStatement();
            try {
                s.execute(sql);
            } finally {
                s.close();
            }
        }
        // create all the database indexes
        for (String sql : getCreateIndexesSQL()) {
            if (log.isDebugEnabled()) {
                log.debug("DatabaseFullPrunedBlockStore : CREATE index " + sql);
            }
            Statement s = conn.get().createStatement();
            try {
                s.execute(sql);
            } finally {
                s.close();
            }
        }
        createNewStore(params);
    }

    /**
     * Create a new store for the given
     * {@link net.bigtangle.core.NetworkParameters}.
     * 
     * @param params
     *            The network.
     * @throws BlockStoreException
     *             If the store couldn't be created.
     */
    private void createNewStore(NetworkParameters params) throws BlockStoreException {
    }

    @Override
    public void beginDatabaseBatchWrite() throws BlockStoreException {
        maybeConnect();
        if (log.isDebugEnabled())
            log.debug("Starting database batch write with connection: " + conn.get().toString());
        try {
            conn.get().setAutoCommit(false);    
        } catch (SQLException e) {
            throw new BlockStoreException(e);
        }
    }

    @Override
    public void commitDatabaseBatchWrite() throws BlockStoreException {
        maybeConnect();
        if (log.isDebugEnabled())
            log.debug("Committing database batch write with connection: " + conn.get().toString());
        try {
            conn.get().commit();
            conn.get().setAutoCommit(true);
        } catch (SQLException e) {
            throw new BlockStoreException(e);
        }
    }

    @Override
    public void abortDatabaseBatchWrite() throws BlockStoreException {
        maybeConnect();
        if (log.isDebugEnabled())
            log.debug("Rollback database batch write with connection: " + conn.get().toString());
        try {
            if (!conn.get().getAutoCommit()) {
                conn.get().rollback();
                conn.get().setAutoCommit(true);
            } else {
                log.warn("Warning: Rollback attempt without transaction");
            }
        } catch (SQLException e) {
            throw new BlockStoreException(e);
        }
    }

    @Override
    public NetworkParameters getParams() {
        return params;
    }

    /**
     * Resets the store by deleting the contents of the tables and
     * reinitialising them.
     * 
     * @throws BlockStoreException
     *             If the tables couldn't be cleared and initialised.
     */
    public void resetStore() throws BlockStoreException {
        maybeConnect();
        try {
            deleteStore();
            createTables();
        } catch (SQLException ex) {
            log.warn("Warning: deleteStore", ex);
            throw new RuntimeException(ex);
        }
    }

    /**
     * Deletes the store by deleting the tables within the database.
     * 
     * @throws BlockStoreException
     *             If tables couldn't be deleted.
     */
    public void deleteStore() throws BlockStoreException {
        maybeConnect();
        try {
            /*
             * for (String sql : getDropIndexsSQL()) { Statement s =
             * conn.get().createStatement(); try { log.info("drop index : " +
             * sql); s.execute(sql); } finally { s.close(); } }
             */
            for (String sql : getDropTablesSQL()) {
                Statement s = conn.get().createStatement();
                try {
                    log.info("drop table : " + sql);
                    s.execute(sql);
                } finally {
                    s.close();
                }
            }
        } catch (Exception ex) {
            log.warn("Warning: deleteStore", ex);
            // throw new RuntimeException(ex);
        }
    }

    @Override
    public void saveOrderPublish(OrderPublish orderPublish) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(INSERT_ORDERPUBLISH_SQL);
            preparedStatement.setString(1, orderPublish.getOrderId());
            preparedStatement.setString(2, orderPublish.getAddress());
            preparedStatement.setString(3, orderPublish.getTokenId());
            preparedStatement.setInt(4, orderPublish.getType());
            if (orderPublish.getValidateTo() == null) {
                preparedStatement.setDate(5, null);
            } else {
                preparedStatement.setDate(5, new Date(orderPublish.getValidateTo().getTime()));
            }
            if (orderPublish.getValidateFrom() == null) {
                preparedStatement.setDate(6, null);
            } else {
                preparedStatement.setDate(6, new Date(orderPublish.getValidateFrom().getTime()));
            }
            preparedStatement.setLong(7, orderPublish.getPrice());
            preparedStatement.setLong(8, orderPublish.getAmount());
            preparedStatement.setInt(9, orderPublish.getState());
            preparedStatement.setString(10, orderPublish.getMarket());
            preparedStatement.setDate(11, new Date(System.currentTimeMillis()));
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new BlockStoreException(e);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Could not close statement");
                }
            }
        }
    }

    @Override
    public List<OrderPublish> getOrderPublishListWithCondition(Map<String, Object> request) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        List<OrderPublish> list = new ArrayList<OrderPublish>();
        try {
            StringBuffer whereStr = new StringBuffer(" WHERE 1 = 1 ");
            for (Entry<String, Object> entry : request.entrySet()) {
                if (StringUtils.isBlank(entry.getValue().toString())) {
                    continue;
                }
                whereStr.append(" AND ").append(entry.getKey() + "=" + "'" + entry.getValue() + "' ");
            }
            String sql = SELECT_ORDERPUBLISH_SQL + whereStr;
            // System.out.println(sql);
            preparedStatement = conn.get().prepareStatement(sql);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                OrderPublish orderPublish = new OrderPublish();
                orderPublish.setOrderId(resultSet.getString("orderid"));
                orderPublish.setAddress(resultSet.getString("address"));
                orderPublish.setTokenId(resultSet.getString("tokenid"));
                orderPublish.setType(resultSet.getInt("type"));
                orderPublish.setPrice(resultSet.getLong("price"));
                orderPublish.setAmount(resultSet.getLong("amount"));
                orderPublish.setState(resultSet.getInt("state"));
                orderPublish.setValidateTo(resultSet.getDate("validateto"));
                orderPublish.setValidateFrom(resultSet.getDate("validatefrom"));
                orderPublish.setMarket(resultSet.getString("market"));
                list.add(orderPublish);
            }
            return list;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public void saveExchange(Exchange exchange) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(INSERT_EXCHANGE_SQL);
            preparedStatement.setString(1, exchange.getOrderid());
            preparedStatement.setString(2, exchange.getFromAddress());
            preparedStatement.setString(3, exchange.getFromTokenHex());
            preparedStatement.setString(4, exchange.getFromAmount());
            preparedStatement.setString(5, exchange.getToAddress());
            preparedStatement.setString(6, exchange.getToTokenHex());
            preparedStatement.setString(7, exchange.getToAmount());
            preparedStatement.setBytes(8, exchange.getData());
            preparedStatement.setInt(9, exchange.getToSign());
            preparedStatement.setInt(10, exchange.getFromSign());
            preparedStatement.setString(11, exchange.getToOrderId());
            preparedStatement.setString(12, exchange.getFromOrderId());
            preparedStatement.setString(13, exchange.getMarket());
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new BlockStoreException(e);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Could not close statement");
                }
            }
        }
    }

    @Override
    public List<Exchange> getExchangeListWithAddress(String address) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        List<Exchange> list = new ArrayList<Exchange>();
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_EXCHANGE_SQL);
            preparedStatement.setString(1, address);
            preparedStatement.setString(2, address);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                Exchange exchange = new Exchange();
                exchange.setOrderid(resultSet.getString("orderid"));
                exchange.setFromAddress(resultSet.getString("fromAddress"));
                exchange.setFromTokenHex(resultSet.getString("fromTokenHex"));
                exchange.setFromAmount(resultSet.getString("fromAmount"));
                exchange.setToAddress(resultSet.getString("toAddress"));
                exchange.setToTokenHex(resultSet.getString("toTokenHex"));
                exchange.setToAmount(resultSet.getString("toAmount"));
                exchange.setData(resultSet.getBytes("data"));
                exchange.setToSign(resultSet.getInt("toSign"));
                exchange.setFromSign(resultSet.getInt("fromSign"));
                exchange.setToOrderId(resultSet.getString("toOrderId"));
                exchange.setFromOrderId(resultSet.getString("fromOrderId"));
                exchange.setMarket(resultSet.getString("market"));
                list.add(exchange);
            }
            return list;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public void saveOrderMatch(OrderMatch orderMatch) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(INSERT_ORDERMATCH_SQL);
            preparedStatement.setString(1, orderMatch.getMatchid());
            preparedStatement.setString(2, orderMatch.getRestingOrderId());
            preparedStatement.setString(3, orderMatch.getIncomingOrderId());
            preparedStatement.setInt(4, orderMatch.getType());
            preparedStatement.setLong(5, orderMatch.getPrice());
            preparedStatement.setLong(6, orderMatch.getExecutedQuantity());
            preparedStatement.setLong(7, orderMatch.getRemainingQuantity());
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new BlockStoreException(e);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Could not close statement");
                }
            }
        }
    }

    @Override
    public void updateExchangeSign(String orderid, String signtype, byte[] data) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            String sql = "";
            if (signtype.equals("to")) {
                sql = "UPDATE exchange SET toSign = 1, data = ? WHERE orderid = ?";
            } else {
                sql = "UPDATE exchange SET fromSign = 1, data = ? WHERE orderid = ?";
            }
            preparedStatement = conn.get().prepareStatement(sql);
            preparedStatement.setString(2, orderid);
            preparedStatement.setBytes(1, data);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new BlockStoreException(e);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Could not close statement");
                }
            }
        }
    }

    @Override
    public OrderPublish getOrderPublishByOrderid(String orderid) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            StringBuffer whereStr = new StringBuffer(" WHERE orderid = ? ");
            String sql = SELECT_ORDERPUBLISH_SQL + whereStr;
            preparedStatement = conn.get().prepareStatement(sql);
            preparedStatement.setString(1, orderid);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (!resultSet.next()) {
                return null;
            }
            OrderPublish orderPublish = new OrderPublish();
            orderPublish.setOrderId(resultSet.getString("orderid"));
            orderPublish.setAddress(resultSet.getString("address"));
            orderPublish.setTokenId(resultSet.getString("tokenid"));
            orderPublish.setType(resultSet.getInt("type"));
            orderPublish.setPrice(resultSet.getLong("price"));
            orderPublish.setAmount(resultSet.getLong("amount"));
            orderPublish.setState(resultSet.getInt("state"));
            orderPublish.setValidateTo(resultSet.getDate("validateto"));
            orderPublish.setValidateFrom(resultSet.getDate("validatefrom"));
            orderPublish.setMarket(resultSet.getString("market"));
            return orderPublish;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public Exchange getExchangeInfoByOrderid(String orderid) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(SELECT_EXCHANGE_ORDERID_SQL);
            preparedStatement.setString(1, orderid);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (!resultSet.next()) {
                return null;
            }
            Exchange exchange = new Exchange();
            exchange.setOrderid(resultSet.getString("orderid"));
            exchange.setFromAddress(resultSet.getString("fromAddress"));
            exchange.setFromTokenHex(resultSet.getString("fromTokenHex"));
            exchange.setFromAmount(resultSet.getString("fromAmount"));
            exchange.setToAddress(resultSet.getString("toAddress"));
            exchange.setToTokenHex(resultSet.getString("toTokenHex"));
            exchange.setToAmount(resultSet.getString("toAmount"));
            exchange.setData(resultSet.getBytes("data"));
            exchange.setToSign(resultSet.getInt("toSign"));
            exchange.setFromSign(resultSet.getInt("fromSign"));
            exchange.setToOrderId(resultSet.getString("toOrderId"));
            exchange.setFromOrderId(resultSet.getString("fromOrderId"));
            exchange.setMarket(resultSet.getString("market"));
            return exchange;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public void updateOrderPublishState(String orderid, int state) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            String sql = "UPDATE orderpublish SET state = ? WHERE orderid = ?";
            preparedStatement = conn.get().prepareStatement(sql);
            preparedStatement.setInt(1, state);
            preparedStatement.setString(2, orderid);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new BlockStoreException(e);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Could not close statement");
                }
            }
        }
    }

    @Override
    public List<OrderPublish> getOrderPublishListWithNotMatch() throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        List<OrderPublish> list = new ArrayList<OrderPublish>();
        try {
            String sql = SELECT_ORDERPUBLISH_SQL + " WHERE 1 = 1 AND state = ?";
            preparedStatement = conn.get().prepareStatement(sql);
            preparedStatement.setInt(1, OrderState.publish.ordinal());
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                OrderPublish orderPublish = new OrderPublish();
                orderPublish.setOrderId(resultSet.getString("orderid"));
                orderPublish.setAddress(resultSet.getString("address"));
                orderPublish.setTokenId(resultSet.getString("tokenid"));
                orderPublish.setType(resultSet.getInt("type"));
                orderPublish.setPrice(resultSet.getLong("price"));
                orderPublish.setAmount(resultSet.getLong("amount"));
                orderPublish.setState(resultSet.getInt("state"));
                orderPublish.setValidateTo(resultSet.getDate("validateto"));
                orderPublish.setValidateFrom(resultSet.getDate("validatefrom"));
                orderPublish.setMarket(resultSet.getString("market"));
                list.add(orderPublish);
            }
            return list;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public void put(StoredBlock block) throws BlockStoreException {
    }

    @Override
    public StoredBlock get(Sha256Hash hash) throws BlockStoreException {
        return null;
    }

    @Override
    public List<UTXO> getOpenTransactionOutputs(List<Address> addresses) throws UTXOProviderException {
        return null;
    }

    @Override
    public List<UTXO> getOpenTransactionOutputs(List<Address> addresses, byte[] tokenid) throws UTXOProviderException {
        return null;
    }
    

    @Override
    public void deleteOrderPublish(String orderid) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(DELETE_ORDERPUBLISH_SQL);
            preparedStatement.setString(1, orderid);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new BlockStoreException(e);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Could not close statement");
                }
            }
        }
    }

    @Override
    public void deleteExchangeInfo(String orderid) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(DELETE_EXCHANGE_SQL);
            preparedStatement.setString(1, orderid);
            preparedStatement.setString(2, orderid);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new BlockStoreException(e);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Could not close statement");
                }
            }
        }
    }

    @Override
    public void deleteOrderMatch(String orderid) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.get().prepareStatement(DELETE_ORDERMATCH_SQL);
            preparedStatement.setString(1, orderid);
            preparedStatement.setString(2, orderid);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new BlockStoreException(e);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Could not close statement");
                }
            }
        }
    }

    @Override
    public List<OrderPublish> getOrderPublishListRemoveDaily(int i) throws BlockStoreException {
        maybeConnect();
        PreparedStatement preparedStatement = null;
        List<OrderPublish> list = new ArrayList<OrderPublish>();
        try {
            String sql = SELECT_ORDERPUBLISH_SQL + " WHERE DATE_ADD(submitDate, INTERVAL 2 DAY) <= NOW()";
            preparedStatement = conn.get().prepareStatement(sql);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                OrderPublish orderPublish = new OrderPublish();
                orderPublish.setOrderId(resultSet.getString("orderid"));
                orderPublish.setAddress(resultSet.getString("address"));
                orderPublish.setTokenId(resultSet.getString("tokenid"));
                orderPublish.setType(resultSet.getInt("type"));
                orderPublish.setPrice(resultSet.getLong("price"));
                orderPublish.setAmount(resultSet.getLong("amount"));
                orderPublish.setState(resultSet.getInt("state"));
                orderPublish.setValidateTo(resultSet.getDate("validateto"));
                orderPublish.setValidateFrom(resultSet.getDate("validatefrom"));
                orderPublish.setMarket(resultSet.getString("market"));
                list.add(orderPublish);
            }
            return list;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }
}
