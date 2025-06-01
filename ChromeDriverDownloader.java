import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.Scanner;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * @author Kripply.com
 * @version V1.1.2 - 06/02/2025 PUBLIC RELEASE
 */
public class ChromeDriverDownloader {

    public static void main(String[] args) {
        try {
            // Determine the operating system
            String os = System.getProperty("os.name").toLowerCase();

            Scanner scanner = new Scanner(System.in);
            System.out.println("Choose browser (chrome/edge) [chrome]: ");
            String browser = scanner.nextLine().trim().toLowerCase();
            boolean useEdge = browser.startsWith("edge");

            if (useEdge) {
                updateEdgeDriver(os);
                return;
            }

            String chromeVersion = getChromeVersion(os);

            if (chromeVersion != null) {
                // Extract the major version
                String[] versionParts = chromeVersion.split("\\.");
                int installedMajorVersion = Integer.parseInt(versionParts[0]);

                // Path to the target directory and version.txt file using OS‑agnostic separators
                String targetDirectory = Paths.get("resources", "drivers").toString();
                String versionFilePath = Paths.get(targetDirectory, "version.txt").toString();

                // Check the version in version.txt
                int currentDriverVersion = readVersionFromFile(versionFilePath);

                // Only proceed with download if necessary
                if (currentDriverVersion < installedMajorVersion) {
                    System.out.println("Updating ChromeDriver...");

                    // Fetch the latest stable ChromeDriver version
                    String latestDriverVersion = fetchLatestChromeDriverVersion();
                    int latestDriverMajorVersion = Integer.parseInt(latestDriverVersion.split("\\.")[0]);
                    System.out.println("Latest available ChromeDriver major version: " + latestDriverMajorVersion);

                    String driverVersion;
                    if (installedMajorVersion > latestDriverMajorVersion) {
                        // Use the chrome‑for‑testing site to get the compatible ChromeDriver version
                        driverVersion = fetchCompatibleChromeDriverVersion(installedMajorVersion);
                    } else {
                        // Use the latest stable ChromeDriver version
                        driverVersion = latestDriverVersion;
                    }

                    if (driverVersion != null) {
                        String downloadURL = String.format(
                                "https://edgedl.me.gvt1.com/edgedl/chrome/chrome-for-testing/%s/%s/chromedriver-%s.zip",
                                driverVersion, getOsSpecificDriverName(os), getOsSpecificDriverName(os));
                        System.out.println("Downloading ChromeDriver from: " + downloadURL);

                        // Download the ChromeDriver
                        String zipFilePath = "chromedriver.zip";
                        downloadFile(downloadURL, zipFilePath);

                        // Extract the downloaded zip file and move the driver to the target folder
                        String driverFileName = os.contains("win") ? "chromedriver.exe" : "chromedriver";
                        extractAndMoveDriver(zipFilePath, targetDirectory, driverFileName);

                        // Write the version number to a text file
                        writeVersionToFile(driverVersion, targetDirectory);

                        System.out.println("ChromeDriver update completed!");
                        Files.deleteIfExists(Paths.get(zipFilePath));
                        PdfPageImageSaver.main(new String[] { "chrome" });
                    } else {
                        System.out.println("Could not find a compatible ChromeDriver version.");
                    }
                } else {
                    System.out.println("ChromeDriver is up to date.");
                    PdfPageImageSaver.main(new String[] { "chrome" });
                }
            } else {
                System.out.println("Chrome version could not be determined.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * UPDATED: downloads EdgeDriver using LATEST_STABLE / LATEST_RELEASE_{major} endpoints,
     * and retrieves the proper platform‑specific zip from microsoft.com.
     */
    private static void updateEdgeDriver(String os) throws Exception {
        String edgeVersion = getEdgeVersion(os);
        if (edgeVersion == null) {
            System.out.println("Edge version could not be determined.");
            return;
        }

        String[] parts = edgeVersion.split("\\.");
        int installedMajorVersion = Integer.parseInt(parts[0]);

        String targetDirectory = Paths.get("resources", "drivers").toString();
        String versionFilePath = Paths.get(targetDirectory, "version.txt").toString();
        int currentVersion = readVersionFromFile(versionFilePath);

        if (currentVersion >= installedMajorVersion) {
            System.out.println("EdgeDriver is up to date. (Installed Edge major: "
                    + installedMajorVersion + "; Current driver: " + currentVersion + ")");
            PdfPageImageSaver.main(new String[] { "edge" });
            return;
        }

        System.out.println("Updating EdgeDriver for Edge " + edgeVersion + " …");

        // 1. Fetch the latest stable EdgeDriver version string
        String latestStableRaw = fetchLatestEdgeDriverVersion();
        if (latestStableRaw == null || latestStableRaw.isEmpty()) {
            System.out.println("Failed to fetch the latest EdgeDriver (LATEST_STABLE). Skipping update.");
            PdfPageImageSaver.main(new String[] { "edge" });
            return;
        }

        // Strip any leading replacement characters from latestStableRaw
        String latestStable = stripLeadingReplacementChars(latestStableRaw);

        // Extract the major version and ensure parsing safely
        String rawMajor = latestStable.split("\\.")[0];  // e.g., "137"
        if (rawMajor.length() > 2 && !Character.isDigit(rawMajor.charAt(0))) {
            rawMajor = rawMajor.substring(2);
        }
        String latestStableMajor = rawMajor;

        String driverVersion;
        if (!Integer.toString(installedMajorVersion).equals(latestStableMajor)) {
            // Fetch a compatible driver for this major
            driverVersion = fetchCompatibleEdgeDriverVersion(installedMajorVersion);
            if (driverVersion == null) {
                System.out.println("Could not find a compatible EdgeDriver for Edge "
                        + installedMajorVersion + ". Skipping update.");
                PdfPageImageSaver.main(new String[] { "edge" });
                return;
            }
        } else {
            driverVersion = latestStable;
        }

        // Remove any leading replacement characters or whitespace from the version
        driverVersion = stripLeadingReplacementChars(driverVersion == null ? "" : driverVersion.trim());

        System.out.println("Resolved EdgeDriver version: " + driverVersion);
        String arch = System.getProperty("os.arch").toLowerCase();
        String downloadURL;

        // 2. Build the direct Edge download URL
        if (os.contains("win")) {
            if (arch.contains("aarch64") || arch.contains("arm")) {
                downloadURL = String.format(
                        "https://msedgedriver.azureedge.net/%s/edgedriver_arm64.zip",
                        driverVersion);
            } else if (isWindows64Bit()) {
                downloadURL = String.format(
                        "https://msedgedriver.azureedge.net/%s/edgedriver_win64.zip",
                        driverVersion);
            } else {
                downloadURL = String.format(
                        "https://msedgedriver.azureedge.net/%s/edgedriver_win32.zip",
                        driverVersion);
            }
        } else if (os.contains("mac")) {
            if (arch.contains("aarch64") || arch.contains("arm")) {
                downloadURL = String.format(
                        "https://msedgedriver.azureedge.net/%s/edgedriver_mac64_m1.zip",
                        driverVersion);
            } else {
                downloadURL = String.format(
                        "https://msedgedriver.azureedge.net/%s/edgedriver_mac64.zip",
                        driverVersion);
            }
        } else {
            System.out.println("Unsupported OS for direct EdgeDriver download URLs. Skipping update.");
            PdfPageImageSaver.main(new String[] { "edge" });
            return;
        }

        // 3. Download, extract, write version.txt, and invoke PDF snapshot
        System.out.println("Downloading EdgeDriver from: " + downloadURL);
        String zipFilePath = "edgedriver.zip";
        downloadFile(downloadURL, zipFilePath);

        String driverFileName = os.contains("win") ? "msedgedriver.exe" : "msedgedriver";
        extractAndMoveDriver(zipFilePath, targetDirectory, driverFileName);
        writeVersionToFile(driverVersion, targetDirectory);
        Files.deleteIfExists(Paths.get(zipFilePath));
        PdfPageImageSaver.main(new String[] { "edge" });

        System.out.println("EdgeDriver update finished.");
    }

    /**
     * Returns the installed Edge browser version (e.g., “137.0.3296.52”).
     */
    private static String getEdgeVersion(String os) {
        String edgeVersion = null;
        try {
            String[] command;
            if (os.contains("win")) {
                command = new String[]{
                    "cmd", "/c",
                    "reg query \"HKEY_CURRENT_USER\\Software\\Microsoft\\Edge\\BLBeacon\" /v version"
                };
            } else if (os.contains("mac")) {
                String edgePath = null;
                String[] candidates = {
                    "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
                    "/Applications/Microsoft Edge Beta.app/Contents/MacOS/Microsoft Edge Beta",
                    "/Applications/Microsoft Edge Dev.app/Contents/MacOS/Microsoft Edge Dev"
                };
                for (String candidate : candidates) {
                    if (new File(candidate).exists()) {
                        edgePath = candidate;
                        break;
                    }
                }
                if (edgePath == null) edgePath = "microsoft-edge";
                command = new String[]{ edgePath, "--version" };
            } else {
                command = new String[]{ "microsoft-edge", "--version" };
            }

            Process process = new ProcessBuilder(command).start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            Pattern pattern = Pattern.compile("(\\d+\\.\\d+\\.\\d+\\.\\d+)");
            while ((line = reader.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    edgeVersion = matcher.group(1);
                    break;
                }
            }
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return edgeVersion;
    }

    /**
     * Returns the installed Chrome browser version (e.g., “117.0.5938.88”).
     */
    private static String getChromeVersion(String os) {
        String chromeVersion = null;
        try {
            String[] command;
            if (os.contains("win")) {
                command = new String[]{
                    "cmd", "/c",
                    "reg query \"HKEY_CURRENT_USER\\Software\\Google\\Chrome\\BLBeacon\" /v version"
                };
            } else if (os.contains("mac")) {
                String chromePath = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
                command = new String[]{ chromePath, "--version" };
            } else {
                command = new String[]{ "google-chrome", "--version" };
            }

            Process process = new ProcessBuilder(command).start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            Pattern pattern = Pattern.compile("(\\d+\\.\\d+\\.\\d+\\.\\d+)");
            while ((line = reader.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    chromeVersion = matcher.group(1);
                    break;
                }
            }
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return chromeVersion;
    }

    /**
     * Reads current major version from resources/drivers/version.txt.
     * If the first two characters are non‑numeric (e.g., “��”), trim them off before parsing.
     * Returns –1 if the file is missing or cannot be parsed to an integer.
     */
    private static int readVersionFromFile(String versionFilePath) {
        int version = -1;
        Path path = Paths.get(versionFilePath);

        if (!Files.exists(path)) {
            System.out.println("version.txt not found. Proceeding with fresh download…");
            return -1;
        }

        try {
            // Read entire file as UTF-8 text (Java 7+)
            String content = Files.readString(path, Charset.forName("UTF-8")).trim();

            // If the first two characters are non‑digits ("��" or similar), drop them
            if (content.length() > 2 && !Character.isDigit(content.charAt(0))) {
                content = content.substring(2).trim();
            }

            // Now parse the remaining string as an integer
            version = Integer.parseInt(content);
            System.out.println("Current driver major version: " + version);
        } catch (IOException e) {
            System.out.println("Error reading version.txt: " + e.getMessage());
        } catch (NumberFormatException e) {
            System.out.println("version.txt does not contain a valid integer after trimming. Proceeding with fresh download…");
        }

        return version;
    }

    /**
     * FETCHES: https://msedgedriver.azureedge.net/LATEST_STABLE
     * Returns a string like “137.0.3296.52”, but strips leading “��” if present.
     */
    private static String fetchLatestEdgeDriverVersion() {
        String url = "https://msedgedriver.azureedge.net/LATEST_STABLE";
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new URL(url).openStream()))) {
            String line = reader.readLine();
            return stripLeadingReplacementChars(line == null ? null : line.trim());
        } catch (Exception e) {
            System.err.println("fetchLatestEdgeDriverVersion error: " + e.getMessage());
        }
        return null;
    }

    /**
     * FETCHES: https://msedgedriver.azureedge.net/LATEST_RELEASE_{major}
     * For example, if major=137, calls:
     *   https://msedgedriver.azureedge.net/LATEST_RELEASE_137
     * Returns a string like “137.0.3296.52”.
     */
    private static String fetchCompatibleEdgeDriverVersion(int majorVersion) {
        String url = "https://msedgedriver.azureedge.net/LATEST_RELEASE_" + majorVersion;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new URL(url).openStream()))) {
            String line = reader.readLine();
            return stripLeadingReplacementChars(line == null ? null : line.trim());
        } catch (Exception e) {
            System.err.println("fetchCompatibleEdgeDriverVersion error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Strips a leading pair of Unicode replacement characters ("��") if present.
     */
    private static String stripLeadingReplacementChars(String input) {
        if (input != null && input.startsWith("\uFFFD\uFFFD")) {
            return input.substring(2);
        }
        return input;
    }

    /**
     * FETCHES: https://chromedriver.storage.googleapis.com/LATEST_RELEASE
     * Returns a string like “117.0.5938.88”.
     */
    private static String fetchLatestChromeDriverVersion() {
        String url = "https://chromedriver.storage.googleapis.com/LATEST_RELEASE";
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new URL(url).openStream()))) {
            return reader.readLine();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * FETCHES known‑good versions JSON and returns the first version that
     * starts with the requested major. Example URL:
     * https://googlechromelabs.github.io/chrome-for-testing/known-good-versions-with-downloads.json
     */
    private static String fetchCompatibleChromeDriverVersion(int majorVersion) {
        try {
            HttpURLConnection conn = (HttpURLConnection)
                    new URL("https://googlechromelabs.github.io/chrome-for-testing/known-good-versions-with-downloads.json")
                            .openConnection();
            conn.setRequestMethod("GET");
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
            reader.close();

            JSONArray versionsArray = new JSONObject(jsonBuilder.toString()).getJSONArray("versions");
            for (int i = 0; i < versionsArray.length(); i++) {
                String version = versionsArray.getJSONObject(i).getString("version");
                if (version.startsWith(String.valueOf(majorVersion))) {
                    return version;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Downloads the file at fileURL to savePath (e.g. “edgedriver.zip”).
     */
    private static void downloadFile(String fileURL, String savePath) throws Exception {
        HttpURLConnection httpConn = null;
        try {
            httpConn = (HttpURLConnection) new URL(fileURL).openConnection(java.net.Proxy.NO_PROXY);
            int responseCode = httpConn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedInputStream in = new BufferedInputStream(httpConn.getInputStream());
                     FileOutputStream out = new FileOutputStream(savePath)) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }
                System.out.println("File downloaded: " + savePath);
            } else {
                throw new IOException("Server returned non‑OK status: " + responseCode);
            }
        } catch (Exception e) {
            System.out.println("Failed to download driver: " + e.getMessage());
            throw e;
        } finally {
            if (httpConn != null) {
                httpConn.disconnect();
            }
        }
    }

    /**
     * Unzips zipFilePath, finds the “msedgedriver[.exe]” or “chromedriver[.exe]” entry,
     * and moves it into targetDirectory.
     */
    private static void extractAndMoveDriver(String zipFilePath, String targetDirectory, String driverFileName)
            throws IOException {
        Files.createDirectories(Paths.get(targetDirectory));
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(driverFileName)) {
                    Path outputFile = Paths.get(targetDirectory, driverFileName);
                    Files.copy(zis, outputFile, StandardCopyOption.REPLACE_EXISTING);
                    System.out.println(driverFileName + " extracted to " + targetDirectory);
                    break;
                }
            }
        }
    }

