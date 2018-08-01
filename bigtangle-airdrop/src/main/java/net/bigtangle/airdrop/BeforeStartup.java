/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.airdrop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import net.bigtangle.airdrop.config.ServerConfiguration;
import net.bigtangle.airdrop.service.WechatInviteService;

@Component
public class BeforeStartup implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments applicationArguments) throws Exception{
        if (serverConfiguration.isReset()) {
            wechatInviteService.clearWechatInviteStatus();
        }
    }
    
    @Autowired
    private ServerConfiguration serverConfiguration;
    
    @Autowired
    private WechatInviteService wechatInviteService;
}
