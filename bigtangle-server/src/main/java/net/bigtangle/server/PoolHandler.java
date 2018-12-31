package net.bigtangle.server;

import java.io.IOException;

import javax.annotation.PostConstruct;

import org.springframework.stereotype.Service;

import net.bigtangle.pool.PoolServer;

@Service
public class PoolHandler {

    PoolServer poolServer;
    @PostConstruct
    public void startrun() {
        try {
            poolServer= new PoolServer();
            poolServer.startListeningIncomingConnections(null, 3334);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    public PoolServer getPoolServer() {
        return poolServer;
    }
    public void setPoolServer(PoolServer poolServer) {
        this.poolServer = poolServer;
    }
    
}
