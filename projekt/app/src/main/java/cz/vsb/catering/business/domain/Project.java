package cz.vsb.catering.business.domain;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** One catering order (event). Owns exactly one root folder of its content tree (UC02). */
public class Project extends DomainObject {
    public static final int MAX_NOTE = 2000;
    private final Customer customer;
    private final long responsibleUserId;
    private String name;
    private LocalDate eventDate;
    private String eventPlace;
    private int guestCount;
    private ProjectStatus status;
    private String note;
    private Folder rootFolder;

    public Project(Customer customer, long responsibleUserId, String name, LocalDate eventDate,
                   String eventPlace, int guestCount, ProjectStatus status, String note) {
        this.customer = customer;
        this.responsibleUserId = responsibleUserId;
        this.name = name;
        this.eventDate = eventDate;
        this.eventPlace = eventPlace;
        this.guestCount = guestCount;
        this.status = status;
        this.note = note;
    }

    /** UC02 step 4: validates the form, the project starts IN_PROGRESS with an empty root folder. */
    public static Project create(String name, Customer customer, LocalDate eventDate, String eventPlace,
                                 Integer guestCount, long responsibleUserId, LocalDate today) {
        Map<String, String> errors = validate(name, eventDate, eventPlace, guestCount, today, null);
        if (customer == null) errors.put("customer", "Vyberte zákazníka.");
        ValidationException.throwIfAny(errors);
        Project project = new Project(customer, responsibleUserId, name.trim(), eventDate, eventPlace.trim(),
                guestCount, ProjectStatus.IN_PROGRESS, null);
        project.rootFolder = new Folder(name.trim());
        return project;
    }

    /** UC03: changes the event data with the same rules as UC02, but an unchanged date may already
     *  be in the past. The customer and the responsible worker stay (the customer is fixed by a DB trigger).
     *  Rule for archived projects: the project is fully read-only, so update and archive are refused
     *  and the services refuse every write into its content tree (requireNotArchived). */
    public void update(String name, LocalDate eventDate, String eventPlace, Integer guestCount, String note,
                       LocalDate today) {
        requireNotArchived();
        Map<String, String> errors = validate(name, eventDate, eventPlace, guestCount, today, this.eventDate);
        if (note != null && note.trim().length() > MAX_NOTE) errors.put("note", "Poznámka má nejvýše " + MAX_NOTE + " znaků.");
        ValidationException.throwIfAny(errors);
        this.name = name.trim();
        this.eventDate = eventDate;
        this.eventPlace = eventPlace.trim();
        this.guestCount = guestCount;
        this.note = note == null || note.isBlank() ? null : note.trim();
    }

    /** UC03: after the event the worker archives the project; an archived project cannot be edited. */
    public void archive() {
        requireNotArchived();
        status = ProjectStatus.ARCHIVED;
    }

    /** UC03: an archived project can be taken back to work (IN_PROGRESS), which unlocks its content again. */
    public void reactivate() {
        if (!isArchived()) throw new ValidationException("Projekt není archivovaný, není co obnovovat.");
        status = ProjectStatus.IN_PROGRESS;
    }

    public boolean isArchived() { return status == ProjectStatus.ARCHIVED; }

    /** UC03: an archived project is read-only including its folders, documents, menus and comments. */
    public void requireNotArchived() {
        if (isArchived()) throw new ValidationException("Projekt je archivovaný, nelze jej měnit.");
    }

    /** Common form rules of UC02 and UC03; a date equal to unchangedDate is not checked against today. */
    private static Map<String, String> validate(String name, LocalDate eventDate, String eventPlace, Integer guestCount,
                                                LocalDate today, LocalDate unchangedDate) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (name == null || name.isBlank()) errors.put("name", "Název projektu je povinný.");
        else if (name.trim().length() > 120) errors.put("name", "Název má nejvýše 120 znaků.");
        if (eventDate == null) errors.put("eventDate", "Datum akce je povinné.");
        else if (eventDate.isBefore(today) && !eventDate.equals(unchangedDate))
            errors.put("eventDate", "Datum akce nesmí být v minulosti.");
        if (eventPlace == null || eventPlace.isBlank()) errors.put("eventPlace", "Místo konání je povinné.");
        else if (eventPlace.trim().length() > 200) errors.put("eventPlace", "Místo konání má nejvýše 200 znaků.");
        if (guestCount == null || guestCount <= 0) errors.put("guestCount", "Počet hostů musí být větší než 0.");
        else if (guestCount > 99999) errors.put("guestCount", "Počet hostů má nejvýše 5 číslic.");
        return errors;
    }

    public Customer getCustomer() { return customer; }
    public long getResponsibleUserId() { return responsibleUserId; }
    public String getName() { return name; }
    public LocalDate getEventDate() { return eventDate; }
    public String getEventPlace() { return eventPlace; }
    public int getGuestCount() { return guestCount; }
    public ProjectStatus getStatus() { return status; }
    public String getNote() { return note; }
    public Folder getRootFolder() { return rootFolder; }
    public void setRootFolder(Folder rootFolder) { this.rootFolder = rootFolder; }

    @Override public String toString() { return name; }
}
