package cz.vsb.catering.business.e2e;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cz.vsb.catering.business.domain.Folder;
import cz.vsb.catering.business.domain.Project;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** A-18: created_at defaults to LOCALTIMESTAMP, so a new node is not shifted by the DB zone offset. */
class CreatedAtTimeZoneTest extends E2eBaseTest {
    @Test
    void newNodeCreatedAtIsCloseToNow() {
        Project project = newProject("E2E A-18 čas");
        Folder root = projects.loadTree(project.getId());
        assertNotNull(root.getCreatedAt());
        long minutes = Math.abs(Duration.between(root.getCreatedAt(), LocalDateTime.now()).toMinutes());
        assertTrue(minutes < 2, "created_at is " + root.getCreatedAt() + ", now is " + LocalDateTime.now());
    }
}
