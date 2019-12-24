package net.bigtangle.tools.test;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.Utils;

public class ECKeyGen {

    @Test
    public void ECKey() {
        ECKey key= new ECKey();
        System.out.print("privat key \n ");
        System.out.print(key.getPrivateKeyAsHex());
        System.out.print("\n public key \n ");
        System.out.print(key.getPublicKeyAsHex());
    }
    

}
