/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.protocols.pedersencommitment;

import java.math.BigInteger;

import au.edu.unimelb.cs.culnane.storage.Storable;
import au.edu.unimelb.cs.culnane.storage.StorageObject;
import au.edu.unimelb.cs.culnane.storage.exceptions.StorageException;

/**
 * Contains the information related to a Pedersen Commitment
 * 
 * @author Chris Culnane
 *
 */
public class PedersenCommit implements Storable {

	/**
	 * BigInteger containing the randomness used to blind the message
	 */
	private BigInteger r;
	/**
	 * BigInteger containing the commitment
	 */
	private BigInteger c;

	/**
	 * BigInteger containing the message we are committing to
	 */
	private BigInteger m;

	/**
	 * Static string containing the storage key for the message
	 */
	private static final String M_KEY = "m";
	/**
	 * Static string containing the storage key for the commitment
	 */
	private static final String C_KEY = "c";
	/**
	 * Static string containing the storage key for the randomness used
	 */
	private static final String R_KEY = "r";

	/**
	 * Creates a new Pedersen Commitment object from the specified BigInteger
	 * values
	 * 
	 * @param r
	 *            BigInteger randomness used for blinding
	 * @param c
	 *            BigInteger containing the commitment
	 * @param m
	 *            BigInteger containing the message committed to
	 */
	public PedersenCommit(BigInteger r, BigInteger c, BigInteger m) {
		this.r = r;
		this.c = c;
		this.m = m;
	}

	/**
	 * Creates a new Pedersen Commitment object from the specified string values
	 * that contain hex encoded BigInteger values
	 * 
	 * @param r
	 *            String of hex containing randomness used for blinding
	 * @param c
	 *            String of hex containing containing the commitment
	 * @param m
	 *            String of hex containing containing the message committed to
	 */
	public PedersenCommit(String rString, String cString, String mString) {
		this.r = new BigInteger(rString, 16);
		this.c = new BigInteger(cString, 16);
		this.m = new BigInteger(mString, 16);
	}

	/**
	 * Constructs a new Pedersen Commitment object from a StorageObject
	 * 
	 * @param store
	 *            StorageObject containing a Pedersen Commitment
	 * @throws StorageException
	 *             - if the StorageObject does not contain the necessary fields
	 */
	public PedersenCommit(StorageObject<?> store) throws StorageException {
		this.r = store.getBigInteger(R_KEY);
		this.c = store.getBigInteger(C_KEY);
		this.m = store.getBigInteger(M_KEY);
	}

	/**
	 * Gets the BigInteger commitment
	 * 
	 * @return BigIntegr containing the commitment
	 */
	public BigInteger getCommit() {
		return this.c;
	}

	/**
	 * Gets the randomness used in the commitment
	 * 
	 * @return BigInteger containing the randomness used in the commitment
	 */
	public BigInteger getRandomness() {
		return this.r;
	}

	/**
	 * Gets the message we committed to
	 * 
	 * @return BigInteger containing the message committed to
	 */
	public BigInteger getM() {
		return this.m;
	}

	/**
	 * Gets the message we committed to as a hex string
	 * 
	 * @return String containing a hex encoding of the BigInteger message
	 */
	public String getMString() {
		return this.m.toString(16);
	}

	/**
	 * Gets the commitment as a hex string
	 * 
	 * @return String containing a hex encoding of the BigInteger commitment
	 *         value
	 */
	public String getCommitString() {
		return this.c.toString(16);
	}

	/**
	 * Gets the randomness used in the commitment as a hex string
	 * 
	 * @return String with a hex encoding of the BigInteger random value
	 */
	public String getRandomnessString() {
		return this.r.toString(16);
	}

	@Override
	public StorageObject<?> storeInStorageObject(StorageObject<?> store) {
		store.set(M_KEY, getM());
		store.set(R_KEY, getRandomness());
		store.set(C_KEY, getCommit());
		return store;
	}
}
