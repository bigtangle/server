/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Lockobject;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.store.FullBlockStore;

 

/**
 * <p> 
 * </p>
 */
@Service
public class LockobjectService {

  
    private final Logger log = LoggerFactory.getLogger(this.getClass());
  
    /*
     *    lockobjectid = chain length
     *    block = nullable of the chain block, if null then it is task for update confirm
     *    status = unsolid for orphan blocks
     *    addChain -> insert in table
     *    add update confirm, only there is no other update confirm in table
     *     There is only one task executor for ordered execution of the tasks
     *     if the chain is worked and it will be removed from the table 
     */
   
    public boolean lockChainAdd(String lockobjectid,   FullBlockStore store) throws BlockStoreException {
           List<Lockobject> locks = store.selectLockobject( lockobjectid); 
           if(locks==null || locks.isEmpty() ) {
               store.insertLockobject(new Lockobject("chainadd-locked", null, System.currentTimeMillis()));   
               return true;
           } 
            
        return true;
    }

    /*
     * if there is no chain lock and other update confirm local return true;
     * No Wait
     */
    public boolean lockUpdateConfirm(String lockobjectid,   FullBlockStore store) throws BlockStoreException {
        store.selectLockobject( lockobjectid); 
    return true;
}
    
}
