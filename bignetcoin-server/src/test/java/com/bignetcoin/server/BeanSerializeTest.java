package com.bignetcoin.server;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import net.bigtangle.utils.BeanSerializeUtil;

public class BeanSerializeTest {

    @Test
    public void serialize() throws Exception {
        User user = new User();
        
        user.setId(100);
        user.setSkip(false);
        user.setUsername("Test");
        List<String> password = new ArrayList<String>();
        password.add("1");
        password.add("2");
        user.setPassword(password);
        
        byte[] buf = BeanSerializeUtil.serializer(user);
        User user0 = BeanSerializeUtil.deserialize(buf, User.class);
        
        assertTrue(user0.getUsername().equals(user.getUsername()));
        assertTrue(user0.isSkip() == false);
        assertTrue(user0.getPassword().size() == user.getPassword().size());
    }
}
