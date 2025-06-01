import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Minimal PDF writer that embeds each image as a page.
 * It avoids external dependencies like Apache PDFBox.
 */
public class SimplePDFWriter {

    private static class ImageInfo {
        final byte[] data;
        final int width;
        final int height;
        ImageInfo(byte[] d, int w, int h) { this.data = d; this.width = w; this.height = h; }
    }

    /**
     * Creates a PDF file from a list of image paths.
     */
    public static void createPdf(List<String> imagePaths, String outputPath) throws IOException {
        List<ImageInfo> images = new ArrayList<>();
        for (String path : imagePaths) {
            BufferedImage img = ImageIO.read(new File(path));
            if (img == null) continue;
            BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.drawImage(img, 0, 0, null);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.8f);
            writer.write(null, new IIOImage(rgb, null, null), param);
            writer.dispose();
            ios.close();
            images.add(new ImageInfo(baos.toByteArray(), rgb.getWidth(), rgb.getHeight()));
        }
        writePdf(images, outputPath);
    }

    private static void writePdf(List<ImageInfo> images, String outputPath) throws IOException {
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        List<Integer> xref = new ArrayList<>();
        pdf.write("%PDF-1.4\n".getBytes("ISO-8859-1"));

        int objNum = 1;
        xref.add(pdf.size());
        pdf.write((objNum + " 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n").getBytes("ISO-8859-1"));
        objNum++;

        StringBuilder kids = new StringBuilder("[ ");
        for (int i = 0; i < images.size(); i++) {
            kids.append(3 + i * 3).append(" 0 R ");
        }
        kids.append("]");
        xref.add(pdf.size());
        pdf.write((objNum + " 0 obj\n<< /Type /Pages /Kids " + kids + " /Count " + images.size() + " >>\nendobj\n").getBytes("ISO-8859-1"));
        objNum++;

        for (int i = 0; i < images.size(); i++) {
            ImageInfo img = images.get(i);
            int pageObj = 3 + i * 3;
            int imgObj = 4 + i * 3;
            int contentObj = 5 + i * 3;

            // page object will be written later after image and content to keep ordering
            // image object
            xref.add(pdf.size());
            pdf.write((imgObj + " 0 obj\n<< /Type /XObject /Subtype /Image /Width " + img.width + " /Height " + img.height + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length " + img.data.length + " >>\nstream\n").getBytes("ISO-8859-1"));
            pdf.write(img.data);
            pdf.write("\nendstream\nendobj\n".getBytes("ISO-8859-1"));

            // content object
            String content = "q\n" + img.width + " 0 0 " + img.height + " 0 0 cm\n/Im1 Do\nQ";
            byte[] contentBytes = content.getBytes("ISO-8859-1");
            xref.add(pdf.size());
            pdf.write((contentObj + " 0 obj\n<< /Length " + contentBytes.length + " >>\nstream\n").getBytes("ISO-8859-1"));
            pdf.write(contentBytes);
            pdf.write("\nendstream\nendobj\n".getBytes("ISO-8859-1"));

            // page object
            xref.add(pdf.size());
            String pageStr = pageObj + " 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + img.width + " " + img.height + "] ";
            pageStr += "/Resources << /XObject << /Im1 " + imgObj + " 0 R >> >> ";
            pageStr += "/Contents " + contentObj + " 0 R >>\nendobj\n";
            pdf.write(pageStr.getBytes("ISO-8859-1"));
        }

        int startXref = pdf.size();
        pdf.write("xref\n".getBytes("ISO-8859-1"));
        pdf.write(("0 " + (xref.size() + 1) + "\n").getBytes("ISO-8859-1"));
        pdf.write("0000000000 65535 f \n".getBytes("ISO-8859-1"));
        for (int offset : xref) {
            String line = String.format("%010d 00000 n \n", offset);
            pdf.write(line.getBytes("ISO-8859-1"));
        }
        pdf.write(("trailer\n<< /Size " + (xref.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + startXref + "\n%%EOF").getBytes("ISO-8859-1"));

        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            pdf.writeTo(fos);
        }
    }
}
