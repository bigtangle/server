/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.wallet;

import java.util.List;

import net.bigtangle.core.Transaction;

/**
 * <p>A RiskAnalysis represents an analysis of how likely it is that a transaction (and its dependencies) represents a
 * possible double spending attack. The wallet will create these to decide whether or not to accept a pending
 * transaction. Look at {@link DefaultRiskAnalysis} to see what is currently considered risky.</p>
 *
 * <p>The intention here is that implementing classes can expose more information and detail about the result, for
 * app developers. The core code needs only to know whether it's OK or not.</p>
 *
 * <p>A factory interface is provided. The wallet will use this to analyze new pending transactions.</p>
 */
public interface RiskAnalysis {
    enum Result {
        OK,
        NON_FINAL,
        NON_STANDARD
    }

    Result analyze();

    interface Analyzer {
        RiskAnalysis create(Wallet wallet, Transaction tx, List<Transaction> dependencies);
    }
}
