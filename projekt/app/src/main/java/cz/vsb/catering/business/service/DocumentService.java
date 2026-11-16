package cz.vsb.catering.business.service;

import cz.vsb.catering.business.domain.Document;
import cz.vsb.catering.business.domain.Folder;
import cz.vsb.catering.business.domain.NodeCreator;
import cz.vsb.catering.business.domain.Project;
import cz.vsb.catering.business.domain.ProjectNode;
import cz.vsb.catering.business.domain.ValidationException;
import cz.vsb.catering.business.port.DocumentStorage;
import cz.vsb.catering.business.port.ProjectMapper;
import cz.vsb.catering.business.port.ProjectNodeMapper;
import cz.vsb.catering.business.port.TransactionRunner;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Facade for folders and documents (UC02 steps 5-7, 7.1-7.12, UC19). Works on the two storages:
 *  metadata in Oracle (ProjectNodeMapper), content in the document folder (DocumentStorage).
 *  Every write is refused for an archived project (UC03). */
public class DocumentService {
    private final ProjectNodeMapper nodes;
    private final ProjectMapper projects;
    private final DocumentStorage storage;
    private final TransactionRunner tx;

    public DocumentService(ProjectNodeMapper nodes, ProjectMapper projects, DocumentStorage storage, TransactionRunner tx) {
        this.nodes = nodes;
        this.projects = projects;
        this.storage = storage;
        this.tx = tx;
    }

    /** UC02 step 6; DuplicateNameException when the name is taken (6.1). */
    public Folder createFolder(Folder parent, String name) {
        ProjectNode folder = new NodeCreator.FolderCreator().createIn(parent, name);
        insertOrUndo(parent, folder);
        return (Folder) folder;
    }

    /** UC02 step 7: content of the active folder. */
    public List<ProjectNode> listContent(Folder folder) { return folder.getChildren(); }

    /** UC02 7.4-7.6: checks size and name, stores the file, then the metadata.
     *  If the database insert fails, the stored file is deleted again. */
    public Document createDocument(Folder parent, Path file, String name) {
        return createDocument(parent, file, name, null);
    }

    /** Same with a known content type (UC14 stores generated PDFs); null means detect from the file. */
    public Document createDocument(Folder parent, Path file, String name, String contentType) {
        long size;
        try {
            size = Files.size(file);
            if (contentType == null) contentType = Files.probeContentType(file);
        } catch (IOException e) {
            throw new ValidationException("Soubor nelze přečíst: " + e.getMessage());
        }
        if (contentType == null) contentType = "application/octet-stream";
        Document document = (Document) new NodeCreator.DocumentCreator(contentType, size).createIn(parent, name);
        String key = "p" + parent.getProjectId() + "/" + UUID.randomUUID() + "-" + safe(document.getName());
        document.setStorageKey(key);
        try {
            storage.store(key, file);
        } catch (IOException e) {
            parent.remove(document);
            throw new UncheckedIOException("Soubor se nepodařilo uložit do úložiště dokumentů.", e);
        }
        try {
            insertOrUndo(parent, document);
        } catch (RuntimeException e) {
            deleteQuietly(key);
            throw e;
        }
        return document;
    }

    /** UC02 7.11: copies the selected documents into the target folder; returns how many. */
    public int downloadDocuments(List<Document> documents, Path targetDir) {
        int count = 0;
        Path dir = targetDir.toAbsolutePath().normalize();
        for (Document d : documents) {
            try {
                storage.retrieve(d.getStorageKey(), freeTarget(dir, d.getName()));
                count++;
            } catch (IOException e) {
                throw new UncheckedIOException("Dokument „" + d.getName() + "“ se nepodařilo stáhnout.", e);
            }
        }
        return count;
    }

    /** Target file inside dir; refuses names that leave it (old rows may hold bad names), never overwrites:
     *  an existing file gives "name (2).ext", "name (3).ext"... */
    private static Path freeTarget(Path dir, String name) {
        try {
            Path target = dir.resolve(name).normalize();
            if (!dir.equals(target.getParent()))
                throw new ValidationException("Dokument „" + name + "“ má neplatný název a nelze jej stáhnout.");
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name, ext = dot > 0 ? name.substring(dot) : "";
            for (int n = 2; Files.exists(target); n++) target = dir.resolve(base + " (" + n + ")" + ext);
            return target;
        } catch (InvalidPathException e) {
            throw new ValidationException("Dokument „" + name + "“ má neplatný název a nelze jej stáhnout.");
        }
    }

    /** UC19: deletes all selected nodes or none. Rules are checked on a freshly loaded tree:
     *  a non-empty folder or a menu with an active share link blocks the whole deletion. */
    public void deleteNodes(List<ProjectNode> selected) {
        if (selected.isEmpty()) return;
        long projectId = selected.get(0).getProjectId();
        List<String> keys = tx.inTransaction(() -> {
            requireOpenProject(projectId);
            Folder fresh = nodes.loadTree(projectId);
            List<String> problems = new ArrayList<>();
            List<ProjectNode> toDelete = new ArrayList<>();
            for (ProjectNode s : selected) {
                ProjectNode node = fresh.find(s.getId()).orElse(null);
                if (node == null) continue;                        // already deleted by someone else
                if (node.getParent() == null) problems.add("Kořenovou složku projektu nelze smazat.");
                else if (!node.isDeletable()) problems.add(reason(node));
                toDelete.add(node);
            }
            if (!problems.isEmpty())
                throw new ValidationException("Nic nebylo smazáno. " + String.join(" ", problems));
            List<String> storageKeys = new ArrayList<>();
            for (ProjectNode node : toDelete) {
                nodes.delete(node);
                if (node instanceof Document d) storageKeys.add(d.getStorageKey());
            }
            return storageKeys;
        });
        keys.forEach(this::deleteQuietly);                         // files only after the commit
        for (ProjectNode s : selected) if (s.getParent() != null) s.getParent().remove(s);
    }

    private static String reason(ProjectNode node) {
        return switch (node.type()) {
            case FOLDER -> "Složka „" + node.getName() + "“ není prázdná.";
            case MENU -> "Menu „" + node.getName() + "“ má aktivní sdílený odkaz.";
            case DOCUMENT -> "Dokument „" + node.getName() + "“ nelze smazat.";
        };
    }

    /** UC03: an archived project is read-only, its content tree included. */
    private void requireOpenProject(long projectId) {
        tx.inTransaction(() -> projects.findById(projectId)).ifPresent(Project::requireNotArchived);
    }

    private void insertOrUndo(Folder parent, ProjectNode node) {
        try {
            tx.inTransaction(() -> {
                requireOpenProject(parent.getProjectId());         // same transaction as the insert (UC03)
                nodes.insert(node);
                return null;
            });
        } catch (RuntimeException e) {
            parent.remove(node);
            throw e;
        }
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (IOException e) {
            // shortcut: an orphaned file only wastes space; a cleanup job could remove it later
            System.err.println("Soubor " + key + " se nepodařilo smazat z úložiště: " + e.getMessage());
        }
    }

    /** ASCII-only key part: accents stripped, everything outside [A-Za-z0-9._-] becomes "_". */
    private static String safe(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
