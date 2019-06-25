package net.bigtangle.core;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MultiSignAddressPacker implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private List<MultiSignAddress> multiSignAddresseList;

    private int requiredNumber;

    public List<MultiSignAddress> getMultiSignAddresseList() {
        return multiSignAddresseList;
    }

    public int getRequiredNumber() {
        return requiredNumber;
    }

    public MultiSignAddressPacker(List<MultiSignAddress> multiSignAddresseList, int requiredNumber) {
        this.multiSignAddresseList = multiSignAddresseList;
        this.requiredNumber = requiredNumber;
    }

    public boolean checkPermissionedMultiSignAddressRequiredNumber(List<MultiSignBy> multiSignBies) {
        int requiredNumber_ = 0;

        Set<ByteBuffer> multiSignByRes = new HashSet<ByteBuffer>();
        for (MultiSignBy multiSignBy : multiSignBies) {
            ByteBuffer pubKey = ByteBuffer.wrap(Utils.HEX.decode(multiSignBy.getPublickey()));
            multiSignByRes.add(pubKey);
        }

        for (MultiSignAddress multiSignAddress : this.multiSignAddresseList) {
            byte[] pubKey = Utils.HEX.decode(multiSignAddress.getPubKeyHex());
            if (multiSignByRes.contains(ByteBuffer.wrap(pubKey))) {
                requiredNumber_++;
            }
        }
        return requiredNumber_ >= requiredNumber;
    }
}
