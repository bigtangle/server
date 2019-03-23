/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.airdrop.store;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bigtangle.airdrop.bean.Vm_deposit;
import net.bigtangle.airdrop.bean.WechatInvite;
import net.bigtangle.core.Address;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.ProtocolException;
import net.bigtangle.core.exception.VerificationException;
import net.bigtangle.params.MainNetParams;

/**
 * <p>
 * A generic full pruned block store for a relational database. This generic
 * class requires certain table structures for the block store.
 * </p>
 * 
 */
public abstract class DatabaseFullPrunedBlockStore implements FullPrunedBlockStore {

	public void clearWechatInviteStatusZero() throws BlockStoreException {
		String sql = "update wechatinvite set status = 0";
		maybeConnect();
		PreparedStatement s = null;
		try {
			s = conn.get().prepareStatement(sql);
			s.executeUpdate();
			s.close();
		} catch (SQLException e) {
			if (!(getDuplicateKeyErrorCode().equals(e.getSQLState())))
				throw new BlockStoreException(e);
		} finally {
			if (s != null) {
				try {
					if (s.getConnection() != null)
						s.close();
				} catch (SQLException e) {
				}
			}
		}
	}

	private static final Logger log = LoggerFactory.getLogger(DatabaseFullPrunedBlockStore.class);

	protected String VERSION_SETTING = "version";

	// Tables exist SQL.
	protected String SELECT_CHECK_TABLES_EXIST_SQL = "SELECT * FROM wechatreward WHERE 1 = 2";

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

	@Override
	public void updateWechatInviteStatus(String id, int status) throws BlockStoreException {
		String sql = "update wechatinvite set status = ? where id = ?";
		maybeConnect();
		PreparedStatement s = null;
		try {
			s = conn.get().prepareStatement(sql);
			s.setInt(1, status);
			s.setString(2, id);
			s.executeUpdate();
			s.close();
		} catch (SQLException e) {
			if (!(getDuplicateKeyErrorCode().equals(e.getSQLState())))
				throw new BlockStoreException(e);
		} finally {
			if (s != null) {
				try {
					if (s.getConnection() != null)
						s.close();
				} catch (SQLException e) {
				}
			}
		}
	}

	@Override
	public void updateWechatInviteStatusByWechatId(String wechatId, int status) throws BlockStoreException {
		String sql = "update wechatinvite set status = ? where wechatId = ?";
		maybeConnect();
		PreparedStatement s = null;
		try {
			s = conn.get().prepareStatement(sql);
			s.setInt(1, status);
			s.setString(2, wechatId);
			s.executeUpdate();
			s.close();
		} catch (SQLException e) {
			if (!(getDuplicateKeyErrorCode().equals(e.getSQLState())))
				throw new BlockStoreException(e);
		} finally {
			if (s != null) {
				try {
					if (s.getConnection() != null)
						s.close();
				} catch (SQLException e) {
				}
			}
		}
	}

	public HashMap<String, String> queryByUWechatInvitePubKeyMapping(Set<String> wechatIdSet)
			throws BlockStoreException {
		if (wechatIdSet.isEmpty()) {
			return new HashMap<String, String>();
		}
		StringBuffer stringBuffer = new StringBuffer();
		for (String s : wechatIdSet) {
			stringBuffer.append(",").append("'").append(s).append("'");
		}
		String sql = "select wechatId, pubkey from wechatinvite where wechatId in (" + stringBuffer.substring(1) + ")";
		maybeConnect();
		PreparedStatement s = null;
		try {
			s = conn.get().prepareStatement(sql);
			ResultSet resultSet = s.executeQuery();
			HashMap<String, String> map = new HashMap<String, String>();
			while (resultSet.next()) {
				map.put(resultSet.getString("wechatId"), resultSet.getString("pubkey"));
			}
			return map;
		} catch (SQLException ex) {
			throw new BlockStoreException(ex);
		} catch (ProtocolException e) {
			throw new BlockStoreException(e);
		} catch (VerificationException e) {
			throw new BlockStoreException(e);
		} finally {
			if (s != null) {
				try {
					s.close();
				} catch (SQLException e) {
					throw new BlockStoreException("Failed to close PreparedStatement");
				}
			}
		}
	}

