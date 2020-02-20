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

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens on a socket for incoming connections, then creates a new SocketReader
 * to process the connection. A minimal amount of processing is done on this
 * thread
 * 
 * @author Chris Culnane
 *
 */
public class SocketListener implements Runnable {
	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(SocketListener.class);
	/**
	 * ServerSocket to listen on
	 */
	private ServerSocket serverSocket;
	/**
	 * Boolean to monitor shutdown status
	 */
	private volatile boolean shutdown = false;
	/**
	 * SocketMessageProcessor to process the incoming byte data
	 */
	private SocketMessageProcessor messageProcessor;
	/**
	 * ExecutorService to run this listener on
	 */
	private ExecutorService executor = Executors.newSingleThreadExecutor();

	/**
	 * ExecutorService to run the SocketReaders on
	 */
	private ExecutorService readers = Executors.newCachedThreadPool();

	/**
	 * Create a new SocketListener with the specified ServerSocket and
	 * SocketLayer
	 * 
	 * @param serverSocket
	 *            ServerSocket to listen to
	 * @param socketLayer
	 *            SocketLayer to use for communication
	 */
	public SocketListener(ServerSocket serverSocket, SocketLayer<?> socketLayer) {
		this.serverSocket = serverSocket;
		this.messageProcessor = new SocketMessageProcessor(socketLayer);
	}

	@Override
	public void run() {
		executor.execute(this.messageProcessor);
		// Loop until shutdown
		while (!this.shutdown) {
			try {
				// Get the external socket from the peer and call accept, which
				// blocks until a connection is received
				Socket socket = this.serverSocket.accept();
				logger.info("Received a connection from {}", socket);
				readers.execute(new SocketReader(socket, this.messageProcessor));
			} catch (IOException e) {
				logger.error("IOException whilst accepting connection on External Server Socket", e);
			}
		}
	}

	/**
	 * Shuts down the listener.
	 */
	public void shutdown() {
		// TODO make more graceful, wait for termination or timeout
		this.executor.shutdownNow();
		this.readers.shutdownNow();
		this.shutdown = true;
	}

}
