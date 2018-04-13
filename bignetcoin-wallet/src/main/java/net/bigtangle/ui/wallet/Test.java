/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.ui.wallet;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;

public class Test {

    public static void main(String[] args) throws Exception {
        Path dir = Paths.get(
                "F:\\java\\eclipse-jee-oxygen-2-win32-x86_64\\coinWP\\bignetcoin\\bignetcoin-wallet\\src\\main\\resources\\net\\bigtangle\\ui\\wallet");
        Path src = Paths.get(
                "F:\\java\\eclipse-jee-oxygen-2-win32-x86_64\\coinWP\\bignetcoin\\bignetcoin-wallet\\src\\main\\resources\\net\\bigtangle\\ui\\wallet\\message.properties");
        BufferedWriter writer = Files.newBufferedWriter(src, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        Set<String> set = new HashSet<String>();
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".fxml")) {
                    BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains(" text=")) {
                            int start = line.indexOf(" text=");
                            String temp = line.substring(start + 7);
                            int end = temp.indexOf("\"");
                            set.add(temp.substring(0, end) + "=" + temp.substring(0, end) + "\n");
                        }

                    }
                    reader.close();
                }
                return super.visitFile(file, attrs);
            }
        });

        for (String string : set) {
            writer.write(string);

        }
        writer.close();
    }

}
