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
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.LinkedBlockingDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessage;
import au.edu.unimelb.cs.culnane.crypto.ZpStarSafePrimeGroupRFC3526;
import au.edu.unimelb.cs.culnane.protocols.Protocol;
import au.edu.unimelb.cs.culnane.protocols.ProtocolExecutionException;
import au.edu.unimelb.cs.culnane.protocols.ProtocolStep;
import au.edu.unimelb.cs.culnane.storage.StorageObject;
import au.edu.unimelb.cs.culnane.storage.exceptions.StorageException;

/**
 * Pedersen Commitment class that handles the opening of a commitment and
 * receives the openings from other parties.
 * 
 * @author cculnane
 *
 */
public class PdCOpenCommit<E extends CommunicationLayerMessage<?> & StorageObject<E>>
		implements ProtocolStep<Boolean, E> {
	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(PdCOpenCommit.class);

	/**
	 * Queue for incoming messages
	 */
	private LinkedBlockingDeque<E> incomingMessages = new LinkedBlockingDeque<E>();

	/**
	 * Static string with STEP_ID
	 */
	public static final String STEP_ID = "PdC_OpenCommit";

	/**
	 * Protocol that this step will run in
	 */
	private Protocol<E> protocol;

	/**
	 * Static class with message fields
	 * 
	 * @author Chris Culnane
	 *
	 */
	public static final class MESSAGES {
		public static final String R = "r";
		public static final String M = "m";
		public static final String COMMITID = "commitID";
	}

	/**
	 * Static class with storage fields
	 * 
	 * @author Chris Culnane
	 *
	 */
	public static final class STORAGE {
		// public static final String COMMIT_R = "r";
		// public static final String COMMIT = "commit";
		public static final String RECEIVED_OPENINGS = "receivedOpenings";

	}

	/**
	 * String with commitId that uniquely identifies a particular commitment run
	 */
	private String commitId;

	/**
	 * Constructs a new Pedersen Commitment opening class with the specified
	 * commitID
	 * 
	 * @param commitID
	 *            String containing the commit id - the unique identifier of
	 *            this commitment run
	 */
	public PdCOpenCommit(String commitID) {
		this.commitId = commitID;
	}

	@Override
	public Boolean call() throws Exception {
		logger.info("Starting {}", getStepId());
		String initStorageId = PdCInit.STEP_ID;
		String commitStorageId = PdCCreateCommit.STEP_ID + ":" + commitId;

		StorageObject<E> initStorage = this.protocol.getFromStorage(initStorageId).get();
		if (initStorage == null) {
			throw new ProtocolExecutionException("No initialisation data found");
		}
		StorageObject<E> commitStorage = this.protocol.getFromStorage(commitStorageId).get();
		if (commitStorage == null) {
			throw new ProtocolExecutionException("No previous commit data found");
		}
		logger.info("PedersenCommitment {} found, reloading data.", commitStorageId);

		E initdata = initStorage.get();
		ZpStarSafePrimeGroupRFC3526 group = new ZpStarSafePrimeGroupRFC3526(
				initdata.getStorageObject(PdCInit.STORAGE.GROUP));
		BigInteger v = new BigInteger(initdata.getString(PdCInit.STORAGE.V), 16);

		PedersenCommitment pedCommitment = new PedersenCommitment(group, v);
		// Generate Randomness
		PedersenCommit mycommit = new PedersenCommit(commitStorage.getStorageObject(PdCCreateCommit.STORAGE.COMMIT));

		StorageObject<E> broadcastOpen = this.protocol.getNewStorageObject();
		broadcastOpen.set(MESSAGES.COMMITID, this.commitId);
		broadcastOpen.set(MESSAGES.R, mycommit.getRandomnessString());
		broadcastOpen.set(MESSAGES.M, mycommit.getMString());
		broadcastOpen.set(Protocol.MESSAGES.STEP, getStepId());

		logger.info("Generated my data, about to broadcast: {}", broadcastOpen);
		// Broadcast message
		protocol.sendBroadcast(broadcastOpen.get());
		boolean receivedAll = false;

		TreeSet<String> peerIds = new TreeSet<String>();
		peerIds.addAll(this.protocol.getPeerIds());
		Map<String, String> commitsReceived = commitStorage.getStringMap(PdCCreateCommit.STORAGE.COMMITS);
		StorageObject<E> openReceived = this.protocol.getNewStorageObject();
		// Map<String, StorageObject<E>> openReceived = new HashMap<String,
		// StorageObject<E>>();
		while (!receivedAll) {
			try {
				StorageObject<E> msg = this.incomingMessages.take();
				logger.info("Processing incoming message: {}", msg);
				String sender = msg.getString(Protocol.MESSAGES.SENDER);
				if (commitsReceived.containsKey(sender)) {

					String commitString = commitsReceived.get(sender);
					logger.info("CommitString:{}", commitString);
					PedersenCommit pedC = new PedersenCommit(msg.getString(MESSAGES.R), commitString,
							msg.getString(MESSAGES.M));
					if (!pedCommitment.verify(pedC)) {
						throw new ProtocolExecutionException("Commits did not open successfully");
					}
					if (!peerIds.remove(sender)) {
						throw new ProtocolExecutionException("Received message from unknown peer");
					}
					openReceived.set(sender, pedC);
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
		logger.info("Finished processing messages");
		StorageObject<E> mydata = this.protocol.getNewStorageObject();
		mydata.set(STORAGE.RECEIVED_OPENINGS, openReceived);
		// Store my data
		protocol.addToStorage(getStepId(), mydata.get());
		return true;
	}

	@Override
	public String getStepId() {
		return STEP_ID + ":" + this.commitId;// We do this to distinguish
												// between concurrent runs of
												// this protocol step
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
