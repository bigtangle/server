package net.bigtangle.server.jdbc;

import java.beans.PropertyVetoException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.security.UserGroupInformation;
import org.springframework.jdbc.core.RowMapper;

public class PhoenixService extends JDBCService {
	public Configuration configuration;
	 
	private final Log LOG = LogFactory.getLog(getClass().getName());

	public PhoenixService(ConnectHadoop connectHadoop) throws Exception {
		super(connectHadoop);
		connect();
	}

	public void connect() throws Exception {
		  setLogin();
		List<String> items = Arrays.asList(getConnectHadoop().getHiveHost().split("\\s*,\\s*"));
		for (String servername : items) {
			connect(servername);
		}

	}

	public void setLogin() throws SQLException, IOException, ClassNotFoundException, PropertyVetoException {

		system();
		  configuration = HBaseConfiguration.create();
		configuration.addResource(new Path(getConnectHadoop().getHbaseconfdir(), "hbase-site.xml"));
		configuration.addResource(new Path(getConnectHadoop().getHadoopconfdir(), "core-site.xml"));

 
			// System.setProperty("java.security.auth.login.config",
			// "gss-jaas.conf");
			// System.setProperty("sun.security.jgss.debug","true");
			System.setProperty("javax.security.auth.useSubjectCredsOnly", "false");
			//System.setProperty("java.security.krb5.conf", "krb5.conf");

			UserGroupInformation.setConfiguration(configuration);
			UserGroupInformation userGroupInformation = UserGroupInformation.loginUserFromKeytabAndReturnUGI(
					getConnectHadoop().getUserPrincipal(), getConnectHadoop().getUserKeytab());
			userGroupInformation.reloginFromKeytab();
			UserGroupInformation.setLoginUser(userGroupInformation);
		 
 
	}

	public void connect(String servername) throws Exception {

		connect(servername, "org.apache.phoenix.jdbc.PhoenixDriver", "jdbc:phoenix:172.18.5.1:hbase:/home/cui/test.keytab:test@MEW.KAFKA.DIBA.CORP.INT" ,
				"root", "");
	}

	public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
		try {
			  setLogin();
		} catch (Exception e) {
			LOG.warn(e);
		}
		return super.query(sql, rowMapper, args);
	}

	public Map<String, List<?>> query(String sql) throws Exception {
		Connection con = getJdbcTemplate().getJdbcTemplate().getDataSource().getConnection();
		Statement stmt = con.createStatement();
		ResultSet results = stmt.executeQuery(sql);
		ResultSetMetaData resultMetaData = results.getMetaData();
		int cols = resultMetaData.getColumnCount();
		List<String> list = new ArrayList<String>();
		List<String> tables = new ArrayList<String>();
		List<Map<String, Object>> maps = new ArrayList<Map<String, Object>>();

		for (int i = 1; i <= cols; i++) {
			list.add(resultMetaData.getColumnName(i).split("\\.")[1]);
			if (!tables.contains(resultMetaData.getTableName(i))) {
				tables.add(resultMetaData.getTableName(i));
			}
		}
		while (results.next()) {
			Map<String, Object> temp = new HashMap<String, Object>();
			for (String col : list) {
				temp.put(col, results.getObject(col));
			}
			maps.add(temp);
		}
		Map<String, List<?>> resultMap = new HashMap<String, List<?>>();
		resultMap.put("colnames", list);
		resultMap.put("res", maps);
		resultMap.put("tables", tables);
		return resultMap;
	}

	public int update(String sql, Object... args) {
		try {
			// setLogin();
		} catch (Exception e) {
			LOG.warn(e);
		}
		return super.update(sql, args);
	}
}
