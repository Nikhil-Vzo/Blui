package blui.app;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure Java ANSI 24-bit Truecolor terminal image renderer using half-block characters (▀).
 * Allows rendering high-resolution pixel art in modern terminals with zero external dependencies.
 */
public final class TerminalArt {

    private TerminalArt() {}

    /**
     * Renders an image to a list of terminal lines.
     * Returning List<String> allows side-by-side split layouts with text.
     */
    public static List<String> renderLines(String imagePath, int columns) throws IOException {
        return renderLines(imagePath, columns, -1);
    }

    /**
     * Renders an image to a list of terminal lines with an optional fixed row count.
     */
    public static List<String> renderLines(String imagePath, int columns, int fixedRows) throws IOException {
        File file = new File(imagePath);
        if (!file.exists()) {
            throw new IOException("Image file not found: " + imagePath);
        }

        BufferedImage source = ImageIO.read(file);
        if (source == null) {
            throw new IOException("Unsupported image: " + imagePath);
        }

        int width = columns;
        int height;
        if (fixedRows > 0) {
            height = fixedRows * 2;
        } else {
            height = Math.max(
                    2,
                    (int) Math.round(source.getHeight() * (double) width / source.getWidth())
            );
            height -= height % 2;
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(8, 9, 12));
        g.fillRect(0, 0, width, height);

        g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC
        );
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();

        List<String> lines = new ArrayList<>();

        for (int y = 0; y < height; y += 2) {
            StringBuilder line = new StringBuilder();
            for (int x = 0; x < width; x++) {
                Color top = new Color(image.getRGB(x, y));
                Color bottom = new Color(image.getRGB(x, y + 1));

                line.append("\033[38;2;")
                    .append(top.getRed()).append(';')
                    .append(top.getGreen()).append(';')
                    .append(top.getBlue()).append('m');

                line.append("\033[48;2;")
                    .append(bottom.getRed()).append(';')
                    .append(bottom.getGreen()).append(';')
                    .append(bottom.getBlue()).append('m');

                line.append('\u2580'); // ▀ half block
            }
            line.append("\033[0m");
            lines.add(line.toString());
        }

        return lines;
    }

    /**
     * Prints the rendered image directly to System.out using UTF-8 bytes.
     */
    public static void render(String imagePath, int columns) throws IOException {
        List<String> lines = renderLines(imagePath, columns);
        for (String line : lines) {
            System.out.write((line + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        System.out.write("\033[0m\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        System.out.flush();
    }

    public static void main(String[] args) throws IOException {
        int cols = args.length > 0 ? Integer.parseInt(args[0]) : 50;
        String path = args.length > 1 ? args[1] : "assets/blui.png";
        render(path, cols);
    }
}
