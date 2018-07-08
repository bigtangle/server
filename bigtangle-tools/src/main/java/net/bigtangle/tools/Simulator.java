package net.bigtangle.tools;

import net.bigtangle.tools.container.AccountContainer;

public class Simulator {

    public static void main(String[] args) {
        AccountContainer.newInstance().startBuyOrder(1, 1);
        AccountContainer.newInstance().startSellOrder(2, 5);
    }
}
