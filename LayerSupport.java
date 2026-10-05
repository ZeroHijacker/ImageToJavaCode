/** Readable keyboard controls included only when layer viewing is selected. */
final class LayerSupport {
    static String source(String name,int count) {
        return "    static final int LAYER_COUNT = "+count+";\n" + """
                int selectedLayer = LAYER_COUNT;
                boolean legendVisible = false;
                String layerInput = "";
                String layerMessage = "";

                public CLASS_NAME() {
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

            """.replace("CLASS_NAME",name);
    }
}
