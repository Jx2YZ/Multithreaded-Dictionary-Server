/**
 * Jx2
 * 20/04/2026
 */

package com.server;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class DictionaryServerGUI extends JFrame {
    private DictionaryServer server;
    private final int port;
    private final String dictionaryFile;
    private JTextArea logArea;
    private JLabel statusLabel;
    private JLabel connectionCountLabel;
    private JLabel requestCountLabel;
    private JButton startButton;
    private JButton shutdownButton;
    private final AtomicInteger connectionCount = new AtomicInteger(0);
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final List<String> connectedClients = new ArrayList<>();
    private final JList<String> clientList = new JList<>();
    private final DefaultListModel<String> clientListModel = new DefaultListModel<>();
    private volatile boolean serverRunning = false;

    public DictionaryServerGUI(int port, String dictionaryFile) {
        this.port = port;
        this.dictionaryFile = dictionaryFile;
        setupGUI();
        // Server is NOT started automatically — user must click Start.
    }
    
    private void setupGUI() {
        setTitle("Dictionary Server - Port " + port);
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        
        // Status Panel
        JPanel statusPanel = new JPanel(new GridLayout(1, 3, 10, 0));
        statusPanel.setBorder(BorderFactory.createTitledBorder("Server Status"));
        
        statusLabel = new JLabel("Status: Stopped");
        statusLabel.setForeground(Color.RED);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        
        connectionCountLabel = new JLabel("Active Connections: 0");
        requestCountLabel = new JLabel("Total Requests: 0");
        
        statusPanel.add(statusLabel);
        statusPanel.add(connectionCountLabel);
        statusPanel.add(requestCountLabel);
        
        // Log Panel
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(BorderFactory.createTitledBorder("Server Log"));
        
        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane logScrollPane = new JScrollPane(logArea);
        logPanel.add(logScrollPane, BorderLayout.CENTER);
        
        // Control Panel
        JPanel controlPanel = new JPanel();
        controlPanel.setBorder(BorderFactory.createTitledBorder("Controls"));
        
        startButton = new JButton("Start Server");
        startButton.addActionListener(e -> startServer());
        
        shutdownButton = new JButton("Shutdown Server");
        shutdownButton.addActionListener(e -> shutdownServer());
        shutdownButton.setEnabled(false);
        
        JButton clearLogButton = new JButton("Clear Log");
        clearLogButton.addActionListener(e -> logArea.setText(""));
        
        controlPanel.add(startButton);
        controlPanel.add(shutdownButton);
        controlPanel.add(clearLogButton);
        
        // Client List Panel
        JPanel clientPanel = new JPanel(new BorderLayout());
        clientPanel.setBorder(BorderFactory.createTitledBorder("Connected Clients"));
        
        clientList.setModel(clientListModel);
        JScrollPane clientScrollPane = new JScrollPane(clientList);
        clientPanel.add(clientScrollPane, BorderLayout.CENTER);
        
        // Dictionary Info Panel
        JPanel dictionaryPanel = new JPanel(new BorderLayout());
        dictionaryPanel.setBorder(BorderFactory.createTitledBorder("Dictionary Information"));
        
        JTextArea dictionaryInfo = new JTextArea();
        dictionaryInfo.setEditable(false);
        dictionaryInfo.setText("Dictionary File: " + dictionaryFile + "\n");
        JScrollPane dictionaryScrollPane = new JScrollPane(dictionaryInfo);
        dictionaryPanel.add(dictionaryScrollPane, BorderLayout.CENTER);
        
        // Arrange panels
        JPanel rightPanel = new JPanel(new GridLayout(2, 1));
        rightPanel.add(clientPanel);
        rightPanel.add(dictionaryPanel);
        
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(statusPanel, BorderLayout.NORTH);
        bottomPanel.add(controlPanel, BorderLayout.SOUTH);
        
        add(logPanel, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);
        
        // Handle window close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (serverRunning) {
                    shutdownServer();
                }
            }
        });
        
        setLocationRelativeTo(null);
    }

    private void startServer() {
        if (serverRunning) {
            return;
        }
        try {
            server = new DictionaryServer(port, dictionaryFile, this);
            serverRunning = true;

            statusLabel.setText("Status: Running");
            statusLabel.setForeground(Color.GREEN.darker());
            startButton.setEnabled(false);
            shutdownButton.setEnabled(true);

            new Thread(() -> {
                server.start();
                // start() returns once the server has fully stopped
                SwingUtilities.invokeLater(() -> {
                    serverRunning = false;
                    statusLabel.setText("Status: Stopped");
                    statusLabel.setForeground(Color.RED);
                    startButton.setEnabled(true);
                    shutdownButton.setEnabled(false);
                    // Clear client list on stop
                    connectionCount.set(0);
                    connectionCountLabel.setText("Active Connections: 0");
                    connectedClients.clear();
                    clientListModel.clear();
                });
            }, "DictionaryServer-Thread").start();

            log("Starting server on port " + port + "...");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Error starting server: " + e.getMessage(),
                    "Server Error",
                    JOptionPane.ERROR_MESSAGE);
            serverRunning = false;
            statusLabel.setText("Status: Stopped");
            statusLabel.setForeground(Color.RED);
            startButton.setEnabled(true);
            shutdownButton.setEnabled(false);
        }
    }
    
    private void shutdownServer() {
        if (!serverRunning || server == null) {
            return;
        }
        log("Shutting down server...");
        statusLabel.setText("Status: Shutting down");
        statusLabel.setForeground(Color.ORANGE.darker());
        shutdownButton.setEnabled(false);
        server.shutdown();
        // The Start/Stop state is reset by the server thread's completion callback
        // in startServer(), so we don't touch it here.
        log("Server shutdown complete.");
    }
    
    public void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            // Auto-scroll to bottom
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
    
    public void clientConnected(String clientAddress) {
        SwingUtilities.invokeLater(() -> {
            connectionCount.incrementAndGet();
            connectionCountLabel.setText("Active Connections: " + connectionCount.get());
            
            if (!connectedClients.contains(clientAddress)) {
                connectedClients.add(clientAddress);
                clientListModel.addElement(clientAddress);
            }
            
            log("Client connected: " + clientAddress);
        });
    }
    
    public void clientDisconnected(String clientAddress) {
        SwingUtilities.invokeLater(() -> {
            int count = connectionCount.decrementAndGet();
            if (count < 0) { count = 0; connectionCount.set(0); }
            connectionCountLabel.setText("Active Connections: " + count);
            
            if (connectedClients.contains(clientAddress)) {
                connectedClients.remove(clientAddress);
                clientListModel.removeElement(clientAddress);
            }
            
            log("Client disconnected: " + clientAddress);
        });
    }
    
    public void requestProcessed(String clientAddress, String operation, String word) {
        SwingUtilities.invokeLater(() -> {
            int count = requestCount.incrementAndGet();
            requestCountLabel.setText("Total Requests: " + count);
            log(String.format("Request from %s: %s %s", clientAddress, operation, word));
        });
    }
    
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java DictionaryServerGUI <port> <dictionary-file>");
            System.exit(1);
        }

        try {
            int port = Integer.parseInt(args[0]);
            String dictionaryFile = args[1];
            
            SwingUtilities.invokeLater(() -> {
                DictionaryServerGUI gui = new DictionaryServerGUI(port, dictionaryFile);
                gui.setVisible(true);
                gui.log("Dictionary Server GUI ready. Click 'Start Server' to begin.");
                gui.log("Port: " + port);
                gui.log("Dictionary file: " + dictionaryFile);
            });
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number");
            System.exit(1);
        }
    }
}