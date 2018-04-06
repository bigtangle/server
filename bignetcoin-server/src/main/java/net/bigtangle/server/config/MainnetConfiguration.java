/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import net.bigtangle.core.NetworkParameters;
import net.bigtangle.params.MainNetParams;

@Configuration
@Profile("mainent")
public class MainnetConfiguration {

    @Bean
    public NetworkParameters networkParameters() {
        return new MainNetParams();
    }
}
