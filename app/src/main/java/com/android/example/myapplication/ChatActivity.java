package com.android.example.myapplication;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.example.myapplication.api.ApiService;
import com.android.example.myapplication.api.RetrofitClient;
import com.android.example.myapplication.api.ServerResponse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;

public class ChatActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> messages;
    private Button btnAttachVideo;
    private Uri videoUri;

    // Video capture launcher
    private final ActivityResultLauncher<Intent> videoCaptureLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            handleVideoResult(videoUri.toString());
                        }
                    }
            );

    // Gallery picker launcher
    private final ActivityResultLauncher<Intent> galleryLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                            Uri selectedVideo = result.getData().getData();
                            if (selectedVideo != null) {
                                handleVideoResult(selectedVideo.toString());
                            }
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        recyclerView = findViewById(R.id.recyclerView);
        btnAttachVideo = findViewById(R.id.btnAttachVideo);
        
        messages = new ArrayList<>();
        chatAdapter = new ChatAdapter(this, messages);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(chatAdapter);

        // Set up video button
        btnAttachVideo.setOnClickListener(v -> showVideoOptions());

        // Get video path from intent
        String videoPath = getIntent().getStringExtra("videoPath");
        if (videoPath != null) {
            handleVideoResult(videoPath);
        }
    }

    private void showVideoOptions() {
        // Show dialog to choose between camera and gallery
        String[] options = {"Record Video", "Choose from Gallery"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Select Video")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        recordVideo();
                    } else {
                        openGallery();
                    }
                })
                .show();
    }

    private void recordVideo() {
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            try {
                File videoFile = createVideoFile();
                if (videoFile != null) {
                    videoUri = FileProvider.getUriForFile(
                            getApplicationContext(),
                            getPackageName() + ".fileprovider",
                            videoFile
                    );
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, videoUri);
                    intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
                    videoCaptureLauncher.launch(intent);
                }
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to create video file", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(intent);
    }

    private File createVideoFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyMMdd_HHmm").format(new Date());
        String videoFileName = "SIGN_" + timeStamp;
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        return File.createTempFile(videoFileName, ".mp4", storageDir);
    }

    private void handleVideoResult(String videoPath) {
        // 1. 사용자 메시지로 영상 추가
        messages.add(new ChatMessage("Video sent", ChatMessage.TYPE_USER, videoPath));
        chatAdapter.notifyItemInserted(messages.size() - 1);
        recyclerView.scrollToPosition(messages.size() - 1);

        // 2. 영상 파일을 서버에 업로드
        Uri videoUri = Uri.parse(videoPath);
        File videoFile = getFileFromUri(videoUri);
        if (videoFile == null || !videoFile.exists()) {
            messages.add(new ChatMessage("영상 파일을 찾을 수 없습니다.", ChatMessage.TYPE_BOT));
            chatAdapter.notifyItemInserted(messages.size() - 1);
            return;
        }

        // 3. Retrofit 구성
        RequestBody requestFile = RequestBody.create(videoFile, okhttp3.MediaType.parse("video/mp4"));
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", videoFile.getName(), requestFile);

        ApiService apiService = RetrofitClient.getClient().create(ApiService.class);
        Call<ServerResponse> call = apiService.uploadVideo(body);

        // 4. 서버 응답 처리
        call.enqueue(new retrofit2.Callback<ServerResponse>() {
            @Override
            public void onResponse(Call<ServerResponse> call, retrofit2.Response<ServerResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String sentence = response.body().sentence;
                    messages.add(new ChatMessage(sentence, ChatMessage.TYPE_BOT));
                } else {
                    messages.add(new ChatMessage("서버 응답 실패", ChatMessage.TYPE_BOT));
                }
                chatAdapter.notifyItemInserted(messages.size() - 1);
                recyclerView.scrollToPosition(messages.size() - 1);
            }

            @Override
            public void onFailure(Call<ServerResponse> call, Throwable t) {
                messages.add(new ChatMessage("서버 연결 실패: " + t.getMessage(), ChatMessage.TYPE_BOT));
                chatAdapter.notifyItemInserted(messages.size() - 1);
                recyclerView.scrollToPosition(messages.size() - 1);
            }
        });
    }


    private String interpretSignLanguage(String videoPath) {
        // This is where you would integrate with your sign language interpretation API/model
        // For now, returning a placeholder response
        return "I detected the following sign language gestures:\n\n" +
               "\"Hello, how are you today?\"\n\n" +
               "The signs were clear and the interpretation confidence is high.";
    }

    private File getFileFromUri(Uri uri) {
        File file = null;
        try {
            String fileName = "uploaded_video.mp4";
            File tempFile = new File(getCacheDir(), fileName);
            try (InputStream inputStream = getContentResolver().openInputStream(uri);
                 OutputStream outputStream = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                file = tempFile;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return file;
    }


}