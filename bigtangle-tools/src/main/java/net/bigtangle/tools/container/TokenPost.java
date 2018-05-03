package net.bigtangle.tools.container;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.Utils;
import net.bigtangle.tools.account.Account;

public class TokenPost extends ArrayList<String> {

    private static final long serialVersionUID = -8299620590125212324L;
    
    private static final TokenPost instance = new TokenPost();
    
    public static TokenPost getInstance() {
        return instance;
    }
    
    public void initialize() throws Exception {
//        for (Iterator<Account> iterator = Container.getInstance().iterator(); iterator.hasNext(); ) {
//            Account account = iterator.next();
//            for (ECKey ecKey : account.walletKeys()) {
//                this.add(ecKey.getPublicKeyAsHex());
//            }
//        }
        for (Iterator<Account> iterator = Container.getInstance().iterator(); iterator.hasNext(); ) {
            Account account = iterator.next();
            ECKey outKey = account.getRandomECKey();
//            byte[] pubKey = outKey.getPubKey();
            this.add(Utils.HEX.encode(outKey.getPubKeyHash()));
        }
    }

    public String randomTokenHex() {
        Random random = new Random();
        int index = random.nextInt(this.size());
        return this.get(index);
//        return this.get(0);
    }
}
