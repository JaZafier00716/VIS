package cv04.datamapper;

import business.domainmodel.Order;
import business.domainmodel.OrderSpecification;
import business.service.PlaceOrderService.AddressData;
import business.service.PlaceOrderService.CustomerData;
import configuration.ApplicationContext;

/**
 * CV04 Data Mapper (DM steps 2, 6 and 11) on the application itself: the business layer works with pure
 * entities through repository interfaces ({@code business.repository}); the {@code dataaccess.mapper.*DataMapper}
 * classes map them to the storage records. The use case: load and check the material, build the Order entity in
 * state CEKA_NA_POTVRZENI, {@code validate()}, {@code repo.add()}, one commit - done by the real
 * {@code PlaceOrderService}. Nothing here knows SQL or files.
 */
public final class DataMapperDemonstration {
    private DataMapperDemonstration() {}

    /** Places the order through the application and returns it reloaded from storage (a fresh unit of work). */
    public static Order placeOrder(ApplicationContext application, CustomerData customer, String materialId,
                                   double width, double height, double depth, String designNotes,
                                   AddressData address) {
        OrderSpecification specification = OrderSpecification.builder().width(width).height(height).depth(depth)
                .designNotes(designNotes).build();
        Order placed = application.placeOrderService().placeOrder(customer, address, materialId, specification);
        return application.orderService().findOrder(placed.getOrderId());
    }
}
