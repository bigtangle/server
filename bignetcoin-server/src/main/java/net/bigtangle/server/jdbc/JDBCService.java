package net.bigtangle.server.jdbc;

import java.beans.PropertyVetoException;
import java.sql.Driver;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

public abstract class JDBCService extends BaseService {

	public JDBCService(ConnectHadoop ConnectHadoop) {
		super(ConnectHadoop);

	}

	private final Log LOG = LogFactory.getLog(getClass().getName());

	List<JdbcTemplateStatus> jdbcTemplateList = new ArrayList<JdbcTemplateStatus>();

	public void connect(String servername, String drive, String url, String username, String password)
			throws Exception {
		if (getConnectHadoop().isConnectionpool()) {
			connectPooled(servername, drive, url, username, password);
		} else {
			connectSimple(servername, drive, url, username, password);
		}
	}

	@SuppressWarnings("unchecked")
	public void connectSimple(String servername, String drive, String url, String username, String password)
			throws Exception {

		SimpleDriverDataSource ds = new SimpleDriverDataSource();

		ds.setDriverClass((Class<Driver>) Class.forName(drive));
		ds.setUrl(url);
		ds.setUsername(username);
		ds.setPassword(password);
		JdbcTemplate jdbcTemplate = new JdbcTemplate(ds);
		jdbcTemplate.setIgnoreWarnings(true);
		// jdbcTemplate.setQueryTimeout(queryTimeout);

		JdbcTemplateStatus j = new JdbcTemplateStatus();
		j.jdbcTemplate = jdbcTemplate;
		j.servername = servername;
		j.status = WorkflowStatus.READY;
		jdbcTemplateList.add(j);
		// LOG.debug(" Host is added to HA pool : " + servername);
	}

	public void connectPooled(String servername, String drive, String url, String username, String password)
			throws PropertyVetoException {

		com.mchange.v2.c3p0.ComboPooledDataSource ds = new com.mchange.v2.c3p0.ComboPooledDataSource();

		ds.setDriverClass(drive);
		ds.setJdbcUrl(url);
		ds.setUser(username);
		ds.setPassword(password);

		// force connections to renew after 1 hours
		ds.setMaxConnectionAge(1 * 60 * 60);
		// get rid too many of idle connections after 3 minutes
		ds.setMaxIdleTimeExcessConnections(3 * 60);
		// ds.setMaxAdministrativeTaskTime(100000);
		// SELECT 1 FROM any_existing_table WHERE 1=0
		ds.setPreferredTestQuery(tesSQL());
		ds.setTestConnectionOnCheckout(false);
		JdbcTemplate jdbcTemplate = new JdbcTemplate(ds);
		jdbcTemplate.setIgnoreWarnings(true);
		// jdbcTemplate.setQueryTimeout(queryTimeout);

		JdbcTemplateStatus j = new JdbcTemplateStatus();
		j.jdbcTemplate = jdbcTemplate;
		j.servername = servername;
		j.status = WorkflowStatus.READY;
		jdbcTemplateList.add(j);
		// LOG.debug(" Host is added to HA pool : " + servername);
	}

	/*
	 * check the database connection, even if the table is not created.
	 */
	public String tesSQL() {
		return "SHOW TABLES LIKE \'" + getConnectHadoop().getImpalaDB() + "." + "L_9001_SYSTEM_PARMS \' ";
	}

	/*
	 * return a working JdbcTemplateStatus or null
	 */
	public synchronized JdbcTemplateStatus getJdbcTemplate() {
		Random randomizer = new Random();
		JdbcTemplateStatus j = getJdbcTemplateList().get(randomizer.nextInt(getJdbcTemplateList().size()));
		if (WorkflowStatus.READY.equals(j.status)) {
			return j;
		}
		for (JdbcTemplateStatus d : jdbcTemplateList) {
			if (WorkflowStatus.READY.equals(j.status)) {
				return d;
			}
		}
		return null;

	}

	public synchronized void resetHost() {
		jdbcTemplateList = new ArrayList<JdbcTemplateStatus>();

		try {
			connect();
		} catch (Exception e) {
			LOG.warn(e);
		}
	}

	public abstract void connect() throws Exception;

	public synchronized void badHost(String servername) {
		LOG.debug("  dead server : " + servername);

		for (JdbcTemplateStatus j : jdbcTemplateList) {
			if (servername.equals(j.servername)) {
				j.status = WorkflowStatus.ERROR;
			}
		}
	}

