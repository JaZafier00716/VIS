package cz.vsb.catering.business.domain;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Customer comment on a revision or on one menu item (ARC in the schema); resolved in UC13. */
public class Comment extends DomainObject {
    public static final int MAX_BODY = 2000;
    public static final int MAX_RESOLUTION_NOTE = 200;

    private final long menuId;
    private final int revisionNo;
    private final Long categoryId;
    private final Long productId;
    private final String productName;
    private final String authorName;
    private final String body;
    private final LocalDateTime createdAt;
    private boolean resolved;
    private String resolutionNote;
    private Long resolvedBy;
    private String resolvedByName;
    private LocalDateTime resolvedAt;

    public Comment(long menuId, int revisionNo, Long categoryId, Long productId, String productName,
                   String authorName, String body, LocalDateTime createdAt, boolean resolved,
                   String resolutionNote, Long resolvedBy, String resolvedByName, LocalDateTime resolvedAt) {
        this.menuId = menuId;
        this.revisionNo = revisionNo;
        this.categoryId = categoryId;
        this.productId = productId;
        this.productName = productName;
        this.authorName = authorName;
        this.body = body;
        this.createdAt = createdAt;
        this.resolved = resolved;
        this.resolutionNote = resolutionNote;
        this.resolvedBy = resolvedBy;
        this.resolvedByName = resolvedByName;
        this.resolvedAt = resolvedAt;
    }

    /** UC11 step 7: author and text are required, text has at most 2000 characters.
     *  categoryId/productId are null for a comment on the whole menu. */
    public static Comment create(long menuId, int revisionNo, Long categoryId, Long productId,
                                 String authorName, String body, LocalDateTime now) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (authorName == null || authorName.isBlank()) errors.put("author", "Vyplňte své jméno.");
        else if (authorName.trim().length() > 100) errors.put("author", "Jméno má nejvýše 100 znaků.");
        if (body == null || body.isBlank()) errors.put("body", "Napište text komentáře.");
        else if (body.trim().length() > MAX_BODY) errors.put("body", "Komentář má nejvýše 2000 znaků.");
        ValidationException.throwIfAny(errors);
        return new Comment(menuId, revisionNo, categoryId, productId, null, authorName.trim(), body.trim(), now,
                false, null, null, null, null);
    }

    /** UC13 steps 4-5. Throws ConflictException when someone resolved it in the meantime (4.1). */
    public void resolve(long userId, String note, LocalDateTime now) {
        if (resolved)
            throw new ConflictException("Komentář již vyřídil " + (resolvedByName == null ? "jiný pracovník" : resolvedByName)
                    + (resolvedAt == null ? "" : " " + resolvedAt.toLocalDate()) + ".");
        String trimmed = note == null ? null : note.trim();
        if (trimmed != null && trimmed.length() > MAX_RESOLUTION_NOTE) {
            String msg = "Poznámka má nejvýše 200 znaků.";
            throw new ValidationException(msg, Map.of("note", msg));
        }
        resolved = true;
        resolutionNote = trimmed == null || trimmed.isEmpty() ? null : trimmed;
        resolvedBy = userId;
        resolvedAt = now;
    }

    public boolean isOnItem() { return productId != null; }

    public long getMenuId() { return menuId; }
    public int getRevisionNo() { return revisionNo; }
    public Long getCategoryId() { return categoryId; }
    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getAuthorName() { return authorName; }
    public String getBody() { return body; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean isResolved() { return resolved; }
    public String getResolutionNote() { return resolutionNote; }
    public Long getResolvedBy() { return resolvedBy; }
    public String getResolvedByName() { return resolvedByName; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
}
