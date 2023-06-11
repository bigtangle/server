/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;

public class ContractEventInfo extends DataClass implements java.io.Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static final long FROMTIME = System.currentTimeMillis() / 1000 - 5;

 
	// valid until this date, maximum is set in Network parameter
	private Long validToTime;
	// valid from this date, maximum is set in Network parameter
	private Long validFromTime;
	// owner public address of the order for query
	private String beneficiaryAddress;

	private BigInteger offerValue;
	private String offerTokenid;

	private String contractTokenid;
	private String offerSystem;

	public ContractEventInfo() {
		super();
	}

	public ContractEventInfo(String contractTokenid, BigInteger offerValue, String offerTokenid,
			String beneficiaryAddress, Long validToTimeMilli, Long validFromTimeMilli, String offerSystem) {
		super();
		this.contractTokenid = contractTokenid;
		if (validFromTimeMilli == null) {
			this.validFromTime = FROMTIME;
		} else {
			this.validFromTime = validFromTimeMilli / 1000;
		}
		if (validToTimeMilli == null) {
			this.validToTime = validFromTime + NetworkParameters.ORDER_TIMEOUT_MAX;
		} else {
			this.validToTime = Math.min(validToTimeMilli / 1000, validFromTime + NetworkParameters.ORDER_TIMEOUT_MAX);
		}
		this.beneficiaryAddress = beneficiaryAddress;

		this.offerValue = offerValue;
		this.offerTokenid = offerTokenid;
	}

	public byte[] toByteArray() {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			DataOutputStream dos = new DataOutputStream(baos);

			dos.write(super.toByteArray());

			dos.writeLong(validToTime);
			dos.writeLong(validFromTime);
  
			dos.writeBoolean(beneficiaryAddress != null);
			if (beneficiaryAddress != null) {
				dos.writeInt(beneficiaryAddress.getBytes("UTF-8").length);
				dos.write(beneficiaryAddress.getBytes("UTF-8"));
			}
			Utils.writeNBytes(dos, offerValue.toByteArray());
			if (offerTokenid != null) {
				dos.writeInt(offerTokenid.getBytes("UTF-8").length);
				dos.write(offerTokenid.getBytes("UTF-8"));
			}

			dos.writeBoolean(contractTokenid != null);
			if (contractTokenid != null) {
				dos.writeInt(contractTokenid.getBytes("UTF-8").length);
				dos.write(contractTokenid.getBytes("UTF-8"));
			}

			dos.writeBoolean(offerSystem != null);
			if (offerSystem != null) {
				dos.writeInt(offerSystem.getBytes("UTF-8").length);
				dos.write(offerSystem.getBytes("UTF-8"));
			}

			dos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return baos.toByteArray();
	}

	public ContractEventInfo parseDIS(DataInputStream dis) throws IOException {
		super.parseDIS(dis);

		validToTime = dis.readLong();
		validFromTime = dis.readLong(); 
		beneficiaryAddress = Utils.readNBytesString(dis);
		offerValue = new BigInteger(Utils.readNBytes(dis));
		contractTokenid = Utils.readNBytesString(dis);
		offerSystem = Utils.readNBytesString(dis);

		return this;
	}

	public ContractEventInfo parse(byte[] buf) throws IOException {
		ByteArrayInputStream bain = new ByteArrayInputStream(buf);
		DataInputStream dis = new DataInputStream(bain);

		parseDIS(dis);

		dis.close();
		bain.close();
		return this;
	}

	public ContractEventInfo parseChecked(byte[] buf) throws IOException {
		try {
			return parse(buf);
		} catch (IOException e) {
			throw new RuntimeException();
		}
	}
 

	public Long getValidToTime() {
		return validToTime;
	}

	public void setValidToTime(Long validToTime) {
		this.validToTime = validToTime;
	}

	public Long getValidFromTime() {
		return validFromTime;
	}

	public void setValidFromTime(Long validFromTime) {
		this.validFromTime = validFromTime;
	}

	public String getBeneficiaryAddress() {
		return beneficiaryAddress;
	}

	public void setBeneficiaryAddress(String beneficiaryAddress) {
		this.beneficiaryAddress = beneficiaryAddress;
	}

	public BigInteger getOfferValue() {
		return offerValue;
	}

	public void setOfferValue(BigInteger offerValue) {
		this.offerValue = offerValue;
	}

	public String getOfferTokenid() {
		return offerTokenid;
	}

	public void setOfferTokenid(String offerTokenid) {
		this.offerTokenid = offerTokenid;
	}

	public String getContractTokenid() {
		return contractTokenid;
	}

	public void setContractTokenid(String contractTokenid) {
		this.contractTokenid = contractTokenid;
	}

	public String getOfferSystem() {
		return offerSystem;
	}

	public void setOfferSystem(String offerSystem) {
		this.offerSystem = offerSystem;
	}

}
