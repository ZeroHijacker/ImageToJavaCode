import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.tools.ToolProvider;
import java.util.zip.*;

/** Automatic image analysis, vector fitting, coverage repair, and verified export. */
final class AutomaticEngine {
    record Result(Path javaFile, BufferedImage reference, BufferedImage portrait,
                  int techniques, int shapes, double rmse, String report) {}

    record Draft(Result result,String source,byte[] attachments,BufferedImage styleTarget) {}

    static Path archivePath(Path javaFile) {
        return javaFile.resolveSibling(javaFile.getFileName().toString().replaceFirst("\\.java$", "_assets.zip"));
    }

    static Result save(Draft draft,AtomicBoolean stop)throws IOException {
        Path javaFile=draft.result().javaFile(),archive=archivePath(javaFile);
        check(stop);
        for(Path path:List.of(javaFile,archive))if(Files.exists(path))throw new IOException("Output already exists: "+path+". Choose another filename.");
        Files.createDirectories(javaFile.getParent());
        List<Path> created=new ArrayList<>();
        try {
            // Reserve each name without overwriting; roll back only our own files.
            Files.createFile(javaFile);created.add(javaFile);
            Files.writeString(javaFile,draft.source(),StandardCharsets.UTF_8);
            check(stop);
            Files.createFile(archive);created.add(archive);Files.write(archive,draft.attachments());
            check(stop);
        }catch(IOException|RuntimeException ex){for(Path path:created)Files.deleteIfExists(path);throw ex;}
        return draft.result();
    }

    static void check(AtomicBoolean stop) {
        if(stop.get())throw new CancellationException("Conversion cancelled. No output was committed.");
    }

    static void stopProcess(Process process)throws InterruptedException {
        var children=process.descendants().toList();
        for(var child:children)if(child.isAlive())child.destroyForcibly();
        if(process.isAlive())process.destroyForcibly();
        process.waitFor(5,java.util.concurrent.TimeUnit.SECONDS);
        for(var child:children) {
            try{child.onExit().get(5,java.util.concurrent.TimeUnit.SECONDS);}
            catch(java.util.concurrent.ExecutionException|java.util.concurrent.TimeoutException ignored){}
        }
    }

