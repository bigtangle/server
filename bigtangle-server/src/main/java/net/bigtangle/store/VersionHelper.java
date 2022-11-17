package net.bigtangle.store;

import org.apache.commons.lang3.ClassPathUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Comparator;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Class which displays the current versions as from pom.properties and
 * *-git.properties files in the jar.
 */
public class VersionHelper {
    private final StringBuilder sb = new StringBuilder();

    private static class Versions {
        private final String moduleName;
        private final Properties properties;

        public Versions(String moduleName, Properties properties) {
            this.moduleName = moduleName;
            this.properties = properties;
        }
    }

    /**
     * May be called from the command line.
     *
     * @param args
     *            no args expected
     */
    public static void main(String[] args) {
        System.out.println(new VersionHelper().getVersionInfo(VersionHelper.class));
    }

    /**
     * Collects the version info.
     *
     * @param clazz
     *            the class for which we want the version info
     * @return the version info
     */
    public String getVersionInfo(Class<?> clazz) {
        sb.setLength(0);
        try {
            File jarFile = findThisJarFile(
                    ClassPathUtils.toFullyQualifiedPath(clazz, clazz.getSimpleName() + ".class"));
            if (jarFile != null) {
                logGitVersionsInZipFile(jarFile);
                logMavenVersionsInZipFile(jarFile);
                extractSmsBundleGitProps();
            }
        } catch (Exception e) {
            sb.append(ExceptionUtils.getStackTrace(e));
        }
        return sb.toString();
    }

    private File findThisJarFile(String resourcePath) throws UnsupportedEncodingException {
        URL res = getSomeResourceFromThisJarFile(resourcePath);
        if (res == null) {
            sb.append(String.format("getVersionInfo: no resource %s found", resourcePath));
            return null;
        }
        String resString = URLDecoder.decode(res.toString(), "UTF-8");
        int endX = resString.indexOf('!');
        if (resString.startsWith("jar:file:/") && endX > 4
                && resString.substring(endX - 4, endX).equalsIgnoreCase(".jar")) {
            return new File(resString.substring(9, endX));
        }
        sb.append("getVersionInfo: resource is somehow unexpected URL: ");
        sb.append(resString);
        return null;
    }

    URL getSomeResourceFromThisJarFile(String resourcePath) {
        return VersionHelper.class.getClassLoader().getResource(resourcePath);
    }

    private void logGitVersionsInZipFile(File jarFile) throws IOException {
        String START_STRING = "META-INF/";
        String END_STRING = "-git.properties";
        sb.append("Git version:\n");
        traverseResources(jarFile, START_STRING, END_STRING, this::extractGitVersionAndLog);
    }

    private void logMavenVersionsInZipFile(File jarFile) throws IOException {
        String START_STRING = "META-INF/maven/de.rewe.msl.ie/";
        String END_STRING = "/pom.properties";
        sb.append("Maven versions:\n");
        traverseResources(jarFile, START_STRING, END_STRING, this::extractMavenVersionAndLog);
    }

    private void traverseResources(File jarFile, String START_STRING, String END_STRING,
            BiConsumer<Versions, StringBuilder> extractAndAddToLog) throws IOException {
        ZipFile zf = new ZipFile(jarFile);
        zf.stream()
                .filter(ze -> !ze.isDirectory() && ze.getName().startsWith(START_STRING)
                        && ze.getName().endsWith(END_STRING))
                .map(ze -> new Versions(
                        ze.getName().substring(START_STRING.length(), ze.getName().length() - END_STRING.length()),
                        readAsProperties(zf, ze)))
                .sorted(Comparator.comparing(v -> v.moduleName)).forEach(v -> extractAndAddToLog.accept(v, sb));
    }

    private Properties readAsProperties(ZipFile zf, ZipEntry ze) {
        Properties p = new Properties();
        try {
            p.load(zf.getInputStream(ze));
        } catch (IOException e) {
            e.printStackTrace();
        }
        p.setProperty("addedCreationDate", ze.getLastModifiedTime().toString());
        return p;
    }

    private void extractGitVersionAndLog(Versions versions, StringBuilder out) {
        out.append(String.format("  %s: %s, %s\n", versions.moduleName,
                versions.properties.get("git.commit.id.describe"), versions.properties.get("git.commit.time")));
    }

    private void extractMavenVersionAndLog(Versions versions, StringBuilder out) {
        out.append(String.format("  %s: %s (%s)\n", versions.moduleName, versions.properties.get("version"),
                versions.properties.get("addedCreationDate")));
    }

    private void extractSmsBundleGitProps() {
        sb.append("SMS bundle git version:\n");
        String bundleGitResourceName = getBundleGitResourceName();
        try {
            Properties p = new Properties();
            p.load(ClassLoader.getSystemClassLoader().getResourceAsStream(bundleGitResourceName));
            sb.append(
                    String.format("  git.version: %s %s\n", p.get("git.commit.id.describe"), p.get("git.commit.time")));
            sb.append(String.format("  build.version: %s (%s)", p.get("git.build.version"), p.get("git.build.time")));
        } catch (Exception e) {
            sb.append("  exception during reading ");
            sb.append(bundleGitResourceName);
            sb.append("\n  ");
            sb.append(e.getMessage());
        }
        sb.append("\n");
    }

    String getBundleGitResourceName() {
        return "sms-bundle-git.properties";
    }
}
