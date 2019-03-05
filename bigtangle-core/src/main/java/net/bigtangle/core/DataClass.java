package net.bigtangle.core;

/*
 * Block may contains data with the dataClassName and the class has a version number
 */
public abstract class DataClass {
    private long version = 1l;

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

}
