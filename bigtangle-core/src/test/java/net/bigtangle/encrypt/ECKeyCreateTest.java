package net.bigtangle.encrypt;

import org.junit.Test;

import net.bigtangle.core.ECKey;
import net.bigtangle.params.TestParams;

public class ECKeyCreateTest {

	@Test
	public void newECKeyDecrypt() {
		for (int i = 0; i < 10; i++) {
			ECKey ecKey = new ECKey();
			System.out.println("  \n private  "+ ecKey.getPrivateKeyString().length() +"=" + ecKey.getPrivateKeyString());
			System.out.println(" \n public= " + ecKey.getPublicKeyAsHex().length() +"=" + ecKey.getPublicKeyAsHex());
			 System.out.println("  \n  public address= " + ecKey.toAddress(TestParams.get()));
		}

	}
}
