/**
 * Yunze Jiang
 * 20/04/2026
 */

package com.jayjiang.server;

import com.jayjiang.common.DictionaryRequest;
import com.jayjiang.common.DictionaryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


public class DictionaryServer {
    private final int port;
    private final Dictionary dictionary;
    private ExecutorService threadPool;
    private final ObjectMapper objectMapper;
    private volatile boolean running;
    private DictionaryServerGUI gui;
    private ServerSocket serverSocket;
    // Track every connected client socket so force-close can be performed on shutdown.
    private final Set<Socket> activeClientSockets = ConcurrentHashMap.newKeySet();

    public DictionaryServer(int port, String dictionaryFile) throws IOException {
        this.port = port;
        this.dictionary = new Dictionary(dictionaryFile);
        this.objectMapper = new ObjectMapper();
        this.running = false;
    }
    
    public DictionaryServer(int port, String dictionaryFile, DictionaryServerGUI gui) throws IOException {
        this(port, dictionaryFile);
        this.gui = gui;
    }

    public void start() {
        // Create a fresh thread pool on each start so the server can be restarted
        // after a shutdown without creating a new DictionaryServer instance.
        this.threadPool = Executors.newFixedThreadPool(10);
        this.running = true;

        try {
            serverSocket = new ServerSocket(port);
            log("Dictionary Server started on port " + port);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    String clientAddress = clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();

                    activeClientSockets.add(clientSocket);

                    if (gui != null) {
                        gui.clientConnected(clientAddress);
                    }
                    
                    threadPool.execute(new ClientHandler(clientSocket, clientAddress));
                } catch (IOException e) {
                    if (running) {
                        log("Error accepting client connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            log("Could not listen on port " + port + ": " + e.getMessage());
        } finally {
            internalCleanup();
        }
    }

    public void shutdown() {
        running = false;

        // Close the server socket so no new clients can connect
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                log("Error closing server socket: " + e.getMessage());
            }
        }

        // Force-close all existing client sockets so their handler threads
        // Receive an IOException on their next read and exit, makes shutdown stop in-flight work.
        for (Socket s : activeClientSockets) {
            try {
                if (!s.isClosed()) {
                    s.close();
                }
            } catch (IOException e) {
                log("Error closing client socket: " + e.getMessage());
            }
        }
        activeClientSockets.clear();
    }

    private void internalCleanup() {
        running = false;
        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(2, TimeUnit.SECONDS)) {
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                log("Error closing server socket: " + e.getMessage());
            }
        }
    }
    
    private void log(String message) {
        if (gui != null) {
            gui.log(message);
        } else {
            System.out.println(message);
        }
    }

    private class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private final String clientAddress;

        public ClientHandler(Socket socket, String clientAddress) {
            this.clientSocket = socket;
            this.clientAddress = clientAddress;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

                String inputLine;
                while (running && (inputLine = in.readLine()) != null) {
                    try {
                        // Strip leading null bytes / whitespace from heartbeat pings
                        int start = 0;
                        while (start < inputLine.length()
                                && (inputLine.charAt(start) == '\u0000' || Character.isWhitespace(inputLine.charAt(start)))) {
                            start++;
                        }
                        if (start == inputLine.length()) {
                            continue; // heartbeat-only line, nothing to process
                        }
                        inputLine = inputLine.substring(start);

                        DictionaryRequest request = objectMapper.readValue(inputLine, DictionaryRequest.class);

                        // Refuse to process new work if the server has been asked to stop.
                        // This prevents writes from landing after shutdown was requested.
                        if (!running) {
                            DictionaryResponse stopped = new DictionaryResponse(false, "Server is shutting down");
                            out.println(objectMapper.writeValueAsString(stopped));
                            break;
                        }

                        // The simulated delay is applied inside Dictionary methods, while the read/write lock is held. 

                        if (gui != null) {
                            gui.requestProcessed(clientAddress, request.getOperation().toString(), request.getWord());
                        }
                        
                        DictionaryResponse response = processRequest(request);
                        out.println(objectMapper.writeValueAsString(response));
                    } catch (Exception e) {
                        DictionaryResponse errorResponse = new DictionaryResponse(false, "Error processing request: " + e.getMessage());
                        out.println(objectMapper.writeValueAsString(errorResponse));
                        log("Error processing request from " + clientAddress + ": " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                // Expected when the server force-closes the socket during shutdown
                if (running) {
                    log("Error handling client connection: " + e.getMessage());
                }
            } finally {
                activeClientSockets.remove(clientSocket);
                try {
                    if (!clientSocket.isClosed()) {
                        clientSocket.close();
                    }
                    if (gui != null) {
                        gui.clientDisconnected(clientAddress);
                    }
                } catch (IOException e) {
                    log("Error closing client socket: " + e.getMessage());
                }
            }
        }

        private DictionaryResponse processRequest(DictionaryRequest request) {
            try {
                int delay = request.getDelay();
                switch (request.getOperation()) {
                    case QUERY:
                        var meanings = dictionary.query(request.getWord(), delay);
                        if (meanings == null) {
                            return new DictionaryResponse(false, "Word not found");
                        }
                        return new DictionaryResponse(true, "Word found", meanings);

                    case ADD_WORD:
                        boolean added = dictionary.addWord(request.getWord(), request.getMeanings(), delay);
                        return new DictionaryResponse(added, added ? "Word added successfully" : "Word already exists");

                    case REMOVE_WORD:
                        boolean removed = dictionary.removeWord(request.getWord(), delay);
                        return new DictionaryResponse(removed, removed ? "Word removed successfully" : "Word not found");

                    case ADD_MEANING:
                        boolean meaningAdded = dictionary.addMeaning(request.getWord(), request.getNewMeaning(), delay);
                        return new DictionaryResponse(meaningAdded, meaningAdded ? "Meaning added successfully" : "Word not found or meaning already exists");

                    case UPDATE_MEANING:
                        boolean updated = dictionary.updateMeaning(request.getWord(), request.getExistingMeaning(), request.getNewMeaning(), delay);
                        return new DictionaryResponse(updated, updated ? "Meaning updated successfully" : "Word or meaning not found");

                    default:
                        return new DictionaryResponse(false, "Unknown operation");
                }
            } catch (IOException e) {
                return new DictionaryResponse(false, "Error processing request: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java DictionaryServer <port> <dictionary-file>");
            System.exit(1);
        }

        try {
            int port = Integer.parseInt(args[0]);
            String dictionaryFile = args[1];
            DictionaryServer server = new DictionaryServer(port, dictionaryFile);
            server.start();
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Error starting server: " + e.getMessage());
            System.exit(1);
        }
    }
}