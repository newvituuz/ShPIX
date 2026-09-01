package dev.singlehope.free.shpix.qrcode;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;

public final class QrCodeImage {

    public static final int MAP_SIZE = 128;

    private static final int DARK = 0xFF000000;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final double LOGO_RATIO = 0.28D;

    private static volatile BufferedImage cachedLogo;
    private static volatile boolean logoChecked;

    private QrCodeImage() {
    }

    public static void reset() {
        cachedLogo = null;
        logoChecked = false;
    }

    public static BufferedImage render(final String data, final File logoFile, final Logger logger) {
        final Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.MARGIN, 1);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

        final BitMatrix matrix;
        try {
            matrix = new QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, 1, 1, hints);
        } catch (WriterException | IllegalArgumentException exception) {
            logger.warning("Não foi possível gerar a imagem do QR Code.");
            return null;
        }

        final int width = matrix.getWidth();
        final int height = matrix.getHeight();
        final BufferedImage image = new BufferedImage(MAP_SIZE, MAP_SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < MAP_SIZE; y++) {
            final int matrixY = y * height / MAP_SIZE;
            for (int x = 0; x < MAP_SIZE; x++) {
                final int matrixX = x * width / MAP_SIZE;
                image.setRGB(x, y, matrix.get(matrixX, matrixY) ? DARK : LIGHT);
            }
        }

        drawLogo(image, logoFile, logger);
        return image;
    }

    private static void drawLogo(final BufferedImage target, final File logoFile, final Logger logger) {
        final BufferedImage logo = logo(logoFile, logger);
        if (logo == null) {
            return;
        }
        final int size = (int) (MAP_SIZE * LOGO_RATIO);
        final int offset = (MAP_SIZE - size) / 2;
        final Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(logo, offset, offset, size, size, null);
        } finally {
            graphics.dispose();
        }
    }

    private static BufferedImage logo(final File logoFile, final Logger logger) {
        if (logoChecked) {
            return cachedLogo;
        }
        synchronized (QrCodeImage.class) {
            if (logoChecked) {
                return cachedLogo;
            }
            logoChecked = true;
            if (logoFile == null || !logoFile.isFile() || logoFile.length() > 2L * 1024L * 1024L) {
                return null;
            }
            try {
                cachedLogo = ImageIO.read(logoFile);
            } catch (IOException | OutOfMemoryError exception) {
                logger.warning("Não foi possível carregar logo.png; o QR Code será gerado sem logo.");
                cachedLogo = null;
            }
            return cachedLogo;
        }
    }
}
