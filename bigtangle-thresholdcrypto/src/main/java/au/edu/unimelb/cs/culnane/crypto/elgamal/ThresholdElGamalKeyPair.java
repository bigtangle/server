/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.crypto.elgamal;

import java.math.BigInteger;
import java.util.List;

import au.edu.unimelb.cs.culnane.storage.StorageObject;
import au.edu.unimelb.cs.culnane.storage.exceptions.StorageException;

/**
 * @author Chris Culnane
 *
 */
public class ThresholdElGamalKeyPair extends ElGamalKeyPair {

	private static final String VERIFY = "verify";
	List<BigInteger> verifyData = null;

	public List<BigInteger> getVerifyData() {
		return verifyData;
	}

	public void setVerifyData(List<BigInteger> verifyData) {
		this.verifyData = verifyData;
	}

	/**
	 * @param publicKey
	 * @param privateKey
	 */
	public ThresholdElGamalKeyPair(BigInteger publicKey, BigInteger privateKey) {
		super(publicKey, privateKey);
	}

	/**
	 * @param store
	 * @throws StorageException
	 */
	public ThresholdElGamalKeyPair(StorageObject<?> store) throws StorageException {
		super(store);
		if (store.has(VERIFY)) {
			this.verifyData = store.getBigIntList(VERIFY);
		}
	}

	/**
	 * 
	 */
	public ThresholdElGamalKeyPair() {
	}

}
