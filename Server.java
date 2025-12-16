import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;


public class Server{
    
    private static int PORT;
    private static String fileDirectory;

    public static void main(String[] args) {
        
        if (args.length < 2) {
            System.err.println("Usage: java FileServer <port> <file_directory_path>");
            return;
        }

        try {
            PORT = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number.");
            return;
        }

        fileDirectory = args[1];

        boolean dirValidated = false;
        Scanner scanner = new Scanner(System.in);

        while (!dirValidated) {
            Path dirPath = Paths.get(fileDirectory);
            
            if (Files.exists(dirPath) && Files.isDirectory(dirPath)) {
                // Βρέθηκε και είναι φάκελος. Ο έλεγχος ολοκληρώθηκε.
                dirValidated = true;
                System.out.println(" Correct file: " + dirPath.toAbsolutePath());
            } else {                
                String errorMsg = Files.exists(dirPath) ? 
                                  "The path exists but is not a folder" : 
                                  "The file folder was not found in the path: " + dirPath.toAbsolutePath();
                
                System.err.println("Error: " + errorMsg);
                
                // Ζητάμε ξανά  τη διαδρομή
                System.out.print("Please enter a valid path to an existing folder: ");
                fileDirectory = scanner.nextLine().trim(); // Διαβάζουμε τη νέα διαδρομή
            }
        }
        
        scanner.close();

        System.out.println("Starting Server on port " + PORT + "...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)){
            String hostAddress = InetAddress.getLocalHost().getHostAddress();
            System.out.println("Server Listening on IP: " + hostAddress + " Port : " + PORT);
            System.out.println("Waiting for files on: " + Paths.get(fileDirectory).toAbsolutePath() + "\n");
            while (true) {
                // Περιμένει για μια εισερχόμενη σύνδεση (blocking call)
                Socket clientSocket = serverSocket.accept();

                // Δημιουργεί ένα νέο thread για να διαχειριστεί τον client, 
                // περνώντας το socket και τη διαδρομή του φακέλου.
                new Thread(new ClientHandler(clientSocket, fileDirectory)).start();
            }
        } catch (IOException e) {
            System.err.println("Error while starting the Server: " + e.getMessage());
            System.err.println("Verify that the Port:" + PORT + "is free");
        }

    }

    private static class ClientHandler implements Runnable{
        private Socket socket;
        private ObjectInputStream in;
        private ObjectOutputStream out;
        private String fileDirectory;

        public ClientHandler(Socket socket, String fileDirectory) {
                this.socket = socket;
                this.fileDirectory = fileDirectory;
                try{
                    this.out=new ObjectOutputStream(socket.getOutputStream());
                    out.flush();
                    this.in=new ObjectInputStream(socket.getInputStream());
                    
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }

        @Override
        public void run() {
            String clientAddress = socket.getInetAddress().getHostAddress();
            System.out.println("Connection from: " + clientAddress);
            if (in == null || out == null) {
                System.err.println(" Impossible to serve the client " + clientAddress + "due to stream error");
                return; 
            }
            try {
                // 1. Λήψη ονόματος αρχείου (Διαβάζουμε String Object)
                String fileName = (String) in.readObject();
                    
                System.out.println(" Request for file: " + fileName);
                    
                // Δημιουργία του Path για το αρχείο
                Path filePath = Paths.get(fileDirectory, fileName);
                File file = filePath.toFile();

                    // Έλεγχος αν υπάρχει το αρχείο
                if (!file.exists() || !file.isFile()) {
                    System.out.println(" File not found: " + fileName);
                    // Στέλνουμε -1 για να δηλώσουμε σφάλμα
                    out.writeLong(-1);
                    out.flush();
                    return;
                }

                // 2. Αποστολή μεγέθους αρχείου (ως Long)
                long fileSize = file.length();
                out.writeLong(fileSize);
                out.flush();
                    
                System.out.println("Sending file: " + fileName + " (" + fileSize + " bytes)");
                    
                // 3. Αποστολή των bytes του αρχείου
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[8192]; 
                    int bytesRead;
                    long bytesSent = 0;
                        
                    while ((bytesRead = fis.read(buffer)) > 0) {
                        out.write(buffer, 0, bytesRead);
                        bytesSent += bytesRead;
                    }
                }
                    
                out.flush(); // Βεβαιωνόμαστε ότι στάλθηκαν όλα τα δεδομένα
                System.out.println(" Transfer complete for: " + fileName + ". Total bytes sent: " + fileSize);

            } catch (EOFException e) {
                // Αυτό συμβαίνει αν ο client κλείσει τη σύνδεση ξαφνικά
                System.out.println(" Client disconnected unexpectedly.");
            } catch (IOException | ClassNotFoundException e) {
                System.err.println(" Error processing request from " + clientAddress + ": " + e.getMessage());
            } finally {
                // Κλείσιμο πόρων (socket, in, out)
                try {
                    if (socket != null && !socket.isClosed()) {
                        socket.close();
                        System.out.println(" Connection with " + clientAddress + " closed.");
                    }
                } catch (IOException e) {
                    System.err.println("Error closing socket: " + e.getMessage());
                }
                
            }
        }
    }
}