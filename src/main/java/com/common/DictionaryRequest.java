/**
 * Jx2
 * 20/04/2026
 */

package com.common;

import java.io.Serializable;
import java.util.List;

public class DictionaryRequest implements Serializable {
    private DictionaryOperation operation;
    private String word;
    private List<String> meanings;
    private String existingMeaning;
    private String newMeaning;
    private int delay;

    // Default constructor for JSON deserialization
    public DictionaryRequest() {}

    public DictionaryRequest(DictionaryOperation operation, String word) {
        this.operation = operation;
        this.word = word;
    }

    // Getters and setters
    public DictionaryOperation getOperation() {
        return operation;
    }

    public void setOperation(DictionaryOperation operation) {
        this.operation = operation;
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public List<String> getMeanings() {
        return meanings;
    }

    public void setMeanings(List<String> meanings) {
        this.meanings = meanings;
    }

    public String getExistingMeaning() {
        return existingMeaning;
    }

    public void setExistingMeaning(String existingMeaning) {
        this.existingMeaning = existingMeaning;
    }

    public String getNewMeaning() {
        return newMeaning;
    }

    public void setNewMeaning(String newMeaning) {
        this.newMeaning = newMeaning;
    }

    public int getDelay() {
        return delay;
    }

    public void setDelay(int delay) {
        this.delay = delay;
    }
}