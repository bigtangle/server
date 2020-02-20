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

import java.math.BigInteger;
import java.util.TreeSet;
import java.util.concurrent.LinkedBlockingDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessage;
import au.edu.unimelb.cs.culnane.crypto.Group;
import au.edu.unimelb.cs.culnane.crypto.elgamal.ElGamalCipher;
import au.edu.unimelb.cs.culnane.protocols.Protocol;
import au.edu.unimelb.cs.culnane.protocols.ProtocolExecutionException;
import au.edu.unimelb.cs.culnane.protocols.ProtocolStep;
import au.edu.unimelb.cs.culnane.protocols.threshold.decryption.EqualityDiscreteLogProof;
import au.edu.unimelb.cs.culnane.storage.StorageObject;
import au.edu.unimelb.cs.culnane.storage.exceptions.StorageException;

/**
 * Step to prepare for a plaintext equivalence test. This performs the necessary
 * calculations and construct the appropriate proofs, before broadcasting them
 * in preparation for the threshold decryption of the answer
 * 
 * @author Chris Culnane
 *
 */
public class ThresholdPETPrepare<E extends CommunicationLayerMessage<?> & StorageObject<E>>
		implements ProtocolStep<Boolean, E> {
	/**
	 * Static class with message keys
	 * 
	 * @author Chris Culnane
	 *
	 */
	public static final class MESSAGES {
		public static final String D1_PROOF = "d1_proof";
		public static final String D2_PROOF = "d2_proof";
		// public static final String D1 = "d1";
		// public static final String D2 = "d2";
		public static final String REENC = "reencryption";
	}

	/**
	 * Static class with storage keys
	 * 
	 * @author Chris Culnane
	 *
	 */
	public static final class STORAGE {
		public static final String COMMITS = "commits";
		public static final String Z = "z";
	}

	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(ThresholdPETPrepare.class);

	/**
	 * Static string with STEP_ID
	 */
	public static final String STEP_ID = "ThresholdPETPrepare";

	/**
	 * Queue for incoming messages
	 */
	private LinkedBlockingDeque<E> incomingMessages = new LinkedBlockingDeque<E>();

	/**
	 * Protocol this step will run in
	 */
	private Protocol<E> protocol;

	/**
	 * String containing UUID for this PET run
	 */
	private String uuid;

	/**
	 * Group to perform operations on
	 */
	private Group group;

	/**
	 * int threshold of key
	 */
	private int threshold;

	/**
	 * StorageObject containing first cipher
	 */
	private ElGamalCipher cipherOne;

	/**
	 * StorageObject containing second cipher
	 */
	private ElGamalCipher cipherTwo;

	/**
	 * StorageObject to store resulting cipher to decrypt
	 */
	private StorageObject<?> decryptCipher;

	/**
	 * Constructs a new step to prepare the PET
	 * 
	 * 
	 * 
	 * @param group
	 *            Group to perform operations on
	 * @param cipherOne
	 *            ElGamalCipher containing cipher one
	 * @param cipherTwo
	 *            ElGamalCipher containing cipher two
	 * @param decryptCipher
	 *            StorageObject to store calculated cipher in
	 * @param uuid
	 *            String UUID of this run
	 * @param threshold
	 *            int threshold of the key
	 */
	public ThresholdPETPrepare(Group group, ElGamalCipher cipherOne, ElGamalCipher cipherTwo,
			StorageObject<?> decryptCipher, String uuid, int threshold) {
		this.group = group;
		this.uuid = uuid;
		this.threshold = threshold;
		this.cipherOne = cipherOne;
		this.cipherTwo = cipherTwo;
		this.decryptCipher = decryptCipher;

	}

	@Override
	public Boolean call() throws Exception {
		logger.info("Starting {} with threshold:{}", getStepId(), threshold);

		ElGamalCipher cipherThree = new ElGamalCipher(
				cipherOne.getC1().multiply(cipherTwo.getC1().modInverse(group.get_p())).mod(group.get_p()),
				cipherOne.getC2().multiply(cipherTwo.getC2().modInverse(group.get_p())).mod(group.get_p()));

		StorageObject<E> commitdata = protocol.getFromStorage(ThresholdPETCommit.STEP_ID + ":" + uuid);
		StorageObject<E> commits = commitdata.getStorageObject(ThresholdPETCommit.STORAGE.COMMITS);
		BigInteger z = commitdata.getBigInteger(ThresholdPETCommit.STORAGE.Z);

		ElGamalCipher reEncCipherThree = cipherThree.reEncrypt(z, group.get_p());

		EqualityDiscreteLogProof d1_proof = EqualityDiscreteLogProof.constructProof(group, cipherThree.getC1(),
				group.get_g(), reEncCipherThree.getC1(), group.get_g().modPow(z, group.get_p()), z);// we
																									// don't
																									// need
																									// to
																									// recalculate
																									// g^z,
																									// we
																									// could
																									// get
																									// it
																									// from
																									// the
																									// storage
		EqualityDiscreteLogProof d2_proof = EqualityDiscreteLogProof.constructProof(group, cipherThree.getC2(),
				group.get_g(), reEncCipherThree.getC2(), group.get_g().modPow(z, group.get_p()), z);// we
																									// don't
																									// need
																									// to

		StorageObject<E> broadcast = this.protocol.getNewStorageObject();
		broadcast.set(MESSAGES.REENC, reEncCipherThree);
		broadcast.set(MESSAGES.D1_PROOF, d1_proof);
		broadcast.set(MESSAGES.D2_PROOF, d2_proof);
		broadcast.set(Protocol.MESSAGES.STEP, getStepId());
		this.protocol.sendBroadcast(broadcast.get());

		boolean receivedAll = false;

		TreeSet<String> peerIds = new TreeSet<String>();
		peerIds.addAll(this.protocol.getPeerIds());
		StorageObject<E> reencsReceived = protocol.getNewStorageObject();

		while (!receivedAll) {
			try {
				E msg = this.incomingMessages.take();
				logger.info("Processing incoming message: {}", msg);
				String sender = msg.getString(Protocol.MESSAGES.SENDER);
				ElGamalCipher receivedCipher = new ElGamalCipher(msg.getStorageObject(MESSAGES.REENC));

				logger.info("commits:{}", commits);
				BigInteger commit = new BigInteger(commits.getString(sender), 16);
				EqualityDiscreteLogProof received_d1_proof = new EqualityDiscreteLogProof(
						msg.getStorageObject(MESSAGES.D1_PROOF), cipherThree.getC1(), group.get_g(),
						receivedCipher.getC1(), commit);
				EqualityDiscreteLogProof received_d2_proof = new EqualityDiscreteLogProof(
						msg.getStorageObject(MESSAGES.D2_PROOF), cipherThree.getC2(), group.get_g(),
						receivedCipher.getC2(), commit);
				if (!reencsReceived.has(sender)) {
					if (EqualityDiscreteLogProof.verify(this.group, received_d1_proof)
							&& EqualityDiscreteLogProof.verify(this.group, received_d2_proof)) {

						logger.info("Check Proof 1:{}", EqualityDiscreteLogProof.verify(this.group, received_d1_proof));
						logger.info("Check Proof 2:{}", EqualityDiscreteLogProof.verify(this.group, received_d2_proof));
						reencsReceived.set(sender, msg);
						reEncCipherThree.multiplyCipher(receivedCipher, group.get_p());
						if (!peerIds.remove(sender)) {
							throw new ProtocolExecutionException("Received message from unknown peer");
						}
						if (peerIds.isEmpty()) {
							break;
						}
					} else {
						throw new ProtocolExecutionException("Zero Knowledge Proof failed:" + sender);
					}
				} else {
					throw new ProtocolExecutionException("Received duplicate message from peer:" + sender);
				}
			} catch (StorageException e) {
				throw new ProtocolExecutionException("Received message was not correctly formatted", e);
			}
		}
		reEncCipherThree.storeInStorageObject(this.decryptCipher);
		StorageObject<E> mydata = this.protocol.getNewStorageObject();
		mydata.set(STORAGE.COMMITS, reencsReceived);
		protocol.addToStorage(getStepId(), mydata);
		logger.info("Finished processing messages");
		return true;
	}

	@Override
	public String getStepId() {
		return STEP_ID + ":" + this.uuid;
	}

	@Override
	public void processMessage(E msg) {
		this.protocol.addToTranscript(msg);
		incomingMessages.add(msg);
	}

	@Override
	public void setProtocol(Protocol<E> protocol) {
		this.protocol = protocol;

	}

}
