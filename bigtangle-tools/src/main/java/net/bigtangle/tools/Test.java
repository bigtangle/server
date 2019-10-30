package net.bigtangle.tools;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

public class Test {

    public static void main(String[] args) throws IOException  {
        System.setProperty("https.proxyHost", "anwproxy.anwendungen.localnet.de");
        System.setProperty("https.proxyPort", "3128");
        URL website = new URL("http://downloads.typesafe.com/scalaide-pack/4.7.0-vfinal-oxygen-212-20170929/scala-SDK-4.7.0-vfinal-2.12-win32.win32.x86_64.zip");
        ReadableByteChannel rbc = Channels.newChannel(website.openStream());
        FileOutputStream fos = new FileOutputStream("spark-2.4.4-bin-hadoop2.7.tgz");
        fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
    }
}
