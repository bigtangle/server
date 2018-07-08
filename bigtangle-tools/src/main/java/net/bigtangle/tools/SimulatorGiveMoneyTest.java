package net.bigtangle.tools;

import net.bigtangle.tools.container.AccountContainer;

public class SimulatorGiveMoneyTest {

    public static void main(String[] args) throws Exception {
        AccountContainer container = AccountContainer.newInstance();
        container.startGiveMoney(1, 5);
    }
}
