/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.wallet;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bigtangle.core.ECKey;
import net.bigtangle.kits.WalletUtil;
import net.bigtangle.params.MainNetParams;

public class WalletUtilTest {

	private static final Logger log = LoggerFactory.getLogger(WalletUtilTest.class);

	@Test
	public void walletCreateLoadTest() throws Exception {

		byte[] a = WalletUtil.createWallet(MainNetParams.get());
		Wallet wallet = WalletUtil.loadWallet(false, new ByteArrayInputStream(a), MainNetParams.get());

		List<ECKey> issuedKeys = wallet.walletKeys(null);
		assertTrue(issuedKeys.size() > 0);
		for (ECKey ecKey : issuedKeys) {
			log.debug(ecKey.getPublicKeyAsHex());
			log.debug(ecKey.getPrivateKeyAsHex());
			log.debug(ecKey.toAddress(MainNetParams.get()).toString());
		}

	}

}
