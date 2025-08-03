package com.android.example.myapplication.api;


import java.util.List;
public class ServerResponse {
    public List<Word> words;
    public String sentence;

    public static class Word {
        public String word;
        public float confidence;
    }
}
