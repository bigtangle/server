package net.bigtangle.tools.test;

import org.junit.Test;

import net.bigtangle.core.ECKey;

public class ECKeyGen {

    @Test
    public void ECKey() {
        ECKey key= new ECKey();
        System.out.print(" private key \n ");
        System.out.print(key.getPrivateKeyAsHex());
        System.out.print("\n public key \n ");
        System.out.print(key.getPublicKeyAsHex());
    }
    

}
