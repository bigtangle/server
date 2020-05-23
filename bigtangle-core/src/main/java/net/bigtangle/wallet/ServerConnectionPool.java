/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.wallet;

import java.util.ArrayList;
import java.util.List;

/*
 * keep the potential list of servers and check the servers.
 * A List of server, which can provide block service
 * 1) check the server chain length 
 * 2) check the response speed of the server
 * 3) check the health of the server
 * 4) balance of the server select for random 
 * 
 * 
 */
public class ServerConnectionPool {

    private List<String> pool =new ArrayList<String>();

}
