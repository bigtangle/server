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
import java.security.SecureRandom;

import au.edu.unimelb.cs.culnane.crypto.Group;

/**
 * Pedersen Commitment class that is used to construct and verify a pedersen
 * commitment.
 * 
 * @author Chris Culnane
 *
 */
public class PedersenCommitment {

	/**
	 * Group we will use for operations
	 */
	private Group group;

	/**
	 * BigInteger v value containing the generator to use
	 */
	private BigInteger v;

	/**
	 * Constructs a PedersenCommitment with the specified group and constructs a
	 * new generator to use
	 * 
	 * @param group
	 *            Group to use for operations
	 * @param rand
	 *            SecureRandom to use in generator construction
	 */
	public PedersenCommitment(Group group, SecureRandom rand) {
		this.group = group;
		init(rand);
	}

	/**
	 * Constructs a PedersenCommitment with the specified generator
	 * 
	 * @param group
	 *            Group to use for operations
	 * @param v
	 *            BigInteger containing the generator to use
	 */
	public PedersenCommitment(Group group, BigInteger v) {
		this.group = group;
		this.v = v;
	}

	/**
	 * Constructs a PedersenCommitment with the specified generator
	 * 
	 * @param group
	 *            Group to use for operations
	 * @param vString
	 *            String containing hex encoding of generator
	 */
	public PedersenCommitment(Group group, String vString) {
		this.group = group;
		this.v = new BigInteger(vString, 16);
	}

	/**
	 * Initialises this pedersen commitment by randomly generating a generator
	 * from the group
	 * 
	 * @param rand
	 *            SecureRandom to use when creating a generator
	 */
	private void init(SecureRandom rand) {
		v = group.createGenerator(rand);
	}

	/**
	 * Gets V (the generator) as a BigInteger
	 * 
	 * @return BigInteger containing v
	 */
	public BigInteger getV() {
		return v;
	}

	/**
	 * Gets V as a hex encoded string
	 * 
	 * @return String containing a hex encoded copy of v
	 */
	public String getVasString() {
		return v.toString(16);
	}

	/**
	 * Commit to value m and return a PedersenCommit object containing the
	 * commitment
	 * 
	 * @param m
	 *            BigInteger value to commit to
	 * @return PedersenCommit containing the commitment
	 */
	public PedersenCommit commit(BigInteger m) {
		BigInteger randVal = group.getRandomValueInQ(new SecureRandom());
		BigInteger g_to_r = group.get_g().modPow(randVal, group.get_p());
		BigInteger v_to_m = v.modPow(m, group.get_p());
		return new PedersenCommit(randVal, g_to_r.multiply(v_to_m).mod(group.get_p()), m);
	}

	/**
	 * Verifies a Pedersen Commitment based on the hex encoded string values
	 * passed in
	 * 
	 * @param commit
	 *            String containing hex encoded commitment value
	 * @param r
	 *            String containing hex encoded randomness
	 * @param m
	 *            String containing hex encoded message
	 * @return boolean, true if value, false if not
	 */
	public boolean verify(String commit, String r, String m) {
		return verify(new BigInteger(commit, 16), new BigInteger(r, 16), new BigInteger(m, 16));
	}

	/**
	 * Verifies a Pedersen Commitment based on the PedersenCommit object passed
	 * in
	 * 
	 * @param commit
	 *            PedersenCommit object to verify
	 * @return true if valid, false if not
	 */
	public boolean verify(PedersenCommit commit) {
		return verify(commit.getCommit(), commit.getRandomness(), commit.getM());
	}

	/**
	 * Verifies a Pedersen Commitment based on the BigInteger values passed in
	 * 
	 * @param commit
	 *            BigInteger containing commitment value
	 * @param r
	 *            BigInteger containing randomness
	 * @param m
	 *            BigInteger containing message
	 * @return boolean, true if value, false if not
	 */
	public boolean verify(BigInteger commit, BigInteger r, BigInteger m) {
		BigInteger g_to_r = group.get_g().modPow(r, group.get_p());
		BigInteger v_to_m = v.modPow(m, group.get_p());
		return g_to_r.multiply(v_to_m).mod(group.get_p()).equals(commit);
	}

}
