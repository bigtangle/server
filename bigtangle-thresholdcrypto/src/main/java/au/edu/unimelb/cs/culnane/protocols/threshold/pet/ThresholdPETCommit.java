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
import java.security.SecureRandom;
import java.util.TreeSet;
import java.util.concurrent.LinkedBlockingDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessage;
import au.edu.unimelb.cs.culnane.crypto.Group;
import au.edu.unimelb.cs.culnane.protocols.Protocol;
import au.edu.unimelb.cs.culnane.protocols.ProtocolExecutionException;
import au.edu.unimelb.cs.culnane.protocols.ProtocolStep;
import au.edu.unimelb.cs.culnane.storage.StorageObject;
import au.edu.unimelb.cs.culnane.storage.exceptions.StorageException;

/**
 * Creates a Commitment for the Threshold Plaintext Equivalence Test and waits
 * to receive commitments from all other parties
 * 
 * @author Chris Culnane
 *
 */
public class ThresholdPETCommit<E extends CommunicationLayerMessage<?> & StorageObject<E>>
		implements ProtocolStep<Boolean, E> {
	/**
	 * Static class containing message keys
	 * 
	 * @author Chris Culnane
	 *
	 */
	public static final class MESSAGES {
		public static final String COMMIT_Z = "commit_z";
	}

	/**
	 * Static class containing storage keys
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
	private static final Logger logger = LoggerFactory.getLogger(ThresholdPETCommit.class);

	/**
	 * Static string containing STEP_ID
	 */
	public static final String STEP_ID = "ThresholdPETCommit";

	/**
	 * Queue for incoming message
	 */
	private LinkedBlockingDeque<E> incomingMessages = new LinkedBlockingDeque<E>();

	/**
	 * Protocol this step will run in
	 */
	private Protocol<E> protocol;

	/**
	 * String containing UUID of this run
	 */
	private String uuid;

	/**
	 * Group the operations will be performed on
	 */
	private Group group;
	/**
	 * int threshold of the key
	 */
	private int threshold;

	/**
	 * Constructs a new commitment step for a PET
	 * 
	 * @param group
	 *            Group to perform operations on
	 * @param uuid
	 *            String UUID of this PET run
	 * @param threshold
	 *            int threshold of key
	 */
	public ThresholdPETCommit(Group group, String uuid, int threshold) {
		this.group = group;
		this.uuid = uuid;
		this.threshold = threshold;

	}

	@Override
	public Boolean call() throws Exception {
		logger.info("Starting {}, threshold {}", getStepId(), this.threshold);

		BigInteger z = group.getRandomValueInQ(new SecureRandom());

		BigInteger commitZ = group.get_g().modPow(z, group.get_p());
		StorageObject<E> mydata = this.protocol.getNewStorageObject();
		mydata.set(STORAGE.Z, z);

		StorageObject<E> broadcast = this.protocol.getNewStorageObject();
		broadcast.set(MESSAGES.COMMIT_Z, commitZ);
		broadcast.set(Protocol.MESSAGES.STEP, getStepId());
		this.protocol.sendBroadcast(broadcast.get());

		boolean receivedAll = false;

		TreeSet<String> peerIds = new TreeSet<String>();
		peerIds.addAll(this.protocol.getPeerIds());
		StorageObject<E> commitsReceived = this.protocol.getNewStorageObject();
		// Map<String, String> commitsReceived = new HashMap<String, String>();

		while (!receivedAll) {
			try {
				E msg = this.incomingMessages.take();
				logger.info("Processing incoming message: {}", msg);
				String sender = msg.getString(Protocol.MESSAGES.SENDER);
				BigInteger receivedCommit = msg.getBigInteger(MESSAGES.COMMIT_Z);
				if (!commitsReceived.has(sender)) {
					commitsReceived.set(sender, receivedCommit);
					if (!peerIds.remove(sender)) {
						throw new ProtocolExecutionException("Received message from unknown peer");
					}
					if (peerIds.isEmpty()) {
						receivedAll = true;
						break;
					}
				} else {
					throw new ProtocolExecutionException("Received duplicate message from peer:" + sender);
				}
			} catch (StorageException e) {
				throw new ProtocolExecutionException("Received message was not correctly formatted", e);
			}
		}

		mydata.set(STORAGE.COMMITS, commitsReceived);
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