    /**
     * Writes the major version (e.g. “137”) to version.txt so we don’t re‑download.
     */
    private static void writeVersionToFile(String version, String targetDirectory) {
        try {
            Path versionFile = Paths.get(targetDirectory, "version.txt");
            Files.writeString(versionFile, version.split("\\.")[0], StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("Driver version written to " + versionFile);
        } catch (IOException e) {
            System.out.println("Failed to write version file: " + e.getMessage());
        }
    }

    /**
     * Determines if Windows is running 64‑bit.
     */
    private static boolean isWindows64Bit() {
        String arch = System.getenv("PROCESSOR_ARCHITECTURE");
        String wow64Arch = System.getenv("PROCESSOR_ARCHITEW6432");
        return (arch != null && arch.endsWith("64")) || (wow64Arch != null && wow64Arch.endsWith("64"));
    }

    /**
     * Returns OS‑specific suffix for ChromeDriver downloads. E.g.:
     * “win64”, “win32”, “mac-arm64”, “mac-x64”, “linux-x64”.
     */
    private static String getOsSpecificDriverName(String os) {
        if (os.contains("win")) {
            return isWindows64Bit() ? "win64" : "win32";
        } else if (os.contains("mac")) {
            String arch = System.getProperty("os.arch");
            return (arch.contains("aarch64") || arch.contains("arm")) ? "mac-arm64" : "mac-x64";
        } else {
            return "linux-x64";
        }
    }
}
