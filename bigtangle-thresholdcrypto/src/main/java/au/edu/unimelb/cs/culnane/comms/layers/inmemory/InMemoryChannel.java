/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.comms.layers.inmemory;

import java.io.IOException;

import au.edu.unimelb.cs.culnane.comms.CommunicationLayerChannel;
import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessage;

/**
 * Simple example of an in-memory communication channel. This is useful for
 * debugging, where you want to simulate multiple nodes on a single machine. The
 * channel is constructed with a reference to the corresponding end-point
 * InMemoryLayer and directly calls the receive method on that layer to send the
 * message.
 * 
 * @author Chris Culnane
 *
 * @param <E>
 *            Underlying message type of the InMemoryChannel
 */
public class InMemoryChannel<E extends CommunicationLayerMessage<?>> extends CommunicationLayerChannel<E> {
	/**
	 * String containing the channel id
	 */
	private String id;
	/**
	 * Boolean holding state of whether the channel is open or closed.
	 */
	private boolean isClosed = false;
	/**
	 * InMemoryLayer of the endpoint - where to send messages
	 */
	private InMemoryLayer<E> endPoint;

	/**
	 * Create a new InMemoryChannel with the specified id and endpoint
	 * 
	 * @param id
	 *            String id of this channel
	 * @param endPoint
	 *            InMemoryLayer of the endpoint - where to send messages
	 */
	public InMemoryChannel(String id, InMemoryLayer<E> endPoint) {
		super();
		this.id = id;
		this.endPoint = endPoint;
		this.isClosed = false;
	}

	@Override
	public String getId() {
		return this.id;
	}

	@Override
	public void close() {
		this.isClosed = true;
	}

	@Override
	public void send(byte[] msg) throws IOException {
		if (!this.isClosed) {
			endPoint.receive(msg);
		} else {
			throw new IOException("Channel is not closed, call open before sending a message");
		}

	}

	@Override
	public void send(E msg) throws IOException {
		send(msg.getRawMessage());
	}

	/**
	 * This is redundant on the InMemoryChannel, since it is always running
	 */
	@Override
	public void run() {

	}

	/**
	 * Currently this is redundant on the InMemoryChannel, opening and closing
	 * has no impact
	 */
	@Override
	public void open() throws IOException {
		this.isClosed = false;
	}

}
