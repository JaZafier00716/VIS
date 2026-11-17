package dataaccess.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import dataaccess.dto.OrderDto;
import dataaccess.dto.OrderItemDto;
import dataaccess.mapper.OrderRowMapper;
import dataaccess.gateway.OrderGateway;
import dataaccess.row.OrderItemRow;
import dataaccess.row.OrderRow;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Table Data Gateway used by the Data Mapper (JSON file(s) orders + dependent order_items).
 * Orders in orders.json, their dependent items in order_items.json (Dependent Mapping): items are only
 * written, replaced and deleted together with their order.
 */
public class JsonOrderGateway implements OrderGateway {
    static final String FILE = "orders.json";
    static final String ITEMS_FILE = "order_items.json";
    static final TypeReference<List<OrderRow>> TYPE = new TypeReference<>() {};
    static final TypeReference<List<OrderItemRow>> ITEMS_TYPE = new TypeReference<>() {};
    private static final String ENTITY = "Objednávka";
    /** The FK to the strategy's customer root table (customers.json for CTI, customers_sti.json for STI). */
    private final Predicate<String> customerExists;

    /** Orders of a CTI store (customers.json). */
    public JsonOrderGateway() {
        this(JsonCustomerGateway::exists);
    }

    public JsonOrderGateway(Predicate<String> customerExists) {
        this.customerExists = customerExists;
    }

    @Override
    public void insert(OrderDto order) {
        String id = RecordIds.require(order.orderId(), ENTITY);
        checkReferences(order);
        List<OrderRow> rows = JsonFiles.read(FILE, TYPE);
        if (rows.stream().anyMatch(row -> row.orderId().equals(id))) {
            throw RecordIds.duplicate(ENTITY, id);
        }
        rows.add(OrderRowMapper.toRow(order));
        JsonFiles.write(FILE, rows);
        writeItems(order);
    }

    @Override
    public void update(OrderDto order) {
        String id = RecordIds.require(order.orderId(), ENTITY);
        checkReferences(order);
        List<OrderRow> rows = JsonFiles.read(FILE, TYPE);
        rows.set(indexOf(rows, id), OrderRowMapper.toRow(order));
        JsonFiles.write(FILE, rows);
        writeItems(order);
    }

    @Override
    public void delete(String id) {
        RecordIds.require(id, ENTITY);
        List<OrderRow> rows = JsonFiles.read(FILE, TYPE);
        rows.remove(indexOf(rows, id));
        JsonFiles.write(FILE, rows);
        List<OrderItemRow> items = JsonFiles.read(ITEMS_FILE, ITEMS_TYPE);
        if (items.removeIf(item -> item.orderId().equals(id))) {
            JsonFiles.write(ITEMS_FILE, items);
        }
    }

    @Override
    public Optional<OrderDto> findById(String id) {
        List<OrderItemRow> items = JsonFiles.read(ITEMS_FILE, ITEMS_TYPE);
        return JsonFiles.read(FILE, TYPE).stream().filter(row -> row.orderId().equals(id))
                .findFirst().map(row -> OrderRowMapper.toDto(row, items));
    }

    @Override
    public List<OrderDto> findAll() {
        List<OrderItemRow> items = JsonFiles.read(ITEMS_FILE, ITEMS_TYPE);
        return JsonFiles.read(FILE, TYPE).stream().map(row -> OrderRowMapper.toDto(row, items)).toList();
    }

    /** Dependent Mapping: replace all item records of the order with its current items. */
    private static void writeItems(OrderDto order) {
        List<OrderItemRow> items = JsonFiles.read(ITEMS_FILE, ITEMS_TYPE);
        items.removeIf(item -> item.orderId().equals(order.orderId()));
        items.addAll(OrderRowMapper.toItemRows(order));
        JsonFiles.write(ITEMS_FILE, items);
    }

    /** Foreign Key Mapping: the referenced customer and materials must exist (like the H2 FKs). */
    private void checkReferences(OrderDto order) {
        if (order.customerId() == null || !customerExists.test(order.customerId())) {
            throw new IllegalArgumentException("Objednávka odkazuje na neexistujícího zákazníka "
                    + order.customerId() + ".");
        }
        if (order.materialId() == null || !JsonMaterialGateway.exists(order.materialId())) {
            throw new IllegalArgumentException("Objednávka odkazuje na neexistující materiál "
                    + order.materialId() + ".");
        }
        for (OrderItemDto item : order.items()) {
            if (!JsonMaterialGateway.exists(item.materialId())) {
                throw new IllegalArgumentException("Položka " + item.lineNumber()
                        + " odkazuje na neexistující materiál " + item.materialId() + ".");
            }
        }
    }

    private static int indexOf(List<OrderRow> rows, String id) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).orderId().equals(id)) {
                return i;
            }
        }
        throw RecordIds.missing(ENTITY, id);
    }
}
