package net.bigtangle.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bigtangle.core.NetworkParameters;
import net.bigtangle.server.config.ServerConfiguration;

public class ServiceContract {


	private ServerConfiguration serverConfiguration;
	private NetworkParameters networkParameters;

	private static final Logger logger = LoggerFactory.getLogger(ServiceBase.class);

	public ServiceContract(ServerConfiguration serverConfiguration, NetworkParameters networkParameters) {
		super();
		this.serverConfiguration = serverConfiguration;
		this.networkParameters = networkParameters;
	}

}
