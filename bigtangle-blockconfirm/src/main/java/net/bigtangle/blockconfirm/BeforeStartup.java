/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.blockconfirm;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BeforeStartup implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments applicationArguments) throws Exception {
//        if (serverConfiguration.isReset()) {
//            wechatInviteService.clearStatus();
//        }
    }

     
}
