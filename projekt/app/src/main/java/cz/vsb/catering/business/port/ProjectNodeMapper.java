package cz.vsb.catering.business.port;

import cz.vsb.catering.business.domain.Folder;
import cz.vsb.catering.business.domain.ProjectNode;

/** Data Mapper for project_nodes (Single Table Inheritance: FOLDER, DOCUMENT, MENU). */
public interface ProjectNodeMapper {
    /** Loads the whole tree of a project in one query and returns its root folder (Composite).
     *  Menu nodes get their menuId and the flag of an active, unexpired share link. */
    Folder loadTree(long projectId);

    /** Inserts one node (parent must be stored) and sets its id. */
    void insert(ProjectNode node);

    /** Deletes the node row; for a MENU the menus row and its revisions go by ON DELETE CASCADE. */
    void delete(ProjectNode node);
}
