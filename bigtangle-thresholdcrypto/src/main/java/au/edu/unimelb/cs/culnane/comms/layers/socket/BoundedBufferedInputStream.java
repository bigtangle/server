/*******************************************************************************
 * Copyright (c) 2013, 2016 Coasca Limited.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Chris Culnane - initial API and implementation
 *     Matthew Casey - review
 ******************************************************************************/
package au.edu.unimelb.cs.culnane.comms.layers.socket;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.edu.unimelb.cs.culnane.comms.exceptions.StreamBoundExceededException;

/**
 * Wrapper of a BufferedInputStream to provide a bounded readLine method and
 * readFile methods.
 * 
 * This is of particular importance in our setting. We don't have fixed size
 * messages, but there is an upper bound on the size of messages. We utilise a
 * newline character to terminate messages. As such, a message is a JSON string
 * (without any newlines) terminated by a newline character. This provides a
 * simple and effective communication protocol. However, the default
 * BufferedReader provided by Java is susceptible to a simple denial of service
 * attack. A client can just transmit data without a newline character until the
 * heap space of the server is exhausted. As such, this wrapper provides a bound
 * on the readLine method. The method itself operates in the same way as the
 * readLine on the BufferedReader. More specifically, it blocks until a line is
 * available to read. However, if in the process of reading data the bound is
 * exceeded an exception is thrown to allow the server to close the connection.
 * 
 * The same requirement for a bounded readLine is needed when reading the
 * database files transmitted during round of the Commit procedure. These files
 * cannot be trusted and a dishonest peer could attempt to denial of service
 * another by transmitting a large database file that contained no newlines. The
 * same wrapper is used for reading these files.
 * 
 * Additionally a readFile method is provided for reading bulk file transfers on
 * the underlying socket. These bulk transfers can be larger than the bound set
 * for readLine, with the quantity of data to be read being defined by the the
 * fileSize property. When utilising the readFile method it is important a
 * socket timeout is set on the underlying socket to catch cases where a large
 * file is specified for transfer but the client stops transmitting data.
 * 
 * @author Chris Culnane
 */
public class BoundedBufferedInputStream extends BufferedInputStream {

	/**
	 * Default bound on the readLine method - can be overridden in the
	 * constructor
	 */
	private int bound = 10240;

	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(BoundedBufferedInputStream.class);

	/**
	 * Constructor for BoundedBufferedInputStream that uses default BOUND
	 * 
	 * @param in
	 *            underlying InputStream
	 */
	public BoundedBufferedInputStream(InputStream in) {
		super(in);
	}

	/**
	 * Constructor for BoundedBufferedInputStream that sets upper bound
	 * 
	 * @param in
	 *            underlying InputStream
	 * @param bound
	 *            upper bound of data to read before throwing an exception on
	 *            readLine, must be > 0
	 */
	public BoundedBufferedInputStream(InputStream in, int bound) {
		super(in);

		// The bound must be larger than 0
		if (bound > 0) {
			this.bound = bound;
		}
	}

	/**
	 * Constructor for BoundedBufferedInputStream that sets upper bound and
	 * buffer size
	 * 
	 * @param in
	 *            underlying InputStream
	 * @param size
	 *            of underlying buffer
	 * @param bound
	 *            upper bound of data to read before throwing an exception on
	 *            readLine, must be > 0
	 */
	public BoundedBufferedInputStream(InputStream in, int size, int bound) {
		super(in, size);

		// The bound must be larger than 0
		if (bound > 0) {
			this.bound = bound;
		}
	}

	/**
	 * Blocks until a line is available to read from the InputStream
	 * 
	 * Throws an exception if the data read since the last line, or start of the
	 * document, has exceeded BOUND.
	 * 
	 * The format of the data should be either Linux (LF) or Windows (CRLF)
	 * 
	 * @return bytes of the the line read or null if the end of the stream has
	 *         been reached
	 * @throws IOException
	 * @throws StreamBoundExceededException
	 *             - when the data read has exceeded the bound and no newline
	 *             character found
	 */
	public byte[] readLine() throws IOException, StreamBoundExceededException {
		// In memory buffer for the data to be read into
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		int b;

		// We read each byte separately, which shouldn't impact on performance
		// because we have wrapped the InputStream by a
		// BufferedInputStream. We need to check each byte anyway in order to
		// find the newline character

		while ((b = super.read()) != -1) {
			if (b == '\n') {
				// If we have found a newline we return the contents of the
				// buffer - this could be part of an CRLF or just LF
				return baos.toByteArray();
			} else if (b == '\r') {
				// We have found a \r, which could be part of a newline feed on
				// Windows, either way, ignore it
			} else {
				// Else we add the byte to the buffer
				baos.write(b);
			}

			// Check if the buffer now exceeds the bound. If it does log it and
			// throw an exception
			if (baos.size() > this.bound) {
				// Note this is only a warn on the logging, it indicates a
				// possible malicious client, but the server will continue as
				// normal
				// having rejected the connection
				logger.warn(
						"More than {} bytes have been read without finding a newline character - will reject and close",
						this.bound);
				throw new StreamBoundExceededException(
						"More than " + this.bound + " bytes have been read without finding a newline character");
			}
		}
		// Handle the situation where the last line is not terminated by EOL but
		// instead EOF
		// If we have any remaining data and have reached the end of the file
		// return the data first
		if (baos.size() > 0) {
			return baos.toByteArray();
		} else {
			return null;
		}
	}
}
