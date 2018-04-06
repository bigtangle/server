/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import net.bigtangle.core.NetworkParameters;
import net.bigtangle.params.TestNet3Params;

@Configuration
@Profile("testnet")
public class TestnetConfiguration {

    @Bean
    public NetworkParameters networkParameters() {
        return new TestNet3Params();
    }
}
