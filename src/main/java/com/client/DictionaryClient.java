/**
 * Jx2
 * 20/04/2026
 */

package com.client;

import com.common.DictionaryOperation;
import com.common.DictionaryRequest;
import com.common.DictionaryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class DictionaryClient extends JFrame {
    private final String serverAddress;
    private final int serverPort;
    private final int delay;
    private final ObjectMapper objectMapper;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private final int MAX_RETRY_ATTEMPTS = 5;
    private final int RETRY_DELAY_MS = 2000; // 2 seconds
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1);
    private JLabel connectionStatusLabel;

    // GUI Components
    private JTextField wordField;
    private JTextArea meaningArea;
    private JTextArea resultArea;
    private JButton queryButton;
    private JButton addWordButton;
    private JButton removeWordButton;
    private JButton addMeaningButton;
    private JButton updateMeaningButton;
    private JButton reconnectButton;

    public DictionaryClient(String serverAddress, int serverPort, int delay) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.delay = delay;
        this.objectMapper = new ObjectMapper();

        setupGUI();
        connectToServerWithRetry();
    }

    private void setupGUI() {
        setTitle("Dictionary Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // Input Panel
        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        // Word input
        gbc.gridx = 0;
        gbc.gridy = 0;
        inputPanel.add(new JLabel("Word:"), gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        wordField = new JTextField(20);
        inputPanel.add(wordField, gbc);

        // Meaning input
        gbc.gridx = 0;
        gbc.gridy = 1;
        inputPanel.add(new JLabel("Meaning:"), gbc);

        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        meaningArea = new JTextArea(3, 20);
        meaningArea.setLineWrap(true);
        inputPanel.add(new JScrollPane(meaningArea), gbc);

        // Buttons Panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        queryButton = new JButton("Query");
        addWordButton = new JButton("Add Word");
        removeWordButton = new JButton("Remove Word");
        addMeaningButton = new JButton("Add Meaning");
        updateMeaningButton = new JButton("Update Meaning");

        buttonPanel.add(queryButton);
        buttonPanel.add(addWordButton);
        buttonPanel.add(removeWordButton);
        buttonPanel.add(addMeaningButton);
        buttonPanel.add(updateMeaningButton);

        // Connection status and reconnect
        JPanel connectionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        connectionStatusLabel = new JLabel("Disconnected");
        connectionStatusLabel.setForeground(Color.RED);
        reconnectButton = new JButton("Reconnect");
        reconnectButton.addActionListener(e -> connectToServerWithRetry());
        reconnectButton.setEnabled(false);
        connectionPanel.add(connectionStatusLabel);
        connectionPanel.add(reconnectButton);

        // Result Area
        resultArea = new JTextArea(10, 40);
        resultArea.setEditable(false);
        resultArea.setLineWrap(true);

        // Add components to frame
        add(inputPanel, BorderLayout.NORTH);
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(buttonPanel, BorderLayout.CENTER);
        centerPanel.add(connectionPanel, BorderLayout.SOUTH);
        add(centerPanel, BorderLayout.CENTER);
        add(new JScrollPane(resultArea), BorderLayout.SOUTH);

        // Add button listeners
        setupButtonListeners();

        pack();
        setLocationRelativeTo(null);
    }

    private void setupButtonListeners() {
        queryButton.addActionListener(e -> {
            String word = wordField.getText().trim();
            if (word.isEmpty()) {
                showError("Please enter a word to query");
                return;
            }
            sendRequest(new DictionaryRequest(DictionaryOperation.QUERY, word));
        });

        addWordButton.addActionListener(e -> {
            String word = wordField.getText().trim();
            String meaningText = meaningArea.getText().trim();
            if (word.isEmpty() || meaningText.isEmpty()) {
                showError("Please enter both word and meaning");
                return;
            }
            DictionaryRequest request = new DictionaryRequest(DictionaryOperation.ADD_WORD, word);
            request.setMeanings(Arrays.asList(meaningText.split("\n")));
            sendRequest(request);
        });

        removeWordButton.addActionListener(e -> {
            String word = wordField.getText().trim();
            if (word.isEmpty()) {
                showError("Please enter a word to remove");
                return;
            }
            sendRequest(new DictionaryRequest(DictionaryOperation.REMOVE_WORD, word));
        });

        addMeaningButton.addActionListener(e -> {
            String word = wordField.getText().trim();
            String meaning = meaningArea.getText().trim();
            if (word.isEmpty() || meaning.isEmpty()) {
                showError("Please enter both word and meaning");
                return;
            }
            DictionaryRequest request = new DictionaryRequest(DictionaryOperation.ADD_MEANING, word);
            request.setNewMeaning(meaning);
            sendRequest(request);
        });

        updateMeaningButton.addActionListener(e -> {
            String word = wordField.getText().trim();
            String[] meanings = meaningArea.getText().trim().split("\n");
            if (word.isEmpty() || meanings.length != 2) {
                showError("Please enter word and both old and new meanings (on separate lines)");
                return;
            }
            DictionaryRequest request = new DictionaryRequest(DictionaryOperation.UPDATE_MEANING, word);
            request.setExistingMeaning(meanings[0]);
            request.setNewMeaning(meanings[1]);
            sendRequest(request);
        });
    }
    
    private void updateConnectionStatus(boolean connected) {
        SwingUtilities.invokeLater(() -> {
            if (connected) {
                connectionStatusLabel.setText("Connected");
                connectionStatusLabel.setForeground(Color.GREEN.darker());
                reconnectButton.setEnabled(false);
                setButtonsEnabled(true);
            } else {
                connectionStatusLabel.setText("Disconnected");
                connectionStatusLabel.setForeground(Color.RED);
                reconnectButton.setEnabled(!isConnecting.get());
                setButtonsEnabled(false);
            }
        });
    }
    
    private void setButtonsEnabled(boolean enabled) {
        queryButton.setEnabled(enabled);
        addWordButton.setEnabled(enabled);
        removeWordButton.setEnabled(enabled);
        addMeaningButton.setEnabled(enabled);
        updateMeaningButton.setEnabled(enabled);
    }

    private void connectToServerWithRetry() {
        if (isConnecting.compareAndSet(false, true)) {
            updateConnectionStatus(false);
            closeConnection();
            
            resultArea.append("Connecting to server at " + serverAddress + ":" + serverPort + "...\n");
            
            new Thread(() -> {
                int attempts = 0;
                boolean connected = false;
                
                while (!connected && attempts < MAX_RETRY_ATTEMPTS) {
                    attempts++;
                    
                    try {
                        socket = new Socket(serverAddress, serverPort);
                        out = new PrintWriter(socket.getOutputStream(), true);
                        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        
                        connected = true;
                        
                        SwingUtilities.invokeLater(() -> {
                            resultArea.append("Connected to server at " + serverAddress + ":" + serverPort + "\n");
                            resultArea.append("------------------------\n");
                            updateConnectionStatus(true);
                        });
                        
                        // Start a heartbeat thread to detect disconnections
                        startHeartbeat();
                        
                    } catch (IOException e) {
                        String errorMsg = "Connection attempt " + attempts + " failed: " + e.getMessage();
                        final int currentAttempt = attempts;
                        
                        SwingUtilities.invokeLater(() -> {
                            resultArea.append(errorMsg + "\n");
                            if (currentAttempt < MAX_RETRY_ATTEMPTS) {
                                resultArea.append("Retrying in " + (RETRY_DELAY_MS / 1000) + " seconds...\n");
                            } else {
                                resultArea.append("Maximum retry attempts reached. Please try reconnecting manually.\n");
                                resultArea.append("------------------------\n");
                            }
                        });
                        
                        // Wait before retrying
                        try {
                            Thread.sleep(RETRY_DELAY_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
                
                isConnecting.set(false);
                
                if (!connected) {
                    SwingUtilities.invokeLater(() -> {
                        updateConnectionStatus(false);
                    });
                }
            }).start();
        }
    }
    
    private void startHeartbeat() {
        scheduler.scheduleAtFixedRate(() -> {
            if (socket != null && !socket.isClosed()) {
                try {
                    // Send a ping to check if connection is still alive
                    socket.getOutputStream().write(0);
                } catch (IOException e) {
                    // Connection lost, try to reconnect
                    SwingUtilities.invokeLater(() -> {
                        resultArea.append("Connection to server lost. Attempting to reconnect...\n");
                        updateConnectionStatus(false);
                        connectToServerWithRetry();
                    });
                    // Cancel the current heartbeat
                    throw new RuntimeException("Connection lost");
                }
            }
        }, 5, 5, TimeUnit.SECONDS);
    }
    
    private void closeConnection() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Ignore
        }
    }

    private void sendRequest(DictionaryRequest request) {
        if (socket == null || socket.isClosed() || !socket.isConnected()) {
            showError("Not connected to server. Please reconnect.");
            updateConnectionStatus(false);
            return;
        }
        
        try {
            request.setDelay(delay);
            String jsonRequest = objectMapper.writeValueAsString(request);
            out.println(jsonRequest);

            String jsonResponse = in.readLine();
            if (jsonResponse == null) {
                throw new IOException("Server closed the connection");
            }
            
            DictionaryResponse response = objectMapper.readValue(jsonResponse, DictionaryResponse.class);

            if (response.isSuccess()) {
                if (response.getMeanings() != null) {
                    resultArea.append("Success: " + response.getMessage() + "\n");
                    resultArea.append("Meanings:\n");
                    for (String meaning : response.getMeanings()) {
                        resultArea.append("- " + meaning + "\n");
                    }
                } else {
                    resultArea.append("Success: " + response.getMessage() + "\n");
                }
            } else {
                resultArea.append("Error: " + response.getMessage() + "\n");
            }
            resultArea.append("------------------------\n");
        } catch (IOException e) {
            resultArea.append("Communication error: " + e.getMessage() + "\n");
            resultArea.append("Attempting to reconnect...\n");
            resultArea.append("------------------------\n");
            updateConnectionStatus(false);
            connectToServerWithRetry();
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }
    
    @Override
    public void dispose() {
        scheduler.shutdownNow();
        closeConnection();
        super.dispose();
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: java DictionaryClient <server-address> <server-port> <sleep-duration>");
            System.exit(1);
        }

        try {
            String serverAddress = args[0];
            int serverPort = Integer.parseInt(args[1]);
            int delay = Integer.parseInt(args[2]);

            SwingUtilities.invokeLater(() -> {
                DictionaryClient client = new DictionaryClient(serverAddress, serverPort, delay);
                client.setVisible(true);
            });
        } catch (NumberFormatException e) {
            System.err.println("Invalid numeric argument");
            System.exit(1);
        }
    }
}