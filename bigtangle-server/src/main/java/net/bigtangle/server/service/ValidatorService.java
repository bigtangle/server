/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.NetworkParameters;
import net.bigtangle.server.config.ServerConfiguration;

@Service
public class ValidatorService {
 

 
    @Autowired
    protected NetworkParameters networkParameters;
   
    @Autowired
    private ServerConfiguration serverConfiguration;

}
