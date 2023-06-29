/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.seeds;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = { "net.bigtangle" })
@EnableScheduling
public class ServerStart {

    public static void main(String[] args) {
        // SpringApplication.run(ServerStart.class, args);
        SpringApplication springApplication = new SpringApplication(ServerStart.class);

        springApplication.run(args);
    }
}
