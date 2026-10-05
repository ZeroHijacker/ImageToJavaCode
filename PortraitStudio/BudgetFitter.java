import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Fits a complete simplified picture to a source-line budget, not a truncated data dump. */
final class BudgetFitter {
    record Choice(PortraitRuntime.Mark mark,double gain) {}

    static List<PortraitRuntime.Mark> fit(BufferedImage original,List<PortraitRuntime.Mark> proposals,
                                         int maximum,int maximumShapes,boolean comments,boolean viewer,int quality,Consumer<String> progress,AtomicBoolean stop) {
        int longSide=new int[]{144,184,224}[quality];
        double scale=Math.min(1,longSide/(double)Math.max(original.getWidth(),original.getHeight()));
        int w=Math.max(1,(int)Math.round(original.getWidth()*scale));
        int h=Math.max(1,(int)Math.round(original.getHeight()*scale));
        // Uniform coordinates avoid introducing aspect-ratio changes in emitted geometry.
        BufferedImage target=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=target.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(original,0,0,w,h,null);g.dispose();
        List<PortraitRuntime.Mark> pool=new ArrayList<>();
        for(var m:proposals){var small=resize(m,scale,true);if(CourseExporter.cost(small)<maximum/5)pool.add(small);}
        Random random=new Random(26115);
        List<PortraitRuntime.Mark> result=new ArrayList<>();
        long[] colors=new long[3];for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int rgb=target.getRGB(x,y);for(int c=0;c<3;c++)colors[c]+=(rgb>>(16-c*8))&255;
        }
        int rgb=0;for(long c:colors)rgb=(rgb<<8)|(int)(c/(w*(long)h));
        result.add(new PortraitRuntime.Mark(6,rgb,1,true,0,"Automatic base color",0,0,w,h));
        BufferedImage current=PortraitRuntime.render(w,h,result,-1);
        int extra=(comments?1:0)+(viewer?1:0);
        int used=120+(viewer?110:0)+extra+CourseExporter.cost(result.get(0));int[] typeCounts=new int[10];typeCounts[6]=1;
        int misses=0;
        while(used<maximum-12 && misses<20 && (maximumShapes==0||result.size()<maximumShapes)) {
            AutomaticEngine.check(stop);int iteration=result.size();
            // At regular intervals explicitly search a missing/underrepresented technique.
            int requested=-1;
            if(iteration%3==0||maximum-used<220){
                requested=0;for(int t=1;t<10;t++)if(typeCounts[t]<typeCounts[requested])requested=t;
                if(typeCounts[requested]>=2)requested=-1;
            }
            Choice best=null;double bestScore=0;
            int tests=new int[]{220,350,500}[quality];
            for(int test=0;test<tests;test++) {
                PortraitRuntime.Mark candidate;
                if(!pool.isEmpty()&&test<tests*2/3) {
                    var picked=pool.get(random.nextInt(pool.size()));
                    if(requested>=0&&picked.type!=requested)continue;
                    candidate=copy(picked);
                } else {
                    int type=requested>=0?requested:random.nextInt(10);
                    int[] point=AutomaticEngine.errorPoint(target,current,random,List.of());
                    double extent=Math.max(3,Math.max(w,h)*(.28*Math.exp(-iteration/60.0)+.025));
                    candidate=PortraitStudio.candidate(type,point[0],point[1],extent*(.4+random.nextDouble()*1.2),random);
                    if(type<=2)candidate.stroke=Math.max(.7,extent*.1);
                    // Avoid paying for rotation when axis-aligned regions fit naturally.
                    if(type==6||type==7)candidate.angle=0;
                }
                int cost=CourseExporter.cost(candidate)+extra;
                if(used+cost>maximum)continue;
                Choice choice=evaluate(candidate,target,current);
                double score=choice.gain()/Math.pow(cost,.7);
                if(score>bestScore){best=choice;bestScore=score;}
            }
            if(best==null||best.gain()<8){misses++;continue;}
            // Local hill climbing changes placement/scale while retaining the chosen family.
            for(int j=0;j<18;j++) {
                var mutated=mutate(best.mark(),random);
                if(used+CourseExporter.cost(mutated)+extra>maximum)continue;
                Choice choice=evaluate(mutated,target,current);
                if(choice.gain()>best.gain())best=choice;
            }
            var accepted=best.mark();result.add(accepted);used+=CourseExporter.cost(accepted)+extra;typeCounts[accepted.type]++;
            Graphics2D painter=current.createGraphics();PortraitRuntime.paint(painter,List.of(accepted),-1);painter.dispose();
            misses=0;
            if(result.size()%20==0)progress.accept("Fitting readable source: "+result.size()+" shapes; shape limit "+(maximumShapes==0?"none":maximumShapes)+"; line limit "+(maximum==Integer.MAX_VALUE?"none":maximum));
        }
        List<PortraitRuntime.Mark> enlarged=new ArrayList<>();
        for(var m:result){var large=resize(m,1/scale,false);large=CourseExporter.clean(large);enlarged.add(large);}
        // Exact final-image coverage is checked by the caller. No fake technique filler.
        return enlarged;
    }

    static Choice evaluate(PortraitRuntime.Mark mark,BufferedImage target,BufferedImage current) {
        Rectangle bounds=PortraitRuntime.ink(mark).getBounds();bounds.grow(1,1);
        bounds=bounds.intersection(new Rectangle(0,0,target.getWidth(),target.getHeight()));
        if(bounds.isEmpty())return new Choice(mark,0);
        BufferedImage mask=new BufferedImage(bounds.width,bounds.height,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=mask.createGraphics();g.translate(-bounds.x,-bounds.y);mark.rgb=0xffffff;PortraitRuntime.paint(g,List.of(mark),-1);g.dispose();
        double[] sum=new double[3];double weight=0;
        for(int y=0;y<bounds.height;y++)for(int x=0;x<bounds.width;x++) {
            double a=(mask.getRGB(x,y)>>>24)/255.0;if(a==0)continue;
            int t=target.getRGB(bounds.x+x,bounds.y+y),c=current.getRGB(bounds.x+x,bounds.y+y);
            for(int k=0;k<3;k++){int shift=16-8*k;sum[k]+=a*(((t>>shift)&255)-(1-a)*((c>>shift)&255));}weight+=a*a;
        }
        if(weight<1)return new Choice(mark,0);
        int rgb=0;for(double s:sum)rgb=(rgb<<8)|Math.max(0,Math.min(255,(int)Math.round(s/weight)));
        mark.rgb=rgb;double gain=0;
        for(int y=0;y<bounds.height;y++)for(int x=0;x<bounds.width;x++) {
            double a=(mask.getRGB(x,y)>>>24)/255.0;if(a==0)continue;
            int t=target.getRGB(bounds.x+x,bounds.y+y),c=current.getRGB(bounds.x+x,bounds.y+y);
            gain+=PortraitStudio.distance(t,c)-PortraitStudio.distance(t,PortraitStudio.blend(c,rgb,a));
        }
        return new Choice(mark,gain);
    }

    static PortraitRuntime.Mark copy(PortraitRuntime.Mark m){return new PortraitRuntime.Mark(m.type,m.rgb,m.stroke,m.fill,m.angle,m.label,m.p.clone());}

    static PortraitRuntime.Mark mutate(PortraitRuntime.Mark original,Random random) {
        var m=copy(original);Rectangle bounds=PortraitRuntime.geometry(m).getBounds();
        double dx=random.nextGaussian()*Math.max(1,bounds.width*.06),dy=random.nextGaussian()*Math.max(1,bounds.height*.06);
        double scale=.93+random.nextDouble()*.14;
        if(m.type==8){for(int i=0;i<m.p.length;i+=2){m.p[i]+=dx;m.p[i+1]+=dy;}}
        else if(m.type==9&&m.p[0]==-999){int pos=2;for(int ring=0;ring<(int)m.p[1];ring++){int n=(int)m.p[pos++];for(int i=0;i<n;i++){m.p[pos++]+=dx;m.p[pos++]+=dy;}}}
        else if(m.type<=2){for(int i=0;i<m.p.length;i+=2){m.p[i]+=dx;m.p[i+1]+=dy;}m.stroke=Math.max(.5,m.stroke*scale);}
        else{m.p[0]+=dx;m.p[1]+=dy;m.p[2]*=scale;m.p[3]*=scale;}
        return m;
    }

    static PortraitRuntime.Mark resize(PortraitRuntime.Mark original,double scale,boolean simplify) {
        var m=copy(original);m.stroke*=scale;
        if(m.type==8){
            int count=m.p.length/2,keep=simplify?Math.min(count,18):count;double[] p=new double[keep*2];
            for(int i=0;i<keep;i++){int j=i*count/keep;p[2*i]=m.p[2*j]*scale;p[2*i+1]=m.p[2*j+1]*scale;}m.p=p;
        } else if(m.type==9&&m.p[0]==-999){
            List<Double> data=new ArrayList<>();int rings=(int)m.p[1],kept=simplify?Math.min(rings,3):rings;
            data.add(-999.0);data.add((double)kept);int pos=2;
            for(int r=0;r<rings;r++){int count=(int)m.p[pos++];if(r<kept){int n=simplify?Math.min(count,r==0?18:8):count;data.add((double)n);
                for(int i=0;i<n;i++){int j=i*count/n;data.add(m.p[pos+2*j]*scale);data.add(m.p[pos+2*j+1]*scale);}}pos+=2*count;}
            m.p=data.stream().mapToDouble(Double::doubleValue).toArray();
        } else {for(int i=0;i<m.p.length;i++)if(m.type!=3||i<4)m.p[i]*=scale;}
        return m;
    }
}
