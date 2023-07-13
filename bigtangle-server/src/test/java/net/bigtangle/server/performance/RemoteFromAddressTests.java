/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.performance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.Utils;
import net.bigtangle.params.TestParams;
import net.bigtangle.server.test.FromAddressTests;
import net.bigtangle.wallet.Wallet;

public class RemoteFromAddressTests extends FromAddressTests {
	@BeforeEach
	public void setUp() throws Exception {
		contextRoot = "https://bigtangle.de:18088/";
		wallet = Wallet.fromKeys(TestParams.get(), ECKey.fromPrivate(Utils.HEX.decode(testPriv)), contextRoot);

	}
	@AfterEach
	public void close() throws Exception {
		 
	}
}
