package com.bignetcoin.server.config;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.UnitTestParams;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("regnet")
public class RegnetConfiguration {
    @Bean
    public NetworkParameters networkParameters() {
        return new UnitTestParams();
    }
}
