package cz.vsb.catering.business.domain;

import java.util.Map;

/** Composite leaf: file whose content is in the document storage; only metadata is in Oracle. */
public class Document extends ProjectNode {
    public static final long MAX_SIZE_BYTES = 20L * 1024 * 1024;

    private final String contentType;
    private final long sizeBytes;
    private String storageKey;

    public Document(String name, String contentType, long sizeBytes, String storageKey) {
        super(name);
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
    }

    /** UC02 7.4: at most 20 MB. */
    public static void checkSize(long sizeBytes) {
        if (sizeBytes > MAX_SIZE_BYTES) {
            String msg = "Soubor je větší než povolených 20 MB.";
            throw new ValidationException(msg, Map.of("file", msg));
        }
    }

    @Override public NodeType type() { return NodeType.DOCUMENT; }

    @Override public boolean isDeletable() { return true; }

    @Override public int countDocuments() { return 1; }

    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }

    /** Human readable size, e.g. "1,2 MB". */
    public String formattedSize() {
        if (sizeBytes < 1024) return sizeBytes + " B";
        if (sizeBytes < 1024 * 1024) return String.format("%.1f kB", sizeBytes / 1024.0);
        return String.format("%.1f MB", sizeBytes / (1024.0 * 1024));
    }
}