    static void deleteTemporary(Path directory)throws IOException,InterruptedException {
        // Windows can briefly retain the subprocess log handle after process exit.
        for(int attempt=0;;attempt++) {
            try(var paths=Files.walk(directory)) {
                for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);
                return;
            }catch(IOException ex){if(attempt==19)throw ex;Thread.sleep(100);}
        }
    }

    static Path home() throws Exception {
        Path location=Path.of(AutomaticEngine.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return Files.isDirectory(location)?location:location.getParent();
    }

    static String python() throws Exception {
        String override=System.getenv("PORTRAIT_PYTHON");
        if(override!=null&&!override.isBlank())return override;
        Path bundled=home().resolve("python/python.exe");
        if(Files.isRegularFile(bundled))return bundled.toString();
        Path local=home().resolve(".venv/Scripts/python.exe");
        if(Files.isRegularFile(local))return local.toString();
        local=home().resolve(".venv/bin/python");
        return Files.isRegularFile(local)?local.toString():"python";
    }

    static Result convert(Path photo,Path output,int quality,String author,
                          Consumer<String> progress,AtomicBoolean stop)throws Exception {
        return convert(photo,output,quality,0,author,progress,stop);
    }

    static Result convert(Path photo,Path output,int quality,int maximumLines,String author,
                          Consumer<String> progress,AtomicBoolean stop)throws Exception {
        return convert(photo,output,quality,maximumLines,0,author,progress,stop);
    }

    static Result convert(Path photo,Path output,int quality,int maximumLines,int maximumShapes,String author,
                          Consumer<String> progress,AtomicBoolean stop)throws Exception {
        return convert(photo,output,quality,maximumLines,maximumShapes,false,false,author,progress,stop);
    }

    static Result convert(Path photo,Path output,int quality,int maximumLines,int maximumShapes,
                          boolean layerComments,boolean layerViewer,String author,
                          Consumer<String> progress,AtomicBoolean stop)throws Exception {
        return save(prepare(photo,output,quality,maximumLines,maximumShapes,layerComments,layerViewer,author,progress,stop),stop);
    }

    static Draft prepare(Path photo,Path output,int quality,int maximumLines,int maximumShapes,
                         boolean layerComments,boolean layerViewer,String author,
                         Consumer<String> progress,AtomicBoolean stop)throws Exception {
        return prepare(photo,output,quality,maximumLines,maximumShapes,layerComments,layerViewer,"original","none",author,progress,stop);
    }

    static Draft prepare(Path photo,Path output,int quality,int maximumLines,int maximumShapes,
                         boolean layerComments,boolean layerViewer,String style,String recolor,String author,
                         Consumer<String> progress,AtomicBoolean stop)throws Exception {
        if(!List.of("original","realism","mosaic","abstract","cubism","minimalist","pixelated").contains(style)||
           !List.of("none","monochrome","vintage","sepia","cool","warm","posterized").contains(recolor))
            throw new IllegalArgumentException("Unknown art style or recoloring filter.");
        if(maximumShapes<0||maximumShapes>100000)throw new IllegalArgumentException("Maximum shapes must be 1 to 100000, or 0 for no limit.");
        if(quality<0||quality>2)throw new IllegalArgumentException("Quality must be 0, 1, or 2.");
        if(maximumLines!=0&&(maximumLines<300||maximumLines>100000))throw new IllegalArgumentException("Maximum lines must be 300 to 100000, or 0 for no limit.");
        check(stop);
        String filename=output.getFileName().toString();
        if(!filename.endsWith(".java")||!PortraitStudio.validClass(filename.substring(0,filename.length()-5)))
            throw new IllegalArgumentException("Choose a valid Java filename, for example Portrait.java.");
        output=output.toAbsolutePath();
        List<Path> destinations=List.of(output,archivePath(output));
        // Check the whole group before work begins; never silently replace a submission.
        for(Path path:destinations)if(Files.exists(path))throw new IOException("Output already exists: "+path+". Choose another filename.");
        if(ToolProvider.getSystemJavaCompiler()==null)throw new IOException("A full JDK 17 or newer is needed to compile-check the output.");
        Path temporary=Files.createTempDirectory("portrait-auto-");
        try {
            progress.accept("1/6 Reading image, correcting orientation, and extracting contours...");
            Path script=temporary.resolve("AutoAnalysis.py");
            try(InputStream in=AutomaticEngine.class.getResourceAsStream("/AutoAnalysis.py")) {
                if(in==null)throw new IOException("AutoAnalysis.py is missing from the application. Rebuild the JAR.");
                Files.copy(in,script);
            }
            ProcessBuilder builder=new ProcessBuilder(python(),script.toString(),photo.toAbsolutePath().toString(),temporary.toString(),"--quality",Integer.toString(quality),"--style",style,"--recolor",recolor);
            builder.environment().put("PYTHONUTF8","1");
            builder.environment().remove("PYTHONHOME");
            Path log=temporary.resolve("analysis.log");builder.redirectErrorStream(true).redirectOutput(log.toFile());
            Process process;
            try {process=builder.start();}catch(IOException ex){throw new IOException("Python could not be started. Run Setup automatic converter.bat once, then retry.",ex);}
            try {
                while(!process.waitFor(200,java.util.concurrent.TimeUnit.MILLISECONDS))check(stop);
            } finally {if(process.isAlive())stopProcess(process);}
            if(process.exitValue()!=0)throw new IOException("Image analysis failed. Run Setup automatic converter.bat if dependencies are missing.\n"+Files.readString(log));
            check(stop);
            BufferedImage reference=ImageIO.read(temporary.resolve("reference.png").toFile());
            BufferedImage originalImage=ImageIO.read(temporary.resolve("original.png").toFile());
            progress.accept("2/6 Building adaptive color regions...");
            List<PortraitRuntime.Mark> marks=new ArrayList<>();
            int tile=new int[]{8,5,3}[quality];
            if(maximumLines==0&&maximumShapes==0)base(reference,marks,0,0,reference.getWidth(),reference.getHeight(),tile,stop);
            BufferedImage current=PortraitRuntime.render(reference.getWidth(),reference.getHeight(),marks,-1);
            List<PortraitRuntime.Mark> proposals=new ArrayList<>();
            for(String row:Files.readAllLines(temporary.resolve("proposals.tsv"),StandardCharsets.UTF_8)) {
                if(row.isBlank())continue;
                String[] parts=row.split("\t",2);
                proposals.add(PortraitRuntime.Mark.parse(parts[0],parts.length==2?parts[1]:"Extracted image feature"));
            }
            int[] extracted=new int[10];
            if(maximumLines==0&&maximumShapes==0) {
            progress.accept("3/6 Fitting image-derived paths, curves, arcs, and shapes...");
            for(int i=0;i<proposals.size();i++) {
                check(stop);var mark=proposals.get(i);
                if(PortraitStudio.improve(mark,reference,current)){marks.add(mark);extracted[mark.type]++;}
                if(i%1500==0)progress.accept("3/6 Evaluating image contours: "+i+" / "+proposals.size());
            }
            List<Rectangle> faces=new ArrayList<>();
            for(String row:Files.readAllLines(temporary.resolve("faces.tsv")))if(!row.isBlank()){
                String[] p=row.split(",");faces.add(new Rectangle(Integer.parseInt(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2]),Integer.parseInt(p[3])));
            }
            progress.accept("4/6 Refining likeness with all ten Java 2D techniques...");
            Random random=new Random(26115);
            int tries=new int[]{16000,45000,85000}[quality];
            for(int step=0;step<tries;step++) {
                check(stop);
                int[] point=errorPoint(reference,current,random,faces);
                double size=1.3+random.nextDouble()*tile*(2.0-1.35*step/(double)tries);
                var mark=PortraitStudio.candidate(step%10,point[0],point[1],size,random);
                mark.label="Automatic image-detail reconstruction";
                if(PortraitStudio.improve(mark,reference,current))marks.add(mark);
                if(step%5000==0)progress.accept("4/6 Refining image details: "+step+" / "+tries);
            }
            progress.accept("5/6 Checking visible techniques and repairing missing coverage...");
            int[] visible=visiblePixels(reference,marks,stop);
            // Repair is still image fitting. No invisible stamps or unrelated decorations.
            for(int t=0;t<10;t++)if(visible[t]<8) {
                for(var original:proposals)if(original.type==t) {
                    check(stop);
                    var m=PortraitRuntime.Mark.parse(original.row(),original.label);
                    if(PortraitStudio.improve(m,reference,current))marks.add(m);
                }
                for(int i=0;i<2500;i++) {
                    check(stop);int[] point=errorPoint(reference,current,random,faces);
                    var m=PortraitStudio.candidate(t,point[0],point[1],1.5+random.nextDouble()*tile*2,random);
                    m.label="Automatic technique-coverage refinement";
                    if(PortraitStudio.improve(m,reference,current))marks.add(m);
                }
            }
            } else {
                progress.accept("Fitting a complete portrait within the selected limits...");
                marks=BudgetFitter.fit(reference,proposals,maximumLines==0?Integer.MAX_VALUE:maximumLines,maximumShapes,layerComments,layerViewer,quality,progress,stop);
                for(var m:marks)if(m.label.startsWith("Extracted"))extracted[m.type]++;
            }
            marks=marks.stream().map(CourseExporter::clean).toList();
            int[] visible=visiblePixels(reference,marks,stop);
            int coverage=0;for(int v:visible)if(v>=8)coverage++;
            double sum=0;int w=reference.getWidth(),h=reference.getHeight();
            BufferedImage portrait=PortraitRuntime.render(w,h,marks,-1);
            for(int y=0;y<h;y++)for(int x=0;x<w;x++)sum+=PortraitStudio.distance(reference.getRGB(x,y),portrait.getRGB(x,y));
            double rmse=Math.sqrt(sum/(w*(double)h*3));
            String report=report(marks,visible,extracted,coverage,rmse,author);
            check(stop);progress.accept("6/6 Exporting and compiling the standalone Java program...");
            Path staged=temporary.resolve(filename);
            int layerCount=LayerPlan.build(marks,w,h).count();
            String source=CourseExporter.source(filename.substring(0,filename.length()-5),w,h,marks,author,layerComments,layerViewer);
            int actualLines=CourseExporter.count(source);
            if(maximumShapes>0&&marks.size()>maximumShapes)throw new IOException("Shape limit exceeded; no output saved.");
            long usedFamilies=marks.stream().map(m->m.type).distinct().count();
            if(maximumLines>0&&actualLines>maximumLines)throw new IOException("Readable source requires "+actualLines+" lines, exceeding the selected limit of "+maximumLines+". No oversized output was saved.");
            Files.writeString(staged,source,StandardCharsets.UTF_8);
            List<Path> stagedFiles=new ArrayList<>(PortraitStudio.exportPaths(staged));
            ImageIO.write(portrait,"png",stagedFiles.get(1).toFile());
            ImageIO.write(originalImage,"png",stagedFiles.get(2).toFile());
            Path styleImage=temporary.resolve(filename.replaceFirst("\\.java$","_style.png"));
            ImageIO.write(reference,"png",styleImage.toFile());
            report="ART STYLE: "+style+"; recoloring: "+recolor+". Deterministic non-neural processing.\n"
                +"RMSE compares the Java rendering to the style target, not to the original photograph.\n"
                +"Style target is an approximation guide; line/shape budgets can reduce the final effect and likeness.\n"
                +"SOURCE STYLE: Readable source; explicit Java 2D constructors and named methods.\n"
                +"LAYERS: "+layerCount+" cumulative overlap-depth layers; comments="+layerComments+"; keyboard viewer="+layerViewer+".\n"
                +(layerViewer?"Viewer: H/F1 legend, arrows step, Home base, A/End all, type number then Enter.\n":"")
                +"TOTAL DRAWING SHAPES: "+marks.size()+" / "+(maximumShapes==0?"unlimited":maximumShapes)+".\n"
                +"DISTINCT TECHNIQUE FAMILIES USED: "+usedFamilies+" / 10; visible families: "+coverage+" / 10.\n"
                +"Shape count includes each colored drawing mark once; an Area with holes counts as one. Canvas setup is excluded.\n"
                +"SOURCE LINES: "+actualLines+" / "+(maximumLines==0?"unlimited":maximumLines)+" (including blank lines and comments).\n"
                +"A smaller source budget simplifies the picture; it does not hide geometry in encoded data.\n\n"+report;
            Files.writeString(stagedFiles.get(3),report,StandardCharsets.UTF_8);
            ByteArrayOutputStream errors=new ByteArrayOutputStream();
            int exit=ToolProvider.getSystemJavaCompiler().run(null,errors,errors,"--release","17","-encoding","UTF-8","-classpath",temporary.toString(),"-d",temporary.toString(),staged.toString());
            if(exit!=0)throw new IOException("Generated Java did not compile:\n"+errors.toString(StandardCharsets.UTF_8));
            check(stop);
            // Actually execute the compiled source, rather than trusting compilation alone.
            String javaExecutable=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
            Path verified=temporary.resolve("verified.png");
            Process verify=new ProcessBuilder(javaExecutable,"-Djava.awt.headless=true","-cp",temporary.toString(),filename.substring(0,filename.length()-5),"--png",verified.toString()).redirectErrorStream(true).redirectOutput(temporary.resolve("verify.log").toFile()).start();
            try {while(!verify.waitFor(200,java.util.concurrent.TimeUnit.MILLISECONDS))check(stop);}finally{if(verify.isAlive())stopProcess(verify);}
            if(verify.exitValue()!=0)throw new IOException("Generated Java execution failed:\n"+Files.readString(temporary.resolve("verify.log")));
            BufferedImage actual=ImageIO.read(verified.toFile());
            if(actual==null||actual.getWidth()!=w||actual.getHeight()!=h)throw new IOException("Generated render dimensions differ.");
            for(int y=0;y<h;y++)for(int x=0;x<w;x++)if(portrait.getRGB(x,y)!=actual.getRGB(x,y))throw new IOException("Generated render does not match preview.");
            report+="\nVERIFICATION: javac --release 17 succeeded; compiled Java executed; PNG matches preview pixel-for-pixel.\n";
            Files.writeString(stagedFiles.get(3),report,StandardCharsets.UTF_8);
            stagedFiles.add(styleImage);
            stagedFiles.add(temporary.resolve("analysis.json"));
            check(stop);
            ByteArrayOutputStream packed=new ByteArrayOutputStream();
            try(ZipOutputStream zip=new ZipOutputStream(packed,StandardCharsets.UTF_8)) {
                for(int i=1;i<stagedFiles.size();i++) {
                    Path file=stagedFiles.get(i);
                    String entry=i==stagedFiles.size()-1?filename.replaceFirst("\\.java$","_analysis.json"):file.getFileName().toString();
                    zip.putNextEntry(new ZipEntry(entry));Files.copy(file,zip);zip.closeEntry();
                }
            }
            check(stop);
            return new Draft(new Result(output,originalImage,portrait,coverage,marks.size(),rmse,report),source,packed.toByteArray(),reference);
        } finally {
            // Only this conversion's newly created private temporary directory is removed.
            deleteTemporary(temporary);
        }
    }

    /** Adaptive subdivision preserves small facial detail and merges flat regions. */
    static void base(BufferedImage image,List<PortraitRuntime.Mark> marks,int x,int y,int w,int h,int min,AtomicBoolean stop) {
        check(stop);long[] sum=new long[3],squared=new long[3];
        for(int yy=y;yy<y+h;yy++)for(int xx=x;xx<x+w;xx++) {
            int rgb=image.getRGB(xx,yy);
            for(int c=0;c<3;c++){int v=(rgb>>(16-c*8))&255;sum[c]+=v;squared[c]+=(long)v*v;}
        }
        double count=w*(double)h,variance=0;int rgb=0;
        for(int c=0;c<3;c++){double mean=sum[c]/count;variance+=squared[c]/count-mean*mean;rgb=(rgb<<8)|(int)Math.round(mean);}
        if(variance>24 && (w>min || h>min)) {
            if(w>min && h>min) {
                int a=w/2,b=h/2;
                base(image,marks,x,y,a,b,min,stop);base(image,marks,x+a,y,w-a,b,min,stop);
                base(image,marks,x,y+b,a,h-b,min,stop);base(image,marks,x+a,y+b,w-a,h-b,min,stop);
            } else if(w>min) {
                int a=w/2;base(image,marks,x,y,a,h,min,stop);base(image,marks,x+a,y,w-a,h,min,stop);
            } else {
                int b=h/2;base(image,marks,x,y,w,b,min,stop);base(image,marks,x,y+b,w,h-b,min,stop);
            }
        } else marks.add(new PortraitRuntime.Mark(w==h?7:6,rgb,1,true,0,"Automatic adaptive color region",x,y,w,h));
    }

    static int[] errorPoint(BufferedImage ref,BufferedImage current,Random random,List<Rectangle> faces) {
        int x=0,y=0;double maximum=-1;
        for(int k=0;k<7;k++) {
            int xx,yy;
            if(!faces.isEmpty()&&k<3){Rectangle f=faces.get(random.nextInt(faces.size()));xx=f.x+random.nextInt(f.width);yy=f.y+random.nextInt(f.height);}
            else {xx=random.nextInt(ref.getWidth());yy=random.nextInt(ref.getHeight());}
            xx=Math.max(0,Math.min(ref.getWidth()-1,xx));yy=Math.max(0,Math.min(ref.getHeight()-1,yy));
            double error=PortraitStudio.distance(ref.getRGB(xx,yy),current.getRGB(xx,yy));
            if(error>maximum){maximum=error;x=xx;y=yy;}
        }
        return new int[]{x,y};
    }

    static int[] visiblePixels(BufferedImage reference,List<PortraitRuntime.Mark> marks,AtomicBoolean stop) {
        int w=reference.getWidth(),h=reference.getHeight();
        BufferedImage full=PortraitRuntime.render(w,h,marks,-1);int[] counts=new int[10];
        for(int t=0;t<10;t++) {
            check(stop);BufferedImage omitted=PortraitRuntime.render(w,h,marks,t);
            for(int y=0;y<h;y++)for(int x=0;x<w;x++)if(PortraitStudio.distance(full.getRGB(x,y),omitted.getRGB(x,y))>9)counts[t]++;
        }
        return counts;
    }

    static String report(List<PortraitRuntime.Mark> marks,int[] visible,int[] extracted,int coverage,double rmse,String author) {
        StringBuilder b=new StringBuilder("AUTOMATIC PHOTO-TO-JAVA REPORT\nAuthor (optional): "+author+"\n\n");
        b.append("Conversion requires no tracing, landmarks, or manual shape placement.\n");
        b.append("The output contains vector geometry and sampled colors, with no embedded photograph.\n\n");
        b.append("Visible technique families: "+coverage+" / 10 (at least 8 affected pixels per family).\n");
        b.append(coverage>=9?"9-10 technique visibility target achieved.\n":"9-10 technique target NOT achieved for this image; output is provided without claiming compliance.\n");
        b.append("\nTechnique                 Total shapes  Image-contour fits  Affected pixels\n");
        for(int t=0;t<10;t++){int n=0;for(var m:marks)if(m.type==t)n++;b.append(String.format(Locale.ROOT,"%-24s %8d %18d %16d%n",PortraitRuntime.NAMES[t],n,extracted[t],visible[t]));}
        b.append(String.format(Locale.ROOT,"\nRGB reconstruction RMSE: %.3f / 255. Lower is closer; this is not a grade.%n",rmse));
        b.append("\nHOW THE PICTURE IS CONSTRUCTED\n");
        b.append("Rectangles/squares reconstruct tonal regions. General paths follow color-region boundaries.\n");
        b.append("Area subtracts detected holes or fits crescent shading. Lines, quadratic/cubic curves, and arcs\n");
        b.append("fit connected image edges. Ellipses/circles are proposed from region contours. All ten families\n");
        b.append("also receive error-driven detail fitting, with colors optimized against actual Java 2D coverage.\n");
        b.append("Only proposals improving image error are accepted. Later layers may cover earlier contour fits.\n");
        b.append("The visibility audit removes each family from the final painting and measures changed pixels.\n");
        b.append("No tiny off-canvas technique stamps or unrelated decorations are added.\n\n");
        b.append("Technique visibility does not guarantee likeness or aesthetic quality.\n");
        b.append("Keep the original photo, Java source and companion assets ZIP together.\n");
        return b.toString();
    }
}
