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

import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessage;
import au.edu.unimelb.cs.culnane.protocols.Protocol;
import au.edu.unimelb.cs.culnane.protocols.cointoss.CTGenRandAndCommit;
import au.edu.unimelb.cs.culnane.protocols.cointoss.CTOpenCommit;
import au.edu.unimelb.cs.culnane.storage.StorageObject;

/**
 * Protocol that creates a Pedersen Commitment amongst a group of peers
 * 
 * @author Chris Culnane
 *
 * @param <E>
 *            Underlying type for communication and storage, should implement
 *            CommunicationLayerMessage and StorageObject
 */
public class PedersenCommitmentProtocol<E extends CommunicationLayerMessage<?> & StorageObject<E>> extends Protocol<E> {

	/**
	 * Static string containing storage prefix for this protocol
	 */
	private static final String STORAGE_PREFIX = "PedersenCommit";

	/**
	 * Creates a new PedersenCommitmentProtocol with the specified commitId and
	 * BigInteger message to commit to
	 * 
	 * @param commitId
	 *            String unique id of this commitment
	 * @param m
	 *            BigInteger containing message to commit to
	 */
	public PedersenCommitmentProtocol(String commitId, BigInteger m) {
		super("PedersenCommitment");
		this.addProtocolStep(new CTGenRandAndCommit<E>(2048));
		this.addProtocolStep(new CTOpenCommit<E>());
		this.addProtocolStep(new PdCInit<E>());
		this.addProtocolStep(new PdCCreateCommit<E>(commitId, m));
	}

	@Override
	public String getStoragePrefix() {
		return STORAGE_PREFIX;
	}

}