	List<JdbcTemplateStatus> getJdbcTemplateList() {
		return jdbcTemplateList;
	}

	public void setJdbcTemplateList(List<JdbcTemplateStatus> jdbcTemplateList) {
		this.jdbcTemplateList = jdbcTemplateList;
	}

	/*
	 * add HA function to query
	 */
	public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {

		JdbcTemplateStatus template = getJdbcTemplate();
		if (template != null) {
			try {
				// LOG.debug(" query on this host: " +template.getServername());
				// LOG.debug("sql: " +sql);
				return template.getJdbcTemplate().query(sql, rowMapper, args);

			} catch (org.springframework.jdbc.UncategorizedSQLException
					| org.springframework.beans.BeanInstantiationException
					| org.springframework.jdbc.BadSqlGrammarException
					| org.springframework.dao.DataAccessResourceFailureException
					| org.springframework.jdbc.IncorrectResultSetColumnCountException
					| org.springframework.jdbc.InvalidResultSetAccessException e) {
				throw e;

			} catch (Exception e) {
				LOG.warn(e);
				badHost(template.servername);
				return this.query(sql, rowMapper, args);
			}
		} else {
			if (getConnectHadoop().getWaitMinutesForNoServers() > 0) {
				sleep();
				return this.query(sql, rowMapper, args);
			} else {
				resetHost();
				throw new NoHostsException(" no Host avaiable");
			}
		}
	}

	public List<Map<String, Object>> queryForList(String sql, Object... args) {

		JdbcTemplateStatus template = getJdbcTemplate();
		if (template != null) {
			try {
				// LOG.debug(" query on this host: " +template.getServername());
				// LOG.debug("sql: " +sql);
				return template.getJdbcTemplate().queryForList(sql, args);

			} catch (org.springframework.jdbc.UncategorizedSQLException
					| org.springframework.beans.BeanInstantiationException
					| org.springframework.jdbc.BadSqlGrammarException
					| org.springframework.dao.DataAccessResourceFailureException
					| org.springframework.jdbc.IncorrectResultSetColumnCountException
					| org.springframework.jdbc.InvalidResultSetAccessException e) {
				throw e;

			} catch (Exception e) {
				LOG.warn(e);
				badHost(template.servername);
				return this.queryForList(sql, args);
			}
		} else {
			if (getConnectHadoop().getWaitMinutesForNoServers() > 0) {
				sleep();
				return this.queryForList(sql, args);
			} else {
				resetHost();
				throw new NoHostsException(" no Host avaiable");
			}
		}
	}

	/*
	 * add HA function to update, based on Impala with Hbase
	 */
	public int update(String sql, Object... args) {
		JdbcTemplateStatus template = getJdbcTemplate();
		if (template != null) {
			try {
				LOG.debug(" update on this host: " + template.getServername() + " sql: " + sql);

				return template.getJdbcTemplate().update(sql, args);

			} catch (org.springframework.jdbc.UncategorizedSQLException
					| org.springframework.beans.BeanInstantiationException
					| org.springframework.jdbc.BadSqlGrammarException
					| org.springframework.jdbc.IncorrectResultSetColumnCountException
					| org.springframework.jdbc.InvalidResultSetAccessException e) {
				throw e;

			} catch (Exception e) {
				LOG.warn(e);
				badHost(template.servername);
				return this.update(sql, args);
			}
		} else {
			if (getConnectHadoop().getWaitMinutesForNoServers() > 0) {
				sleep();
				return this.update(sql, args);
			} else {
				resetHost();
				throw new NoHostsException(" no Host avaiable");
			}
		}

	}

	public void sleep() {
		try {
			LOG.warn(" all servers are not down, sleep " + getConnectHadoop().getWaitMinutesForNoServers()
					+ "   minutes and do reset and start again. ");
			Thread.sleep(60000 * getConnectHadoop().getWaitMinutesForNoServers());
			// after sleep try again
			resetHost();
			// try again
			LOG.warn("server connection are reset and try again.  ");
		} catch (Exception e) {
			LOG.warn(e);
		}
	}

	public void sleepBeforeSQLExecution() {
		try {
			if (getConnectHadoop().getSleepSecondsBeforeSQLExecution() > 0) {
				LOG.debug("sleep minutes" + getConnectHadoop().getSleepSecondsBeforeSQLExecution());

				Thread.sleep(60000 * getConnectHadoop().getSleepSecondsBeforeSQLExecution());
			}

		} catch (Exception e) {
			LOG.warn(e);
		}
	}

}