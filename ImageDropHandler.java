import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.nio.file.*;
import java.util.List;
import java.util.Locale;
import javax.swing.*;

/** Accepts one local image without changing the original file. */
final class ImageDropHandler extends TransferHandler {
    private final AutomaticStudio owner;
    ImageDropHandler(AutomaticStudio owner){this.owner=owner;}

    static Path validate(List<?> files) {
        if(files.size()!=1||!(files.get(0) instanceof File file))
            throw new IllegalArgumentException("Drop one image at a time.");
        Path path=file.toPath().toAbsolutePath();
        if(!Files.isRegularFile(path)||!Files.isReadable(path))
            throw new IllegalArgumentException("Choose a readable image file, not a folder.");
        String name=path.getFileName().toString().toLowerCase(Locale.ROOT);
        if(!name.matches(".+\\.(png|jpe?g|webp|bmp|tiff?)$"))
            throw new IllegalArgumentException("Supported formats: PNG, JPEG, WEBP, BMP, and TIFF.");
        return path;
    }

    public boolean canImport(TransferSupport support) {
        if(owner.busy||!support.isDataFlavorSupported(DataFlavor.javaFileListFlavor))return false;
        if(support.isDrop()) {
            if((support.getSourceDropActions()&COPY)==0)return false;
            support.setDropAction(COPY);
        }
        return true;
    }

    public boolean importData(TransferSupport support) {
        if(!canImport(support))return false;
        try {
            Object data=support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
            if(!(data instanceof List<?> files))return false;
            return owner.loadImage(validate(files));
        }catch(Exception ex){owner.error(ex);return false;}
    }
}
