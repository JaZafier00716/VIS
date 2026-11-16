package cz.vsb.catering.business.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CommentTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 13, 10, 0);

    @Test void createTrimsAndIsUnresolved() {
        Comment c = Comment.create(5, 2, 10L, 1L, " Jana ", " Méně soli ", NOW);
        assertEquals("Jana", c.getAuthorName());
        assertEquals("Méně soli", c.getBody());
        assertFalse(c.isResolved());
        assertTrue(c.isOnItem());
        assertEquals(NOW, c.getCreatedAt());
        assertFalse(Comment.create(5, 2, null, null, "J", "B", NOW).isOnItem());
    }

    @Test void createValidates() {
        ValidationException e = assertThrows(ValidationException.class, () -> Comment.create(5, 2, null, null, " ", null, NOW));
        assertEquals(Set.of("author", "body"), e.getFieldErrors().keySet());
        e = assertThrows(ValidationException.class,
                () -> Comment.create(5, 2, null, null, "x".repeat(101), "x".repeat(2001), NOW));
        assertEquals(Set.of("author", "body"), e.getFieldErrors().keySet());
        assertDoesNotThrow(() -> Comment.create(5, 2, null, null, "x".repeat(100), "x".repeat(2000), NOW));
    }

    @Test void resolveSetsResolution() {
        Comment c = Comment.create(5, 2, null, null, "J", "B", NOW);
        c.resolve(3L, " Upraveno v revizi č. 3 ", NOW.plusHours(1));
        assertTrue(c.isResolved());
        assertEquals("Upraveno v revizi č. 3", c.getResolutionNote());
        assertEquals(3L, c.getResolvedBy());
        assertEquals(NOW.plusHours(1), c.getResolvedAt());
    }

    @Test void blankNoteBecomesNull() {
        Comment c = Comment.create(5, 2, null, null, "J", "B", NOW);
        c.resolve(3L, "  ", NOW);
        assertNull(c.getResolutionNote());
    }

    @Test void noteLongerThan200IsRejected() {
        Comment c = Comment.create(5, 2, null, null, "J", "B", NOW);
        ValidationException e = assertThrows(ValidationException.class, () -> c.resolve(3L, "x".repeat(201), NOW));
        assertTrue(e.getFieldErrors().containsKey("note"));
        assertFalse(c.isResolved());
        c.resolve(3L, "x".repeat(200), NOW);
        assertTrue(c.isResolved());
    }

    @Test void alreadyResolvedIsConflict() {
        Comment c = new Comment(5, 2, null, null, null, "J", "B", NOW, true, null, 4L, "Eva Horáková", NOW);
        ConflictException e = assertThrows(ConflictException.class, () -> c.resolve(3L, null, NOW));
        assertTrue(e.getMessage().contains("Eva Horáková"));
        assertTrue(e.getMessage().contains("2026-11-13"));
        assertEquals(4L, c.getResolvedBy());
    }
}
