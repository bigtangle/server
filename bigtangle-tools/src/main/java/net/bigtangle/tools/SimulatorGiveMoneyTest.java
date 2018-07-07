package net.bigtangle.tools;

import net.bigtangle.tools.container.Container;

public class SimulatorGiveMoneyTest {

    public static void main(String[] args) throws Exception {
        Container container = Container.getInstance();
        container.startGiveMoney(1, 5);
    }
}
