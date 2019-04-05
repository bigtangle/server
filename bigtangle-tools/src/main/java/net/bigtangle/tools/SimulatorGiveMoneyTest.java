package net.bigtangle.tools;

import net.bigtangle.tools.container.AccountContainer;

public class SimulatorGiveMoneyTest {

    public static void main(String[] args) throws Exception {
        System.setProperty("https.proxyHost", "anwproxy.anwendungen.localnet.de");
        System.setProperty("https.proxyPort", "3128");
        AccountContainer container = AccountContainer.newInstance();
        container.startGiveMoney(1, 5);
    }
}
