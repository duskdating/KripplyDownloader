import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;
/**
 * @author Kripply.com
 * @version V1.0.0 - 08/29/2024 PUBLIC RELEASE
 */
public class PDFConverter {

    public static void main(String[] args) throws InterruptedException {
        int currentIndex = 0;
        // Directory containing images
        String compressedDirectory = "temp/compressed";
        String uncompressedDirectory = "temp/uncompressed";
        // Check if there are files in compressed directory
        File compressed = new File(compressedDirectory);
        // Get a list of all files in the input directory
        File uncompressed = new File(uncompressedDirectory);
        File[] files = uncompressed.listFiles();
        File[] compressedFiles = compressed.listFiles();
        // Output PDF file path
        String filePath = "PDF - OUTPUT/";
        String title = "ebook.pdf";
        
        // Get list of PNG image paths
        List<String> imagePaths = getPngImagesFromDirectory(uncompressedDirectory);
        // Check if the directory exists and is a directory
        if (compressed.exists() && compressed.isDirectory()) {
            // Check if the directory contains any files
            if (compressedFiles != null && compressedFiles.length > 0) {
                imagePaths = getJpgImagesFromDirectory(compressedDirectory);
                files = compressedFiles;
        } else {
            System.out.println("The specified path is not a valid directory.");
        }}
        
        
        

        File titleFile = new File("temp/title.txt");
        if (titleFile.exists()) {
            try (Scanner titleScanner = new Scanner(titleFile)) {
                if (titleScanner.hasNextLine()) {
                    title = titleScanner.nextLine() + ".pdf";
                    System.out.println("Using title from temp/title.txt: " + title);
                } else {
                    System.out.println("Invalid title in temp/title.txt. Using default title: " + title);
                }
            } catch (FileNotFoundException e) {
                System.out.println("temp/title.txt not found. Using default title: " + title);
            }
        }
        // Document path
        File docPath = new File(filePath + title);
        // Create PDF document
        System.out.println("Creating PDF...");
        try (PDDocument document = new PDDocument()) {
            for (String imagePath : imagePaths) {
                addImageToPdf(document, imagePath);
                currentIndex++;
                System.out.println("Added page " + imagePath);
                // Calculate and display progress percentage
                 // Calculate progress percentage
            int progressPercentage = (int) ((currentIndex / (double) imagePaths.size()) * 100);
            System.out.println("Progress: " + progressPercentage + "% complete.");
        }

            // Save the PDF document
            document.save(filePath + title);
            System.out.println("Deleting temporary files...");
            Thread.sleep(200);
            for (File file : files) {
                // Delete the original PNG file after successful conversion
                if (file.delete()) {
                    System.out.println("Deleted temp file: " + file.getAbsolutePath());
                } else {
                    System.out.println("Failed to delete temp file: " + file.getAbsolutePath());
                }
            }
            System.out.println("PDF created successfully: " + title);
            System.out.println("PDF location: " + docPath.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static List<String> getPngImagesFromDirectory(String directory) {
        try {
            // Use Java NIO to list PNG files in the directory and sort by natural numeric order
            return Files.walk(Paths.get(directory))
                    .filter(Files::isRegularFile)
                    .map(Path::toString)
                    .filter(path -> path.toLowerCase().endsWith(".png"))
                    .sorted(Comparator.comparing(PDFConverter::extractNumericValueFromFileName))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
            return List.of(); // Return an empty list if an exception occurs
        }
    }
    private static List<String> getJpgImagesFromDirectory(String directory) {
        try {
            // Use Java NIO to list JPG files in the directory and sort by natural numeric order
            return Files.walk(Paths.get(directory))
                    .filter(Files::isRegularFile)
                    .map(Path::toString)
                    .filter(path -> path.toLowerCase().endsWith(".jpg"))
                    .sorted(Comparator.comparing(PDFConverter::extractNumericValueFromFileName))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
            return List.of(); // Return an empty list if an exception occurs
        }
    }
    private static int extractNumericValueFromFileName(String fileName) {
        // Extract the numeric part from the filename for sorting
        String baseName = Paths.get(fileName).getFileName().toString();
        String number = baseName.replaceAll("\\D", ""); // Remove all non-digit characters
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE; // For files that don't contain numbers
        }
    }

    private static void addImageToPdf(PDDocument document, String imagePath) throws IOException {
        // Load the image
        PDImageXObject image = PDImageXObject.createFromFile(imagePath, document);

        // Create a new page with the size of the image
        PDPage page = new PDPage(new PDRectangle(image.getWidth(), image.getHeight()));
        document.addPage(page);

        // Draw the image on the page
        try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
            contentStream.drawImage(image, 0, 0, image.getWidth(), image.getHeight());
        }
    }
}
