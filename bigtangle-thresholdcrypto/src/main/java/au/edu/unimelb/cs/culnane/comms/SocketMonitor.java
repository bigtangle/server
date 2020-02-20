/*******************************************************************************
 * Copyright (c) 2015, 2016 Coasca Limited.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Chris Culnane - initial API and implementation
 ******************************************************************************/
package au.edu.unimelb.cs.culnane.comms;

import java.io.IOException;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The SocketMonitor attempts to read from a socket input stream. It discards anything that is read, but allows a rapid detection of
 * a closed socket. If the server socket is closed it will duly close the client socket and stop further writing taking place. This
 * is useful because our sockets are one way (a client connects to a server, but the server never responds - it has separate client
 * connection). By monitoring the response we can detect if the server is shutdown and prevent further writing. Otherwise we have to
 * wait until the underlying OS buffer fills and an exception is raised. This can result in several messages being lost and cause a
 * lag when a peer is restarted.
 * 
 * NOTE: All received data is discarded, do not use this monitor if you want to receive responses.
 * 
 * @author Chris Culnane
 * 
 */
public class SocketMonitor implements Runnable {

  /**
   * Socket we are monitoring
   */
  private Socket              socket;
  /**
   * String id for the SocketMonitor
   */
  private String              socketID;
  /**
   * Thread to run the socket monitor on
   */
  private Thread              thread;
  /**
   * Logger
   */
  private static final Logger logger = LoggerFactory.getLogger(SocketMonitor.class);

  /**
   * Constructor for SocketMonitor
   * 
   * Create the SocketMonitor and a thread to run it on
   * 
   * @param socket
   *          Socket to monitor
   * @param socketID
   *          String value to identify this socket monitor
   */
  public SocketMonitor(Socket socket, String socketID) {
    this.socket = socket;
    this.socketID = socketID;
    this.thread = new Thread(this, socketID + "MonitorThread");
    this.thread.setDaemon(true);
  }

  /**
   * Starts the monitor by starting the thread
   */
  public void start() {
    this.thread.start();
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.lang.Runnable#run()
   */
  @Override
  public void run() {
    logger.info("Starting to monitor Connection:{}", this.socketID);
    try {
      while (socket.getInputStream().read() != -1) {
        // do nothing - discard data - we don't expect to ever receive any data
      }
    }
    catch (IOException e) {
      logger.warn("Connection for {} has unexpectedly closed", this.socketID);
    }
    try {
      logger.warn("Connection for {} has closed. Will close client socket", this.socketID);
      socket.close();
    }
    catch (IOException e) {
      logger.error("Exception whilst closing socket {}", this.socketID, e);
    }
  }

}
