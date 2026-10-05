import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

/** One-step interface: select a photo, then automatically create verified Java. */
public class AutomaticStudio extends JFrame {
    static final String VERSION="0.2.1";
    final JLabel status=new JLabel("Choose an image or drop one onto the portrait preview to begin.");
    final JComboBox<String> quality=new JComboBox<>(new String[]{"Quick (560 px)","Balanced (800 px)","Fine (1000 px)"});
    final JComboBox<String> maximumLines=new JComboBox<>(new String[]{"500", "1000", "2000", "5000", "10000", "No limit"});
    final JComboBox<String> maximumShapes=new JComboBox<>(new String[]{"No limit", "10", "25", "50", "100", "250", "500", "1000"});
    final JComboBox<String> countMode=new JComboBox<>(new String[]{"Both", "Total shapes", "Visible technique families"});
    final JCheckBox layerComments=new JCheckBox("Add layer comments to Java");
    final JCheckBox layerViewer=new JCheckBox("Enable keyboard layer viewer in Java");
    final JLabel counts=new JLabel("No conversion yet.");
    final JTextField author=new JTextField("",25);
    final JButton convert=new JButton("Choose image and preview"), stop=new JButton("Cancel"), folder=new JButton("Open output folder");
    final JButton regenerate=new JButton("Regenerate");
    final JLabel selectedImageLabel=new JLabel("No image selected");
    Path selectedImage,selectedOutput,lastSaved;
    boolean settingsDirty;
    final JProgressBar progress=new JProgressBar();
    final Picture original=new Picture("Original"), result=new Picture("Java 2D result");
    final JComboBox<String> artStyle=new JComboBox<>(new String[]{"Original", "Realism", "Mosaic", "Abstract", "Cubism", "Minimalist", "Pixelated"});
    final JComboBox<String> recolor=new JComboBox<>(new String[]{"None", "Monochrome", "Vintage", "Sepia", "Cool", "Warm", "Posterized"});
    final Picture styleTarget=new Picture("Style target (Java reconstruction may simplify it)");
    final JCheckBox darkMode=new JCheckBox("Dark mode",true);
    final JButton save=new JButton("Save Java + assets ZIP");
    final JButton discard=new JButton("Discard image");
    final JTextArea code=new JTextArea();
    final JTabbedPane tabs=new JTabbedPane();
    AutomaticEngine.Draft draft;
    final JTextArea report=new JTextArea();
    final AtomicBoolean cancelled=new AtomicBoolean();
    AutomaticEngine.Result latest;
    boolean busy;

