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
import java.util.ArrayList;
import java.util.List;

public class UserSettingDataInfo extends DataClass implements java.io.Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = -8908923887095777610L;
    private List<UserSettingData> userSettingDatas = new ArrayList<UserSettingData>();
    public byte[] toByteArray() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            DataOutputStream dos = new DataOutputStream(baos);
            dos.write(super.toByteArray());
            dos.writeInt(userSettingDatas.size());
            for (UserSettingData c : userSettingDatas)
                dos.write(c.toByteArray());

            dos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    @Override
    public UserSettingDataInfo parseDIS(DataInputStream dis) throws IOException {
        super.parseDIS(dis);
        userSettingDatas = new ArrayList<>();
        int size = dis.readInt();
        for (int i = 0; i < size; i++) {
            userSettingDatas.add(new UserSettingData().parseDIS(dis));
        }

        return this;
    }

    public UserSettingDataInfo parse(byte[] buf) throws IOException {
        ByteArrayInputStream bain = new ByteArrayInputStream(buf);
        DataInputStream dis = new DataInputStream(bain);
        parseDIS(dis);
        dis.close();
        bain.close();
        return this;
    }


    public List<UserSettingData> getUserSettingDatas() {
        return userSettingDatas;
    }

    public void setUserSettingDatas(List<UserSettingData> userSettingDatas) {
        this.userSettingDatas = userSettingDatas;
    }

}
