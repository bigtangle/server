/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.crypto.bls;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import au.edu.unimelb.cs.culnane.crypto.exceptions.BLSSignatureException;
import it.unisa.dia.gas.jpbc.Element;

/**
 * BLSSignature Class based on the Coasca BLSSignature - but with the
 * TVSKeyStore removed. This class allows the generationa and verification of
 * BLS signatures.
 * 
 * @author Chris Culnane
 *
 */
public class BLSSignature {

	/**
	 * Message Digest to use in the signature
	 */
	private MessageDigest md;

	/**
	 * BLSPrivateKey to use for signing
	 */
	private BLSPrivateKey privateKey;

	/**
	 * BLSPublicKey to use for verification
	 */
	private BLSPublicKey publicKey;

	/**
	 * BLSSystemParameters to use for generating and verifying the signature
	 */
	private BLSSystemParameters sysParams;

	/**
	 * Constructs a new BLSSignature object with the appropriate MessageDigest
	 * and BLSSystemParameters
	 * 
	 * @param messageDigest
	 *            MessageDigest to use in signatures
	 * @param sysParams
	 *            BLSSystemParameters to use in signature generation and
	 *            verification
	 * @throws NoSuchAlgorithmException
	 */
	public BLSSignature(String messageDigest, BLSSystemParameters sysParams) throws NoSuchAlgorithmException {
		md = MessageDigest.getInstance(messageDigest);
		this.sysParams = sysParams;

	}

	/**
	 * Initialise the signature object to signing - reset message digest and
	 * prepare for new data
	 * 
	 * @param privKey
	 *            BLSPrivateKey to use
	 */
	public void initSign(BLSPrivateKey privKey) {
		md.reset();
		this.privateKey = privKey;
	}

	/**
	 * Initialise the signature object for verification - reset the message
	 * digest and prepare for new data.
	 * 
	 * @param pubKey
	 *            BLSPublicKey for verification
	 */
	public void initVerify(BLSPublicKey pubKey) {
		this.publicKey = pubKey;
		md.reset();

	}

	/**
	 * Update the digest with additional string data, which will be converted to
	 * bytes
	 * 
	 * @param data
	 *            String of data to add - will be converted to bytes
	 */
	public void update(String data) {
		this.md.update(data.getBytes());
	}

	/**
	 * Updates the digest with additional byte data
	 * 
	 * @param data
	 *            byte array of data to add
	 */
	public void update(byte[] data) {
		this.md.update(data);
	}

	/**
	 * Signs the contents of the underlying message digest using the
	 * BLSPrivateKey. If no BLSPrivateKey has been set, i.e. initSign was not
	 * called previously, an exception will be thrown.
	 * 
	 * The actual signing algorithm is as follows:
	 * <ul>
	 * <li>Take the bytes from the MessageDigest and map them onto an element
	 * (h) on G1</li>
	 * <li>Compute h^x (where x = privateKey) to calculate the signature</li>
	 * </ul>
	 * 
	 * @return Element containing the signature
	 * @throws TVSSignatureException
	 */
	public Element sign() throws BLSSignatureException {
		if (this.privateKey == null) {
			// if the privatekey is null we cannot generate the signature
			throw new BLSSignatureException(
					"Incorrectly initialised BLSSignature object, cannot sign without a non-null BLSPrivateKey");
		}
		// Get the hash bytes
		byte[] hash = md.digest();

		// Map them onto an element in G1
		Element h = sysParams.getPairing().getG1().newElement().setFromHash(hash, 0, hash.length).getImmutable();

		// Calculate h^x, where x is the private key
		return h.powZn(this.privateKey.getKey());
	}

	/**
	 * Verifies the contents of the message digest with the specified signature
	 * bytes. This first converts the bytes into an Element and then verifies it
	 * as a standard signature. This defaults the partial parameter to false.
	 * This method calls the verify(Element sig, boolean partial) method to
	 * perform the actual verification. See @see
	 * {@link BLSSignature#verify(Element, boolean)} for full details.
	 * 
	 * @param signature
	 *            byte array of signature bytes
	 * @return boolean true if the signature is valid, false if invalid
	 * @throws TVSSignatureException
	 */
	public boolean verify(byte[] signature) throws BLSSignatureException {
		return verify(signature, false);
	}

