/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BlockValidator {
	@Autowired
	private MilestoneService milestoneSevice;
	@Autowired
	private BlockService blockService;
	
	

}
