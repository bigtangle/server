/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service.base;

import net.bigtangle.core.NetworkParameters;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.service.CacheBlockService;

public class ServiceBaseOrder extends ServiceBase {

	public ServiceBaseOrder(ServerConfiguration serverConfiguration, NetworkParameters networkParameters,
			CacheBlockService cacheBlockService) {
		super(serverConfiguration, networkParameters, cacheBlockService);
 
	}
 

}
