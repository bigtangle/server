/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.apps.lottery;

import java.math.BigInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Utils;
import net.bigtangle.params.MainNetParams;
import net.bigtangle.params.TestParams;

public class StartLottery {

    private static final Logger log = LoggerFactory.getLogger(Lottery.class);
    public static String CNYTOKENIDPROD = "03bed6e75294e48556d8bb2a53caf6f940b70df95760ee4c9772681bbf90df85ba";

    // with context_root= https://p.bigtangle.org:8088/
    public static void main(String[] args) throws Exception {
 
        NetworkParameters params = MainNetParams.get();
        String tokenid=CNYTOKENIDPROD;
        Lottery startLottery = new Lottery();
        if (args[0].equals("test")) {
            startLottery.setContextRoot(  "https://test.bigtangle.info:8089/");
            params = TestParams.get();
            tokenid="02fbef0f3e1344f548abb7d4b6a799e372d2310ff13fe023b3ba0446f2e3f58e04";
        } else {
            startLottery.setContextRoot( "https://p.bigtangle.org:8088/");
            params = MainNetParams.get(); 
        }
        ;
        startLottery.setAccountKey(ECKey.fromPrivate(Utils.HEX.decode(args[1])));
        startLottery.setParams(params);
        startLottery.setTokenid(tokenid);
        startLottery.setWinnerAmount(new BigInteger(args[2]));

        while (true) {
            startLottery.start();
            Thread.sleep(15000);
        }

    }

}
