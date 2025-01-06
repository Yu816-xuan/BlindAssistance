package com.example.blindassistance;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private static final int REQUEST_CAMERA_PERMISSION = 1001;
    private static final int REQUEST_LOCATION_PERMISSION = 1002;

    private PreviewView previewView;
    private TextToSpeech tts;
    private EditText editText;
    private Button buttonSpeak;
    private FusedLocationProviderClient fusedLocationClient;
    private TextView locationTextView;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        editText = findViewById(R.id.editText);
        buttonSpeak = findViewById(R.id.buttonSpeak);
        locationTextView = findViewById(R.id.locationTextView);

        // 初始化 SharedPreferences
        sharedPreferences = getSharedPreferences("AppPrefs", MODE_PRIVATE);

        // 初始化 TextToSpeech
        tts = new TextToSpeech(this, this);

        // 初始化位置服务
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // 检查并请求权限
        checkPermissions();

        // 按钮点击事件，朗读输入的文本
        buttonSpeak.setOnClickListener(v -> {
            String text = editText.getText().toString();
            speakOut(text);
        });
    }

    private void checkPermissions() {
        // 如果之前已经授予权限，直接启动相机和获取位置
        boolean cameraPermissionGranted = sharedPreferences.getBoolean("cameraPermissionGranted", false);
        boolean locationPermissionGranted = sharedPreferences.getBoolean("locationPermissionGranted", false);

        if (cameraPermissionGranted && locationPermissionGranted) {
            startCamera();
            getLastLocation();
        } else {
            // 请求权限
            if (!cameraPermissionGranted) {
                requestCameraPermission();
            }
            if (!locationPermissionGranted) {
                requestLocationPermission();
            }
        }
    }

    // 请求相机权限
    private void requestCameraPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            // 显示权限请求解释
            new AlertDialog.Builder(this)
                    .setMessage("This app needs camera permission to capture images.")
                    .setPositiveButton("OK", (dialog, which) -> ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION))
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            // 直接请求相机权限
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    // 请求位置权限
    private void requestLocationPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            // 显示权限请求解释
            new AlertDialog.Builder(this)
                    .setMessage("This app needs location permission to get your current location.")
                    .setPositiveButton("OK", (dialog, which) -> ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION))
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            // 直接请求位置权限
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
        }
    }

    // 处理权限请求的结果
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 相机权限授予，保存权限状态
                sharedPreferences.edit().putBoolean("cameraPermissionGranted", true).apply();
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 位置权限授予，保存权限状态
                sharedPreferences.edit().putBoolean("locationPermissionGranted", true).apply();
                getLastLocation();
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 启动相机
    private void startCamera() {
        // 获取 CameraProvider 实例
        ProcessCameraProvider cameraProvider = null;
        try {
            cameraProvider = ProcessCameraProvider.getInstance(this).get();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        // 创建预览用例
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // 选择后置摄像头
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK) // 使用后置摄像头
                .build();

        // 绑定生命周期和用例
        cameraProvider.unbindAll(); // 在绑定新用例之前先解绑所有用例
        cameraProvider.bindToLifecycle(this, cameraSelector, preview); // 绑定到生命周期（生命周期所有者）
    }

    // 获取当前位置
    private void getLastLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.getLastLocation().addOnCompleteListener(this, task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                Location location = task.getResult();
                getAddressFromLocation(location.getLatitude(), location.getLongitude());
            } else {
                locationTextView.setText("Unable to get last known location.");
            }
        });
    }

    // 获取地址
    private void getAddressFromLocation(double latitude, double longitude) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                StringBuilder sb = new StringBuilder();
                if (address.getMaxAddressLineIndex() >= 0) {
                    sb.append(address.getAddressLine(0)).append("\n");
                }
                if (address.getLocality() != null) {
                    sb.append(address.getLocality()).append(", ");
                }
                if (address.getAdminArea() != null) {
                    sb.append(address.getAdminArea()).append(", ");
                }
                if (address.getPostalCode() != null) {
                    sb.append(address.getPostalCode()).append(", ");
                }
                if (address.getCountryName() != null) {
                    sb.append(address.getCountryName());
                }
                locationTextView.setText(sb.toString());
            } else {
                locationTextView.setText("No address found.");
            }
        } catch (IOException e) {
            e.printStackTrace();
            locationTextView.setText("Error getting address.");
        }
    }

    // 初始化 TextToSpeech
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.US);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "This language is not supported.");
            } else {
                buttonSpeak.setEnabled(true);
            }
        } else {
            Log.e("TTS", "Initialization failed.");
        }
    }

    // 朗读文本
    private void speakOut(String text) {
        if (tts != null && !tts.isSpeaking()) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
