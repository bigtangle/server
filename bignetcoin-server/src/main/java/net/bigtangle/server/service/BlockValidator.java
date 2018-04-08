/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Block;

@Service
public class BlockValidator {
	@Autowired
	private MilestoneService milestoneSevice;
	@Autowired
	private BlockService blockService;
	
	public boolean assessMiningRewardBlock(Block header) {
        // TODO begin checking local validity assessment since it begins as false
		return true;
	}
}
