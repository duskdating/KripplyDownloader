import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
/**
 * @author Kripply.com
 * @version V1.0.0 - 08/29/2024 PUBLIC RELEASE
 */
public class PngToJpegConverter {

    public static void main(String[] args) throws InterruptedException {
        // Define the directory path containing the PNG images
        String inputDirectoryPath = "temp/uncompressed";  // Path to the folder with PNG images
        String outputDirectoryPath = "temp/compressed"; // Path to the folder for output JPEG images

        // Create File objects for the input and output directories
        File inputDirectory = new File(inputDirectoryPath);
        File outputDirectory = new File(outputDirectoryPath);

        // Create the output directory if it doesn't exist
        if (!outputDirectory.exists()) {
            if (outputDirectory.mkdirs()) {
                System.out.println("Output directory created: " + outputDirectoryPath);
            } else {
                System.out.println("Failed to create output directory: " + outputDirectoryPath);
                return;
            }
        }

        // Get a list of all files in the input directory
        File[] files = inputDirectory.listFiles();

        // Check if the input directory is empty or doesn't exist
        if (files == null || files.length == 0) {
            System.out.println("The input directory is empty or doesn't exist.");
            return;
        }

        // Iterate over each file in the directory
        for (File file : files) {
            // Check if the file is a PNG image
            if (file.isFile() && file.getName().toLowerCase().endsWith(".png")) {
                try {
                    System.out.println("Processing file: " + file.getAbsolutePath());

                    // Read the PNG image
                    BufferedImage pngImage = ImageIO.read(file);

                    // If image reading fails, pngImage will be null
                    if (pngImage == null) {
                        System.out.println("Failed to read image: " + file.getName());
                        continue;
                    }

                    // Convert the image to a compatible format (remove alpha channel)
                    BufferedImage rgbImage = new BufferedImage(
                            pngImage.getWidth(), pngImage.getHeight(), BufferedImage.TYPE_INT_RGB);
                    
                    Graphics2D g = rgbImage.createGraphics();
                    g.drawImage(pngImage, 0, 0, null);
                    g.dispose();

                    // Define the output file name and path (change the extension to .jpg)
                    String outputFileName = file.getName().replace(".png", ".jpg");
                    File outputFile = new File(outputDirectory, outputFileName);

                    // Set JPEG compression quality
                    try (FileOutputStream fos = new FileOutputStream(outputFile);
                         ImageOutputStream ios = ImageIO.createImageOutputStream(fos)) {

                        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
                        writer.setOutput(ios);

                        ImageWriteParam param = writer.getDefaultWriteParam();
                        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                        param.setCompressionQuality(0.25f); // Compression quality (0.0f is low, 1.0f is max quality)

                        writer.write(null, new IIOImage(rgbImage, null, null), param);
                        writer.dispose();

                        System.out.println("Converted: " + file.getName() + " -> " + outputFile.getAbsolutePath());

                        // Delete the original PNG file after successful conversion
                        if (file.delete()) {
                            System.out.println("Deleted uncompressed file: " + file.getAbsolutePath());
                        } else {
                            System.out.println("Failed to delete original uncompressed file: " + file.getAbsolutePath());
                        }
                    }

                } catch (IOException e) {
                    System.err.println("Error processing file: " + file.getName());
                    e.printStackTrace();
                }
            }
        }
        
        System.out.println("Compression process completed.");
        System.out.println("Converting pages to PDF...");
        PDFConverter.main(new String[]{});
    }
}
