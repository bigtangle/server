/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.utils;

import org.junit.Test;

public class ProbabilityTest {

    @Test
    public void testSigns() throws Exception {

        for (int z = 1; z < 300; z++) {
            System.out.println(
                    z + "=" +ProbabilityBlock.attackerSuccessProbability(0.3, z));
        }

    }

}
