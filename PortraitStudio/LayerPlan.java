import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Groups shapes by actual painted overlap, including antialiased edge pixels. */
final class LayerPlan {
    record Entry(PortraitRuntime.Mark mark,int depth) {}
    record Plan(List<Entry> entries,int count) {}

    static Plan build(List<PortraitRuntime.Mark> marks,int width,int height) {
        int[] depths=new int[Math.multiplyExact(width,height)];
        List<Entry> entries=new ArrayList<>();
        int highest=0;
        for(var mark:marks) {
            Rectangle bounds=PortraitRuntime.ink(mark).getBounds();
            bounds.grow(2,2);
            bounds=bounds.intersection(new Rectangle(0,0,width,height));
            int depth=1;
            if(!bounds.isEmpty()) {
                BufferedImage mask=new BufferedImage(bounds.width,bounds.height,BufferedImage.TYPE_INT_ARGB);
                Graphics2D g=mask.createGraphics();g.translate(-bounds.x,-bounds.y);
                PortraitRuntime.paint(g,List.of(mark),-1);g.dispose();
                for(int y=0;y<bounds.height;y++)for(int x=0;x<bounds.width;x++) {
                    if((mask.getRGB(x,y)>>>24)!=0)
                        depth=Math.max(depth,depths[(bounds.y+y)*width+bounds.x+x]+1);
                }
                for(int y=0;y<bounds.height;y++)for(int x=0;x<bounds.width;x++) {
                    if((mask.getRGB(x,y)>>>24)!=0)
                        depths[(bounds.y+y)*width+bounds.x+x]=depth;
                }
            }
            entries.add(new Entry(mark,depth));highest=Math.max(highest,depth);
        }
        // Stable sorting retains the original order within each depth. Shapes at
        // the same depth share no painted pixels, so this preserves composition.
        entries.sort(Comparator.comparingInt(Entry::depth));
        return new Plan(List.copyOf(entries),highest);
    }
}
