/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.comms.layers.socket;

import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessage;
import au.edu.unimelb.cs.culnane.comms.exceptions.CommunicationLayerMessageException;

/**
 * Message processor for a socket layer. This runs in a separate thread and
 * passes on messages received as they arrive. It is required to prevent any
 * blocking taking place on the receive thread, hence messages can be received
 * concurrently with a message being processed.
 * 
 * @author Chris Culnane
 *
 */
public class SocketMessageProcessor implements Runnable {
	/**
	 * logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(SocketMessageProcessor.class);
	/**
	 * Concurrent message process queue
	 */
	private LinkedBlockingQueue<byte[]> processQueue = new LinkedBlockingQueue<byte[]>();
	/**
	 * Boolean to monitor shutdown state
	 */
	private volatile boolean shutdown = false;

	/**
	 * Underlying SocketLayer with a appropriate types
	 */
	private SocketLayer<? extends CommunicationLayerMessage<?>> socketLayer;

	/**
	 * Constructs a SocketMessageProcessor for the specified underlying
	 * SocketLayer
	 * 
	 * @param socketLayer
	 *            SocketLayer to send messages on to
	 */
	public SocketMessageProcessor(SocketLayer<? extends CommunicationLayerMessage<?>> socketLayer) {
		this.socketLayer = socketLayer;
	}

	@Override
	public void run() {
		try {
			while (!this.shutdown) {
				try {
					this.socketLayer.receive(this.processQueue.take());
				} catch (CommunicationLayerMessageException e) {
					logger.error("Exception in SocketMessageProcessor", e);
				}
			}
		} catch (InterruptedException e) {
			logger.error("SocketMessageProcessor interrupted - could be a shutdown request. Shutdown:{}",
					this.shutdown);
		}
	}

	/**
	 * Adds a message to the process queue
	 * 
	 * @param msg
	 *            byte array message to add to queue
	 */
	public void addMsgToProcessQueue(byte[] msg) {
		processQueue.add(msg);
	}

}
