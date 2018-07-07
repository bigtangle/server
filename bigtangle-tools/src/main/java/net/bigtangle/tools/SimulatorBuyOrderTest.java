package net.bigtangle.tools;

import net.bigtangle.tools.container.Container;

public class SimulatorBuyOrderTest {

    public static void main(String[] args) {
        Container container = Container.getInstance();
        container.startBuyOrder(1, 1);
    }
}
