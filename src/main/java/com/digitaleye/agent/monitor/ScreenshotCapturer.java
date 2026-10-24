package com.digitaleye.agent.monitor;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.AWTException;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Grabs the (primary) screen with java.awt.Robot and returns it as a JPEG. The image is
 * scaled down to a maximum width first, because a full-resolution capture would be several
 * MB while a 1280-px-wide JPEG is typically 80-200 KB - plenty to see what is on screen.
 */
public class ScreenshotCapturer {

    public byte[] captureJpeg(int maxWidth, float quality) throws AWTException, IOException {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("No display available - screenshots need a desktop session");
        }
        Rectangle screen = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        BufferedImage capture = new Robot().createScreenCapture(screen);
        return encodeJpeg(capture, maxWidth, quality);
    }

    /** Scales the image down to maxWidth (never up) and compresses it as a JPEG. Public so it can be tested without a screen. */
    public static byte[] encodeJpeg(BufferedImage source, int maxWidth, float quality) throws IOException {
        BufferedImage rgb = toScaledRgb(source, maxWidth);

        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(rgb, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    /** JPEG has no transparency, so the image is always redrawn onto a plain RGB canvas. */
    private static BufferedImage toScaledRgb(BufferedImage source, int maxWidth) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width > maxWidth) {
            height = Math.max(1, (int) Math.round(height * (maxWidth / (double) width)));
            width = maxWidth;
        }

        BufferedImage rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }
}
