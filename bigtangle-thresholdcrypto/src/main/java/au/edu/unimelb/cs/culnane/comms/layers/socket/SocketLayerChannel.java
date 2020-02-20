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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.LinkedBlockingQueue;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.edu.unimelb.cs.culnane.comms.CommunicationLayerChannel;
import au.edu.unimelb.cs.culnane.comms.CommunicationLayerMessage;

/**
 * SocketLayerChannel used to create a CommunicationLayerChannel that uses
 * sockets to communicate. This contains a message buffer that will hold
 * messages until the underlying socket connection has been established.
 * 
 * @author Chris Culnane
 *
 * @param <E>
 *            underlying type of CommunicationLayerMessage
 */
public class SocketLayerChannel<E extends CommunicationLayerMessage<?>> extends CommunicationLayerChannel<E> {
	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(SocketLayerChannel.class);
	/**
	 * Thread to run this SocketLayerChannel on
	 */
	private Thread runner;

	/**
	 * String id of channel
	 */
	private String id;
	/**
	 * Socket used for underlying communication
	 */
	private Socket socket;

	/**
	 * InetSocketAddress - the endpoint we want to communicate with
	 */
	private InetSocketAddress endPoint;

	/**
	 * BufferedOutputStream to write data to do
	 */
	private BufferedOutputStream bos;
	/**
	 * SocketFactory to use for creating sockets. If secure sockets are needed
	 * this must be an instance of an SSLSocketFactory
	 */
	private SocketFactory factory;
	/**
	 * Concurrent Queue to buffer messages
	 */
	private LinkedBlockingQueue<byte[]> messageBuffer = new LinkedBlockingQueue<byte[]>();
	/**
	 * Boolean to hold secure state
	 */
	private boolean isSecure = false;
	/**
	 * Boolean for whether to use client authentication
	 */
	private boolean requireClientAuth = false;

	/**
	 * Creates new SocketLayerChannel with the specified id, connecting to the
	 * specified host and port. If a SocketFactory is specified it will be used
	 * for creating Sockets. For secure sockets, specify an SSLSocketFactory.
	 * Set requireClientAuth to true to use client authentication, this can only
	 * be set to true if an SSLSocketFactory has been specified.
	 * 
	 * @param id
	 *            String id of this channel
	 * @param host
	 *            String host to connect to
	 * @param port
	 *            int port to connect on
	 * @param factory
	 *            SocketFactory to use for creating sockets, pass in an
	 *            SSLSocketFactory to use SecureSockets
	 * @param requireClientAuth
	 *            boolean, set to true to use client authentication - must be
	 *            paired with an SSLSocketFactory
	 * @throws UnknownHostException
	 * @throws IOException
	 */
	public SocketLayerChannel(String id, String host, int port, SocketFactory factory, boolean requireClientAuth)
			throws UnknownHostException, IOException {
		this.id = id;
		this.factory = factory;
		this.requireClientAuth = requireClientAuth;
		if (this.factory != null) {
			// socket = this.factory.createSocket();
			if (this.factory instanceof SSLSocketFactory) {
				logger.info("SocketFactory is an SSLSocketFactory, will switch to secure mode");
				isSecure = true;
			}
			if (this.requireClientAuth && !isSecure) {
				throw new IOException("Cannot use client authentication with a non SSLSocket");
			}
		} else {
			logger.info("No SocketFactory specified, will use standard sockets");
			isSecure = false;
			// this.socket = new Socket();
		}
		this.endPoint = new InetSocketAddress(host, port);

	}

	/**
	 *
	 * Creates new SocketLayerChannel with the specified id, connecting to the
	 * specified host and port.
	 * 
	 * @param id
	 *            String id of this channel
	 * @param host
	 *            String host to connect to
	 * @param port
	 *            int port to connect on
	 * @throws UnknownHostException
	 * @throws IOException
	 */
	public SocketLayerChannel(String id, String host, int port) throws UnknownHostException, IOException {
		this(id, host, port, null, false);

	}

	@Override
	public String getId() {
		return this.id;
	}

	@Override
	public void close() throws IOException {
		logger.info("Closing socket {}", this.id);
		this.socket.close();
		this.runner.interrupt();
	}

	@Override
	public void send(byte[] msg) throws IOException {
		logger.info("Message added to queue");
		messageBuffer.add(msg);

	}

	@Override
	public void send(E msg) throws IOException {
		send(msg.getRawMessage());
	}

	@Override
	public void open() throws IOException {
		this.runner = new Thread(this, this.id + "RunnerThread");
		this.runner.start();
	}

	@Override
	public void run() {
		try {
			while (true) {
				try {
					logger.info("Socket {} trying to open Connection to {}", this.id, this.endPoint);
					if (this.factory != null) {
						this.socket = this.factory.createSocket();
					} else {
						this.socket = new Socket();
					}
					if (isSecure && this.requireClientAuth) {
						((SSLSocket) this.socket).setUseClientMode(true);
					}
					this.socket.connect(this.endPoint);
					logger.info("Socket {} opened Connection to {}", this.id, this.endPoint);
					if (this.isSecure) {
						logger.info("SecureSocket:{}", ((SSLSocket) this.socket).getSession().getPeerPrincipal());
					}
					this.bos = new BufferedOutputStream(this.socket.getOutputStream());
					while (true) {
						this.bos.write(this.messageBuffer.take());

						this.bos.write('\n');
						this.bos.flush();
					}
				} catch (IOException e) {
					logger.info("Socket {} failed to open Connection to {}", this.id, this.endPoint);
					System.out.println("Socket connection failed, will retry in 5 seconds");
					Thread.sleep(5000);
				}
			}
		} catch (InterruptedException e) {
			logger.error("SocketLayerChannel was interrupted, possible shutdown.");
		}
	}

}
