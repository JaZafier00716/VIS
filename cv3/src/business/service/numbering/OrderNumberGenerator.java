package business.service.numbering;

/**
 * CV09 Singleton: the one source of order numbers in the running application. The instance is created eagerly
 * when the class is loaded (thread-safe without locking); the constructor is private, so nobody else can create
 * a second generator that would hand out the same numbers again.
 * <p>
 * The Singleton guarantees one instance per run of the program, not persistence: the last number lives in
 * memory only and starts again from 1001 after a restart.
 */
public final class OrderNumberGenerator {
    private static final int FIRST_NUMBER = 1001;
    private static final OrderNumberGenerator INSTANCE = new OrderNumberGenerator();

    private int lastNumber = FIRST_NUMBER - 1;

    private OrderNumberGenerator() {
    }

    public static OrderNumberGenerator getInstance() {
        return INSTANCE;
    }

    /** The next order number; synchronized, so two threads never get the same one. */
    public synchronized int getNextNumber() {
        return ++lastNumber;
    }
}























