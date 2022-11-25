/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.server.model;

import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.SpentBlock;
import net.bigtangle.core.UTXO;

/*
  * Block output dynamic evaluation data
  */
public class SpentBlockModel {
    private String blockhash;
    // confirmed=true, MCMC rating > 75 or block is on a milestone
    private boolean confirmed;
    private boolean spent;
    private String spenderblockhash;
    // create time of the block output
    private long time;

    public void toSpentBlock(SpentBlock s) {
        s.setSpent(isSpent());
        s.setConfirmed(isConfirmed());
        s.setBlockHash(Sha256Hash.wrap(getBlockhash()));
        s.setTime(getTime());
        s.setSpenderBlockHash(Sha256Hash.wrap(getSpenderblockhash()));

    }

    public void fromSpentBlock(SpentBlock s) {
        setSpent(s.isSpent());
        setConfirmed(s.isConfirmed());
        setBlockhash(s.getBlockHash().toString());
        setTime(s.getTime());
        setSpenderblockhash(s.getSpenderBlockHash().toString());

    }

    public String getBlockhash() {
        return blockhash;
    }

    public void setBlockhash(String blockhash) {
        this.blockhash = blockhash;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }

    public boolean isSpent() {
        return spent;
    }

    public void setSpent(boolean spent) {
        this.spent = spent;
    }

    public String getSpenderblockhash() {
        return spenderblockhash;
    }

    public void setSpenderblockhash(String spenderblockhash) {
        this.spenderblockhash = spenderblockhash;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

}
