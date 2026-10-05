import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.List;

/** Emits ordinary, editable Java 2D statements with named shapes and drawing methods. */
final class CourseExporter {
    static String number(double value) {
        if(value==Math.rint(value))return Long.toString((long)value);
        return Double.toString(value);
    }

    static PortraitRuntime.Mark clean(PortraitRuntime.Mark original) {
        double[] p=original.p.clone();
        for(int i=0;i<p.length;i++)p[i]=Math.rint(p[i]*100)/100;
        return new PortraitRuntime.Mark(original.type,original.rgb,
            Math.max(.1,Math.rint(original.stroke*100)/100),original.fill,
            Math.rint(original.angle*100)/100,original.label,p);
    }

    static List<String> block(PortraitRuntime.Mark m,int id) {
        List<String> lines=new ArrayList<>();double[] p=m.p;
        String variable=switch(m.type){case 0->"line";case 1->"qc";case 2->"cc";
            case 3->"arc";case 4->"ellipse";case 5->"circle";case 6->"rectangle";
            case 7->"square";case 8->"gp";default->"area";}+id;
        lines.add("        // "+PortraitRuntime.NAMES[m.type]);
        if(m.type==8) {
            path(lines,variable,p,0,p.length/2,m.fill);
        } else if(m.type==9 && p[0]==-999) {
            int offset=2;
            for(int ring=0;ring<(int)p[1];ring++) {
                int count=(int)p[offset++];String name="boundary"+id+"_"+(ring+1);
                path(lines,name,p,offset,count,true);offset+=count*2;
                lines.add(ring==0?"        Area "+variable+" = new Area("+name+");":
                    "        "+variable+".subtract(new Area("+name+"));");
            }
        } else if(m.type==9) {
            constructor(lines,"Ellipse2D.Double","outer"+id,p[0],p[1],p[2],p[3]);
            lines.add("        Ellipse2D.Double cutout"+id+" = new Ellipse2D.Double(");
            lines.add("                "+number(p[0])+" + "+number(p[2])+" * 0.28,");
            lines.add("                "+number(p[1])+" - "+number(p[3])+" * 0.12,");
            lines.add("                "+number(p[2])+", "+number(p[3]));
            lines.add("        );");
            lines.add("        Area "+variable+" = new Area(outer"+id+");");
            lines.add("        "+variable+".subtract(new Area(cutout"+id+"));");
        } else if(m.type==3) {
            lines.add("        Arc2D.Double "+variable+" = new Arc2D.Double(");
            lines.add("                "+number(p[0])+", "+number(p[1])+",");
            lines.add("                "+number(p[2])+", "+number(p[3])+",");
            lines.add("                "+number(p[4])+", "+number(p[5])+", "+(m.fill?"Arc2D.PIE":"Arc2D.OPEN"));
            lines.add("        );");
        } else {
            String type=switch(m.type){case 0->"Line2D.Double";case 1->"QuadCurve2D.Double";
                case 2->"CubicCurve2D.Double";case 4,5->"Ellipse2D.Double";default->"Rectangle2D.Double";};
            double[] values=p.clone();if(m.type==5||m.type==7)values[3]=values[2];
            constructor(lines,type,variable,values);
        }
        String drawn=variable;
        if(m.angle!=0) {
            // Obtain the pivot from the unrotated geometry, exactly as the renderer does.
            PortraitRuntime.Mark unrotated=new PortraitRuntime.Mark(m.type,m.rgb,m.stroke,m.fill,0,m.label,p);
            Rectangle2D bounds=PortraitRuntime.geometry(unrotated).getBounds2D();
            lines.add("        Rectangle2D bounds"+id+" = "+variable+".getBounds2D();");
            lines.add("        AffineTransform rotation"+id+" = new AffineTransform();");
            lines.add("        rotation"+id+".rotate(");
            lines.add("                Math.toRadians("+number(m.angle)+"),");
            lines.add("                bounds"+id+".getCenterX(), bounds"+id+".getCenterY()");
            lines.add("        );");
            drawn="rotated"+id;
            lines.add("        Shape "+drawn+" = rotation"+id+".createTransformedShape("+variable+");");
        }
        int rgb=m.rgb;
        lines.add("        g2d.setColor(new Color("+((rgb>>16)&255)+", "+((rgb>>8)&255)+", "+(rgb&255)+"));");
        if(!m.fill||m.type==0) {
            lines.add("        BasicStroke bs"+id+" = new BasicStroke(");
            lines.add("                "+number(m.stroke)+"f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND");
            lines.add("        );");
            lines.add("        g2d.setStroke(bs"+id+");");
            lines.add("        g2d.draw("+drawn+");");
        } else lines.add("        g2d.fill("+drawn+");");
        lines.add("");return lines;
    }

