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
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.edu.unimelb.cs.culnane.comms.exceptions.StreamBoundExceededException;

/**
 * Reads data from the underlying socket and passes it onto the message
 * processor. Runs in a separate thread to allow concurrent receiving and
 * processing of incoming messages
 * 
 * @author Chris Culnane
 *
 */
public class SocketReader implements Runnable {
	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(SocketReader.class);
	/**
	 * The socket to read data from
	 */
	private Socket sock;
	/**
	 * SocketMessageProcessor to pass a received message on to
	 */
	private SocketMessageProcessor messageProcessor;
	/**
	 * Boolean to monitor shutdown state
	 */
	private volatile boolean shutdown = false;

	/**
	 * Integer containing bufferBound, if zero, will use default
	 */
	private int bufferBound = 0;

	/**
	 * Create a new SocketReader for the specified Socket, which will pass on
	 * received messages to the specified SocketMessageProcessor
	 * 
	 * @param sock
	 *            Socket to read from
	 * @param messageProcessor
	 *            SocketMessageProcessor to pass message on to
	 */
	public SocketReader(Socket sock, SocketMessageProcessor messageProcessor) {
		this.sock = sock;
		this.messageProcessor = messageProcessor;
	}

	/**
	 * Create a new SocketReader for the specified Socket, which will pass on
	 * received messages to the specified SocketMessageProcessor
	 * 
	 * @param sock
	 *            Socket to read from
	 * @param messageProcessor
	 *            SocketMessageProcessor to pass message on to
	 * @param bufferBound
	 *            int value to override default BoundedBuffer limit
	 */
	public SocketReader(Socket sock, SocketMessageProcessor messageProcessor, int bufferBound) {
		this.sock = sock;
		this.messageProcessor = messageProcessor;
		this.bufferBound = bufferBound;
	}

	@Override
	public void run() {
		BoundedBufferedInputStream bbis = null;
		try {
			// Create a new BoundedBufferedInputStream
			if (bufferBound != 0) {
				bbis = new BoundedBufferedInputStream(sock.getInputStream(), this.bufferBound);
			} else {
				bbis = new BoundedBufferedInputStream(sock.getInputStream());
			}
			while (!shutdown) {
				logger.info("About to wait for readline");
				byte[] msg = bbis.readLine();
				logger.info("Read line from socket");
				if (msg == null) {
					logger.info("Closing socket");
					// A null response indicates the stream has been closed
					sock.close();
					break;
				}
				this.messageProcessor.addMsgToProcessQueue(msg);
			}
		} catch (IOException e) {
			logger.error("IOException reading from socket", e);
		} catch (StreamBoundExceededException e) {
			logger.error("Read has exceeded buffer bound", e);
		} finally {
			if (bbis != null) {
				try {
					bbis.close();
				} catch (IOException e) {
					logger.error("Exception closing socket", e);
				}
			}
		}

	}

}
