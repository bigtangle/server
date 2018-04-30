package net.bigtangle.server.jdbc;

import java.io.File;
import java.io.IOException;

public class BaseService {

	ConnectHadoop connectHadoop;

	public BaseService() {

	}

	public BaseService(ConnectHadoop connectHadoop) {
		super();
		this.connectHadoop = connectHadoop;
	}

	public void system() throws IOException {
		// System.setProperty("java.security.auth.login.config",
		// "gss-jaas.conf");
		System.setProperty("sun.security.jgss.debug", "true");
		System.setProperty("sun.security.krb5.debug", "true");
		System.setProperty("javax.security.auth.useSubjectCredsOnly", "false");
		//System.setProperty("java.security.krb5.conf",
		//		getConnectHadoop().getHadoopconfdir() + File.separator + "krb5.conf");
		// System.setProperty("java.security.krb5.realm",
		// getConnectHadoop().getKdcrealm());
		// "dasimi.com");
		// System.setProperty("java.security.krb5.kdc",
		// getConnectHadoop().getKdc());
	}

	public ConnectHadoop getConnectHadoop() {
		return connectHadoop;
	}

	public void setConnectHadoop(ConnectHadoop connectHadoop) {
		this.connectHadoop = connectHadoop;
	}

}