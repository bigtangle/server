/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.airdrop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = { "net.bigtangle" })
@EnableScheduling
public class Start {

    public static void main(String[] args) {
        SpringApplication springApplication = new SpringApplication(Start.class);
        springApplication.run(args);
    }
}
