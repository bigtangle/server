/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TipsServiceTest extends AbstractIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(TipsServiceTest.class);
    
    /*
     *  TODO Tipsservice test
        -> CHECK: no conflicts with milestone: used "generalized UTXOs" are confirmed + unspent for approved non-milestone blocks
        -> CHECK: no conflicts with each other  
        -> CHECK: type-specific selection conditions (see below)
        -> Reward CHECK: eligibility==eligible or (eligibility==ineligible and overruled)
     */
    
    
}