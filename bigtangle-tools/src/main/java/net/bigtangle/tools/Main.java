package net.bigtangle.tools;

import net.bigtangle.tools.container.Container;

public class Main {

    public static void main(String[] args) {
        Container container = Container.getInstance();
        container.run();
    }
}
