import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.function.IntConsumer;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Local, dependency-free photo-to-Java application. Requires JDK 17 or later. */
public class PortraitStudio extends JFrame {
    BufferedImage reference, preview;
    List<PortraitRuntime.Mark> marks=new ArrayList<>();
    final ArrayDeque<List<PortraitRuntime.Mark>> undo=new ArrayDeque<>();
    final ArrayList<Point2D.Double> points=new ArrayList<>();
    final JComboBox<String> tool=new JComboBox<>(PortraitRuntime.NAMES);
    final JSpinner stroke=new JSpinner(new SpinnerNumberModel(2.0,.2,100.0,.5));
    final JSpinner angle=new JSpinner(new SpinnerNumberModel(0.0,-360.0,360.0,5.0));
    final JSpinner arcStart=new JSpinner(new SpinnerNumberModel(0.0,-360.0,360.0,5.0));
    final JSpinner arcExtent=new JSpinner(new SpinnerNumberModel(180.0,-360.0,360.0,5.0));
    final JComboBox<String> detail=new JComboBox<>(new String[]{"Quick", "Balanced", "Fine"});
    final JCheckBox fill=new JCheckBox("Filled shape",true), trace=new JCheckBox("Trace photo (45%)");
    final JTextField label=new JTextField("Portrait detail"), author=new JTextField("");
    final JLabel status=new JLabel("Open a photograph to begin."), hint=new JLabel();
    final JTextArea counts=new JTextArea(12,24);
    final JProgressBar progress=new JProgressBar(0,100);
    final Canvas sourceCanvas=new Canvas(true), drawingCanvas=new Canvas(false);
    Color color=new Color(65,42,35);
    final ArrayList<JComponent> controls=new ArrayList<>();
    volatile boolean stop;
    boolean busy, dirty;

