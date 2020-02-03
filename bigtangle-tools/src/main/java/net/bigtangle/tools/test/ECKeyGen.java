package net.bigtangle.tools.test;

import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.Token;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.response.GetBalancesResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.OkHttp3Util;

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
