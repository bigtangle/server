package net.bigtangle.server;

import java.io.IOException;

import org.junit.Test;

import net.bigtangle.docker.DockerHelper;

public class RemoveDBTest {

    @Test
    public void blockType() throws IOException, InterruptedException { 
        DockerHelper.shellExecute( "  docker rm -f  mysql-test "  );

    }


   
    
}
