/**
 * Yunze Jiang
 * 20/04/2026
 */

package com.jayjiang.common;

import java.io.Serializable;
import java.util.List;

public class DictionaryResponse implements Serializable {
    private boolean success;
    private String message;
    private List<String> meanings;

    // Default constructor for JSON deserialization
    public DictionaryResponse() {}

    public DictionaryResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public DictionaryResponse(boolean success, String message, List<String> meanings) {
        this.success = success;
        this.message = message;
        this.meanings = meanings;
    }

    // Getters and setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getMeanings() {
        return meanings;
    }

    public void setMeanings(List<String> meanings) {
        this.meanings = meanings;
    }
} 