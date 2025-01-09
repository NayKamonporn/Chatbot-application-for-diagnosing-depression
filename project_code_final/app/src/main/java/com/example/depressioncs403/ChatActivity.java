package com.example.depressioncs403;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ChatActivity extends AppCompatActivity {
    RecyclerView recyclerView;
    TextView welcomeTextView;
    EditText messageEditText;
    ImageButton sendButton;
    List<Message> messageList;
    MessageAdapter messageAdapter;
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    OkHttpClient client = new OkHttpClient();
    String accessToken, userId, question, sessionId;
    private DatabaseReference chatDatabase;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        initFirebase();
        fetchChatHistory();
        sessionId = UUID.randomUUID().toString();
        messageList = new ArrayList<>();
        recyclerView = findViewById(R.id.recycler_view);
        welcomeTextView = findViewById(R.id.welcome_text);
        messageEditText = findViewById(R.id.message_edit_text);
        sendButton = findViewById(R.id.send_btn);

        messageAdapter = new MessageAdapter(messageList);
        recyclerView.setAdapter(messageAdapter);
        LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setStackFromEnd(true);
        recyclerView.setLayoutManager(llm);

        sendButton.setOnClickListener((v) -> {
            String question = messageEditText.getText().toString().trim();
            if (!question.isEmpty()) {
                addToChat(question, Message.SENT_BY_ME);
                messageEditText.setText("");
                callAPI(question);  // เชื่อมต่อ Dialogflow API
                welcomeTextView.setVisibility(View.GONE);
            }
        });
    }

    private void initFirebase() {
        // Initialize Firebase Database
        chatDatabase = FirebaseDatabase.getInstance().getReference("chat_history");
        FirebaseAuth auth = FirebaseAuth.getInstance();
        userId = Objects.requireNonNull(auth.getCurrentUser()).getUid();
    }

    private void addToChat(String message, String sentBy) {
        runOnUiThread(() -> {
            Long timeStamp = System.currentTimeMillis();
            messageList.add(new Message(message, sentBy, timeStamp));
            messageAdapter.notifyDataSetChanged();
            recyclerView.smoothScrollToPosition(messageAdapter.getItemCount());
            // Save message to Firebase
            saveMessageToFirebase(message, sentBy, timeStamp);
        });
    }

    private void saveMessageToFirebase(String message, String sentBy, Long timeStamp) {
        DatabaseReference userChatRef = chatDatabase.child(userId);
        String key = userChatRef.push().getKey(); // Generate a unique key for each message
        if (key != null) {
            HashMap<String, Object> chatMessage = new HashMap<>();
            chatMessage.put("message", message);
            chatMessage.put("sentBy", sentBy);
            chatMessage.put("timestamp", timeStamp);

            chatDatabase.child(userId).child(key).setValue(chatMessage).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Log.d("Firebase", "Message saved successfully.");
                } else {
                    Log.e("Firebase", "Failed to save message: ", task.getException());
                }
            });
        }
    }

    private void addResponse(String response) {
        runOnUiThread(() -> {
            messageList.remove(messageList.size() - 1);  // Remove "Typing..." message
            addToChat(response, Message.SENT_BY_BOT);
        });
    }

    private void callAPI(String question) {
        messageList.add(new Message("Typing...", Message.SENT_BY_BOT, System.currentTimeMillis()));
        this.question = question;
        getAccessToken();
    }

    private void fetchChatHistory() {
        if (chatDatabase != null) {
            DatabaseReference userChatRef = chatDatabase.child(userId);
            userChatRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    messageList.clear(); // Clear the existing messages
                    for (DataSnapshot messageSnapshot : snapshot.getChildren()) {
                        Message message = messageSnapshot.getValue(Message.class);
                        if (message != null) {
                            messageList.add(message);
                        }
                    }

                    messageAdapter.notifyDataSetChanged();
                    recyclerView.smoothScrollToPosition(messageAdapter.getItemCount());
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e("FirebaseDB", "Error fetching chat history: " + error.getMessage());
                }
            });
        }
    }

    private void callNewCallApi() {

        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("queryInput", new JSONObject()
                    .put("text", new JSONObject()
                            .put("text", question)
                            .put("languageCode", "th")));
        } catch (JSONException e) {
            e.printStackTrace();
            addResponse("Error creating JSON body: " + e.getMessage());
            return;
        }

        RequestBody body = RequestBody.create(jsonBody.toString(), JSON);

        String projectId = "chatbot-depression-vdnh";  // Project ID
        String url = "https://dialogflow.googleapis.com/v2/projects/" + projectId + "/agent/sessions/" + sessionId + ":detectIntent";

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + accessToken)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("DialogflowAPI", "API call failed: " + e.getMessage());
                addResponse("Failed to load response: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try (ResponseBody responseBody = response.body()) {
                        if (responseBody != null) {
                            JSONObject jsonObject = new JSONObject(responseBody.string());

                            JSONObject queryResult = jsonObject.getJSONObject("queryResult");

                            JSONArray fulfillmentMessages = queryResult.getJSONArray("fulfillmentMessages");

                            for (int i = 0; i < fulfillmentMessages.length(); i++) {

                                JSONObject messageObject = fulfillmentMessages.getJSONObject(i);

                                JSONObject textObject = messageObject.getJSONObject("text");

                                JSONArray textArray = textObject.getJSONArray("text");

                                String text = textArray.getString(0);

                                addResponse(text.trim());
                            }
                        } else {
                            addResponse("Empty response from server.");
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
                    Log.e("DialogflowAPI", "Response failed: " + response.message());
                    addResponse("Failed to load response: " + response.message());
                }
            }
        });
    }

    private void getAccessToken() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    InputStream serviceAccountStream = getAssets().open("chatbot-depression-vdnh-d95b0243a859.json");


                    GoogleCredentials credentials = GoogleCredentials.fromStream(serviceAccountStream)
                            .createScoped("https://www.googleapis.com/auth/cloud-platform");

                    credentials.refreshIfExpired();

                    accessToken = credentials.getAccessToken().getTokenValue();
                    callNewCallApi();
                } catch (IOException e) {
                    e.printStackTrace();
                    runOnUiThread(() -> addResponse("Error loading access token: " + e.getMessage()));
                    accessToken = null;
                    addResponse("Failed to get access token");
                }
            }
        });
        thread.start();
    }
}