	public Map<String, HashMap<String, String>> queryByUWechatInvitePubKeyInviterIdMap(Collection<String> wechatIdSet)
			throws BlockStoreException {
		if (wechatIdSet.isEmpty()) {
			return new HashMap<String, HashMap<String, String>>();
		}
		StringBuffer stringBuffer = new StringBuffer();
		for (String s : wechatIdSet) {
			stringBuffer.append(",").append("'").append(s).append("'");
		}
		String sql = "select wechatId, pubkey,wechatInviterId from wechatinvite where wechatId in ("
				+ stringBuffer.substring(1) + ")";
		maybeConnect();
		PreparedStatement s = null;
		try {
			s = conn.get().prepareStatement(sql);
			ResultSet resultSet = s.executeQuery();
			HashMap<String, String> map = new HashMap<String, String>();
			HashMap<String, String> map1 = new HashMap<String, String>();
			Map<String, HashMap<String, String>> temp = new HashMap<String, HashMap<String, String>>();
			while (resultSet.next()) {
				map.put(resultSet.getString("wechatId"), resultSet.getString("pubkey"));
				map1.put(resultSet.getString("wechatId"), resultSet.getString("wechatInviterId"));
			}
			temp.put("pubkey", map);
			temp.put("wechatInviterId", map1);
			return temp;
		} catch (SQLException ex) {
			throw new BlockStoreException(ex);
		} catch (ProtocolException e) {
			throw new BlockStoreException(e);
		} catch (VerificationException e) {
			throw new BlockStoreException(e);
		} finally {
			if (s != null) {
				try {
					s.close();
				} catch (SQLException e) {
					throw new BlockStoreException("Failed to close PreparedStatement");
				}
			}
		}
	}

	public List<Vm_deposit> queryDepositKeyFromOrderKey() throws BlockStoreException {
		List<Vm_deposit> l = new ArrayList<Vm_deposit>();
		String sql = "select userid , amount,  d.status, pubkey " + "from vm_deposit d "
				+ "join Account a on d.userid=a.id "
				+ "join wechatinvite w on a.email=w.wechatId and w.pubkey is not null ";

		maybeConnect();
		PreparedStatement s = null;
		try {
			s = conn.get().prepareStatement(sql);
			ResultSet resultSet = s.executeQuery();
			while (resultSet.next()) {
				Vm_deposit vm_deposit = new Vm_deposit();
				vm_deposit.setStatus(resultSet.getString("status"));
				vm_deposit.setUserid(resultSet.getLong("userid"));
				vm_deposit.setAmount(resultSet.getBigDecimal("amount"));
				vm_deposit.setPubkey(resultSet.getString("pubkey"));
				// add only correct pub key to return list for transfer money
				if (!"PAID".equalsIgnoreCase(vm_deposit.getStatus())) {
					boolean flag = true;
					String pubkey = resultSet.getString("pubkey");
					try {
						Address.fromBase58(MainNetParams.get(), pubkey);
					} catch (Exception e) {
						// logger.debug("", e);
						flag = false;
					}
					if (flag) {
						l.add(vm_deposit);
					}

				}

			}
			return l;
		} catch (SQLException ex) {
			throw new BlockStoreException(ex);
		} catch (ProtocolException e) {
			throw new BlockStoreException(e);
		} catch (VerificationException e) {
			throw new BlockStoreException(e);
		} finally {
			if (s != null) {
				try {
					s.close();
				} catch (SQLException e) {
					throw new BlockStoreException("Failed to close PreparedStatement");
				}
			}
		}
	}

	public void updateDepositStatus(Long id, String status) throws BlockStoreException {
		String sql = "update vm_deposit set status = ? where userid = ? ";
		maybeConnect();
		PreparedStatement s = null;
		try {
			s = conn.get().prepareStatement(sql);
			s.setString(1, status);
			s.setLong(2, id);
			s.executeUpdate();
			s.close();
		} catch (SQLException e) {
			if (!(getDuplicateKeyErrorCode().equals(e.getSQLState())))
				throw new BlockStoreException(e);
		} finally {
			if (s != null) {
				try {
					if (s.getConnection() != null)
						s.close();
				} catch (SQLException e) {
				}
			}
		}
	}

