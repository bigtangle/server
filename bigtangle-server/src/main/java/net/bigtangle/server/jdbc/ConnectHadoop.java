/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.jdbc;

public class ConnectHadoop {

	String truststore = "";
	String truststorepwd = "";

	String userPrincipal = "";
	String userKeytab = "";

	String impalaDriver = "";
	String impalaHost = "";
	String impalaPort = "";
	String impalaDB = "";
	String impalaUsername = "";
	String impalaPassword = "";

	String hiveDriver = "";
	String hiveHost = "";
	String hivePort = "";
	String hiveDB = "";
	String hiveUsername = "";
	String hivePassword = "";

	String hbaseconfdir = "";
	String hadoopconfdir = "";
	String hdfsuser = "hdfs";
	
	Integer sleepSecondsBeforeSQLExecution;
	
	String getTruststore() {
		return truststore;
	}

	public void setTruststore(String truststore) {
		this.truststore = truststore;
	}

	public String getTruststorepwd() {
		return truststorepwd;
	}

	public void setTruststorepwd(String truststorepwd) {
		this.truststorepwd = truststorepwd;
	}

	public String getUserPrincipal() {
		return userPrincipal;
	}

	public void setUserPrincipal(String userPrincipal) {
		this.userPrincipal = userPrincipal;
	}

	public String getUserKeytab() {
		return userKeytab;
	}

	public void setUserKeytab(String userKeytab) {
		this.userKeytab = userKeytab;
	}

	public String getImpalaDriver() {
		return impalaDriver;
	}

	public void setImpalaDriver(String impalaDriver) {
		this.impalaDriver = impalaDriver;
	}

	public String getImpalaHost() {
		return impalaHost;
	}

	public void setImpalaHost(String impalaHost) {
		this.impalaHost = impalaHost;
	}

	public String getImpalaPort() {
		return impalaPort;
	}

	public void setImpalaPort(String impalaPort) {
		this.impalaPort = impalaPort;
	}

	public String getImpalaDB() {
		return impalaDB;
	}

	public void setImpalaDB(String impalaDB) {
		this.impalaDB = impalaDB;
	}

	public String getImpalaUsername() {
		return impalaUsername;
	}

	public void setImpalaUsername(String impalaUsername) {
		this.impalaUsername = impalaUsername;
	}

	public String getImpalaPassword() {
		return impalaPassword;
	}

	public void setImpalaPassword(String impalaPassword) {
		this.impalaPassword = impalaPassword;
	}

	public String getHiveDriver() {
		return hiveDriver;
	}

	public void setHiveDriver(String hiveDriver) {
		this.hiveDriver = hiveDriver;
	}

	public String getHiveHost() {
		return hiveHost;
	}

	public void setHiveHost(String hiveHost) {
		this.hiveHost = hiveHost;
	}

	public String getHivePort() {
		return hivePort;
	}

	public void setHivePort(String hivePort) {
		this.hivePort = hivePort;
	}

	public String getHiveDB() {
		return hiveDB;
	}

	public void setHiveDB(String hiveDB) {
		this.hiveDB = hiveDB;
	}

	public String getHbaseconfdir() {
		return hbaseconfdir;
	}

	public void setHbaseconfdir(String hbaseconfdir) {
		this.hbaseconfdir = hbaseconfdir;
	}

	public String getHadoopconfdir() {
		return hadoopconfdir;
	}

	public void setHadoopconfdir(String hadoopconfdir) {
		this.hadoopconfdir = hadoopconfdir;
	}

	public String getHiveUsername() {
		return hiveUsername;
	}

	public void setHiveUsername(String hiveUsername) {
		this.hiveUsername = hiveUsername;
	}

	public String getHivePassword() {
		return hivePassword;
	}

	public void setHivePassword(String hivePassword) {
		this.hivePassword = hivePassword;
	}

	public String getHdfsuser() {
		return hdfsuser;
	}

	public void setHdfsuser(String hdfsuser) {
		this.hdfsuser = hdfsuser;
	}

	public Integer getSleepSecondsBeforeSQLExecution() {
		return sleepSecondsBeforeSQLExecution;
	}

	public void setSleepSecondsBeforeSQLExecution(Integer sleepSecondsBeforeSQLExecution) {
		this.sleepSecondsBeforeSQLExecution = sleepSecondsBeforeSQLExecution;
	}

	public int getWaitMinutesForNoServers() {
		 
		return 0;
	}

	public boolean isConnectionpool() {
		 
		return false;
	}

	 

}
