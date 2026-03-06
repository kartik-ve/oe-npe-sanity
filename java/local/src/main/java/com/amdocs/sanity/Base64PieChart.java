package com.amdocs.sanity;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

import javax.imageio.ImageIO;

final class Base64PieChart {

    static String produce(int passedCount, int failedCount) throws IOException {
        BufferedImage image = producePieChartImage(passedCount, failedCount);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);

        byte[] imageBytes = baos.toByteArray();
        return Base64.getEncoder().encodeToString(imageBytes);
    }

    private static BufferedImage producePieChartImage(int passedCount, int failedCount) throws IOException {
        int size = 220;
        int padding = 10;

        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Transparent background
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, size, size);
        g.setComposite(AlphaComposite.SrcOver);

        int diameter = size - (padding * 2);
        int center = size / 2;
        int radius = diameter / 2;

        int total = passedCount + failedCount;
        double passedPercent = total > 0 ? (passedCount * 100.0) / total : 0;
        double failedPercent = total > 0 ? (failedCount * 100.0) / total : 0;

        // Angles
        double passedAngle = passedPercent * 360.0 / 100.0;

        int startAngle = 90;
        int passedSweep = (int) Math.round(-passedAngle);
        int failedSweep = -360 - passedSweep;

        // --- Draw slices ---
        g.setColor(new Color(0, 128, 0)); // passed
        g.fillArc(padding, padding, diameter, diameter, startAngle, passedSweep);

        g.setColor(Color.RED); // failed
        g.fillArc(padding, padding, diameter, diameter,
                startAngle + passedSweep, failedSweep);

        // --- Text settings ---
        g.setColor(Color.WHITE);
        g.setFont(new Font("Source Sans Pro", Font.BOLD, 13));

        // Passed text
        drawSliceText(
                g,
                center,
                center,
                radius * 0.65,
                startAngle + passedSweep / 2.0,
                passedCount,
                passedPercent);

        // Failed text
        drawSliceText(
                g,
                center,
                center,
                radius * 0.65,
                startAngle + passedSweep + failedSweep / 2.0,
                failedCount,
                failedPercent);

        g.dispose();
        return image;
    }

    private static void drawSliceText(
            Graphics2D g,
            int centerX,
            int centerY,
            double textRadius,
            double angleDeg,
            int count,
            double percent) {

        // Skip tiny slices (prevents unreadable overlap)
        if (percent < 5) {
            return;
        }

        double angleRad = Math.toRadians(angleDeg);

        int x = (int) (centerX + textRadius * Math.cos(angleRad));
        int y = (int) (centerY - textRadius * Math.sin(angleRad));

        String line1 = String.valueOf(count);
        String line2 = String.format("%.2f%%", percent);

        FontMetrics fm = g.getFontMetrics();

        int line1Width = fm.stringWidth(line1);
        int line2Width = fm.stringWidth(line2);

        int lineHeight = fm.getHeight();

        g.drawString(line1, x - line1Width / 2, y);
        g.drawString(line2, x - line2Width / 2, y + lineHeight);
    }
}
