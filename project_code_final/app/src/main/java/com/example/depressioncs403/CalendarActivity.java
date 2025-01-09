package com.example.depressioncs403;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class CalendarActivity extends AppCompatActivity implements CalendarAdapter.OnItemListener {
    private TextView monthYearText;
    private RecyclerView calendarRecyclerView;
    private LocalDate selectedDate;
    private DatabaseReference chatDatabase;
    private CalendarAdapter calendarAdapter;
    String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);
        initFirebase();
        initWidgets();
        selectedDate = LocalDate.now();
        setMonthView();
        fetchMoodsFromFirebase();
    }

    private void initFirebase() {
        chatDatabase = FirebaseDatabase.getInstance().getReference("calendar");
        FirebaseAuth auth = FirebaseAuth.getInstance();
        userId = Objects.requireNonNull(auth.getCurrentUser()).getUid();
    }

    private void initWidgets() {
        calendarRecyclerView = findViewById(R.id.calendarRecyclerView);
        monthYearText = findViewById(R.id.monthYearTV);
    }

    private void setMonthView() {
        monthYearText.setText(monthYearFromDate(selectedDate));
        ArrayList<String> daysInMonth = daysInMonthArray(selectedDate);

        calendarAdapter = new CalendarAdapter(daysInMonth, this);
        RecyclerView.LayoutManager layoutManager = new GridLayoutManager(getApplicationContext(), 7);
        calendarRecyclerView.setLayoutManager(layoutManager);
        calendarRecyclerView.setAdapter(calendarAdapter);
        fetchMoodsFromFirebase();
    }

    private ArrayList<String> daysInMonthArray(LocalDate date) {
        ArrayList<String> daysInMonthArray = new ArrayList<>();
        YearMonth yearMonth = YearMonth.from(date);

        int daysInMonth = yearMonth.lengthOfMonth();

        LocalDate firstOfMonth = selectedDate.withDayOfMonth(1);
        int dayOfWeek = firstOfMonth.getDayOfWeek().getValue();

        for (int i = 1; i <= 42; i++) {
            if (i <= dayOfWeek || i > daysInMonth + dayOfWeek) {
                daysInMonthArray.add("");
            } else {
                daysInMonthArray.add(String.valueOf(i - dayOfWeek));
            }
        }
        return daysInMonthArray;
    }

    private String monthYearFromDate(LocalDate date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM yyyy");
        return date.format(formatter);
    }

    public void previousMonthAction(View view) {
        selectedDate = selectedDate.minusMonths(1);
        setMonthView();
    }

    public void nextMonthAction(View view) {
        selectedDate = selectedDate.plusMonths(1);
        setMonthView();
    }

    @Override
    public void onItemClick(int position, String dayText) {
        if (!dayText.equals("")) {
            String message = "Selected Date " + dayText + " " + monthYearFromDate(selectedDate);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();

            LocalDate date = selectedDate.withDayOfMonth(Integer.parseInt(dayText));
            if (isFutureDate(date)) {
                Toast.makeText(this, "ไม่สามารถเลือกวันที่ในอนาคตได้!", Toast.LENGTH_SHORT).show();
            } else {

                openEmojiPicker(date);
            }
        }
    }

    private boolean isFutureDate(LocalDate date) {
        return date.isAfter(LocalDate.now());
    }

    private void openEmojiPicker(LocalDate date) {

        int[] imageResIds = {R.drawable.rad, R.drawable.happy, R.drawable.neutural, R.drawable.bad, R.drawable.awful};
        String[] imageNames = {"Rad", "Happy", "Neutral", "Bad", "Awful"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select your mood for " + date.toString());
        builder.setItems(imageNames, (dialog, which) -> {
            int selectedImageResId = imageResIds[which];
            String selectedMood = imageNames[which];
            Toast.makeText(this, "You selected: " + imageNames[which], Toast.LENGTH_SHORT).show();

            saveMoodToFirebase(date, selectedMood, selectedImageResId);

            String day = String.valueOf(date.getDayOfMonth());
            CalendarAdapter adapter = (CalendarAdapter) calendarRecyclerView.getAdapter();
            if (adapter != null) {
                adapter.setImage(day, selectedImageResId);
            }
        });
        builder.show();
    }

    private void saveMoodToFirebase(LocalDate date, String mood, int imageResId) {

        DatabaseReference userChatRef = chatDatabase.child(userId);


        String formattedDate = date.toString();
        Map<String, Object> moodData = new HashMap<>();
        moodData.put("mood", mood);
        moodData.put("imageResId", imageResId);

        userChatRef.child(formattedDate).setValue(moodData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Mood saved successfully!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to save mood data", Toast.LENGTH_SHORT).show();
                });
    }

    private void fetchMoodsFromFirebase() {
        DatabaseReference userChatRef = chatDatabase.child(userId);
        userChatRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                HashMap<String, Integer> moodMap = new HashMap<>();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    String dateKey = snapshot.getKey();
                    if (snapshot.child("imageResId").exists()) {
                        Integer imageResId = snapshot.child("imageResId").getValue(Integer.class);
                        moodMap.put(dateKey, imageResId);
                    }
                }
                updateAdapterWithMoods(moodMap);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(CalendarActivity.this, "Failed to fetch mood data", Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void updateAdapterWithMoods(HashMap<String, Integer> moodMap) {

        int selectedMonth = selectedDate.getMonthValue();
        int selectedYear = selectedDate.getYear();


        if (calendarAdapter != null) {
            for (Map.Entry<String, Integer> entry : moodMap.entrySet()) {
                String dateKey = entry.getKey();
                Integer imageResId = entry.getValue();

                String[] dateParts = dateKey.split("-");
                int fetchedYear = Integer.parseInt(dateParts[0]);
                int fetchedMonth = Integer.parseInt(dateParts[1]);

                if (fetchedYear == selectedYear && fetchedMonth == selectedMonth) {

                    String day = dateParts[2];

                    calendarAdapter.setImage(day, imageResId);
                }
            }
        }
    }
}
