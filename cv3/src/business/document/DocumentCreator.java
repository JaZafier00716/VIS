package business.document;

import business.domainmodel.Order;

/**
 * CV09 Factory Method – the Creator. The common logic ({@link #produce}, {@link #render}) is final and never
 * names a concrete document; each subclass decides in {@link #createDocument} which document is made.
 */
public abstract class DocumentCreator {

    /** The factory method. */
    protected abstract OrderDocument createDocument(Order order);

    /** Common logic shared by all creators: checks the input and lets the subclass create the document. */
    public final OrderDocument produce(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("Objednávka je povinná.");
        }
        return createDocument(order);
    }

    /** The document as text: title, underline and content. */
    public final String render(Order order) {
        OrderDocument document = produce(order);
        return document.getTitle() + '\n' + "=".repeat(document.getTitle().length()) + '\n' + document.getContent();
    }
}
