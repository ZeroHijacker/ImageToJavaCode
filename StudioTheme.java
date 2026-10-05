import java.awt.*;
import javax.swing.*;
import javax.swing.text.JTextComponent;

/** Light/dark colors for the converter, its previews, and Swing dialogs. */
final class StudioTheme {
    static void apply(JFrame window,boolean dark) {
        Color background=dark?new Color(30,33,39):new Color(242,244,248);
        Color surface=dark?new Color(42,47,56):Color.white;
        Color text=dark?new Color(231,235,241):new Color(28,36,48);
        Color muted=dark?new Color(160,171,188):new Color(100,110,124);
        for(String type:new String[]{"Panel","Label","CheckBox","Button","ToggleButton","ComboBox","TextField","TextArea","Viewport","ScrollPane","TabbedPane","OptionPane","List","Table","Tree","Menu","MenuItem","PopupMenu","ToolTip","ScrollBar","ProgressBar"}) {
            UIManager.put(type+".background",background);UIManager.put(type+".foreground",text);
            UIManager.put(type+".disabledText",muted);UIManager.put(type+".disabledForeground",muted);
            UIManager.put(type+".selectionBackground",new Color(59,102,158));UIManager.put(type+".selectionForeground",Color.white);
        }
        UIManager.put("Button.gradient",java.util.List.of(0f,0f,background,background,background));
        UIManager.put("CheckBox.gradient",java.util.List.of(0f,0f,background,background,background));
        UIManager.put("control",background);UIManager.put("text",surface);
        UIManager.put("OptionPane.messageForeground",text);
        UIManager.put("TabbedPane.selected",surface);
        SwingUtilities.updateComponentTreeUI(window);
        recolor(window.getContentPane(),background,surface,text,muted);
        checkboxIcons(window.getContentPane(),dark);
        window.repaint();
    }

    static void checkboxIcons(Container parent,boolean dark) {
        for(Component child:parent.getComponents()) {
            if(child instanceof JCheckBox box) {
                Icon icon=new CheckIcon(dark);
                box.setIcon(icon);box.setSelectedIcon(icon);box.setDisabledIcon(icon);
                box.setDisabledSelectedIcon(icon);box.setPressedIcon(icon);box.setRolloverIcon(icon);
                box.setRolloverSelectedIcon(icon);
            }
            if(child instanceof Container nested)checkboxIcons(nested,dark);
        }
    }

    /** Explicit checked/unchecked/disabled colors independent of Look-and-Feel. */
    static final class CheckIcon implements Icon {
        final boolean dark;
        CheckIcon(boolean dark){this.dark=dark;}
        public int getIconWidth(){return 20;}
        public int getIconHeight(){return 20;}
        public void paintIcon(Component component,Graphics graphics,int x,int y) {
            AbstractButton button=(AbstractButton)component;
            Graphics2D g=(Graphics2D)graphics.create();g.translate(x,y);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            boolean checked=button.isSelected(),enabled=button.isEnabled();
            g.setColor(checked?(enabled?new Color(35,100,175):new Color(82,93,111)):(dark?new Color(40,45,54):Color.white));
            g.fillRoundRect(1,1,17,17,4,4);
            g.setColor(dark?new Color(184,198,216):new Color(69,83,104));
            g.drawRoundRect(1,1,17,17,4,4);
            if(checked) {
                g.setColor(Color.white);g.setStroke(new BasicStroke(2.6f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                g.drawLine(5,10,8,13);g.drawLine(8,13,14,6);
            }
            g.dispose();
        }
    }

    static void recolor(Component c,Color background,Color surface,Color text,Color muted) {
        c.setBackground(c instanceof JTextComponent||c instanceof JList?surface:background);
        c.setForeground(text);
        if(c instanceof JTextComponent t){t.setCaretColor(text);t.setDisabledTextColor(muted);t.setSelectionColor(new Color(59,102,158));t.setSelectedTextColor(Color.white);}
        if(c instanceof Container container)for(Component child:container.getComponents())recolor(child,background,surface,text,muted);
    }
}
