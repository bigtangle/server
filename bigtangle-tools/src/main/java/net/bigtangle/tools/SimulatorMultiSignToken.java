package net.bigtangle.tools;

import net.bigtangle.tools.container.AccountContainer;

public class SimulatorMultiSignToken {

    public static void main(String[] args) {
        System.setProperty("https.proxyHost", "anwproxy.anwendungen.localnet.de");
        System.setProperty("https.proxyPort", "3128");
        AccountContainer.newInstance().startMultiSignToken(1, 5);
    }
}