	/**
	 * Verifies the contents of the message digest with the specified signature
	 * bytes. This first converts the bytes into an Element and then verifies it
	 * as a standard signature. This method calls the verify(Element sig,
	 * boolean partial) method to perform the actual verification. See @see
	 * {@link BLSSignature#verify(Element, boolean)} for full details.
	 * 
	 * @param signature
	 *            byte array of signature bytes
	 * @param partial
	 *            boolean true if verifying a threshold signature share, false
	 *            if verifying a standard signature or joint signature
	 * @return boolean true if the signature is valid, false if invalid
	 * @throws TVSSignatureException
	 */
	public boolean verify(byte[] signature, boolean partial) throws BLSSignatureException {
		return verify(getSignatureElement(signature), partial);

	}

	/**
	 * Utility method for converting a byte array containing the byte contents
	 * of the signature Element, back into an Element of G1. This is useful when
	 * saving and loading signatures from files, where they are likely to have
	 * been stored as pure bytes.
	 * 
	 * @param signature
	 *            byte array containing a signature element
	 * @return Element on G1 of the byte array
	 */
	public Element getSignatureElement(byte[] signature) {
		Element sig = sysParams.getPairing().getG1().newElement();
		sig.setFromBytes(signature);
		return sig;
	}

	/**
	 * Performs the actual signature verification using the contents of the
	 * message digest that holds the data submitted via the update methods. If
	 * no PublicKey has been set an exception will be thrown. This method can
	 * handle both normal BLS and threhsold BLS. If the partial parameter is set
	 * to true the verification will get the BLSPartialPublicKey instead of the
	 * full public key from the specified BLSPublicKey object.
	 * 
	 * The actual verification is as follows:
	 * <ul>
	 * <li>Take the bytes from the MessageDigest and map them onto an element
	 * (h) on G1</li>
	 * <li>Create a pairing between the signature element and the generator g
	 * </li>
	 * <li>Create a second pairing between the hash element h and the public key
	 * element</li>
	 * <li>Check the pairings are equal, if they are the signature is valid,
	 * else it is invalid</li>
	 * </ul>
	 * Note: the generator g is a system parameter, it is out of convenience we
	 * choose to store it in the public key object.
	 * 
	 * The actual verification is checking that e(signature, g) = e(h, g^x),
	 * where is e represents a pairing.
	 * 
	 * @param signature
	 *            Element containing the signature
	 * @param partial
	 *            boolean set to true if verifying a partial signature, false if
	 *            a standard signature
	 * @return boolean true if valid, false if invalid
	 * @throws TVSSignatureException
	 */
	public boolean verify(Element signature, boolean partial) throws BLSSignatureException {
		if (this.publicKey == null) {
			throw new BLSSignatureException(
					"Incorrectly initialised BLSSignature object, cannot verify without a non-null BLSPublicKey");
		}
		// get either the full public key or the partial public key
		Element pubKey = null;
		if (partial) {
			pubKey = this.publicKey.getPartialPublicKey();
		} else {
			pubKey = this.publicKey.getPublicKeyElement();
		}

		// Check we actually have a valid key
		if (pubKey == null) {
			throw new BLSSignatureException(
					"Incorrectly initialised BLSSignature object, the specified BLSPublicKey does not contain a valid key.");
		}

		// Construct the hash bytes
		byte[] hash = md.digest();

		// Map the hash onto an element in G1
		Element h = sysParams.getPairing().getG1().newElement().setFromHash(hash, 0, hash.length).getImmutable();

		// Create the signature pairing
		Element sigPairing = sysParams.getPairing().pairing(signature, sysParams.getGenerator());

		// Create the hash pairing
		Element hashPairing = sysParams.getPairing().pairing(h, pubKey);

		// If the pairing are equal the signature is valid
		return sigPairing.isEqual(hashPairing);

	}
}
