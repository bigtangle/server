package net.bigtangle.tools;

import net.bigtangle.tools.test.MoneyForOrderBuyTest;
import net.bigtangle.tools.test.OrderBuyTest;
import net.bigtangle.tools.test.OrderSellTest;

public class Simulator {

    public static void main(String[] args) {
     //   System.setProperty("https.proxyHost", "anwproxy.anwendungen.localnet.de");
     //   System.setProperty("https.proxyPort", "3128");

        new Thread(new Runnable() {
            public void run() {
                try {
                    ( new OrderSellTest()).sellThread();
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } ;

            }
        }).start();

        
        new Thread(new Runnable() {
            public void run() {
                try {
                    ( new MoneyForOrderBuyTest()).payMoney();
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

            }
        }).start();
      
        new Thread(new Runnable() {
            public void run() {
              try {
                (new OrderBuyTest()).buy();
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            };

            }
        }).start();
        
 
    }
}
