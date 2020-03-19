/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.data.identity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.bigtangle.core.DataClass;
import net.bigtangle.core.KeyValue;

public class KeyvalueList extends DataClass implements java.io.Serializable {
    private static final long serialVersionUID = -1965429530354669140L;
    private List<KeyValue> keyvaluelist = new ArrayList<KeyValue>();

    public byte[] toByteArray() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            DataOutputStream dos = new DataOutputStream(baos);
            dos.write(super.toByteArray());
            dos.writeInt(keyvaluelist.size());
            for (KeyValue c : keyvaluelist)
                dos.write(c.toByteArray());

            dos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    @Override
    public KeyvalueList parseDIS(DataInputStream dis) throws IOException {
        super.parseDIS(dis);
        keyvaluelist = new ArrayList<>();
        int size = dis.readInt();
        for (int i = 0; i < size; i++) {
            keyvaluelist.add(new KeyValue().parseDIS(dis));
        }

        return this;
    }

    public KeyvalueList parse(byte[] buf) throws IOException {
        ByteArrayInputStream bain = new ByteArrayInputStream(buf);
        DataInputStream dis = new DataInputStream(bain);
        parseDIS(dis);
        dis.close();
        bain.close();
        return this;
    }

  
}
