package net.bigtangle.tools;

import net.bigtangle.tools.container.AccountContainer;

public class SimulatorBuyOrderTest {

    public static void main(String[] args) {
        AccountContainer container = AccountContainer.newInstance();
        container.startBuyOrder(1, 1);
    }
}