	public void resetDepositPaid() throws BlockStoreException {
		String sql = "update vm_deposit set status = 'RESET' ";
		maybeConnect();
		PreparedStatement s = null;
		try {
			s = conn.get().prepareStatement(sql);
			s.executeUpdate();
			s.close();
		} catch (SQLException e) {
			if (!(getDuplicateKeyErrorCode().equals(e.getSQLState())))
				throw new BlockStoreException(e);
		} finally {
			if (s != null) {
				try {
					if (s.getConnection() != null)
						s.close();
				} catch (SQLException e) {
				}
			}
		}
	}

	public List<WechatInvite> queryByUnfinishedWechatInvite() throws BlockStoreException {
		String sql = "select id, wechatId, wechatInviterId, createTime, status,pubkey  from wechatinvite where status = 0";
		List<WechatInvite> wechatInvites = new ArrayList<WechatInvite>();
		maybeConnect();
		PreparedStatement s = null;
		try {
			s = conn.get().prepareStatement(sql);
			ResultSet resultSet = s.executeQuery();
			while (resultSet.next()) {
				WechatInvite wechatInvite = new WechatInvite();
				wechatInvite.setId(resultSet.getString("id"));
				wechatInvite.setWechatId(resultSet.getString("wechatId"));
				wechatInvite.setWechatinviterId(resultSet.getString("wechatinviterId"));
				wechatInvite.setCreateTime(resultSet.getDate("createTime"));
				wechatInvite.setStatus(resultSet.getInt("status"));
				wechatInvite.setPubkey(resultSet.getString("pubkey"));
				wechatInvites.add(wechatInvite);
			}
			return wechatInvites;
		} catch (SQLException ex) {
			throw new BlockStoreException(ex);
		} catch (ProtocolException e) {
			throw new BlockStoreException(e);
		} catch (VerificationException e) {
			throw new BlockStoreException(e);
		} finally {
			if (s != null) {
				try {
					s.close();
				} catch (SQLException e) {
					throw new BlockStoreException("Failed to close PreparedStatement");
				}
			}
		}
	}

	/**
	 * <p>
	 * Create a new DatabaseFullPrunedBlockStore, using the full connection URL
	 * instead of a hostname and password, and optionally allowing a schema to be
	 * specified.
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
	 *             If there is a failure to connect and/or initialise the database.
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
		// create();
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
			if (!tablesExists()) {
				createTables();
			}
		} catch (SQLException e) {
			e.printStackTrace();
			throw new BlockStoreException(e);
		}
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

		return sqlStatements;
	}

	/**
	 * <p>
	 * If there isn't a connection on the {@link ThreadLocal} then create and store
	 * it.
	 * </p>
	 * <p>
	 * This will also automatically set up the schema if it does not exist within
	 * the DB.
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
	 * This specifically checks for the 'settings' table and if it exists makes an
	 * assumption that the rest of the data structures are present.
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
	 * Resets the store by deleting the contents of the tables and reinitialising
	 * them.
	 * 
	 * @throws BlockStoreException
	 *             If the tables couldn't be cleared and initialised.
	 */
	public void resetStore() throws BlockStoreException {
		// maybeConnect();
		// try {
		// // deleteStore();
		// createTables();
		// } catch (SQLException ex) {
		// log.warn("Warning: deleteStore", ex);
		// throw new RuntimeException(ex);
		// }
	}

	/**
	 * Deletes the store by deleting the tables within the database.
	 * 
	 * @throws BlockStoreException
	 *             If tables couldn't be deleted.
	 */
	// public void deleteStore() throws BlockStoreException {
	// maybeConnect();
	// try {
	// for (String sql : getDropTablesSQL()) {
	// Statement s = conn.get().createStatement();
	// try {
	// log.info("drop table : " + sql);
	// s.execute(sql);
	// } finally {
	// s.close();
	// }
	// }
	// } catch (Exception ex) {
	// log.warn("Warning: deleteStore", ex);
	// }
	// }
}
