package dataaccess.shipping;

import business.domainmodel.Money;
import business.domainmodel.ShippingQuote;
import business.service.IShippingService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** CV08 Gateway, Service Stub and Mapper (cv08 p.12, p.14, p.16, p.19). */
class ShippingTest {
    @Test
    void stubAlwaysReturns150CzkAnd3Days() {
        IShippingService stub = new ShippingServiceStub();
        assertEquals(new ShippingQuote(Money.czk(150), 3), stub.quote(2));
        assertEquals(new ShippingQuote(Money.czk(150), 3), stub.quote(10));
    }

    @Test
    void gatewayGets120CzkAnd2DaysForTwoPiecesFromTheSimulatedApi() {
        IShippingService gateway = new ShippingGateway(new SimulatedShippingApi());
        assertEquals(new ShippingQuote(Money.czk(120), 2), gateway.quote(2));
    }

    @Test
    void gatewayGets250CzkAnd4DaysForFourOrMorePieces() {
        IShippingService gateway = new ShippingGateway(new SimulatedShippingApi());
        assertEquals(new ShippingQuote(Money.czk(120), 2), gateway.quote(3));
        assertEquals(new ShippingQuote(Money.czk(250), 4), gateway.quote(4));
    }

    @Test
    void mapperTurnsTheExternalResponseIntoAShippingQuote() {
        ShippingQuote quote = ShippingMapper.toQuote(Map.of("cost_czk", 120, "eta_days", 2));
        assertEquals(Money.czk(120), quote.price());
        assertEquals(2, quote.deliveryDays());
        assertEquals(new ShippingQuote(Money.czk(250), 4), ShippingMapper.toQuote(Map.of("cost_czk", 250.0, "eta_days", 4)));
    }

    @Test
    void mapperPreparesTheExternalRequest() {
        assertEquals(Map.of("item_count", 2), ShippingMapper.toRequest(2));
    }

    @Test
    void mapperRejectsAnInvalidOrErrorResponse() {
        assertThrows(IllegalStateException.class, () -> ShippingMapper.toQuote(Map.of("eta_days", 2)));
        assertThrows(IllegalStateException.class, () -> ShippingMapper.toQuote(Map.of("cost_czk", -5, "eta_days", 2)));
        assertThrows(IllegalStateException.class, () -> new ShippingGateway(new SimulatedShippingApi()).quote(0));
    }
}
