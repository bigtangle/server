package net.bigtangle.tools;

import org.junit.runner.JUnitCore;

import net.bigtangle.tools.test.MoneyForOrderBuyTest;
import net.bigtangle.tools.test.OrderBuyTest;
import net.bigtangle.tools.test.OrderSellTest;

public class Simulator {

    public static void main(String[] args) {
     //   System.setProperty("https.proxyHost", "anwproxy.anwendungen.localnet.de");
     //   System.setProperty("https.proxyPort", "3128");

        new Thread(new Runnable() {
            public void run() {
                JUnitCore junit = new JUnitCore();
                // Result result =
                junit.run(OrderSellTest.class);

            }
        }).start();

        
        new Thread(new Runnable() {
            public void run() {
                JUnitCore junit = new JUnitCore();
                // Result result =
                junit.run(MoneyForOrderBuyTest.class);

            }
        }).start();
      
        new Thread(new Runnable() {
            public void run() {
                JUnitCore junit = new JUnitCore();
                // Result result =
                junit.run(OrderBuyTest.class);

            }
        }).start();
        
        new Thread(new Runnable() {
            public void run() {
                JUnitCore junit = new JUnitCore();
                // Result result =
                junit.run(OrderSellTest.class);

            }
        }).start();
    }
}
