package net.bigtangle.tools;

import net.bigtangle.tools.container.Container;

public class GiveMoneyTest {

    public static void main(String[] args) throws Exception {
        Container container = Container.getInstance();
        container.startGiveMoney();
    }
}
