/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
/*
 * Copyright 2013 Google Inc.
 * Copyright 2014 Andreas Schildbach
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.bigtangle.script;

import static com.google.common.base.Preconditions.checkState;
import static net.bigtangle.script.ScriptOpCodes.OP_0;
import static net.bigtangle.script.ScriptOpCodes.OP_1;
import static net.bigtangle.script.ScriptOpCodes.OP_16;
import static net.bigtangle.script.ScriptOpCodes.OP_1NEGATE;
import static net.bigtangle.script.ScriptOpCodes.OP_PUSHDATA1;
import static net.bigtangle.script.ScriptOpCodes.OP_PUSHDATA2;
import static net.bigtangle.script.ScriptOpCodes.OP_PUSHDATA4;
import static net.bigtangle.script.ScriptOpCodes.getOpCodeName;
import static net.bigtangle.script.ScriptOpCodes.getPushDataName;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

import javax.annotation.Nullable;

import com.google.common.base.Objects;

import net.bigtangle.core.Utils;

/**
 * A script element that is either a data push (signature, pubkey, etc) or a
 * non-push (logic, numeric, etc) operation.
 */
public class ScriptChunk {
	/** Operation to be executed. Opcodes are defined in {@link ScriptOpCodes}. */
	public   int opcode;
	/**
	 * For push operations, this is the vector to be pushed on the stack. For
	 * {@link ScriptOpCodes#OP_0}, the vector is empty. Null for non-push
	 * operations.
	 */
	@Nullable
	public   byte[] data;
	private int startLocationInProgram;

	public ScriptChunk(int opcode, byte[] data) {
		this(opcode, data, -1);
	}

	public ScriptChunk(int opcode, byte[] data, int startLocationInProgram) {
		this.opcode = opcode;
		this.data = data;
		this.startLocationInProgram = startLocationInProgram;
	}

	public ScriptChunk() {
		 
	}

	public boolean equalsOpCode(int opcode) {
		return opcode == this.opcode;
	}

	/**
	 * If this chunk is a single byte of non-pushdata content (could be OP_RESERVED
	 * or some invalid Opcode)
	 */
	public boolean isOpCode() {
		return opcode > OP_PUSHDATA4;
	}

	/**
	 * Returns true if this chunk is pushdata content, including the single-byte
	 * pushdatas.
	 */
	public boolean isPushData() {
		return opcode <= OP_16;
	}

	public int getStartLocationInProgram() {
		checkState(startLocationInProgram >= 0);
		return startLocationInProgram;
	}

	/** If this chunk is an OP_N opcode returns the equivalent integer value. */
	public int decodeOpN() {
		checkState(isOpCode());
		return Script.decodeFromOpN(opcode);
	}

	/**
	 * Called on a pushdata chunk, returns true if it uses the smallest possible way
	 * (according to BIP62) to push the data.
	 */
	public boolean isShortestPossiblePushData() {
		checkState(isPushData());
		if (data == null)
			return true; // OP_N
		if (data.length == 0)
			return opcode == OP_0;
		if (data.length == 1) {
			byte b = data[0];
			if (b >= 0x01 && b <= 0x10)
				return opcode == OP_1 + b - 1;
			if ((b & 0xFF) == 0x81)
				return opcode == OP_1NEGATE;
		}
		if (data.length < OP_PUSHDATA1)
			return opcode == data.length;
		if (data.length < 256)
			return opcode == OP_PUSHDATA1;
		if (data.length < 65536)
			return opcode == OP_PUSHDATA2;

		// can never be used, but implemented for completeness
		return opcode == OP_PUSHDATA4;
	}

	public byte[] toByteArray() {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			DataOutputStream dos = new DataOutputStream(baos);

			dos.write(opcode);
			 Utils.writeNBytes (dos, data);
			dos.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return baos.toByteArray();
	}

    public  ScriptChunk parse(DataInputStream dis) throws IOException {
    	this.opcode =  dis.read() ; 
    	this.data = Utils.readNBytes(dis);; 
        
        return this;
    }
	public void write(OutputStream stream) throws IOException {
		DataOutputStream dos = new DataOutputStream(stream);
		if (isOpCode()) {
			checkState(data == null);
			stream.write(opcode);
		} else if (data != null) {
			dos.write(opcode);
			dos.writeInt(data.length);
			dos.write(data);

		} else {
			dos.write(opcode); // smallNum
		}
	}

	@Override
	public String toString() {
		StringBuilder buf = new StringBuilder();
		if (isOpCode()) {
			buf.append(getOpCodeName(opcode));
		} else if (data != null) {
			// Data chunk
			buf.append(getPushDataName(opcode)).append("[").append(Utils.HEX.encode(data)).append("]");
		} else {
			// Small num
			buf.append(Script.decodeFromOpN(opcode));
		}
		return buf.toString();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		ScriptChunk other = (ScriptChunk) o;
		return opcode == other.opcode && startLocationInProgram == other.startLocationInProgram
				&& Arrays.equals(data, other.data);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(opcode, startLocationInProgram, Arrays.hashCode(data));
	}
}
