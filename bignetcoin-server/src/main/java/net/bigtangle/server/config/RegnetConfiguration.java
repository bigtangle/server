/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.bigtangle.core.NetworkParameters;
import net.bigtangle.params.UnitTestParams;

@Configuration
public class RegnetConfiguration {

    @Bean
    public NetworkParameters networkParameters() {
        return new UnitTestParams();
    }
}
