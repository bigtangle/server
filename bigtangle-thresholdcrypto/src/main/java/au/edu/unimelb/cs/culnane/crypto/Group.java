/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.crypto;

import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;

import au.edu.unimelb.cs.culnane.storage.Storable;

/**
 * Interface for a cryptographic group
 * 
 * @author Chris Culnane
 *
 */
public interface Group extends Storable {

	

	/**
	 * Gets a random value in the subgroup Q
	 * 
	 * @param rand
	 *            SecureRandom to use for generating the value
	 * @return BigInteger containing random value in Q
	 */
	public BigInteger getRandomValueInQ(SecureRandom rand);

	/**
	 * Save the group to a file
	 * 
	 * @param filePath
	 *            String file path
	 * @throws IOException
	 *             thrown if an exception occurs during writing
	 */
	public void saveToFile(String filePath) throws IOException;

	/**
	 * Loads the group from file
	 * 
	 * @param filePath
	 *            String file path to load the group from
	 * @throws IOException
	 *             thrown if an exception occurs whilst reading from the file
	 */
	public void load(String filePath) throws IOException;

	/**
	 * Create a generator and returns it
	 * 
	 * @param rand
	 *            SecureRandom to use for generating a generator
	 * @return BigInteger containing the generator of the group
	 */
	public BigInteger createGenerator(SecureRandom rand);

	/**
	 * Gets Q of the group
	 * 
	 * @return BigInteger containing Q
	 */
	public BigInteger get_q();

	/**
	 * Gets P of the group
	 *
	 * @return BigInteger containing P
	 */
	public BigInteger get_p();

	/**
	 * Gets the Generator G of the group
	 * 
	 * @return BigInteger containing G
	 */
	public BigInteger get_g();

	/**
	 * Gets Q as a String
	 * 
	 * @return String containing a Q
	 */
	public String get_qString();

	/**
	 * Gets P as a String
	 * 
	 * @return String containing P
	 */
	public String get_pString();

	/**
	 * Get G as a String
	 * 
	 * @return String containing G
	 */
	public String get_gString();

	/**
	 * Checks that q divides p-1
	 * 
	 * @return true if it does, false if not
	 */
	public boolean verify_q_divides_p_minus_one();

	/**
	 * Checks that p is prime with the specified certainty
	 * 
	 * @param certainty
	 *            int certainty of primality check
	 * @return true if prime check passes, false if not
	 */
	public boolean verify_p_isPrime(int certainty);

	/**
	 * Checks that q is prime with the specified certainty
	 * 
	 * @param certainty
	 *            int certainty of primality check
	 * @return true if prime check passes, false if not
	 */
	public boolean verify_q_isPrime(int certainty);

	/**
	 * Verifies the order of g
	 * 
	 * @return true if g is of the correct order, false if not
	 */
	public boolean verify_g_order();

	/**
	 * Verifies that the specified element is of order of the group
	 * 
	 * @param element
	 *            BigInteger element to check
	 * @return true if it is of the group order, false if not
	 */
	public boolean verify_element_order(BigInteger element);

	/**
	 * Verifies that the specified element is of order q
	 * 
	 * @param element
	 *            BigInteger element to check
	 * @return true if element is of order q, false if not
	 */
	public boolean verify_element_order_q(BigInteger element);

}
