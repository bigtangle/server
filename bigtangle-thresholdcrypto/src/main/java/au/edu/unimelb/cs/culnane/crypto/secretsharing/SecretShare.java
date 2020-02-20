/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.crypto.secretsharing;

import java.math.BigInteger;

import au.edu.unimelb.cs.culnane.storage.Storable;
import au.edu.unimelb.cs.culnane.storage.StorageObject;
import au.edu.unimelb.cs.culnane.storage.exceptions.StorageException;

/**
 * Class that holds the contents of a SecretShare and provides functions to
 * manipulate the underlying share. Also provides serialization to JSON and back
 * to allow it to be easily shared along a communication channel.
 * 
 * @author Chris Culnane
 *
 */
public class SecretShare implements Storable {
	/**
	 * Key for Storage to hold sequenceNo (peer index)
	 */
	public static final String SEQUENCE_NO = "sequenceNo";
	/**
	 * Key for Storage to hold share value
	 */
	public static final String SHARE = "share";

	/**
	 * int sequence number
	 */
	private int sequenceNo;
	/**
	 * BigInteger share
	 */
	private BigInteger share;

	/**
	 * Create new SecretShare from a BigInteger and integer
	 * 
	 * @param sequenceNo
	 *            integer sequence number
	 * @param share
	 *            BigInteger containing share
	 */
	public SecretShare(int sequenceNo, BigInteger share) {
		this.sequenceNo = sequenceNo;
		this.share = share;
	}

	/**
	 * Create a SecretShare from a JSONObject containing a serialized share.
	 * 
	 * @param shareObj
	 *            JSONObject containing SecretShare
	 * @throws StorageException
	 */
	public SecretShare(StorageObject<?> shareObj) throws StorageException {
		this.sequenceNo = shareObj.getInt(SEQUENCE_NO);
		this.share = shareObj.getBigInteger(SHARE);
	}

	/**
	 * Adds a value to the share - useful when combining multiple shares.
	 * 
	 * @param value
	 *            BigInteger value to add
	 * @param order
	 *            BigInteger order of sharing
	 */
	public void add(BigInteger value, BigInteger order) {
		this.share = this.share.add(value).mod(order);
	}

	/**
	 * Gets the sequence number of this share
	 * 
	 * @return integer sequence number
	 */
	public int getSequenceNo() {
		return this.sequenceNo;
	}

	/**
	 * Gets the BigInteger containing the share
	 * 
	 * @return BigInteger containing secret share
	 */
	public BigInteger getShare() {
		return this.share;
	}

	/**
	 * Converts the share to a JSON encoded String
	 */
	public String toString() {
		return this.share + ", seq:" + this.sequenceNo;
	}

	@Override
	public StorageObject<?> storeInStorageObject(StorageObject<?> store) {
		store.set(SEQUENCE_NO, this.sequenceNo);
		store.set(SHARE, share);
		return store;

	}
}
