import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

// Portrait reconstructed automatically from the selected reference.
// Author (optional): 
public class Portrait extends Frame {

    static final int IMAGE_WIDTH = 96;
    static final int IMAGE_HEIGHT = 96;

    static final int LAYER_COUNT = 11;
    int selectedLayer = LAYER_COUNT;
    boolean legendVisible = false;
    String layerInput = "";
    String layerMessage = "";

    public Portrait() {
        addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                handleLayerKey(e.getKeyCode(), e.getKeyChar());
            }
        });
    }

    void handleLayerKey(int key, char character) {
        if (key == KeyEvent.VK_H || key == KeyEvent.VK_F1) {
            legendVisible = !legendVisible;
        } else if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_DOWN) {
            selectedLayer = Math.max(1, selectedLayer - 1);
            layerInput = "";
            layerMessage = "";
        } else if (key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_UP) {
            selectedLayer = Math.min(LAYER_COUNT, selectedLayer + 1);
            layerInput = "";
            layerMessage = "";
        } else if (key == KeyEvent.VK_A || key == KeyEvent.VK_END) {
            selectedLayer = LAYER_COUNT;
            layerInput = "";
            layerMessage = "";
        } else if (key == KeyEvent.VK_HOME) {
            selectedLayer = 1;
            layerInput = "";
            layerMessage = "";
        } else if (character >= '0' && character <= '9') {
            legendVisible = true;
            if (layerInput.length() < 9) layerInput += character;
            layerMessage = "";
        } else if (key == KeyEvent.VK_BACK_SPACE) {
            if (!layerInput.isEmpty()) layerInput = layerInput.substring(0, layerInput.length() - 1);
        } else if (key == KeyEvent.VK_ENTER && !layerInput.isEmpty()) {
            int requested = Integer.parseInt(layerInput);
            if (requested >= 1 && requested <= LAYER_COUNT) {
                selectedLayer = requested;
                layerInput = "";
                layerMessage = "";
            } else {
                layerMessage = "Enter a layer from 1 to " + LAYER_COUNT;
            }
        } else if (key == KeyEvent.VK_ESCAPE) {
            layerInput = "";
            layerMessage = "";
            legendVisible = false;
        }
        repaint();
    }

    void drawLegend(Graphics graphics) {
        Graphics2D legend = (Graphics2D) graphics.create();
        Insets border = getInsets();
        int x = border.left + 12, y = border.top + 12;
        legend.setColor(new Color(20, 25, 32, 235));
        legend.fillRoundRect(x, y, 360, 174, 12, 12);
        legend.setColor(Color.white);
        legend.setFont(new Font("SansSerif", Font.PLAIN, 14));
        legend.drawString("Layers 1-" + selectedLayer + " / " + LAYER_COUNT, x + 12, y + 24);
        legend.drawString("H / F1: toggle legend    Esc: hide", x + 12, y + 48);
        legend.drawString("Left / Down: previous    Right / Up: next", x + 12, y + 70);
        legend.drawString("Home: base layer    A / End: all layers", x + 12, y + 92);
        legend.drawString("Type layer number, then Enter. Backspace: edit", x + 12, y + 114);
        legend.drawString("Go to layer: " + layerInput, x + 12, y + 136);
        legend.drawString(layerMessage, x + 12, y + 158);
        legend.dispose();
    }

    public static void drawPortrait(Graphics2D g2d) {
        drawPortrait(g2d, LAYER_COUNT);
    }

    public void paint(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        Insets border = getInsets();
        int width = getWidth() - border.left - border.right;
        int height = getHeight() - border.top - border.bottom;
        double scale = Math.min(width / (double) IMAGE_WIDTH, height / (double) IMAGE_HEIGHT);
        g2d.translate(border.left + (width - IMAGE_WIDTH * scale) / 2,
                border.top + (height - IMAGE_HEIGHT * scale) / 2);
        g2d.scale(scale, scale);
        drawPortrait(g2d, selectedLayer);
        g2d.dispose();
        if (legendVisible) drawLegend(g);
    }

    public static void drawPortrait(Graphics2D g2d, int layer) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.clip(new Rectangle2D.Double(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT));
        g2d.setColor(Color.white);
        g2d.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);

        drawBaseColors(g2d, layer);
        drawDetails1(g2d, layer);
        drawDetails2(g2d, layer);
    }

    public static void main(String[] argv) throws Exception {
        // Optional PNG output also works without opening a window.
        if (argv.length == 2 && argv[0].equals("--png")) {
            BufferedImage image = new BufferedImage(
                    IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB
            );
            Graphics2D g2d = image.createGraphics();
            drawPortrait(g2d, LAYER_COUNT);
            g2d.dispose();
            ImageIO.write(image, "png", new File(argv[1]));
            return;
        }

        Portrait sg = new Portrait();
        sg.setTitle("Generated Portrait");
        sg.setBackground(Color.white);
        sg.setForeground(Color.black);
        sg.setSize(Math.max(440, IMAGE_WIDTH + 40), Math.max(300, IMAGE_HEIGHT + 65));
        sg.setMinimumSize(new Dimension(440, 300));
        sg.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });
        sg.setVisible(true);
        sg.requestFocus();
    }

    public static void drawBaseColors(Graphics2D g2d, int layer) {

        // Layer 1: base shapes.
        if (layer < 1) return;
        // Rectangle
        Rectangle2D.Double rectangle1 = new Rectangle2D.Double(
                0, 0,
                96, 96
        );
        g2d.setColor(new Color(168, 170, 176));
        g2d.fill(rectangle1);

        // Layer 2: shapes above lower overlapping layers.
        if (layer < 2) return;
        // General path
        GeneralPath gp2 = new GeneralPath();
        gp2.moveTo(-0.16, 64.99);
        gp2.lineTo(-0.16, 95.99);
        gp2.lineTo(94.84, 95.99);
        gp2.lineTo(94.84, 64.99);
        gp2.lineTo(62.84, 64.99);
        gp2.lineTo(51.84, 69.99);
        gp2.lineTo(39.84, 68.99);
        gp2.lineTo(32.84, 64.99);
        gp2.closePath();
        g2d.setColor(new Color(60, 100, 150));
        g2d.fill(gp2);

        if (layer < 2) return;
        // Ellipse
        Ellipse2D.Double ellipse3 = new Ellipse2D.Double(
                21.96, 12,
                52.08, 56.01
        );
        g2d.setColor(new Color(204, 155, 107));
        g2d.fill(ellipse3);

        // Layer 3: shapes above lower overlapping layers.
        if (layer < 3) return;
        // Line
        Line2D.Double line4 = new Line2D.Double(
                64.08, 36.86,
                106.18, 36.86
        );
        Rectangle2D bounds4 = line4.getBounds2D();
        AffineTransform rotation4 = new AffineTransform();
        rotation4.rotate(
                Math.toRadians(304),
                bounds4.getCenterX(), bounds4.getCenterY()
        );
        Shape rotated4 = rotation4.createTransformedShape(line4);
        g2d.setColor(new Color(237, 242, 247));
        BasicStroke bs4 = new BasicStroke(
                3.39f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND
        );
        g2d.setStroke(bs4);
        g2d.draw(rotated4);

        if (layer < 3) return;
        // Rectangle
        Rectangle2D.Double rectangle5 = new Rectangle2D.Double(
                -8.92, 24.3,
                31.69, 39.63
        );
        g2d.setColor(new Color(231, 235, 240));
        g2d.fill(rectangle5);

        if (layer < 3) return;
        // Quadratic curve
        QuadCurve2D.Double qc6 = new QuadCurve2D.Double(
                21.91, 12.12,
                38.82, 2.69,
                55.74, 12.12
        );
        Rectangle2D bounds6 = qc6.getBounds2D();
        AffineTransform rotation6 = new AffineTransform();
        rotation6.rotate(
                Math.toRadians(353),
                bounds6.getCenterX(), bounds6.getCenterY()
        );
        Shape rotated6 = rotation6.createTransformedShape(qc6);
        g2d.setColor(new Color(236, 242, 247));
        BasicStroke bs6 = new BasicStroke(
                3.86f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND
        );
        g2d.setStroke(bs6);
        g2d.draw(rotated6);

        if (layer < 3) return;
        // General path
        GeneralPath gp7 = new GeneralPath();
        gp7.moveTo(1.49, 64.65);
        gp7.lineTo(1.49, 95.65);
        gp7.lineTo(96.49, 95.65);
        gp7.lineTo(96.49, 64.65);
        gp7.lineTo(64.49, 64.65);
        gp7.lineTo(53.49, 69.65);
        gp7.lineTo(41.49, 68.65);
        gp7.lineTo(34.49, 64.65);
        gp7.closePath();
        g2d.setColor(new Color(59, 100, 150));
        g2d.fill(gp7);

        // Layer 4: shapes above lower overlapping layers.
        if (layer < 4) return;
        // Circle
        Ellipse2D.Double circle8 = new Ellipse2D.Double(
                67.18, -8.2,
                52.41, 52.41
        );
        Rectangle2D bounds8 = circle8.getBounds2D();
        AffineTransform rotation8 = new AffineTransform();
        rotation8.rotate(
                Math.toRadians(199),
                bounds8.getCenterX(), bounds8.getCenterY()
        );
        Shape rotated8 = rotation8.createTransformedShape(circle8);
        g2d.setColor(new Color(230, 233, 237));
        g2d.fill(rotated8);

        if (layer < 4) return;
        // Square
        Rectangle2D.Double square9 = new Rectangle2D.Double(
                -3.32, -3.47,
                34.64, 34.64
        );
        g2d.setColor(new Color(229, 231, 233));
        g2d.fill(square9);

        if (layer < 4) return;
        // General path
        GeneralPath gp10 = new GeneralPath();
        gp10.moveTo(0.13, 64.12);
        gp10.lineTo(0.13, 95.12);
        gp10.lineTo(95.13, 95.12);
        gp10.lineTo(95.13, 64.12);
        gp10.lineTo(63.13, 64.12);
        gp10.lineTo(52.13, 69.12);
        gp10.lineTo(40.13, 68.12);
        gp10.lineTo(33.13, 64.12);
        gp10.closePath();
        g2d.setColor(new Color(60, 100, 150));
        g2d.fill(gp10);

        // Layer 5: shapes above lower overlapping layers.
        if (layer < 5) return;
        // Square
        Rectangle2D.Double square11 = new Rectangle2D.Double(
                77.48, 38.77,
                25.01, 25.01
        );
        g2d.setColor(new Color(231, 236, 241));
        g2d.fill(square11);

        if (layer < 5) return;
        // Cubic curve
        CubicCurve2D.Double cc12 = new CubicCurve2D.Double(
                43.75, 3.77,
                54.83, -18.32,
                69.6, 25.87,
                80.68, 3.77
        );
        Rectangle2D bounds12 = cc12.getBounds2D();
        AffineTransform rotation12 = new AffineTransform();
        rotation12.rotate(
                Math.toRadians(227),
                bounds12.getCenterX(), bounds12.getCenterY()
        );
        Shape rotated12 = rotation12.createTransformedShape(cc12);
        g2d.setColor(new Color(237, 242, 247));
        BasicStroke bs12 = new BasicStroke(
                2.39f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND
        );
        g2d.setStroke(bs12);
        g2d.draw(rotated12);

    }

    public static void drawDetails1(Graphics2D g2d, int layer) {

        // Layer 6: shapes above lower overlapping layers.
        if (layer < 6) return;
        // Circle
        Ellipse2D.Double circle13 = new Ellipse2D.Double(
                20.98, 12.98,
                54.04, 54.04
        );
        g2d.setColor(new Color(204, 155, 108));
        g2d.fill(circle13);

        // Layer 7: shapes above lower overlapping layers.
        if (layer < 7) return;
        // General path
        GeneralPath gp14 = new GeneralPath();
        gp14.moveTo(49.97, 8.71);
        gp14.lineTo(63.85, -18.83);
        gp14.lineTo(77.73, 5.27);
        gp14.lineTo(63.85, 15.6);
        gp14.closePath();
        Rectangle2D bounds14 = gp14.getBounds2D();
        AffineTransform rotation14 = new AffineTransform();
        rotation14.rotate(
                Math.toRadians(8),
                bounds14.getCenterX(), bounds14.getCenterY()
        );
        Shape rotated14 = rotation14.createTransformedShape(gp14);
        g2d.setColor(new Color(231, 236, 241));
        g2d.fill(rotated14);

        if (layer < 7) return;
        // Arc
        Arc2D.Double arc15 = new Arc2D.Double(
                18.71, -14.41,
                34.81, 36.79,
                0, 186, Arc2D.PIE
        );
        Rectangle2D bounds15 = arc15.getBounds2D();
        AffineTransform rotation15 = new AffineTransform();
        rotation15.rotate(
                Math.toRadians(56),
                bounds15.getCenterX(), bounds15.getCenterY()
        );
        Shape rotated15 = rotation15.createTransformedShape(arc15);
        g2d.setColor(new Color(231, 236, 241));
        g2d.fill(rotated15);

        if (layer < 7) return;
        // Ellipse
        Ellipse2D.Double ellipse16 = new Ellipse2D.Double(
                34.3, 31.47,
                5.15, 5.15
        );
        g2d.setColor(new Color(9, 26, 43));
        g2d.fill(ellipse16);

        if (layer < 7) return;
        // Area / transformation
        Ellipse2D.Double outer17 = new Ellipse2D.Double(
                21.31, 51.79,
                15.47, 11.1
        );
        Ellipse2D.Double cutout17 = new Ellipse2D.Double(
                21.31 + 15.47 * 0.28,
                51.79 - 11.1 * 0.12,
                15.47, 11.1
        );
        Area area17 = new Area(outer17);
        area17.subtract(new Area(cutout17));
        Rectangle2D bounds17 = area17.getBounds2D();
        AffineTransform rotation17 = new AffineTransform();
        rotation17.rotate(
                Math.toRadians(11),
                bounds17.getCenterX(), bounds17.getCenterY()
        );
        Shape rotated17 = rotation17.createTransformedShape(area17);
        g2d.setColor(new Color(232, 231, 229));
        g2d.fill(rotated17);

        if (layer < 7) return;
        // Circle
        Ellipse2D.Double circle18 = new Ellipse2D.Double(
                57.34, 31.02,
                5.45, 5.45
        );
        g2d.setColor(new Color(21, 34, 47));
        g2d.fill(circle18);

        if (layer < 7) return;
        // General path
        GeneralPath gp19 = new GeneralPath();
        gp19.moveTo(37.93, 45.59);
        gp19.lineTo(37.93, 47.59);
        gp19.lineTo(42.93, 52.59);
        gp19.lineTo(45.93, 53.59);
        gp19.lineTo(55.93, 52.59);
        gp19.lineTo(60.93, 47.59);
        gp19.lineTo(60.93, 45.59);
        gp19.lineTo(59.93, 45.59);
        gp19.lineTo(59.93, 47.59);
        gp19.lineTo(56.93, 50.59);
        gp19.lineTo(52.93, 52.59);
        gp19.lineTo(45.93, 52.59);
        gp19.lineTo(41.93, 50.59);
        gp19.closePath();
        g2d.setColor(new Color(31, 0, 5));
        g2d.fill(gp19);

        if (layer < 7) return;
        // Line
        Line2D.Double line20 = new Line2D.Double(
                62.36, 42.4,
                90.75, 42.4
        );
        Rectangle2D bounds20 = line20.getBounds2D();
        AffineTransform rotation20 = new AffineTransform();
        rotation20.rotate(
                Math.toRadians(270),
                bounds20.getCenterX(), bounds20.getCenterY()
        );
        Shape rotated20 = rotation20.createTransformedShape(line20);
        g2d.setColor(new Color(235, 241, 246));
        BasicStroke bs20 = new BasicStroke(
                2.31f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND
        );
        g2d.setStroke(bs20);
        g2d.draw(rotated20);

        if (layer < 7) return;
        // Arc
        Arc2D.Double arc21 = new Arc2D.Double(
                21.28, 14.58,
                53.57, 53.57,
                -53.61, -65.31, Arc2D.OPEN
        );
        g2d.setColor(new Color(220, 161, 101));
        BasicStroke bs21 = new BasicStroke(
                1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND
        );
        g2d.setStroke(bs21);
        g2d.draw(arc21);

        // Layer 8: shapes above lower overlapping layers.
        if (layer < 8) return;
        // Arc
        Arc2D.Double arc22 = new Arc2D.Double(
                22.39, 11.41,
                52.33, 52.33,
                98.58, 69.56, Arc2D.OPEN
        );
        g2d.setColor(new Color(237, 245, 254));
        BasicStroke bs22 = new BasicStroke(
                1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND
        );
        g2d.setStroke(bs22);
        g2d.draw(arc22);

        if (layer < 8) return;
        // General path
        GeneralPath gp23 = new GeneralPath();
        gp23.moveTo(37, 45);
        gp23.lineTo(37, 47);
        gp23.lineTo(42, 52);
        gp23.lineTo(45, 53);
        gp23.lineTo(55, 52);
        gp23.lineTo(60, 47);
        gp23.lineTo(60, 45);
        gp23.lineTo(59, 45);
        gp23.lineTo(59, 47);
        gp23.lineTo(56, 50);
        gp23.lineTo(52, 52);
        gp23.lineTo(45, 52);
        gp23.lineTo(41, 50);
        gp23.closePath();
        g2d.setColor(new Color(77, 30, 32));
        g2d.fill(gp23);

    }

    public static void drawDetails2(Graphics2D g2d, int layer) {

        if (layer < 8) return;
        // General path
        GeneralPath gp24 = new GeneralPath();
        gp24.moveTo(66.13, 62.24);
        gp24.lineTo(73.1, 55.25);
        gp24.lineTo(80.07, 61.36);
        gp24.lineTo(73.1, 63.98);
        gp24.closePath();
        Rectangle2D bounds24 = gp24.getBounds2D();
        AffineTransform rotation24 = new AffineTransform();
        rotation24.rotate(
                Math.toRadians(246),
                bounds24.getCenterX(), bounds24.getCenterY()
        );
        Shape rotated24 = rotation24.createTransformedShape(gp24);
        g2d.setColor(new Color(232, 236, 240));
        g2d.fill(rotated24);

        if (layer < 8) return;
        // Area / transformation
        Ellipse2D.Double outer25 = new Ellipse2D.Double(
                53.62, 8.42,
                20.67, 11.77
        );
        Ellipse2D.Double cutout25 = new Ellipse2D.Double(
                53.62 + 20.67 * 0.28,
                8.42 - 11.77 * 0.12,
                20.67, 11.77
        );
        Area area25 = new Area(outer25);
        area25.subtract(new Area(cutout25));
        Rectangle2D bounds25 = area25.getBounds2D();
        AffineTransform rotation25 = new AffineTransform();
        rotation25.rotate(
                Math.toRadians(150),
                bounds25.getCenterX(), bounds25.getCenterY()
        );
        Shape rotated25 = rotation25.createTransformedShape(area25);
        g2d.setColor(new Color(230, 235, 239));
        g2d.fill(rotated25);

        // Layer 9: shapes above lower overlapping layers.
        if (layer < 9) return;
        // Quadratic curve
        QuadCurve2D.Double qc26 = new QuadCurve2D.Double(
                22.1, 33.06,
                18.48, 49.72,
                30.1, 62.06
        );
        g2d.setColor(new Color(232, 233, 234));
        BasicStroke bs26 = new BasicStroke(
                1.35f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND
        );
        g2d.setStroke(bs26);
        g2d.draw(qc26);

        if (layer < 9) return;
        // Circle
        Ellipse2D.Double circle27 = new Ellipse2D.Double(
                38.39, -6.63,
                18.7, 18.7
        );
        g2d.setColor(new Color(230, 235, 240));
        g2d.fill(circle27);

        if (layer < 9) return;
        // Arc
        Arc2D.Double arc28 = new Arc2D.Double(
                -3.03, -108.62,
                171.98, 171.98,
                -98.6, 13.97, Arc2D.OPEN
        );
        g2d.setColor(new Color(235, 240, 245));
        BasicStroke bs28 = new BasicStroke(
                1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND
        );
        g2d.setStroke(bs28);
        g2d.draw(arc28);

        // Layer 10: shapes above lower overlapping layers.
        if (layer < 10) return;
        // Cubic curve
        CubicCurve2D.Double cc29 = new CubicCurve2D.Double(
                19.16, 15.11,
                26.59, 10.15,
                36.5, 20.07,
                43.93, 15.11
        );
        Rectangle2D bounds29 = cc29.getBounds2D();
        AffineTransform rotation29 = new AffineTransform();
        rotation29.rotate(
                Math.toRadians(132),
                bounds29.getCenterX(), bounds29.getCenterY()
        );
        Shape rotated29 = rotation29.createTransformedShape(cc29);
        g2d.setColor(new Color(232, 238, 243));
        BasicStroke bs29 = new BasicStroke(
                2.26f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND
        );
        g2d.setStroke(bs29);
        g2d.draw(rotated29);

        // Layer 11: shapes above lower overlapping layers.
        if (layer < 11) return;
        // Circle
        Ellipse2D.Double circle30 = new Ellipse2D.Double(
                23.14, 4.15,
                13.62, 13.62
        );
        Rectangle2D bounds30 = circle30.getBounds2D();
        AffineTransform rotation30 = new AffineTransform();
        rotation30.rotate(
                Math.toRadians(301),
                bounds30.getCenterX(), bounds30.getCenterY()
        );
        Shape rotated30 = rotation30.createTransformedShape(circle30);
        g2d.setColor(new Color(230, 235, 240));
        g2d.fill(rotated30);

    }

}
