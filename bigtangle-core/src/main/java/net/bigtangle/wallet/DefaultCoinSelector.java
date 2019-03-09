/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.bigtangle.wallet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.bigtangle.core.Coin;
import net.bigtangle.core.TransactionOutput;

/**
 * This class implements a {@link CoinSelector} which attempts to get the
 * highest priority possible. This means that the transaction is the most likely
 * to get confirmed. Note that this means we may end up "spending" more priority
 * than would be required to get the transaction we are creating confirmed.
 */
public class DefaultCoinSelector implements CoinSelector {
    @Override
    public CoinSelection select(Coin target, List<TransactionOutput> candidates) {
        ArrayList<TransactionOutput> selected = new ArrayList<TransactionOutput>();
        // Sort the inputs by age*value so we get the highest "coindays" spent.
        // TODO: Consider changing the wallets internal format to track just
        // outputs and keep them ordered.
        ArrayList<TransactionOutput> sortedOutputs = new ArrayList<TransactionOutput>(candidates);
        // When calculating the wallet balance, we may be asked to select all
        // possible coins, if so, avoid sorting
        // them in order to improve performance.
        // TODO: Take in network parameters when instanatiated, and then test
        // against the current network. Or just have a boolean parameter for
        // "give me everything"
      //  if (!target.equals(NetworkParameters.MAX_MONEY)) {
         //   sortOutputs(sortedOutputs);
      //  }
        // Now iterate over the sorted outputs until we have got as close to the
        // target as possible or a little
        // bit over (excessive value will be change).
        long total = 0;
        for (TransactionOutput output : sortedOutputs) {
            if (total >= target.getValue())
                break;
            // Only pick chain-included transactions, or transactions that are
            // ours and pending.
             
            if (Arrays.equals(target.getTokenid(), output.getValue().getTokenid())) {
                selected.add(output);
                total += output.getValue().getValue();
            }
        }
        // Total may be lower than target here, if the given candidates were
        // insufficient to create to requested
        // transaction.
        return new CoinSelection(Coin.valueOf(total, target.getTokenid()), selected);
    }
 

 

   
}
