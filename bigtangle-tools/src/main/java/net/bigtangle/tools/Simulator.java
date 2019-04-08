package net.bigtangle.tools;

import net.bigtangle.tools.container.AccountContainer;

public class Simulator {

    public static void main(String[] args) {
        System.setProperty("https.proxyHost", "anwproxy.anwendungen.localnet.de");
        System.setProperty("https.proxyPort", "3128");
        
        //AccountContainer.newInstance().startBuyOrder(1, 1);
        //AccountContainer.newInstance().startSellOrder(2, 5);
        AccountContainer.newInstance().startTradeOrder(1, 5);
    }
}
