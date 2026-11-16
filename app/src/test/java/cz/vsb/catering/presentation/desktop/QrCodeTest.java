package cz.vsb.catering.presentation.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.zxing.common.BitMatrix;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class QrCodeTest {
    @Test
    void matrixHasRequestedSizeAndDarkFinderCorner() {
        BitMatrix m = QrCode.matrix("http://localhost:8080/menu/0123456789abcdef0123456789abcdef", 200);
        assertEquals(200, m.getWidth());
        assertEquals(200, m.getHeight());
        int[] box = m.getEnclosingRectangle();                 // the dark top-left finder pattern starts the code
        assertTrue(m.get(box[0], box[1]));
        assertTrue(box[0] > 0, "quiet zone (margin) is white");
    }

    @Test
    void czechFormatting() {
        assertEquals("14. 6. 2027", Ui.date(LocalDate.of(2027, 6, 14)));
        assertEquals("2. 11. 2026, 18:42", Ui.dateTime(LocalDateTime.of(2026, 11, 2, 18, 42)));
        assertEquals("1 položka", Ui.plural(1, "položka", "položky", "položek"));
        assertEquals("3 položky", Ui.plural(3, "položka", "položky", "položek"));
        assertEquals("0 položek", Ui.plural(0, "položka", "položky", "položek"));
    }
}
