package net.bigtangle.tools;

import net.bigtangle.tools.container.AccountContainer;

public class SimulatorSellOrderTest {

    public static void main(String[] args) {
        AccountContainer container = AccountContainer.newInstance();
        container.startSellOrder(2, 5);
    }
}
