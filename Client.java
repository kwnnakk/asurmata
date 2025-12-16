import java.io.*;
import java.net.*;
import java.nio.file.*;

public class Client {
    
    private static int nA, nB;
    private static String ipA, ipB;
    private static final int PORT = 5000;
    private static final int TOTAL_FILES = 160;
    private static final String FILE_PREFIX = "s";
    private static final String FILE_SUFFIX = ".m4s";
    
    public static void main(String[] args) {
        if (args.length != 4) {
            System.out.println("Usage: java Client <nA> <nB> <IP_A> <IP_B>");
            System.out.println("Example: java Client 1 2 192.168.1.10 192.168.1.20");
            System.out.println("Note: All servers must use port " + PORT);
            System.out.println("\nTest combinations from the assignment:");
            System.out.println("  client 1 5 IP_A IP_B");
            System.out.println("  client 1 4 IP_A IP_B");
            System.out.println("  ... up to client 5 1 IP_A IP_B");
            return;
        }
        
        try {
            nA = Integer.parseInt(args[0]);
            nB = Integer.parseInt(args[1]);
            ipA = args[2];
            ipB = args[3];
            
            System.out.println("=".repeat(60));
            System.out.println("CLIENT STARTED");
            System.out.println("Parameters: nA=" + nA + ", nB=" + nB);
            System.out.println("Server A: " + ipA + ":" + PORT);
            System.out.println("Server B: " + ipB + ":" + PORT);
            System.out.println("Total files to download: " + TOTAL_FILES);
            System.out.println("=".repeat(60));
            
            // Δημιουργία φακέλου (αν δεν υπάρχει)
            Path downloadDir = Paths.get("downloads");
            if (!Files.exists(downloadDir)) {
                Files.createDirectories(downloadDir);
                System.out.println("Created directory: downloads/");
            }
            
            // ΜΕΤΡΗΣΗ ΧΡΟΝΟΥ
            long startTime = System.nanoTime();
            downloadFilesInSequence();
            long endTime = System.nanoTime();
            
            double totalTimeSeconds = (endTime - startTime) / 1_000_000_000.0;
            
            System.out.println("\n" + "=".repeat(60));
            System.out.println("DOWNLOAD COMPLETE!");
            System.out.printf("TOTAL TIME: %.3f seconds%n", totalTimeSeconds);
            System.out.println("=".repeat(60));
            
            // ΑΥΤΟ ΕΙΝΑΙ ΤΟ ΜΟΝΟ ΠΟΥ ΧΡΕΙΑΖΕΣΑΙ ΓΙΑ ΤΑ ΠΕΙΡΑΜΑΤΑ!
            System.out.println("\nRESULT FOR LOG: " + nA + " " + nB + " " + 
                             String.format("%.3f", totalTimeSeconds) + "s");
            
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
   
    private static void downloadFilesInSequence() throws IOException, ClassNotFoundException {
        int fileIndex = 1;
        int filesDownloaded = 0;
        int cycle = 1;
        int filesInCurrentCycle = 0;
        
        System.out.println("\nStarting download sequence...");
        
        while (filesDownloaded < TOTAL_FILES) {
            System.out.println("\n--- CYCLE " + cycle + " ---");
            
            // Server A
            for (int i = 0; i < nA && filesDownloaded < TOTAL_FILES; i++) {
                String fileName = FILE_PREFIX + String.format("%03d", fileIndex) + FILE_SUFFIX;
                System.out.print("[A] " + fileName + " -> ");
                downloadFile("A", ipA, fileName);
                fileIndex++;
                filesDownloaded++;
                filesInCurrentCycle++;
            }
            
            // Server B
            for (int i = 0; i < nB && filesDownloaded < TOTAL_FILES; i++) {
                String fileName = FILE_PREFIX + String.format("%03d", fileIndex) + FILE_SUFFIX;
                System.out.print("[B] " + fileName + " -> ");
                downloadFile("B", ipB, fileName);
                fileIndex++;
                filesDownloaded++;
                filesInCurrentCycle++;
            }
            
            // Έλεγχος για μετάβαση στον επόμενο κύκλο
            if (filesInCurrentCycle >= (nA + nB)) {
                cycle++;
                filesInCurrentCycle = 0;
            }
        }
        
        System.out.println("\n✓ Downloaded " + filesDownloaded + "/" + TOTAL_FILES + " files");
    }
    
    private static void downloadFile(String serverName, String serverIP, String fileName) 
            throws IOException, ClassNotFoundException {
        
        try (Socket socket = new Socket(serverIP, PORT);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            
            socket.setSoTimeout(30000); // 30 δευτερόλεπτα timeout
            
            // 1. Στέλνουμε το όνομα του αρχείου
            out.writeObject(fileName);
            out.flush();
            
            // 2. Λαμβάνουμε το μέγεθος
            long fileSize = in.readLong();
            
            if (fileSize == -1) {
                throw new IOException("File '" + fileName + "' not found on server " + serverName);
            }
            
            // 3. Λαμβάνουμε τα δεδομένα
            Path filePath = Paths.get("downloads", fileName);
            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                byte[] buffer = new byte[8192];
                long totalRead = 0;
                int bytesRead;
                
                while (totalRead < fileSize) {
                    int toRead = (int) Math.min(buffer.length, fileSize - totalRead);
                    bytesRead = in.read(buffer, 0, toRead);
                    if (bytesRead == -1) break;
                    
                    fos.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;
                }
                
                // Επαλήθευση
                if (totalRead == fileSize) {
                    System.out.println("OK (" + fileSize + " bytes)");
                } else {
                    System.out.println("INCOMPLETE! Expected: " + fileSize + ", Got: " + totalRead);
                    throw new IOException("Incomplete file download");
                }
            }
        } catch (SocketTimeoutException e) {
            System.out.println("TIMEOUT!");
            throw new IOException("Connection timeout for " + fileName);
        }
    }
}