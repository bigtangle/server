/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.protocols;

import java.util.concurrent.Callable;

import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessage;
import au.edu.unimelb.cs.culnane.storage.StorageObject;

/**
 * Interface for a ProtocolStep. Each ProtocolStep should perform a particular
 * part of the protocol. It can both send and process responses, but should only
 * perform one round of outgoing and incoming processing, otherwise further
 * processing of messages would be needed within each ProtocolStep to
 * distinguish where an incoming message should be used.
 * 
 * ProtocolSteps should be implemented to maximize reuse between different
 * protocols. For example, one protocol step might be the initialization of a
 * commit scheme, which would involve creating a value and sharing it with other
 * peers. That would constitute an ideal step, since the running peer could
 * first generate its value, then broadcast it to other peers, and then wait for
 * responses from all or a threshold of peers, before completing the step by
 * storing the received data.
 * 
 * @author Chris Culnane
 *
 * @param <T>
 *            return type of this method, usually boolean
 */
public interface ProtocolStep<T,E extends CommunicationLayerMessage<?> & StorageObject<E>> extends Callable<T> {
	/**
	 * Gets the id of this ProtocolStep
	 * 
	 * @return String containing ProtocolStep id
	 */
	public String getStepId();

	/**
	 * Process an incoming message
	 * 
	 * @param msg
	 *            message to be processed
	 */
	public void processMessage(E msg);

	/**
	 * Sets the Protocol that this step will be running in
	 * 
	 * @param protocol
	 *            Protocol that this step will run in.
	 */
	public void setProtocol(Protocol<E> protocol);
}
