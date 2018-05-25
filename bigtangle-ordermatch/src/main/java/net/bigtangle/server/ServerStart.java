/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = { "net.bigtangle"   })
@EnableScheduling
//@EnableCaching
public class ServerStart {

    public static void main(String[] args) {
//        SpringApplication.run(ServerStart.class, args);
        SpringApplication springApplication = new SpringApplication(ServerStart.class);
        springApplication.addListeners(new BeforeStartup());
        springApplication.run(args);
    }
}
