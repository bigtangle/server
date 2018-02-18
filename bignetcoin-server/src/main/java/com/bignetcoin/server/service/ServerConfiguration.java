package com.bignetcoin.server.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;

@Component
@ConfigurationProperties(prefix = "server")
@Data
public class ServerConfiguration {

 
    private String neighbors;

    @NotNull
    private String port;

    private String udp_receiver_port;

    private String tcp_receiver_port;

    private Boolean debug;

    @NotNull
    private Boolean testnet;

    private String remote;

    private String remote_auth;

    private long remote_limit_api;

}