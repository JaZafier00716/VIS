package cz.vsb.catering.presentation.desktop;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.util.Map;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

/** QR code of the share link (UC11 step 3), drawn with ZXing core into a JavaFX image. */
final class QrCode {
    private QrCode() {}

    static BitMatrix matrix(String text, int size) {
        try {
            return new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, Map.of(EncodeHintType.MARGIN, 1));
        } catch (WriterException e) {
            throw new IllegalArgumentException("QR kód nelze vytvořit: " + e.getMessage(), e);
        }
    }

    static WritableImage image(String text, int size) {
        BitMatrix m = matrix(text, size);
        WritableImage image = new WritableImage(m.getWidth(), m.getHeight());
        PixelWriter w = image.getPixelWriter();
        for (int y = 0; y < m.getHeight(); y++)
            for (int x = 0; x < m.getWidth(); x++) w.setArgb(x, y, m.get(x, y) ? 0xFF1F2937 : 0xFFFFFFFF);
        return image;
    }
}
