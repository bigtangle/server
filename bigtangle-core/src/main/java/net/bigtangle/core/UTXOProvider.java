/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
/*
 * Copyright 2014 Kalpesh Parmar.
 *
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
package net.bigtangle.core;

import java.util.List;

import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.UTXOProviderException;

/**
 * A UTXOProvider encapsulates functionality for returning unspent transaction outputs,
 * for use by the wallet or other code that crafts spends.
 *
 * <p>A {@link org.bitcoinj.store.FullPrunedBlockStore} is an internal implementation within bitcoinj.</p>
 */
public interface UTXOProvider {

    
    
    List<UTXO> getOpenTransactionOutputs(String address) throws UTXOProviderException;

    List<UTXO> getOpenTransactionOutputs(List<Address> addresses) throws UTXOProviderException;
    
    List<UTXO> getOpenTransactionOutputs(List<Address> addresses, byte[] tokenid) throws UTXOProviderException;
  

    List<UTXO> getOpenAllOutputs(String tokenid) throws UTXOProviderException;

    boolean getOutputConfirmation(Sha256Hash blockHash, Sha256Hash hash, long index) throws BlockStoreException;
}