    PortraitStudio() {
        super("Portrait Studio | Photograph to Java 2D");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent e){if(!busy && canDiscard()) dispose();}});
        JPanel toolbar=new JPanel(new FlowLayout(FlowLayout.LEFT));
        button(toolbar,"Open photo",()->openPhoto());
        button(toolbar,"Open project",()->openProject());
        button(toolbar,"Save project",()->saveProject());
        detail.setSelectedIndex(1); toolbar.add(detail);controls.add(detail);
        button(toolbar,"Generate",()->generate());
        JButton cancel=new JButton("Stop generation");cancel.addActionListener(e->stop=true);toolbar.add(cancel);
        button(toolbar,"Export Java + PNG",()->exportDialog());
        add(toolbar,BorderLayout.NORTH);
        JPanel pair=new JPanel(new GridLayout(1,2,12,0));pair.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        pair.add(wrap("REFERENCE / click to sample color",sourceCanvas));pair.add(wrap("JAVA 2D PORTRAIT / click to trace",drawingCanvas));
        add(pair,BorderLayout.CENTER);
        JPanel side=new JPanel();side.setLayout(new BoxLayout(side,BoxLayout.Y_AXIS));
        side.setBorder(BorderFactory.createEmptyBorder(12,8,12,16));side.setPreferredSize(new Dimension(300,600));
        field(side,"Technique",tool);field(side,"Feature label",label);
        field(side,"Stroke width",stroke);field(side,"Rotation in degrees",angle);
        field(side,"Arc start (degrees)",arcStart);field(side,"Arc extent (degrees)",arcExtent);
        side.add(fill);side.add(trace);controls.add(fill);controls.add(trace);
        button(side,"Choose color",()->{Color c=JColorChooser.showDialog(this,"Feature color",color);if(c!=null)color=c;});
        button(side,"Finish general path",()->finishPath());
        button(side,"Cancel points",()->{points.clear();drawingCanvas.repaint();});
        button(side,"Undo",()->{if(!undo.isEmpty()){marks=undo.pop();dirty=true;refresh();}});
        button(side,"Clear drawing",()->{if(reference!=null){checkpoint();marks=new ArrayList<>();dirty=true;refresh();}});
        field(side,"Author (optional)",author);
        counts.setEditable(false);counts.setFont(new Font(Font.MONOSPACED,Font.PLAIN,11));
        side.add(new JScrollPane(counts));
        button(side,"Audit visible techniques",()->auditDialog());
        for(Component component:side.getComponents())if(component instanceof JComponent jc)jc.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(side,BorderLayout.EAST);
        JPanel footer=new JPanel(new GridLayout(3,1));footer.setBorder(BorderFactory.createEmptyBorder(3,12,8,12));
        footer.add(hint);footer.add(status);footer.add(progress);add(footer,BorderLayout.SOUTH);
        tool.addActionListener(e->{points.clear();updateHint();drawingCanvas.repaint();});
        trace.addActionListener(e->drawingCanvas.repaint());updateHint();refresh();
        setSize(1380,860);setMinimumSize(new Dimension(1080,780));setLocationRelativeTo(null);
    }
    void button(JPanel p,String text,Runnable action){JButton b=new JButton(text);b.addActionListener(e->{try{action.run();}catch(Exception ex){error(ex);}});p.add(b);controls.add(b);}
    void field(JPanel p,String title,JComponent c){p.add(new JLabel(title));c.setMaximumSize(new Dimension(260,28));p.add(c);controls.add(c);}
    JPanel wrap(String title,JComponent c){JPanel p=new JPanel(new BorderLayout());p.add(new JLabel(title),BorderLayout.NORTH);p.add(c);return p;}
    void error(Exception e){JOptionPane.showMessageDialog(this,e.getMessage(),"Portrait Studio",JOptionPane.ERROR_MESSAGE);}
    boolean canDiscard(){return !dirty||JOptionPane.showConfirmDialog(this,"Discard unsaved drawing changes?","Unsaved project",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION;}
    void checkpoint(){undo.push(new ArrayList<>(marks));while(undo.size()>20)undo.removeLast();}
    void setBusy(boolean value){busy=value;for(JComponent c:controls)c.setEnabled(!value);}
    void updateHint(){int t=tool.getSelectedIndex();hint.setText(switch(t){
        case 1 -> "Quadratic: click start, control point, end. Use for eyelids or eyebrows.";
        case 2 -> "Cubic: click start, control 1, control 2, end. Use for lips or hair contours.";
        case 8 -> "General path: click outline vertices, then Finish general path. Use for hair, face, or clothing.";
        case 0 -> "Line: click two endpoints. Use for hair strands, seams, or glasses.";
        case 9 -> "Area: click opposite box corners for crescent shading. Rotation changes its direction.";
        default -> "Click opposite bounding-box corners. Circle and square use equal dimensions. Right-click cancels points.";
    });}
    void refresh(){
        points.clear();
        if(reference!=null)preview=PortraitRuntime.render(reference.getWidth(),reference.getHeight(),marks,-1);
        int[] n=new int[10];for(var m:marks)n[m.type]++;
        StringBuilder s=new StringBuilder("Shape records (audit visibility):\n");
        for(int i=0;i<10;i++)s.append(String.format("%-22s %d%n",PortraitRuntime.NAMES[i],n[i]));
        counts.setText(s.toString());sourceCanvas.repaint();drawingCanvas.repaint();
    }
    JFileChooser chooser(){return new JFileChooser();}
    void openPhoto(){
        if(!canDiscard())return;
        JFileChooser fc=chooser();fc.setFileFilter(new FileNameExtensionFilter("Photograph (PNG, JPEG, BMP, GIF)","png","jpg","jpeg","bmp","gif"));
        if(fc.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        try{reference=loadPhoto(fc.getSelectedFile().toPath(),720);marks=new ArrayList<>();undo.clear();dirty=false;refresh();status.setText("Photo loaded. Generate a draft, then refine features. Check orientation before tracing.");}
        catch(Exception ex){error(ex);}
    }
    static BufferedImage loadPhoto(Path file,int max) throws IOException {
        BufferedImage input;
        try(ImageInputStream stream=ImageIO.createImageInputStream(file.toFile())) {
            if(stream==null)throw new IOException("Cannot open image");
            Iterator<ImageReader> readers=ImageIO.getImageReaders(stream);
            if(!readers.hasNext())throw new IOException("Unsupported image. Use PNG, JPEG, BMP, or GIF.");
            ImageReader reader=readers.next();
            try{reader.setInput(stream);int w=reader.getWidth(0),h=reader.getHeight(0);
                if(w<1||h<1||w>100000||h>100000)throw new IOException("Invalid image dimensions");
                ImageReadParam param=reader.getDefaultReadParam();int sample=Math.max(1,Math.max(w,h)/Math.max(1,max*2));
                param.setSourceSubsampling(sample,sample,0,0);input=reader.read(0,param);
            }finally{reader.dispose();}
        }
        double scale=Math.min(1,max/(double)Math.max(input.getWidth(),input.getHeight()));
        int w=Math.max(1,(int)Math.round(input.getWidth()*scale)),h=Math.max(1,(int)Math.round(input.getHeight()*scale));
        BufferedImage output=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);Graphics2D g=output.createGraphics();
        g.setColor(Color.WHITE);g.fillRect(0,0,w,h);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(input,0,0,w,h,null);g.dispose();return output;
    }
    void generate(){
        if(reference==null)return;
        if(!marks.isEmpty()&&JOptionPane.showConfirmDialog(this,"Replace drawing with a new generated draft? Undo will restore it.","Generate",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        final int quality=detail.getSelectedIndex();stop=false;setBusy(true);status.setText("Fitting colored shapes to the photograph...");
        new SwingWorker<List<PortraitRuntime.Mark>,Integer>(){
            protected List<PortraitRuntime.Mark> doInBackground(){return fit(reference,quality,p->publish(p),()->stop);}
            protected void process(List<Integer> values){progress.setValue(values.get(values.size()-1));}
            protected void done(){try{checkpoint();marks=get();dirty=true;refresh();status.setText("Draft ready: "+marks.size()+" shapes. Refine important facial features and audit before export.");}catch(Exception ex){error(ex);}finally{setBusy(false);}}
        }.execute();
    }
    /** A coarse color field followed by error-reducing vector brush strokes of all ten types. */
    static List<PortraitRuntime.Mark> fit(BufferedImage target,int quality,IntConsumer progress,java.util.function.BooleanSupplier cancel){
        int w=target.getWidth(),h=target.getHeight(),tile=new int[]{12,8,5}[quality];
        List<PortraitRuntime.Mark> result=new ArrayList<>();
        for(int y=0;y<h;y+=tile)for(int x=0;x<w;x+=tile){
            int tw=Math.min(tile,w-x),th=Math.min(tile,h-y);long r=0,g=0,b=0;
            for(int yy=y;yy<y+th;yy++)for(int xx=x;xx<x+tw;xx++){int c=target.getRGB(xx,yy);r+=(c>>16)&255;g+=(c>>8)&255;b+=c&255;}
            int n=tw*th,rgb=((int)(r/n)<<16)|((int)(g/n)<<8)|(int)(b/n);
            result.add(new PortraitRuntime.Mark(tw==th?7:6,rgb,1,true,0,"Automatic tonal base",x,y,tw,th));
        }
        BufferedImage current=PortraitRuntime.render(w,h,result,-1);
        Random rng=new Random(26115);int attempts=new int[]{12000,40000,90000}[quality];
        for(int step=0;step<attempts;step++){
            if(cancel.getAsBoolean())break;
            int type=step%10;
            // Prefer locations whose remaining color error is large.
            int x=0,y=0;double best=-1;
            for(int k=0;k<5;k++){int xx=rng.nextInt(w),yy=rng.nextInt(h);double e=distance(target.getRGB(xx,yy),current.getRGB(xx,yy));if(e>best){best=e;x=xx;y=yy;}}
            double size=2+rng.nextDouble()*tile*(1.8-1.2*step/(double)attempts);
            var mark=candidate(type,x,y,size,rng);
            if(improve(mark,target,current))result.add(mark);
            if(step%500==0)progress.accept(step*100/attempts);
        }
        progress.accept(100);return result;
    }
    static double distance(int a,int b){int r=((a>>16)&255)-((b>>16)&255),g=((a>>8)&255)-((b>>8)&255),z=(a&255)-(b&255);return r*r+g*g+z*z;}
    static PortraitRuntime.Mark candidate(int t,double x,double y,double s,Random r){
        double w=s,h=s*(.35+r.nextDouble()),left=x-w/2,top=y-h/2;
        double[] p=switch(t){
            case 0 -> new double[]{left,y,left+w,y};
            case 1 -> new double[]{left,y,x,top,left+w,y};
            case 2 -> new double[]{left,y,left+w*.3,top,left+w*.7,top+h,left+w,y};
            case 3 -> new double[]{left,top,w,h,0,70+r.nextInt(240)};
            case 8 -> new double[]{left,top+h*.8,x,top,left+w,top+h*.7,x,top+h};
            default -> new double[]{left,top,w,h};
        };
        boolean filled=t>=4 || t==3&&r.nextBoolean();
        return new PortraitRuntime.Mark(t,0,Math.max(.65,s*.22),filled,r.nextInt(360),"Automatic photo tone / edge",p);
    }
    /** Fit the constant color analytically using the actual antialiased coverage mask. */
    static boolean improve(PortraitRuntime.Mark m,BufferedImage target,BufferedImage current){
        Shape shape=PortraitRuntime.ink(m);Rectangle bounds=shape.getBounds();bounds.grow(1,1);
        bounds=bounds.intersection(new Rectangle(0,0,target.getWidth(),target.getHeight()));
        if(bounds.isEmpty())return false;
        BufferedImage mask=new BufferedImage(bounds.width,bounds.height,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=mask.createGraphics();g.translate(-bounds.x,-bounds.y);
        int savedColor=m.rgb;m.rgb=0xffffff;PortraitRuntime.paint(g,List.of(m),-1);m.rgb=savedColor;g.dispose();
        double[] sums=new double[3];double weight=0;
        for(int y=0;y<bounds.height;y++)for(int x=0;x<bounds.width;x++){
            double a=(mask.getRGB(x,y)>>>24)/255.0;if(a==0)continue;
            int t=target.getRGB(x+bounds.x,y+bounds.y),c=current.getRGB(x+bounds.x,y+bounds.y);
            for(int k=0;k<3;k++){int shift=16-k*8;sums[k]+=a*(((t>>shift)&255)-(1-a)*((c>>shift)&255));}weight+=a*a;
        }
        if(weight<.3)return false;int rgb=0;for(double sum:sums)rgb=(rgb<<8)|Math.max(0,Math.min(255,(int)Math.round(sum/weight)));
        double gain=0;
        for(int y=0;y<bounds.height;y++)for(int x=0;x<bounds.width;x++){
            double a=(mask.getRGB(x,y)>>>24)/255.0;if(a==0)continue;
            int t=target.getRGB(x+bounds.x,y+bounds.y),c=current.getRGB(x+bounds.x,y+bounds.y),mixed=blend(c,rgb,a);
            gain+=distance(t,c)-distance(t,mixed);
        }
        if(gain<8)return false;
        m.rgb=rgb;Graphics2D cg=current.createGraphics();PortraitRuntime.paint(cg,List.of(m),-1);cg.dispose();return true;
    }
    static int blend(int c,int rgb,double a){int out=0;for(int shift:new int[]{16,8,0})out=(out<<8)|(int)Math.round(((c>>shift)&255)*(1-a)+((rgb>>shift)&255)*a);return out;}

    class Canvas extends JPanel {
        final boolean original;
        Canvas(boolean original){this.original=original;setBackground(new Color(230,233,237));addMouseListener(new MouseAdapter(){public void mousePressed(MouseEvent e){clicked(e);}});}
        double scale(){return reference==null?1:Math.min((getWidth()-20.0)/reference.getWidth(),(getHeight()-20.0)/reference.getHeight());}
        int ox(){return reference==null?0:(int)((getWidth()-reference.getWidth()*scale())/2);}
        int oy(){return reference==null?0:(int)((getHeight()-reference.getHeight()*scale())/2);}
        protected void paintComponent(Graphics graphics){
            super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();PortraitRuntime.configure(g);
            if(reference==null){g.setColor(Color.DARK_GRAY);g.drawString(original?"Open your photograph":"Your vector portrait appears here",35,70);g.dispose();return;}
            g.translate(ox(),oy());g.scale(scale(),scale());g.drawImage(original?reference:preview,0,0,null);
            if(!original&&trace.isSelected()){g.setComposite(AlphaComposite.SrcOver.derive(.45f));g.drawImage(reference,0,0,null);g.setComposite(AlphaComposite.SrcOver);}
            if(!original){g.setColor(Color.CYAN);g.setStroke(new BasicStroke((float)(1/scale())));Point2D prev=null;
                for(Point2D p:points){g.fill(new Ellipse2D.Double(p.getX()-3,p.getY()-3,6,6));if(prev!=null)g.draw(new Line2D.Double(prev,p));prev=p;}}
            g.dispose();
        }
        void clicked(MouseEvent e){
            if(reference==null||busy)return;
            if(SwingUtilities.isRightMouseButton(e)){points.clear();repaint();return;}
            double x=(e.getX()-ox())/scale(),y=(e.getY()-oy())/scale();
            if(x<0||y<0||x>=reference.getWidth()||y>=reference.getHeight())return;
            if(original){color=new Color(reference.getRGB((int)x,(int)y));status.setText(String.format("Sampled #%06X at (%.1f, %.1f)",color.getRGB()&0xffffff,x,y));return;}
            points.add(new Point2D.Double(Math.rint(x*10)/10,Math.rint(y*10)/10));
            int t=tool.getSelectedIndex(),needed=t==1?3:t==2?4:2;
            if(t!=8&&points.size()==needed)commitPoints();else status.setText(points.size()+" point(s) selected. "+(t==8?"Finish general path when ready.":"Continue clicking."));repaint();
        }
    }
    void finishPath(){if(tool.getSelectedIndex()==8&&points.size()>=3)commitPoints();else status.setText("Select General path and place at least 3 points.");}
    void commitPoints(){
        int t=tool.getSelectedIndex();double[] p;
        if(t==0||t==1||t==2||t==8){p=new double[points.size()*2];for(int i=0;i<points.size();i++){p[i*2]=points.get(i).x;p[i*2+1]=points.get(i).y;}}
        else{Point2D a=points.get(0),b=points.get(1);double w=Math.abs(a.getX()-b.getX()),h=Math.abs(a.getY()-b.getY());
            if(w<.5||h<.5){points.clear();status.setText("Choose a larger bounding box.");return;}
            if(t==5||t==7)w=h=Math.min(w,h);
            p=t==3?new double[]{Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),w,h,number(arcStart),number(arcExtent)}:new double[]{Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),w,h};}
        checkpoint();marks.add(new PortraitRuntime.Mark(t,color.getRGB(),number(stroke),fill.isSelected()&&t!=0,number(angle),label.getText(),p));dirty=true;refresh();status.setText("Added "+PortraitRuntime.NAMES[t]+": "+label.getText());
    }
    double number(JSpinner s){return ((Number)s.getValue()).doubleValue();}
    void saveProject(){
        if(reference==null)return;JFileChooser fc=chooser();fc.setSelectedFile(new File("portrait.portrait"));
        if(fc.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        Path path=fc.getSelectedFile().toPath();if(!overwrite(path))return;
        try{writeProject(path,reference,marks,author.getText());dirty=false;status.setText("Project saved: "+path);}catch(Exception ex){error(ex);}
    }
    static void writeProject(Path path,BufferedImage ref,List<PortraitRuntime.Mark> marks,String author)throws IOException{
        ByteArrayOutputStream image=new ByteArrayOutputStream();ImageIO.write(ref,"png",image);
        try(BufferedWriter out=Files.newBufferedWriter(path,StandardCharsets.UTF_8)){
            out.write("PORTRAIT-STUDIO-1\n"+Base64.getEncoder().encodeToString(author.getBytes(StandardCharsets.UTF_8))+"\n");
            out.write(Base64.getEncoder().encodeToString(image.toByteArray())+"\n");
            for(var m:marks)out.write(m.row()+"\t"+Base64.getEncoder().encodeToString(m.label.getBytes(StandardCharsets.UTF_8))+"\n");
        }
    }
    void openProject(){
        if(!canDiscard())return;JFileChooser fc=chooser();fc.setFileFilter(new FileNameExtensionFilter("Portrait Studio project","portrait"));
        if(fc.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        try{Path path=fc.getSelectedFile().toPath();if(Files.size(path)>50_000_000)throw new IOException("Project exceeds 50 MB");
            List<String> lines=Files.readAllLines(path,StandardCharsets.UTF_8);
            if(lines.size()<3||!lines.get(0).equals("PORTRAIT-STUDIO-1"))throw new IOException("Not a Portrait Studio project");
            BufferedImage loaded=ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(lines.get(2))));
            if(loaded==null||loaded.getWidth()>2000||loaded.getHeight()>2000)throw new IOException("Invalid project image");
            List<PortraitRuntime.Mark> parsed=new ArrayList<>();
            for(int i=3;i<lines.size();i++){String[] pair=lines.get(i).split("\t",-1);parsed.add(PortraitRuntime.Mark.parse(pair[0],new String(Base64.getDecoder().decode(pair[1]),StandardCharsets.UTF_8)));}
            reference=loaded;marks=parsed;author.setText(new String(Base64.getDecoder().decode(lines.get(1)),StandardCharsets.UTF_8));undo.clear();dirty=false;refresh();status.setText("Project loaded.");
        }catch(Exception ex){error(ex);}
    }
    boolean overwrite(Path file){return !Files.exists(file)||JOptionPane.showConfirmDialog(this,"Replace "+file.getFileName()+"?","File exists",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION;}
    static String audit(BufferedImage ref,List<PortraitRuntime.Mark> marks){
        int w=ref.getWidth(),h=ref.getHeight();BufferedImage full=PortraitRuntime.render(w,h,marks,-1);
        StringBuilder b=new StringBuilder("TECHNIQUE AUDIT\n\nOmitting each technique measures its visible contribution in the final image.\nA count alone does not establish visual quality.\n\n");
        int represented=0;for(int t=0;t<10;t++){
            int count=0;for(var m:marks)if(m.type==t)count++;
            BufferedImage omitted=PortraitRuntime.render(w,h,marks,t);int changed=0;
            for(int y=0;y<h;y++)for(int x=0;x<w;x++)if(distance(full.getRGB(x,y),omitted.getRGB(x,y))>9)changed++;
            if(count>0&&changed>0)represented++;
            b.append(String.format("%-24s %6d records | %7d affected pixels%n",PortraitRuntime.NAMES[t],count,changed));
        }
        double sum=0;for(int y=0;y<h;y++)for(int x=0;x<w;x++)sum+=distance(ref.getRGB(x,y),full.getRGB(x,y));
        b.append("\nVisible technique families: "+represented+" / 10\n");
        b.append(String.format(Locale.ROOT,"RGB reconstruction RMSE: %.2f / 255 (not a facial-likeness or grading score).%n",Math.sqrt(sum/(w*(double)h*3))));
        b.append("\nReview eyes, nose, mouth, face proportions, hair and clothing against the original.\n");
        b.append("Automatic marks fit local color/edges, without identifying facial features.\nUse tracing and feature labels to explain how each technique constructs your portrait.\n");return b.toString();
    }
    void auditDialog(){if(reference==null)return;setBusy(true);status.setText("Measuring visible technique contributions...");new SwingWorker<String,Void>(){
        protected String doInBackground(){return audit(reference,marks);}
        protected void done(){try{JTextArea a=new JTextArea(get(),23,88);a.setEditable(false);a.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));JOptionPane.showMessageDialog(PortraitStudio.this,new JScrollPane(a),"Technique audit",JOptionPane.INFORMATION_MESSAGE);}catch(Exception e){error(e);}finally{setBusy(false);status.setText("Audit complete.");}}
    }.execute();}
    void exportDialog(){
        if(reference==null||marks.isEmpty()){status.setText("Generate or trace a portrait first.");return;}
        JFileChooser fc=chooser();fc.setSelectedFile(new File("Portrait.java"));
        if(fc.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        Path file=fc.getSelectedFile().toPath().toAbsolutePath();String name=file.getFileName().toString();
        if(!name.endsWith(".java")){error(new Exception("Use a .java filename, such as Portrait.java"));return;}
        String cls=name.substring(0,name.length()-5);
        if(!validClass(cls)){error(new Exception("Use a valid Java class name, such as Portrait."));return;}
        for(Path path:exportPaths(file))if(!overwrite(path))return;
        setBusy(true);status.setText("Exporting source, images, and technique documentation...");
        new SwingWorker<Void,Void>(){protected Void doInBackground()throws Exception{export(file,reference,marks,author.getText());return null;}
            protected void done(){try{get();status.setText("Exported "+file+". Compile with: javac "+name);JOptionPane.showMessageDialog(PortraitStudio.this,"Saved Java, portrait PNG, reference PNG, and technique report.\nCompile: javac "+name+"\nRun: java "+cls+"\nKeep your original full-resolution photograph for submission.");}catch(Exception e){error(e);}finally{setBusy(false);}}}.execute();
    }
    static boolean validClass(String name){return name.matches("[A-Za-z_$][A-Za-z0-9_$]*")&&!javax.lang.model.SourceVersion.isKeyword(name)&&!Set.of("var","yield","record","sealed","permits","PortraitRuntime","PortraitStudio","String","Math","Color","Graphics","Graphics2D","JPanel","JFrame","SwingUtilities","Dimension","ImageIO","File","BufferedImage","Rectangle2D","RenderingHints","List","Shape","BasicStroke","AffineTransform","GeneralPath","Area","Line2D","QuadCurve2D","CubicCurve2D","Arc2D","Ellipse2D","Integer","Double","Boolean","Exception","StringBuilder","IllegalArgumentException","Override","Frame","Insets","WindowAdapter","WindowEvent","KeyAdapter","KeyEvent","Font").contains(name);}
    static List<Path> exportPaths(Path java){String base=java.toString().substring(0,java.toString().length()-5);return List.of(java,Path.of(base+".png"),Path.of(base+"_reference.png"),Path.of(base+"_techniques.txt"));}
    static void export(Path file,BufferedImage reference,List<PortraitRuntime.Mark> marks,String author)throws IOException{
        String cls=file.getFileName().toString().replaceFirst("\\.java$","");if(!validClass(cls))throw new IOException("Invalid class name");
        String runtime;
        try(InputStream in=PortraitStudio.class.getResourceAsStream("/PortraitRuntime.java")){
            if(in!=null)runtime=new String(in.readAllBytes(),StandardCharsets.UTF_8);
            else runtime=Files.readString(Path.of("PortraitRuntime.java"));
        }
        int w=reference.getWidth(),h=reference.getHeight();
        StringBuilder source=new StringBuilder("// Standalone Java 2D portrait. No photograph is loaded or embedded.\n");
        source.append("// Compile: javac "+cls+".java | Run: java "+cls+"\n");
        source.append("// Author (optional): "+safeComment(author)+"\n");
        for(int i=0;i<marks.size();i++){var m=marks.get(i);if(!m.label.startsWith("Automatic"))source.append("// Shape "+(i+1)+" - "+PortraitRuntime.NAMES[m.type]+": "+safeComment(m.label)+"\n");}
        source.append(runtime.substring(0,runtime.indexOf("/** Shared")));
        source.append("import javax.swing.*;\nimport javax.imageio.ImageIO;\nimport java.io.File;\n\n");
        source.append("public class "+cls+" extends JPanel {\n");
        source.append("    static final int WIDTH="+w+", HEIGHT="+h+";\n");
        source.append("    static final java.util.List<PortraitRuntime.Mark> MARKS = new java.util.ArrayList<>();\n");
        // Split numeric geometry into moderate-sized text blocks to avoid JVM method/constant limits.
        List<String> chunks=new ArrayList<>();StringBuilder chunk=new StringBuilder();
        for(var m:marks){String row=m.row()+"\n";if(chunk.length()+row.length()>24000){chunks.add(chunk.toString());chunk.setLength(0);}chunk.append(row);}
        if(chunk.length()>0)chunks.add(chunk.toString());
        source.append("    // Row: technique ID, RGB integer, stroke, filled, rotation degrees, coordinates.\n");
        source.append("    // IDs 0..9: Line, Quadratic, Cubic, Arc, Ellipse, Circle, Rectangle, Square, Path, Area.\n");
        source.append("    // Numeric shape data is kept separate from the readable construction methods below.\n");
        source.append("    static {\n");for(int i=0;i<chunks.size();i++)source.append("        load(data"+i+"());\n");source.append("    }\n");
        source.append("    static void load(String data) { for(String row:data.split(\"\\n\")) if(!row.isBlank()) MARKS.add(PortraitRuntime.Mark.parse(row, \"Portrait shape\")); }\n");
        for(int i=0;i<chunks.size();i++)source.append("    static String data"+i+"() { return \"\"\"\n"+chunks.get(i)+"\"\"\"; }\n");
        source.append("""
            @Override protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                Graphics2D g = (Graphics2D) graphics.create();
                double scale = Math.min(getWidth()/(double)WIDTH, getHeight()/(double)HEIGHT);
                g.translate((getWidth()-WIDTH*scale)/2, (getHeight()-HEIGHT*scale)/2);
                g.scale(scale,scale);
                g.clip(new Rectangle2D.Double(0,0,WIDTH,HEIGHT));
                g.setColor(Color.WHITE); g.fillRect(0,0,WIDTH,HEIGHT);
                PortraitRuntime.paint(g, MARKS, -1);
                g.dispose();
            }
            public static void main(String[] args) throws Exception {
                if(args.length==2 && args[0].equals("--png")) {
                    ImageIO.write(PortraitRuntime.render(WIDTH,HEIGHT,MARKS,-1), "png", new File(args[1]));
                    return;
                }
                SwingUtilities.invokeLater(() -> {
                    JFrame frame = new JFrame("Generated Portrait");
        """);
        source.append("            "+cls+" panel = new "+cls+"();\n");
        source.append("""
                    panel.setBackground(Color.WHITE);
                    panel.setPreferredSize(new Dimension(WIDTH,HEIGHT));
                    frame.setContentPane(panel);
                    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                    frame.pack(); frame.setLocationRelativeTo(null); frame.setVisible(true);
                });
            }
        }

        """);
        source.append(runtime.substring(runtime.indexOf("/** Shared")));
        List<Path> paths=exportPaths(file);Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(paths.get(0),source,StandardCharsets.UTF_8);
        ImageIO.write(PortraitRuntime.render(w,h,marks,-1),"png",paths.get(1).toFile());
        ImageIO.write(reference,"png",paths.get(2).toFile());
        StringBuilder report=new StringBuilder("Author (optional): "+author+"\n\n"+audit(reference,marks));
        report.append("\nMANUALLY LABELED FEATURES (record numbers match shape-data order)\n");
        for(int i=0;i<marks.size();i++){var m=marks.get(i);if(!m.label.startsWith("Automatic"))report.append((i+1)+". "+PortraitRuntime.NAMES[m.type]+": "+m.label+"\n");}
        report.append("\nEXPORT CHECKLIST\n[ ] Original full-resolution photograph\n[ ] Java-generated portrait PNG\n[ ] Standalone .java source\n[ ] Compile and run the exact exported .java\n[ ] Explain 9-10 visibly meaningful techniques\n[ ] Check the filename, author attribution, and output folder\n");
        Files.writeString(paths.get(3),report,StandardCharsets.UTF_8);
    }
    static String safeComment(String text){return text.replaceAll("[^A-Za-z0-9 .,():|/_-]"," ");}
    public static void main(String[] args)throws Exception{
        if(args.length>=3&&args[0].equals("--generate")){
            Path source=Path.of(args[1]),out=Path.of(args[2]);int quality=args.length>3?Integer.parseInt(args[3]):1;
            if(quality<0||quality>2)throw new IllegalArgumentException("Quality must be 0, 1, or 2");
            if(!out.toString().endsWith(".java"))throw new IllegalArgumentException("Output must end in .java");
            for(Path p:exportPaths(out))if(Files.exists(p))throw new IOException("Refusing to overwrite "+p);
            BufferedImage ref=loadPhoto(source,720);var marks=fit(ref,quality,p->{},()->false);export(out,ref,marks,"");
            System.out.println(audit(ref,marks));return;
        }
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeLater(()->new PortraitStudio().setVisible(true));
    }
}
