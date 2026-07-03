/**
 * Jx2
 * 20/04/2026
 */

package com.server;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Dictionary {
    private final ConcurrentHashMap<String, List<String>> dictionary;
    private final String dictionaryFile;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public Dictionary(String dictionaryFile) throws IOException {
        this.dictionaryFile = dictionaryFile;
        this.dictionary = new ConcurrentHashMap<>();
        loadDictionary();
    }

    private void loadDictionary() throws IOException {
        File file = new File(dictionaryFile);
        if (!file.exists()) {
            file.createNewFile();
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    String word = parts[0].trim();
                    String[] meanings = parts[1].split(";");
                    List<String> meaningsList = new ArrayList<>();
                    for (String meaning : meanings) {
                        meaningsList.add(meaning.trim());
                    }
                    dictionary.put(word, meaningsList);
                }
            }
        }
    }

    private void saveDictionary() throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(dictionaryFile))) {
            for (Map.Entry<String, List<String>> entry : dictionary.entrySet()) {
                writer.write(entry.getKey() + ": " + String.join("; ", entry.getValue()));
                writer.newLine();
            }
        }
    }

    /**
     * Sleep inside the held lock to simulate work done under the critical section.
     * concurrent reads, serialised writes, the delay happens WHILE the lock is held.
     */
    private void simulateWork(int delayMs) throws InterruptedException {
        if (delayMs > 0) {
            Thread.sleep(delayMs);
        }
    }

    public List<String> query(String word, int delayMs) {
        lock.readLock().lock();
        try {
            simulateWork(delayMs);
            List<String> meanings = dictionary.get(word.toLowerCase());
            return meanings == null ? null : new ArrayList<>(meanings);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean addWord(String word, List<String> meanings, int delayMs) throws IOException {
        lock.writeLock().lock();
        try {
            simulateWork(delayMs);
            word = word.toLowerCase();
            if (dictionary.containsKey(word)) {
                return false;
            }
            dictionary.put(word, new ArrayList<>(meanings));
            saveDictionary();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean removeWord(String word, int delayMs) throws IOException {
        lock.writeLock().lock();
        try {
            simulateWork(delayMs);
            word = word.toLowerCase();
            if (dictionary.remove(word) != null) {
                saveDictionary();
                return true;
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean addMeaning(String word, String meaning, int delayMs) throws IOException {
        lock.writeLock().lock();
        try {
            simulateWork(delayMs);
            word = word.toLowerCase();
            List<String> meanings = dictionary.get(word);
            if (meanings == null) {
                return false;
            }
            if (!meanings.contains(meaning)) {
                meanings.add(meaning);
                saveDictionary();
                return true;
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean updateMeaning(String word, String existingMeaning, String newMeaning, int delayMs) throws IOException {
        lock.writeLock().lock();
        try {
            simulateWork(delayMs);
            word = word.toLowerCase();
            List<String> meanings = dictionary.get(word);
            if (meanings == null) {
                return false;
            }
            boolean updated = false;
            for (int i = 0; i < meanings.size(); i++) {
                if (meanings.get(i).equals(existingMeaning)) {
                    meanings.set(i, newMeaning);
                    updated = true;
                }
            }
            if (updated) {
                saveDictionary();
            }
            return updated;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
}