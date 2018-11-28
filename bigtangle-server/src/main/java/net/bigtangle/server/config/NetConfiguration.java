/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.bigtangle.core.NetworkParameters;
import net.bigtangle.params.MainNetParams;
import net.bigtangle.params.MainNetParams;

@Configuration
public class NetConfiguration {

    @Autowired
    ServerConfiguration serverConfiguration;
    @Bean
    public NetworkParameters networkParameters() {
        if("Mainnet".equals(serverConfiguration.getNet())) {
            return new MainNetParams();
        }
  
        return new MainNetParams();
    }
    
    
}
