package com.example.health;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HeartRateHistoryActivity extends AppCompatActivity {

    private LineChart lineChart;
    private ProgressBar progressBar;
    private TextView errorText;
    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private String pairingCode;
    private String userEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_heart_rate_history);

        lineChart = findViewById(R.id.heartRateChart);
        progressBar = findViewById(R.id.progressBar);
        errorText = findViewById(R.id.errorText);

        pairingCode = getIntent().getStringExtra("PAIRING_CODE");
        userEmail = getIntent().getStringExtra("USER_EMAIL");

        if (pairingCode == null || pairingCode.trim().isEmpty() || userEmail == null || userEmail.trim().isEmpty()) {
            showError("Missing required user data");
            return;
        }

        Log.d("HeartRateHistory", "Loading data for email: " + userEmail + " with pairing code: " + pairingCode);

        setupChart();
        loadHeartRateData();
    }

    private void setupChart() {
        lineChart.setTouchEnabled(true);
        lineChart.setPinchZoom(true);
        lineChart.getDescription().setEnabled(false);
        lineChart.getLegend().setEnabled(true);

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setLabelRotationAngle(-45f);

        lineChart.getAxisLeft().setAxisMinimum(40f);
        lineChart.getAxisLeft().setAxisMaximum(160f);
        lineChart.getAxisLeft().setDrawGridLines(true);
        lineChart.getAxisRight().setEnabled(false);
    }

    private void loadHeartRateData() {
        showLoading();

        db.collection("heart_rate_readings")
                .whereEqualTo("patientEmail", userEmail)
                .whereEqualTo("pairingCode", pairingCode)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(documents -> {
                    Log.d("HeartRateHistory", "Successfully loaded " + documents.size() + " documents");

                    if (documents.isEmpty()) {
                        showError("No heart rate data available for this user");
                        return;
                    }

                    List<Entry> entries = new ArrayList<>();
                    List<String> dates = new ArrayList<>();
                    SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd HH:mm", Locale.getDefault());

                    int index = 0;
                    for (DocumentSnapshot document : documents) {
                        try {
                            Timestamp timestamp = document.getTimestamp("timestamp");
                            Double heartRateDouble = document.getDouble("averageHeartRate");
                            Long heartRateLong = document.getLong("averageHeartRate");

                            Float heartRate = heartRateDouble != null ? heartRateDouble.floatValue() :
                                    (heartRateLong != null ? heartRateLong.floatValue() : null);

                            if (timestamp != null && heartRate != null) {
                                Date date = timestamp.toDate();
                                entries.add(new Entry(index, heartRate));
                                dates.add(dateFormat.format(date));
                                index++;
                            } else {
                                Log.w("HeartRateHistory", "Document " + document.getId() + " has missing or invalid data");
                            }
                        } catch (Exception e) {
                            Log.e("HeartRateHistory", "Error processing document " + document.getId(), e);
                        }
                    }

                    if (!entries.isEmpty()) {
                        showChart();
                        updateChart(entries, dates);
                    } else {
                        showError("No valid heart rate data found");
                    }
                })
                .addOnFailureListener(exception -> {
                    Log.e("HeartRateHistory", "Error loading data", exception);
                    showError("Failed to load data: " + exception.getMessage());

                    if (exception.getMessage() != null && exception.getMessage().contains("index")) {
                        showError("Firestore index required. Please create this index:\n" +
                                "Collection: heart_rate_readings\nFields: \n1. patientEmail (ASC)\n2. pairingCode (ASC)\n3. timestamp (ASC)");
                    }
                });
    }

    private void updateChart(List<Entry> entries, List<String> dates) {
        LineDataSet dataSet = new LineDataSet(entries, "Heart Rate (BPM)");
        dataSet.setColor(Color.RED);
        dataSet.setCircleColor(Color.RED);
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawCircleHole(false);
        dataSet.setValueTextSize(10f);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        lineChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(dates));
        lineChart.setData(new LineData(dataSet));
        lineChart.invalidate();
        lineChart.fitScreen();
    }

    private void showLoading() {
        progressBar.setVisibility(View.VISIBLE);
        lineChart.setVisibility(View.GONE);
        errorText.setVisibility(View.GONE);
    }

    private void showChart() {
        progressBar.setVisibility(View.GONE);
        lineChart.setVisibility(View.VISIBLE);
        errorText.setVisibility(View.GONE);
    }

    private void showError(String message) {
        progressBar.setVisibility(View.GONE);
        lineChart.setVisibility(View.GONE);
        errorText.setVisibility(View.VISIBLE);
        errorText.setText(message);
    }
}
