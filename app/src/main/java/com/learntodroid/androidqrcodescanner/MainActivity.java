package com.learntodroid.androidqrcodescanner;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.iot.cbor.CborMap;
import com.google.iot.cbor.CborParseException;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.concurrent.ExecutionException;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import COSE.CoseException;
import COSE.Encrypt0Message;
import COSE.Message;
import nl.minvws.encoding.Base45;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CAMERA = 0;

    private PreviewView previewView;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;

    private Button qrCodeFoundButton;
    private String qrCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();

        StrictMode.setThreadPolicy(policy);
        previewView = findViewById(R.id.activity_main_previewView);

        qrCodeFoundButton = findViewById(R.id.activity_main_qrCodeFoundButton);
        qrCodeFoundButton.setVisibility(View.INVISIBLE);
        qrCodeFoundButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getApplicationContext(), qrCode, Toast.LENGTH_SHORT).show();
                Log.i(MainActivity.class.getSimpleName(), "QR Code Found: " + qrCode);
            }
        });

        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        requestCamera();
    }

    private void requestCamera() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CAMERA);
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CAMERA);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CAMERA) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startCamera() {
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, "Error starting camera " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraPreview(@NonNull ProcessCameraProvider cameraProvider) {
        previewView.setPreferredImplementationMode(PreviewView.ImplementationMode.SURFACE_VIEW);

        Preview preview = new Preview.Builder()
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        preview.setSurfaceProvider(previewView.createSurfaceProvider());

        ImageAnalysis imageAnalysis =
                new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), new QRCodeImageAnalyzer(new QRCodeFoundListener() {
            private static final String TAG = "test";

            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onQRCodeFound(String _qrCode) {
                try {
                    final int BUFFER_SIZE = 1024;
                    qrCode = _qrCode;
                    qrCodeFoundButton.setVisibility(View.VISIBLE);
                    Log.i(TAG,"" + qrCode.toString());

                    if(qrCode.contains("HC1:")){
                        String qrText = _qrCode.toString();
                        String qrWithoutPrefix = qrText.substring(4);
                        byte[] bytecompressed = Base45.getDecoder().decode(qrWithoutPrefix);

                        Inflater inflater = new Inflater();
                        inflater.setInput(bytecompressed);
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(bytecompressed.length);
                        byte[] buffer = new byte[BUFFER_SIZE];
                        while (!inflater.finished()) {
                            int count = 0;
                            try {
                                count = inflater.inflate(buffer);
                            } catch (DataFormatException e) {
                                e.printStackTrace();
                            }
                            outputStream.write(buffer, 0, count);
                        }

                        Message a = null;
                        try {
                            a = Encrypt0Message.DecodeFromBytes(outputStream.toByteArray());
                        } catch (CoseException e) {
                            e.printStackTrace();
                        }
                        CborMap cborMap = null;
                        try {
                            cborMap = CborMap.createFromCborByteArray(a.GetContent());
                        } catch (CborParseException e) {
                            e.printStackTrace();
                        }

                        String jsonString = cborMap.toJsonString();
                        JSONObject jsonObj = new JSONObject(jsonString);

                        String vacDateString = jsonObj.getJSONObject("-260").getJSONObject("1").getJSONArray("v").getJSONObject(0).getString("dt");
                        LocalDate vacDate = LocalDate.parse(vacDateString);
                        LocalDate localDateNow = LocalDate.now();
                        long daysDiff = localDateNow.toEpochDay() - vacDate.toEpochDay();

                        if (daysDiff <= 183)
                        {
                            System.out.println("Vaccinated");
                            QrScanClient qrScanClient = new QrScanClient(true);
                            qrScanClient.doInBackground();

                        }else
                        {
                            System.out.println("Not Vaccinated");
                            QrScanClient qrScanClient = new QrScanClient(false);
                            qrScanClient.doInBackground();
                        }
                    }else{
                        QrScanClient qrScanClient = new QrScanClient(false);
                        qrScanClient.doInBackground();
                    }

                }catch (Exception e){
                    e.printStackTrace();
                }

            }

            @Override
            public void qrCodeNotFound() {
                qrCodeFoundButton.setVisibility(View.INVISIBLE);
            }
        }));

        Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, imageAnalysis, preview);
    }
}

