package cz.vsb.catering.business.domain;

import java.util.Map;

/** Factory Method (GoF): the "Nový" button creates a node through a creator; subclasses decide
 *  which ProjectNode type is created, the common steps (name check, adding to the folder) stay here. */
public abstract class NodeCreator {

    /** Factory method implemented by FolderCreator, MenuCreator and DocumentCreator. */
    protected abstract ProjectNode createNode(String name);

    /** Creates the node and places it in the folder; throws DuplicateNameException on a name clash. */
    public final ProjectNode createIn(Folder parent, String name) {
        if (name == null || name.isBlank())
            throw new ValidationException("Název je povinný.", Map.of("name", "Název je povinný."));
        if (name.trim().length() > 100)
            throw new ValidationException("Název má nejvýše 100 znaků.", Map.of("name", "Název má nejvýše 100 znaků."));
        String trimmed = name.trim();
        if (trimmed.equals(".") || trimmed.equals("..") || trimmed.chars().anyMatch(c -> c == '/' || c == '\\' || Character.isISOControl(c)))
            throw new ValidationException("Název nesmí obsahovat lomítka ani řídicí znaky.",
                    Map.of("name", "Název nesmí obsahovat lomítka ani řídicí znaky."));
        ProjectNode node = createNode(trimmed);
        parent.add(node);
        return node;
    }

    public static class FolderCreator extends NodeCreator {
        @Override protected ProjectNode createNode(String name) { return new Folder(name); }
    }

    public static class MenuCreator extends NodeCreator {
        @Override protected ProjectNode createNode(String name) { return new Menu(name); }
    }

    public static class DocumentCreator extends NodeCreator {
        private final String contentType;
        private final long sizeBytes;

        public DocumentCreator(String contentType, long sizeBytes) {
            Document.checkSize(sizeBytes);
            this.contentType = contentType;
            this.sizeBytes = sizeBytes;
        }

        /** The storage key is assigned by DocumentService when the file is stored. */
        @Override protected ProjectNode createNode(String name) {
            return new Document(name, contentType, sizeBytes, null);
        }
    }
}
