package net.bigtangle.server.performance;

import java.util.Arrays;

import net.bigtangle.core.NetworkParameters;
import net.bigtangle.params.MainNetParams;
import net.bigtangle.wallet.ServerPool;

public class ServerPoolTest {
    public static NetworkParameters networkParameters = MainNetParams.get();

    public static void main(String[] args) throws  Exception{
        
          String[] urls = new String[]{ "https://p.bigtangle.de:8088",
                  "https://p.bigtangle.org:8088", "https://p.bigtangle.info:8088"  };
          ServerPool serverPool= new ServerPool();
          serverPool.addServers(Arrays.asList(urls));
          System.out.println(serverPool.getServer());
       }
    
}
