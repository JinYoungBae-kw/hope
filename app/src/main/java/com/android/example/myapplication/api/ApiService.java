package com.android.example.myapplication.api;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.Map;

public interface ApiService {
    @Multipart
    @POST("/process_video")
    Call<ServerResponse> uploadVideo(
            @Part MultipartBody.Part video
    );
}