    AutomaticStudio() {
        super("Portrait Studio "+VERSION+" | Automatic photo to Java");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent e){if(busy){cancelled.set(true);status.setText("Cancelling conversion...");}else dispose();}});
        JPanel controls=new JPanel(new FlowLayout(FlowLayout.LEFT,12,12));
        controls.add(convert);quality.setSelectedIndex(1);controls.add(quality);controls.add(new JLabel("Author (optional)"));controls.add(author);controls.add(stop);controls.add(folder);
        JButton help=new JButton("User guide");controls.add(help);
        help.addActionListener(e->{try{Desktop.getDesktop().browse(AutomaticEngine.home().resolve("USER_GUIDE.html").toUri());}catch(Exception ex){error(ex);}});
        JPanel sourceOptions=new JPanel(new FlowLayout(FlowLayout.LEFT,12,4));
        maximumLines.setEditable(true);maximumLines.setSelectedItem("5000");
        sourceOptions.add(new JLabel("Maximum Java lines"));sourceOptions.add(maximumLines);
        sourceOptions.add(new JLabel("Readable Java source. Includes comments and blank lines. Lower limits reduce detail."));
        JPanel shapeOptions=new JPanel(new FlowLayout(FlowLayout.LEFT,12,4));
        maximumShapes.setEditable(true);
        shapeOptions.add(new JLabel("Maximum drawing shapes"));shapeOptions.add(maximumShapes);
        shapeOptions.add(new JLabel("Show count"));shapeOptions.add(countMode);shapeOptions.add(counts);
        maximumShapes.setToolTipText("Each drawn mark counts once; an Area with holes is one shape. Both limits apply.");
        countMode.addActionListener(e->updateCounts());
        JPanel layerOptions=new JPanel(new FlowLayout(FlowLayout.LEFT,12,4));
        layerOptions.add(darkMode);layerOptions.add(save);layerOptions.add(discard);
        layerOptions.add(layerComments);layerOptions.add(layerViewer);
        layerViewer.setToolTipText("Layers follow overlap. H toggles the hidden legend in the generated program.");
        JPanel styleOptions=new JPanel(new FlowLayout(FlowLayout.LEFT,12,4));
        styleOptions.add(new JLabel("Art style"));styleOptions.add(artStyle);styleOptions.add(new JLabel("Recolor"));styleOptions.add(recolor);
        styleOptions.add(regenerate);styleOptions.add(selectedImageLabel);
        artStyle.setToolTipText("Realism preserves detail; geometric styles are approximations, constrained by your shape and line limits.");
        JPanel top=new JPanel(new GridLayout(5,1));top.add(controls);top.add(sourceOptions);top.add(shapeOptions);top.add(layerOptions);top.add(styleOptions);add(top,BorderLayout.NORTH);
        JPanel pair=new JPanel(new GridLayout(1,2,16,0));pair.setBorder(BorderFactory.createEmptyBorder(8,16,12,16));pair.add(original);pair.add(result);
        report.setEditable(false);report.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));report.setText("Automatic pipeline\n\n1. Read and orient image\n2. Extract color regions and edges\n3. Fit paths, curves, and primitives\n4. Refine image detail\n5. Verify visible techniques\n6. Export, compile, and execute Java\n\nNo tracing or manual shape placement required.");
        JSplitPane split=new JSplitPane(JSplitPane.VERTICAL_SPLIT,pair,new JScrollPane(report));split.setResizeWeight(.8);split.setDividerLocation(580);tabs.addTab("Portrait preview & report",split);
        code.setEditable(false);code.setFont(new Font(Font.MONOSPACED,Font.PLAIN,14));code.setTabSize(4);
        JPanel codePanel=new JPanel(new BorderLayout(8,8));
        codePanel.add(new JScrollPane(code),BorderLayout.CENTER);
        JPanel searchBar=new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField search=new JTextField(25);JButton find=new JButton("Find next");
        find.addActionListener(e->{String term=search.getText();if(term.isEmpty())return;String text=code.getText();int at=text.indexOf(term,code.getSelectionEnd());if(at<0)at=text.indexOf(term);if(at>=0){code.requestFocusInWindow();code.select(at,at+term.length());}});
        search.addActionListener(e->find.doClick());searchBar.add(new JLabel("Find in Java"));searchBar.add(search);searchBar.add(find);
        searchBar.add(new JLabel("Read-only preview. Select text and Ctrl+C to copy."));codePanel.add(searchBar,BorderLayout.NORTH);
        tabs.addTab("Java code preview",codePanel);tabs.addTab("Style target",styleTarget);add(tabs);
        JPanel bottom=new JPanel(new BorderLayout(8,8));bottom.setBorder(BorderFactory.createEmptyBorder(10,16,12,16));bottom.add(status);bottom.add(progress,BorderLayout.SOUTH);add(bottom,BorderLayout.SOUTH);
        stop.setEnabled(false);folder.setEnabled(false);convert.addActionListener(e->choose());stop.addActionListener(e->{cancelled.set(true);status.setText("Cancelling...");});
        folder.addActionListener(e->{try{Desktop.getDesktop().open(lastSaved.getParent().toFile());}catch(Exception ex){error(ex);}});
        discard.setEnabled(false);discard.addActionListener(e->discardPreview());
        save.setEnabled(false);save.addActionListener(e->savePreview());
        regenerate.setEnabled(false);regenerate.addActionListener(e->regenerate());
        for(JComboBox<?> option:List.of(quality,maximumLines,maximumShapes,artStyle,recolor))option.addActionListener(e->settingsChanged());
        layerComments.addActionListener(e->settingsChanged());layerViewer.addActionListener(e->settingsChanged());
        javax.swing.event.DocumentListener changes=new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e){settingsChanged();}
            public void removeUpdate(javax.swing.event.DocumentEvent e){settingsChanged();}
            public void changedUpdate(javax.swing.event.DocumentEvent e){settingsChanged();}
        };
        author.getDocument().addDocumentListener(changes);
        ((JTextField)maximumLines.getEditor().getEditorComponent()).getDocument().addDocumentListener(changes);
        ((JTextField)maximumShapes.getEditor().getEditorComponent()).getDocument().addDocumentListener(changes);
        darkMode.addActionListener(e->applyTheme());applyTheme();
        ImageDropHandler dropHandler=new ImageDropHandler(this);
        for(JComponent target:List.of(original,result,styleTarget,selectedImageLabel,(JComponent)getContentPane(),tabs))target.setTransferHandler(dropHandler);
        original.setToolTipText("Drop one PNG, JPEG, WEBP, BMP, or TIFF image here.");
        setSize(1300,950);setMinimumSize(new Dimension(1100,760));setLocationRelativeTo(null);
    }

    void error(Throwable ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Conversion",JOptionPane.ERROR_MESSAGE);}
    void choose() {
        int limit,shapeLimit;
        try {limit=parseLimit(maximumLines.getEditor().getItem().toString());shapeLimit=parseShapeLimit(maximumShapes.getEditor().getItem().toString());}catch(Exception ex){error(ex);return;}
        JFileChooser photo=new JFileChooser();photo.setDialogTitle("Choose the image to reproduce");
        photo.setFileFilter(new FileNameExtensionFilter("Images (PNG, JPG, JPEG, WEBP, BMP, TIFF)","png","jpg","jpeg","webp","bmp","tif","tiff"));
        if(photo.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        loadImage(photo.getSelectedFile().toPath());
    }

    boolean loadImage(Path image) {
        if(busy)return false;
        try {ImageDropHandler.validate(List.of(image.toFile()));}catch(Exception ex){error(ex);return false;}
        Path destination=chooseDestination();
        if(destination==null)return false;
        selectImage(image,destination);regenerate();return true;
    }

    Path chooseDestination() {
        JFileChooser output=new JFileChooser(javax.swing.filechooser.FileSystemView.getFileSystemView().getDefaultDirectory());output.setDialogTitle("Choose Java name and destination for preview (not saved yet)");output.setSelectedFile(output.getCurrentDirectory().toPath().resolve("Portrait.java").toFile());
        if(output.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return null;
        Path destination=output.getSelectedFile().toPath();
        if(!destination.toString().endsWith(".java"))destination=Path.of(destination+".java");
        return destination;
    }

    void selectImage(Path image,Path output) {
        if(busy)return;
        discardPreview();selectedImage=image.toAbsolutePath();selectedOutput=output.toAbsolutePath();
        selectedImageLabel.setText("Image: "+selectedImage.getFileName());selectedImageLabel.setToolTipText(selectedImage.toString());
        regenerate.setEnabled(true);discard.setEnabled(true);
    }

    static Path availableOutput(Path requested) {
        String filename=requested.getFileName().toString();
        String base=filename.substring(0,filename.length()-5);
        Path candidate=requested;int version=2;
        while(Files.exists(candidate)||Files.exists(AutomaticEngine.archivePath(candidate)))
            candidate=requested.resolveSibling(base+"_"+(version++)+".java");
        return candidate;
    }

    void settingsChanged() {
        if(selectedImage==null||busy)return;
        settingsDirty=true;save.setEnabled(false);
        status.setText("Settings changed. Click Regenerate to apply them to "+selectedImage.getFileName()+". Current preview is unchanged.");
    }

    void working(boolean value) {
        busy=value;convert.setEnabled(!value);regenerate.setEnabled(!value&&selectedImage!=null);
        for(Component c:List.of(quality,maximumLines,maximumShapes,layerComments,layerViewer,artStyle,recolor,author))c.setEnabled(!value);
        save.setEnabled(!value&&draft!=null&&!settingsDirty);discard.setEnabled(!value&&(selectedImage!=null||draft!=null));
        folder.setEnabled(!value&&lastSaved!=null);stop.setEnabled(value);progress.setIndeterminate(value);
    }

    void regenerate() {
        if(busy||selectedImage==null)return;
        int limit,shapeLimit;
        try {limit=parseLimit(maximumLines.getEditor().getItem().toString());shapeLimit=parseShapeLimit(maximumShapes.getEditor().getItem().toString());}catch(Exception ex){error(ex);return;}
        Path photo=selectedImage,selected=availableOutput(selectedOutput);
        int q=quality.getSelectedIndex();String attribution=author.getText();boolean comments=layerComments.isSelected(),viewer=layerViewer.isSelected();
        String style=artStyle.getSelectedItem().toString().toLowerCase(java.util.Locale.ROOT),filter=recolor.getSelectedItem().toString().toLowerCase(java.util.Locale.ROOT);
        cancelled.set(false);working(true);
        status.setText("Regenerating "+photo.getFileName()+" with current settings...");
        new SwingWorker<AutomaticEngine.Draft,String>() {
            protected AutomaticEngine.Draft doInBackground()throws Exception {
                return AutomaticEngine.prepare(photo,selected,q,limit,shapeLimit,comments,viewer,style,filter,attribution,text->publish(text),cancelled);
            }
            protected void process(List<String> updates){status.setText(updates.get(updates.size()-1));}
            protected void done(){
                try{showPreview(get());
                }catch(Exception ex){Throwable cause=ex.getCause()==null?ex:ex.getCause();status.setText(cause.getMessage());if(!(cause instanceof java.util.concurrent.CancellationException))error(cause);}
                finally{working(false);}
            }
        }.execute();
    }

    void showPreview(AutomaticEngine.Draft prepared) {
        settingsDirty=false;draft=prepared;styleTarget.image=draft.styleTarget();styleTarget.repaint();latest=draft.result();code.setText(draft.source());code.setCaretPosition(0);tabs.setSelectedIndex(0);save.setEnabled(true);discard.setEnabled(true);updateCounts();original.image=latest.reference();result.image=latest.portrait();original.repaint();result.repaint();report.setText(latest.report());report.setCaretPosition(0);
                    status.setText("Preview ready: "+CourseExporter.count(draft.source())+" lines | "+latest.techniques()+"/10 techniques | Review portrait and code, then Save Java + assets ZIP. Nothing exported yet.");
    }

    void discardPreview() {
        if(busy)return;
        selectedImage=null;selectedOutput=null;lastSaved=null;settingsDirty=false;
        regenerate.setEnabled(false);selectedImageLabel.setText("No image selected");selectedImageLabel.setToolTipText(null);
        draft=null;latest=null;code.setText("");original.image=null;result.image=null;styleTarget.image=null;styleTarget.repaint();
        original.repaint();result.repaint();report.setText("Choose an image to prepare a new portrait and Java-code preview.\nNothing is exported until you choose Save Java + assets ZIP.");
        save.setEnabled(false);discard.setEnabled(false);folder.setEnabled(false);
        tabs.setSelectedIndex(0);updateCounts();status.setText("Ready for a new preview. Nothing exported.");
    }

    void savePreview() {
        if(draft==null||busy||settingsDirty)return;
        AutomaticEngine.Draft selected=draft;
        cancelled.set(false);working(true);
        status.setText("Saving Java and companion ZIP...");progress.setIndeterminate(true);
        new SwingWorker<AutomaticEngine.Result,Void>() {
            protected AutomaticEngine.Result doInBackground()throws Exception {return AutomaticEngine.save(selected,cancelled);}
            protected void done() {
                try{latest=get();lastSaved=latest.javaFile();draft=null;folder.setEnabled(true);status.setText("Saved "+latest.javaFile().getFileName()+" + "+AutomaticEngine.archivePath(latest.javaFile()).getFileName());}
                catch(Exception ex){Throwable cause=ex.getCause()==null?ex:ex.getCause();error(cause);status.setText("Save failed. Preview retained; choose a new destination or retry.");}
                finally{working(false);}
            }
        }.execute();
    }

    void applyTheme() {
        StudioTheme.apply(this,darkMode.isSelected());
    }

    static class Picture extends JPanel {
        BufferedImage image;final String title;
        Picture(String title){this.title=title;setBackground(new Color(233,236,240));}
        protected void paintComponent(Graphics graphics){super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();g.setColor(getForeground());g.drawString(title,12,22);
            if(image!=null){double scale=Math.min((getWidth()-24.0)/image.getWidth(),(getHeight()-45.0)/image.getHeight());int w=(int)(image.getWidth()*scale),h=(int)(image.getHeight()*scale);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.drawImage(image,(getWidth()-w)/2,35+(getHeight()-45-h)/2,w,h,null);}
            else g.drawString("Drop an image here, or use Choose image and preview",20,70);g.dispose();}
    }

    public static void main(String[] args)throws Exception {
        if(args.length>0&&args[0].equals("--generate")){
            if(args.length<3||args.length>10)throw new IllegalArgumentException("Usage: --generate input-image output.java [quality: 0|1|2] [maximum-lines: 300..100000, 0=unlimited] [maximum-shapes: 1..100000, 0=unlimited] [layer-comments: true|false] [layer-viewer: true|false] [art-style] [recolor]");
            var prepared=AutomaticEngine.prepare(Path.of(args[1]),Path.of(args[2]),args.length>=4?Integer.parseInt(args[3]):1,args.length>=5?parseLimit(args[4]):5000,args.length>=6?parseShapeLimit(args[5]):0,args.length>=7?parseFlag(args[6]):false,args.length>=8?parseFlag(args[7]):false,args.length>=9?args[8].toLowerCase(java.util.Locale.ROOT):"original",args.length>=10?args[9].toLowerCase(java.util.Locale.ROOT):"none","",System.out::println,new AtomicBoolean());
            var result=AutomaticEngine.save(prepared,new AtomicBoolean());
            System.out.println(result.report());return;
        }
        if(args.length>0&&args[0].equals("--editor")){PortraitStudio.main(new String[0]);return;}
        UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        SwingUtilities.invokeLater(()->new AutomaticStudio().setVisible(true));
    }
    void updateCounts() {
        if(latest==null){counts.setText("No conversion yet.");return;}
        String shapes=latest.shapes()+" total shapes";
        String families=latest.techniques()+" / 10 visible technique families";
        counts.setText(switch(countMode.getSelectedIndex()){case 1->shapes;case 2->families;default->shapes+" | "+families;});
    }

    static boolean parseFlag(String text) {
        if(text.equalsIgnoreCase("true"))return true;
        if(text.equalsIgnoreCase("false"))return false;
        throw new IllegalArgumentException("Layer options must be true or false.");
    }
    static int parseShapeLimit(String text) {
        if(text.trim().equalsIgnoreCase("No limit")||text.trim().equals("0"))return 0;
        try{int n=Integer.parseInt(text.trim());if(n>=1&&n<=100000)return n;}catch(NumberFormatException ignored){}
        throw new IllegalArgumentException("Enter 1 to 100000 shapes, or select No limit.");
    }
    static int parseLimit(String text) {
        if(text.trim().equalsIgnoreCase("No limit")||text.trim().equals("0"))return 0;
        try{int n=Integer.parseInt(text.trim());if(n>=300&&n<=100000)return n;}catch(NumberFormatException ignored){}
        throw new IllegalArgumentException("Enter 300 to 100000 lines, or select No limit.");
    }
}

