/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.protocols.threshold.pet;

import java.io.IOException;
import java.math.BigInteger;

import au.edu.unimelb.cs.culnane.crypto.P_Length;
import au.edu.unimelb.cs.culnane.crypto.ZpStarSafePrimeGroupRFC3526;
import au.edu.unimelb.cs.culnane.crypto.elgamal.ElGamal;
import au.edu.unimelb.cs.culnane.crypto.elgamal.ElGamalCipher;
import au.edu.unimelb.cs.culnane.crypto.elgamal.ElGamalKeyPair;
import au.edu.unimelb.cs.culnane.crypto.exceptions.CryptoException;
import au.edu.unimelb.cs.culnane.crypto.exceptions.GroupException;
import au.edu.unimelb.cs.culnane.storage.json.JSONStorageObject;

/**
 * Class to create two encryptions with the same public key, to test the
 * plaintext equivalence test.
 * 
 * 
 * @author Chris Culnane
 *
 */
public class CreateMatchingEncryptions {

	public static void main(String[] args) throws IOException, GroupException, CryptoException {
		JSONStorageObject jso = new JSONStorageObject();
		ElGamalKeyPair kp = new ElGamalKeyPair(jso.readFromFile(args[0]));
		BigInteger m = BigInteger.valueOf(12345678);
		ZpStarSafePrimeGroupRFC3526 group = new ZpStarSafePrimeGroupRFC3526();
		group.initialise(P_Length.P2046);

		// Create first cipher
		ElGamalCipher cipher = ElGamal.encrypt(group, kp, m);
		cipher.storeInStorageObject(jso.getNewStorageObject()).writeToFile("./testcipher.json");

		// Create second cipher
		cipher = ElGamal.encrypt(group, kp, m);
		cipher.storeInStorageObject(jso.getNewStorageObject()).writeToFile("./testcipher2.json");
	}

}
