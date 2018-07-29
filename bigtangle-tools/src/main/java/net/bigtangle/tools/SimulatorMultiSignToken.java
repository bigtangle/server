package net.bigtangle.tools;

import net.bigtangle.tools.container.AccountContainer;

public class SimulatorMultiSignToken {

    public static void main(String[] args) {
        AccountContainer.newInstance().startMultiSignToken(1, 5);
    }
}
