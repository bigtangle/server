package net.bigtangle.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.Test;

import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Utils;
import net.bigtangle.kits.WalletAppKit;

public class WalletPayMoneyTest extends AbstractIntegrationTest {

    @Test
    public void pay() throws Exception {
        importKeys(walletAppKit2.wallet());
        //importKeys(walletAppKit1.wallet());
        this.wallet1();
        this.wallet2();

        walletAppKit1.wallet().setServerURL(TESTSERVER1);
        walletAppKit2.wallet().setServerURL(TESTSERVER1);

        // checkBalance(Coin.valueOf(100, testPub), wallet1Keys.get(0));

        List<WalletAppKit> walletAppKits = new ArrayList<WalletAppKit>();
        for (int i = 10; i < 20; i++) {
            WalletAppKit walletAppKit = this.createAndInitWallet("bigtangle" + i);
            walletAppKit.wallet().setServerURL(TESTSERVER1);
            walletAppKits.add(walletAppKit);
        }

        HashMap<Integer, WalletBook> bookData = new HashMap<Integer, WalletBook>();
        for (WalletBook walletBook : payCoinbaseToWallet(walletAppKits)) {
            bookData.put(walletBook.getWalletID(), walletBook);
        }

        final int count = bookData.size();
        ExecutorService pool = Executors.newFixedThreadPool(count);
        for (Entry<Integer, WalletBook> entry : bookData.entrySet()) {
            pool.execute(new PayMoneyRunnable(entry.getValue()));
        }
        pool.shutdown();
    }

    public static class PayMoneyRunnable implements Runnable {

        public PayMoneyRunnable(WalletBook walletBook) {
            this.walletBook = walletBook;
        }

        private WalletBook walletBook;
        
        @Override
        public void run() {
            while (true) {
                log.info("--------------------- walletID : " + walletBook.getWalletID() + " ---------------------");
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                }
            }
        }
    }

    private List<WalletBook> payCoinbaseToWallet(List<WalletAppKit> walletAppKits) throws Exception {
        List<WalletBook> walletBooks = new ArrayList<WalletBook>();
        int i = 0;
        for (WalletAppKit walletAppKit : walletAppKits) {
            WalletBook walletBook = new WalletBook(i++, walletAppKit);

            for (ECKey fromkey : this.wallet1Keys) {
                ECKey ecKey = new ECKey();
                walletAppKit.wallet().importKey(ecKey);

                String tokenid = Utils.HEX.encode(fromkey.getPubKey());
             //   System.out.println(testPub);
             //   System.out.println(tokenid);

                walletAppKit1.wallet().pay(null, ecKey.toAddress(networkParameters), Coin.valueOf(2l ), "");
               /*  HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();
                giveMoneyResult.put(ecKey.toAddress(networkParameters).toBase58(), 10l);
                

                fromkey =  ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
                walletAppKit1.wallet().payMoneyToECKeyList(null, giveMoneyResult, fromkey);
                */
//                walletAppKit.wallet().

                WalletKeyBook walletKeyBook = new WalletKeyBook(ecKey, this.sumUTXOBalance(tokenid, ecKey));
                walletBook.addWalletKeyBook(walletKeyBook);
            }
        }
        return walletBooks;
    }
}
