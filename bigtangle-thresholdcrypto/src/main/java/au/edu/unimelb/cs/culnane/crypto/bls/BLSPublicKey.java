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

import au.edu.unimelb.cs.culnane.storage.Storable;
import au.edu.unimelb.cs.culnane.storage.StorageObject;
import au.edu.unimelb.cs.culnane.storage.exceptions.StorageException;
import au.edu.unimelb.cs.culnane.utils.IOUtils;
import au.edu.unimelb.cs.culnane.utils.IOUtils.EncodingType;
import it.unisa.dia.gas.jpbc.Element;
import it.unisa.dia.gas.jpbc.Pairing;

/**
 * BLSPublicKey object that holds a JPBC Element of the private key. Includes
 * methods for loading and saving key to a file. Based on Coasca BLSPublicKey
 * 
 * @author Chris Culnane
 *
 */
public class BLSPublicKey implements Storable {

	/**
	 * Constant String of the JSON field name for a partialPublicKey
	 */
	private static final String PARTIAL_PUBLIC_KEY = "partialPublicKey";
	/**
	 * Constant String of the JSON field name for a full (joint in the threshold
	 * case) public key
	 */
	private static final String PUBLIC_KEY = "publicKey";

	/**
	 * Constant String of the JSON field name for the sequence number associated
	 * with a key share
	 */
	private static final String SEQUENCE_NO = "sequenceNo";

	/**
	 * JPBC Element containing the public key
	 */
	private Element publicKey;

	/**
	 * Element to store partial public key in
	 */
	private Element partialPublicKey = null;

	/**
	 * If this is a threshold key there will also be a sequence number (used for
	 * lagrange interpolation)
	 */
	private int sequenceNo = -1;

	/**
	 * Construct a BLSPublicKey from just the PublicKey element. Strictly
	 * speaking this should not be used, since the generator will be needed at a
	 * later point. However, the generator is fixed for all keys generated in a
	 * threshold group, so may not be set in the public key file. This
	 * constructor is to provide flexibility, but should only be used if the
	 * generator G is being stored somewhere else.
	 * 
	 * @param publicKey
	 *            Element containing the public key
	 */
	public BLSPublicKey(Element publicKey) {
		this.publicKey = publicKey;

	}

	/**
	 * Constructs a threshold BLSPublicKey from a public key Element, Generator
	 * Element g and a partial public key Element. This constructor should only
	 * be used in circumstances where the sequence number is stored elsewhere or
	 * can be implied from some other information.
	 * 
	 * @param publicKey
	 *            Element containing the joint public key
	 * @param partialPublicKey
	 *            Element with the partial public key for the corresponding
	 *            share of the secret key
	 */
	public BLSPublicKey(Element publicKey, Element partialPublicKey) {
		this.partialPublicKey = partialPublicKey;
		this.publicKey = publicKey;
	}

	/**
	 * Constructs a complete threshold public key, consisting of a Joint public
	 * key element, generator element, partial public key element and sequence
	 * number. The information contained within this public key is sufficient to
	 * both verify an individual signature share and provide the necessary
	 * information to combine the signature share into a joint signature.
	 * 
	 * @param publicKey
	 *            Element containing the joint public key
	 * @param partialPublicKey
	 *            Element with the partial public key for the corresponding
	 *            share of the secret key
	 * @param sequenceNo
	 *            integer sequence number
	 */
	public BLSPublicKey(Element publicKey, Element partialPublicKey, int sequenceNo) {
		this.partialPublicKey = partialPublicKey;
		this.publicKey = publicKey;
		this.sequenceNo = sequenceNo;
	}

	/**
	 * Constructs a new BLSPublicKey from a suitably formatted StorageObject.
	 * The StorageObject should have the PUBLIC_KEY and G fields at a minimum.
	 * If the key represents a threshold public key it should also have the
	 * SEQUENCE_NO and PARTIAL_PUBLIC_KEY fields set.
	 * 
	 * If a field is not set it will be ignored. As such, it would be possible
	 * to construct an empty BLSPublicKey object, if that was used at any point
	 * it will cause a null pointer exception
	 * 
	 * @param publicKeyObj
	 *            StorageObject with the appropriate fields set
	 * @throws StorageException
	 * 
	 */
	public BLSPublicKey(StorageObject<?> publicKeyObj, Pairing pairing) throws StorageException {

		// Check if there is a partial public key
		if (publicKeyObj.has(PARTIAL_PUBLIC_KEY)) {
			// Construct a new element
			partialPublicKey = pairing.getG2().newElement();
			// Set the element value from the bytes decoded from the JSON
			partialPublicKey
					.setFromBytes(IOUtils.decodeData(EncodingType.BASE64, publicKeyObj.getString(PARTIAL_PUBLIC_KEY)));
		}
		if (publicKeyObj.has(PUBLIC_KEY)) {
			// Construct a new element
			publicKey = pairing.getG2().newElement();
			// Set the element value from the bytes decoded from the JSON
			publicKey.setFromBytes(IOUtils.decodeData(EncodingType.BASE64, publicKeyObj.getString(PUBLIC_KEY)));
		}
		if (publicKeyObj.has(SEQUENCE_NO)) {
			// If sequence number is present set the value
			sequenceNo = publicKeyObj.getInt(SEQUENCE_NO);
		}
	}

	/**
	 * Gets the partial public key Element associated with this public key. This
	 * will only be set if this is a threshold public key, otherwise it will
	 * return null.
	 * 
	 * @return Element containing the partial public key, or null if not set
	 */
	public Element getPartialPublicKey() {
		return this.partialPublicKey;
	}

	/**
	 * Gets the public key element
	 * 
	 * @return Element containing the public key
	 */
	public Element getPublicKeyElement() {
		return this.publicKey;
	}

	/**
	 * Gets the sequence number associated with this public key. This is only
	 * relevant in the threshold setting. If it has not been set it will return
	 * -1.
	 * 
	 * @return int sequence number or -1 if not set
	 */
	public int getSequenceNo() {
		return this.sequenceNo;
	}

	/**
	 * Sets the partial public key element
	 * 
	 * @param partial
	 *            Element containing the partial public key
	 */
	public void setPartialKey(Element partial) {
		this.partialPublicKey = partial;
	}

	/**
	 * Encodes the BLSPublicKey as a StorageObject converting any existing
	 * elements into Base64 encoded strings and setting them in the appropriate
	 * fields. If a value is unset or does not exist it is not exported.
	 * 
	 * 
	 */
	@Override
	public StorageObject<?> storeInStorageObject(StorageObject<?> store) {

		if (this.partialPublicKey != null) {
			store.set(PARTIAL_PUBLIC_KEY, IOUtils.encodeData(EncodingType.BASE64, this.partialPublicKey.toBytes()));
		}
		if (this.publicKey != null) {
			store.set(PUBLIC_KEY, IOUtils.encodeData(EncodingType.BASE64, this.publicKey.toBytes()));
		}

		if (this.sequenceNo >= 0) {
			store.set(SEQUENCE_NO, this.sequenceNo);
		}
		return store;

	}

}
