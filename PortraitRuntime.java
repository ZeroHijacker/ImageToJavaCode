import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/** Shared shape renderer. Also copied verbatim into exported standalone source. */
class PortraitRuntime {
    static final String[] NAMES = {"Line", "Quadratic curve", "Cubic curve", "Arc",
        "Ellipse", "Circle", "Rectangle", "Square", "General path", "Area / transformation"};

    /** Coordinates are image coordinates: origin at top left, y increases downwards. */
    static class Mark {
        int type, rgb;
        double stroke, angle;
        boolean fill;
        double[] p;
        String label;
        Mark(int type, int rgb, double stroke, boolean fill, double angle, String label, double... p) {
            this.type=type; this.rgb=rgb; this.stroke=stroke; this.fill=fill;
            this.angle=angle; this.label=label; this.p=p;
        }
        String row() {
            StringBuilder b=new StringBuilder(type+","+(rgb&0xffffff)+","+stroke+","+fill+","+angle);
            for(double n:p) b.append(',').append(n);
            return b.toString();
        }
        static Mark parse(String row, String label) {
            String[] v=row.split(",");
            int t=Integer.parseInt(v[0]);
            if(t<0 || t>9 || v.length<9 || v.length>2005) throw new IllegalArgumentException("Invalid shape record");
            double[] p=new double[v.length-5];
            for(int i=0;i<p.length;i++) {
                p[i]=Double.parseDouble(v[i+5]);
                if(!Double.isFinite(p[i]) || Math.abs(p[i])>100000) throw new IllegalArgumentException("Invalid coordinate");
            }
            int expected=t==1?6:t==2?8:t==3?6:4;
            if(t==9 && p[0]==-999) validateRings(p);
            else if(t==8 ? (p.length<6 || p.length%2!=0) : p.length!=expected) throw new IllegalArgumentException("Invalid point count");
            double stroke=Double.parseDouble(v[2]), angle=Double.parseDouble(v[4]);
            if(!Double.isFinite(stroke)||stroke<=0||stroke>500||!Double.isFinite(angle)) throw new IllegalArgumentException("Invalid stroke or angle");
            return new Mark(t,Integer.parseInt(v[1]),stroke,Boolean.parseBoolean(v[3]),angle,label,p);
        }
    }

    static Shape geometry(Mark m) {
        double[] p=m.p;
        Shape s;
        switch(m.type) {
            case 0: s=new Line2D.Double(p[0],p[1],p[2],p[3]); break;
            case 1: s=new QuadCurve2D.Double(p[0],p[1],p[2],p[3],p[4],p[5]); break;
            case 2: s=new CubicCurve2D.Double(p[0],p[1],p[2],p[3],p[4],p[5],p[6],p[7]); break;
            case 3: s=new Arc2D.Double(p[0],p[1],p[2],p[3],p[4],p[5],m.fill?Arc2D.PIE:Arc2D.OPEN); break;
            case 4: s=new Ellipse2D.Double(p[0],p[1],p[2],p[3]); break;
            case 5: s=new Ellipse2D.Double(p[0],p[1],p[2],p[2]); break; // equal diameters: circle
            case 6: s=new Rectangle2D.Double(p[0],p[1],p[2],p[3]); break;
            case 7: s=new Rectangle2D.Double(p[0],p[1],p[2],p[2]); break; // equal sides: square
            case 8:
                GeneralPath path=new GeneralPath();
                path.moveTo(p[0],p[1]);
                for(int i=2;i<p.length;i+=2) path.lineTo(p[i],p[i+1]);
                if(m.fill) path.closePath();
                s=path; break;
            case 9:
                if(p[0]==-999) {
                    // Image-derived contour topology: first ring is outer boundary;
                    // subsequent rings are actual holes detected in the color region.
                    Area region=new Area();int offset=2;
                    for(int ring=0;ring<(int)p[1];ring++) {
                        int vertices=(int)p[offset++];GeneralPath boundary=new GeneralPath();
                        boundary.moveTo(p[offset],p[offset+1]);offset+=2;
                        for(int i=1;i<vertices;i++){boundary.lineTo(p[offset],p[offset+1]);offset+=2;}
                        boundary.closePath();
                        if(ring==0)region.add(new Area(boundary));else region.subtract(new Area(boundary));
                    }
                    s=region;break;
                }
                // Boolean difference creates a crescent used for cheek/eyelid/hair shading.
                Area crescent=new Area(new Ellipse2D.Double(p[0],p[1],p[2],p[3]));
                crescent.subtract(new Area(new Ellipse2D.Double(p[0]+p[2]*.28,p[1]-p[3]*.12,p[2],p[3])));
                s=crescent; break;
            default: throw new IllegalArgumentException("Unknown technique");
        }
        Rectangle2D bounds=s.getBounds2D();
        if(m.angle==0)return s;
        // Rotate about each feature's center, preserving its placement in the portrait.
        return AffineTransform.getRotateInstance(Math.toRadians(m.angle),bounds.getCenterX(),bounds.getCenterY()).createTransformedShape(s);
    }

    static void validateRings(double[] data) {
        if(data.length<10 || data[1]<1 || data[1]!=(int)data[1])throw new IllegalArgumentException("Invalid Area rings");
        int offset=2;
        for(int ring=0;ring<(int)data[1];ring++) {
            if(offset>=data.length)throw new IllegalArgumentException("Missing Area ring");
            double vertices=data[offset++];
            if(vertices<3 || vertices!=(int)vertices || offset+2*vertices>data.length)throw new IllegalArgumentException("Invalid Area boundary");
            offset+=(int)vertices*2;
        }
        if(offset!=data.length)throw new IllegalArgumentException("Unexpected Area coordinates");
    }

    static Shape ink(Mark m) {
        Shape s=geometry(m);
        return m.fill && m.type!=0 ? s : new BasicStroke((float)m.stroke,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND).createStrokedShape(s);
    }
    static void configure(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
    }
    static void paint(Graphics2D g, List<Mark> marks, int omit) {
        configure(g);
        for(Mark m:marks) if(m.type!=omit) {
            g.setColor(new Color(m.rgb));
            Shape shape=geometry(m);
            if(m.fill&&m.type!=0)g.fill(shape);
            else {
                g.setStroke(new BasicStroke((float)m.stroke,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                g.draw(shape);
            }
        }
    }
    static BufferedImage render(int w,int h,List<Mark> marks,int omit) {
        BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();
        g.setColor(Color.WHITE);g.fillRect(0,0,w,h);paint(g,marks,omit);g.dispose();return image;
    }
}
