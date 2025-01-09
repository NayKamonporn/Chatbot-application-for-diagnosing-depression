package com.example.depressioncs403;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class ChartActivity extends AppCompatActivity {

    private PieChart moodChart;
    private DatabaseReference chatDatabase;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chart);
        moodChart = findViewById(R.id.moodChart);
        Spinner spinnerFilter = findViewById(R.id.spinnerFilter);
        FirebaseAuth auth = FirebaseAuth.getInstance();
        userId = Objects.requireNonNull(auth.getCurrentUser()).getUid();

        chatDatabase = FirebaseDatabase.getInstance().getReference("calendar");

        spinnerFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedFilter = parent.getItemAtPosition(position).toString();
                fetchFilteredMoodData(selectedFilter);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                fetchFilteredMoodData("ALL");
            }
        });
    }

    private void fetchFilteredMoodData(String filter) {
        DatabaseReference userChatRef = chatDatabase.child(userId);

        userChatRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Map<String, Object> moodData = (Map<String, Object>) dataSnapshot.getValue();
                if (moodData != null) {
                    Map<String, Object> filteredMoodData = filterMoodDataByTimeRange(moodData, filter);
                    ArrayList<PieEntry> chartData = convertMoodDataToChartEntries(filteredMoodData);
                    updateChart(chartData);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(ChartActivity.this, "Failed to load mood data.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private Map<String, Object> filterMoodDataByTimeRange(Map<String, Object> moodData, String filter) {
        Map<String, Object> filteredData = new HashMap<>();
        long currentTime = System.currentTimeMillis();
        long filterTimeMillis = 0L;

        if (filter.equals("1 week")) {
            filterTimeMillis = currentTime - 7L * 24 * 60 * 60 * 1000;
        } else if (filter.equals("2 week")) {
            filterTimeMillis = currentTime - 14L * 24 * 60 * 60 * 1000;
        } else if (filter.equals("All")) {

            filterTimeMillis = Long.MIN_VALUE;
        }

        for (Map.Entry<String, Object> entry : moodData.entrySet()) {
            Map<String, Object> moodDetails = (Map<String, Object>) entry.getValue();
            if (moodDetails != null) {
                long timestamp = convertDateToTimestamp(entry.getKey());
                if (timestamp >= filterTimeMillis) {
                    filteredData.put(entry.getKey(), moodDetails);
                }
            }
        }

        return filteredData;
    }

    private ArrayList<PieEntry> convertMoodDataToChartEntries(Map<String, Object> moodData) {
        ArrayList<PieEntry> entries = new ArrayList<>();

        Map<String, Integer> moodMapping = new HashMap<>();
        moodMapping.put("Rad", 5);
        moodMapping.put("Happy", 4);
        moodMapping.put("Neutral", 3);
        moodMapping.put("Bad", 2);
        moodMapping.put("Awful", 1);


        Map<String, Integer> moodCount = new HashMap<>();
        for (Map.Entry<String, Object> entry : moodData.entrySet()) {
            Map<String, Object> moodDetails = (Map<String, Object>) entry.getValue();
            if (moodDetails != null) {
                String mood = (String) moodDetails.get("mood");
                moodCount.put(mood, moodCount.getOrDefault(mood, 0) + 1);
            }
        }

        String[] moodOrder = {"Rad", "Happy", "Neutral", "Bad", "Awful"};

        for (String mood : moodOrder) {
            int count = moodCount.getOrDefault(mood, 0);
            if (count != 0) {
                entries.add(new PieEntry(count, mood));
            }
        }

        return entries;
    }

    private void updateChart(ArrayList<PieEntry> chartData) {
        PieDataSet dataSet = new PieDataSet(chartData, "Mood Distribution");

        dataSet.setColors(getResources().getColor(R.color.rad_color),
                getResources().getColor(R.color.happy_color),
                getResources().getColor(R.color.netural_color),
                getResources().getColor(R.color.bad_color),
                getResources().getColor(R.color.awful_color));

        PieData pieData = new PieData(dataSet);
        pieData.setDrawValues(true);
        pieData.setValueTextSize(18f);
        pieData.setValueFormatter(new PercentFormatter());

        moodChart.setData(pieData);

        moodChart.setCenterText("MOOD");
        moodChart.setCenterTextSize(30f);
        moodChart.setCenterTextColor(getResources().getColor(R.color.gray));

        Typeface customFont = ResourcesCompat.getFont(this, R.font.poppins_semibold);
        moodChart.setCenterTextTypeface(customFont);


        moodChart.setUsePercentValues(true);

        moodChart.invalidate();
    }

    public static long convertDateToTimestamp(String dateString) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        try {
            Date date = dateFormat.parse(dateString);
            if (date != null) {
                return date.getTime();
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return -1;
    }
}