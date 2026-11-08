package id.lestari.mobile.core;

/** A file chosen by the user for a multipart upload (form fields of type "file"). */
public final class FileRef {
    public final String name;
    public final String mimeType;
    public final byte[] bytes;

    public FileRef(String name, String mimeType, byte[] bytes) {
        this.name = name;
        this.mimeType = mimeType;
        this.bytes = bytes;
    }

    @Override
    public String toString() {
        return name;
    }
}
