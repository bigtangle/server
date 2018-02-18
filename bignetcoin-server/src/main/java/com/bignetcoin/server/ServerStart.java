package com.bignetcoin.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

 

@SpringBootApplication
@ComponentScan(basePackages = {"com.bignetcoin.server"})
public class ServerStart {

 

    public static void main(String[] args) {
        SpringApplication.run(ServerStart.class, args);
    }

}
