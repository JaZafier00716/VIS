package cz.vsb.catering.business.domain;

/** Composite leaf: a menu in the project tree. Its revisions live in menu_revisions (menuId). */
public class Menu extends ProjectNode {
    private Long menuId;
    private boolean activeShareLink;

    public Menu(String name) { super(name); }

    @Override public NodeType type() { return NodeType.MENU; }

    /** UC19: a menu with an active, not expired share link cannot be deleted. */
    @Override public boolean isDeletable() { return !activeShareLink; }

    @Override public int countDocuments() { return 0; }

    public Long getMenuId() { return menuId; }
    public void setMenuId(Long menuId) { this.menuId = menuId; }
    public boolean hasActiveShareLink() { return activeShareLink; }
    public void setActiveShareLink(boolean activeShareLink) { this.activeShareLink = activeShareLink; }
}
