package net.bigtangle.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class KeyValueList extends DataClass implements java.io.Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private List<KeyValue> keyvalues;

    public void addKeyvalue(KeyValue kv) {
        if (keyvalues == null) {
            keyvalues = new ArrayList<KeyValue>();
            keyvalues.add(kv);
        }else {
            keyvalues.add(kv);
        }
    }


    public byte[] toByteArray() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            DataOutputStream dos = new DataOutputStream(baos); 
            
            dos.writeInt(keyvalues.size());
            for (KeyValue c : keyvalues)
                dos.write(c.toByteArray());
            
            dos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }
    
    @Override
    public KeyValueList parseDIS(DataInputStream dis) throws IOException {
        super.parseDIS(dis);

        keyvalues = new ArrayList<>();
        int size = dis.readInt();
        for (int i = 0; i < size; i++) {
            keyvalues.add(new KeyValue().parseDIS(dis));
        }
        
        return this;
    }

    public KeyValueList parse(byte[] buf) throws IOException {
        ByteArrayInputStream bain = new ByteArrayInputStream(buf);
        DataInputStream dis = new DataInputStream(bain);

        parseDIS(dis);
        
        dis.close();
        bain.close();
        return this;
    }


    public List<KeyValue> getKeyvalues() {
        return keyvalues;
    }

    public void setKeyvalues(List<KeyValue> keyvalues) {
        this.keyvalues = keyvalues;
    }


}
