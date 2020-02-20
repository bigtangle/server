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

import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessage;
import au.edu.unimelb.cs.culnane.protocols.Protocol;
import au.edu.unimelb.cs.culnane.storage.StorageObject;

/**
 * PedersenCommitment opening protocol - this is used to open and check a
 * commitment. This is intended for use in a joint setting where everyone is
 * committing to a value and then once everyone has committed, everyone opens
 * and verifies.
 * 
 * @author Chris Culnane
 *
 * @param <E>
 *            Underlying type for the protocol, which will be used for
 *            communication and storage. Should implement
 *            CommunicationLayerMessage and StorageObject interfaces with
 *            appropriate type parameters
 */
public class PedersenCommitmentOpeningProtocol<E extends CommunicationLayerMessage<?> & StorageObject<E>>
		extends Protocol<E> {

	/**
	 * Static string with Storage prefix for data generated/read as part of this
	 * protocol
	 */
	private static final String STORAGE_PREFIX = "PedersenCommit";

	/**
	 * Creates a new PedersenCommitmentOpeningProtocol for the specified
	 * commitId - this should be the same commitId that was used by the
	 * PdCCreateCommit protocol.
	 * 
	 * @param commitId
	 */
	public PedersenCommitmentOpeningProtocol(String commitId) {
		super("PedersenCommitment");
		this.addProtocolStep(new PdCInit<E>());
		this.addProtocolStep(new PdCOpenCommit<E>(commitId));
	}

	@Override
	public String getStoragePrefix() {
		return STORAGE_PREFIX;
	}

}
