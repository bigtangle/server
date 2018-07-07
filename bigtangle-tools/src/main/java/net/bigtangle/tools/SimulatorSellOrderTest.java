package net.bigtangle.tools;

import net.bigtangle.tools.container.Container;

public class SimulatorSellOrderTest {

    public static void main(String[] args) {
        Container container = Container.getInstance();
        container.startSellOrder(2, 5);
    }
}
