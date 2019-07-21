/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.blockconfirm;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import net.bigtangle.blockconfirm.config.ServerConfiguration;

@Component
public class BeforeStartup implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments applicationArguments) throws Exception {
        if (serverConfiguration.isReset()) {
            wechatInviteService.clearStatus();
        }
    }

    @Autowired
    private ServerConfiguration serverConfiguration;

    @Autowired
    private ResetDepositSatusService wechatInviteService;
}
