package com.bignetcoin.server.config;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.TestNet3Params;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("testnet")
public class TestnetConfiguration {
    @Bean
    public NetworkParameters networkParameters() {
        return new TestNet3Params();
    }
}