    static void path(List<String> out,String variable,double[] p,int start,int count,boolean close) {
        out.add("        GeneralPath "+variable+" = new GeneralPath();");
        for(int i=0;i<count;i++)out.add("        "+variable+(i==0?".moveTo(":".lineTo(")+
            number(p[start+i*2])+", "+number(p[start+i*2+1])+");");
        if(close)out.add("        "+variable+".closePath();");
    }

    static void constructor(List<String> out,String type,String name,double... values) {
        out.add("        "+type+" "+name+" = new "+type+"(");
        for(int i=0;i<values.length;i+=2)out.add("                "+number(values[i])+", "+number(values[i+1])+(i+2<values.length?",":""));
        out.add("        );");
    }

    static int count(String text){return (int)text.chars().filter(c->c=='\n').count();}
    static int cost(PortraitRuntime.Mark m){return block(m,1).size()+1;}

    static String source(String name,int w,int h,List<PortraitRuntime.Mark> marks,String author) {
        return source(name,w,h,marks,author,false,false);
    }

    static String source(String name,int w,int h,List<PortraitRuntime.Mark> marks,String author,boolean comments,boolean viewer) {
        LayerPlan.Plan layers=LayerPlan.build(marks,w,h);
        List<List<String>> groups=new ArrayList<>();List<String> group=new ArrayList<>();
        int previousDepth=0;
        for(int i=0;i<marks.size();i++) {
            var entry=layers.entries().get(i);
            List<String> shape=block(entry.mark(),i+1);
            if(viewer)shape.add(0,"        if (layer < "+entry.depth()+") return;");
            if(comments&&entry.depth()!=previousDepth)
                shape.add(0,"        // Layer "+entry.depth()+": "+(entry.depth()==1?"base shapes.":"shapes above lower overlapping layers."));
            previousDepth=entry.depth();
            if(group.size()+shape.size()>180&&!group.isEmpty()){groups.add(group);group=new ArrayList<>();}
            group.addAll(shape);
        }
        if(!group.isEmpty())groups.add(group);
        StringBuilder b=new StringBuilder();
        b.append("import java.awt.*;\nimport java.awt.event.*;\nimport java.awt.geom.*;\n");
        b.append("import java.awt.image.BufferedImage;\nimport java.io.File;\nimport javax.imageio.ImageIO;\n\n");
        b.append("// Portrait reconstructed automatically from the selected reference.\n");
        b.append("// Author (optional): "+PortraitStudio.safeComment(author)+"\n");
        b.append("public class "+name+" extends Frame {\n\n");
        b.append("    static final int IMAGE_WIDTH = "+w+";\n    static final int IMAGE_HEIGHT = "+h+";\n\n");
        b.append("""
                public void paint(Graphics g) {
                    Graphics2D g2d = (Graphics2D) g.create();
                    Insets border = getInsets();
                    int width = getWidth() - border.left - border.right;
                    int height = getHeight() - border.top - border.bottom;
                    double scale = Math.min(width / (double) IMAGE_WIDTH, height / (double) IMAGE_HEIGHT);
                    g2d.translate(border.left + (width - IMAGE_WIDTH * scale) / 2,
                            border.top + (height - IMAGE_HEIGHT * scale) / 2);
                    g2d.scale(scale, scale);
                    drawPortrait(g2d);
                    g2d.dispose();
                }

                public static void drawPortrait(Graphics2D g2d) {
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g2d.clip(new Rectangle2D.Double(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT));
                    g2d.setColor(Color.white);
                    g2d.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);

            """);
        for(int i=0;i<groups.size();i++) {
            if(groups.size()>20){if(i%10==0)b.append("        "+name+"Part"+(i/10+1)+".drawPart(g2d);\n");}
            else b.append("        "+method(i)+"(g2d);\n");
        }
        b.append("    }\n\n");
        b.append("""
                public static void main(String[] argv) throws Exception {
                    // Optional PNG output also works without opening a window.
                    if (argv.length == 2 && argv[0].equals("--png")) {
                        BufferedImage image = new BufferedImage(
                                IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB
                        );
                        Graphics2D g2d = image.createGraphics();
                        drawPortrait(g2d);
                        g2d.dispose();
                        ImageIO.write(image, "png", new File(argv[1]));
                        return;
                    }

            """);
        b.append("        "+name+" sg = new "+name+"();\n");
        b.append("        sg.setTitle(\"Generated Portrait\");\n");
        b.append("""
                    sg.setBackground(Color.white);
                    sg.setForeground(Color.black);
                    sg.setSize(IMAGE_WIDTH + 40, IMAGE_HEIGHT + 65);
                    sg.addWindowListener(new WindowAdapter() {
                        public void windowClosing(WindowEvent e) {
                            System.exit(0);
                        }
                    });
                    sg.setVisible(true);
                }

            """);
        if(groups.size()>20)b.append("}\n\n");
        for(int i=0;i<groups.size();i++) {
            if(groups.size()>20&&i%10==0){
                b.append("class "+name+"Part"+(i/10+1)+" {\n\n    public static void drawPart(Graphics2D g2d) {\n");
                for(int j=i;j<Math.min(i+10,groups.size());j++)b.append("        "+method(j)+"(g2d);\n");
                b.append("    }\n\n");
            }
            b.append("    public static void "+method(i)+"(Graphics2D g2d) {\n\n");
            for(String line:groups.get(i))b.append(line).append('\n');
            b.append("    }\n\n");
            if(groups.size()>20&&(i%10==9||i==groups.size()-1))b.append("}\n\n");
        }
        if(groups.size()<=20)b.append("}\n");
        String source=b.toString();
        if(viewer) {
            source=source.replace("(Graphics2D g2d)","(Graphics2D g2d, int layer)")
                .replace("(g2d);","(g2d, layer);");
            source=source.replace("    public void paint(Graphics g) {",LayerSupport.source(name,layers.count())+"    public void paint(Graphics g) {");
            source=source.replaceFirst("drawPortrait\\(g2d, layer\\);","drawPortrait(g2d, selectedLayer);");
            source=source.replace("        g2d.dispose();\n    }", "        g2d.dispose();\n        if (legendVisible) drawLegend(g);\n    }");
            source=source.replace("            drawPortrait(g2d, layer);","            drawPortrait(g2d, LAYER_COUNT);");
            source=source.replace("        sg.setSize(IMAGE_WIDTH + 40, IMAGE_HEIGHT + 65);","        sg.setSize(Math.max(440, IMAGE_WIDTH + 40), Math.max(300, IMAGE_HEIGHT + 65));\n        sg.setMinimumSize(new Dimension(440, 300));");
            source=source.replace("        sg.setVisible(true);","        sg.setVisible(true);\n        sg.requestFocus();");
        }
        return source;
    }
    static String method(int i){return i==0?"drawBaseColors":"drawDetails"+i;}
}
