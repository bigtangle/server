/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.protocols.cointoss;

import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessage;
import au.edu.unimelb.cs.culnane.protocols.Protocol;
import au.edu.unimelb.cs.culnane.storage.StorageObject;

/**
 * Public coin tossing protocol to generate a joint random value derived from
 * distributed coin tossing.
 * 
 * @author Chris Culnane
 *
 */
public class CoinTossProtocol<E extends CommunicationLayerMessage<?> & StorageObject<E>> extends Protocol<E> {
	/**
	 * Storage prefix
	 */
	private static final String PROTOCOL_ID = "CoinToss";

	/**
	 * Creates new CoinTossProtocol and adds appropriate protocol step
	 */
	public CoinTossProtocol() {
		super(PROTOCOL_ID);
		this.addProtocolStep(new CTGenRandAndCommit<E>(2048));
		this.addProtocolStep(new CTOpenCommit<E>());

	}

	@Override
	public String getStoragePrefix() {
		return PROTOCOL_ID;
	}

}
