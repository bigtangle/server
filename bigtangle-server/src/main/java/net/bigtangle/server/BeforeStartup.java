/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.store.FullPrunedBlockStore;

@Component
public class BeforeStartup {

    @PostConstruct
    public void run() throws Exception {
        //may cleanup of project in mixed eclipse   false in test 
        if (serverConfiguration.getCreatetable()) {
            store.create();
        }
        serverConfiguration.setServiceReady(true);
    }

    @Autowired
    private ServerConfiguration serverConfiguration;
    @Autowired
    FullPrunedBlockStore store;
}
