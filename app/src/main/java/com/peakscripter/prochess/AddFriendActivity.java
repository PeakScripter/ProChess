package com.peakscripter.prochess;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.HashMap;
import java.util.Map;

public class AddFriendActivity extends AppCompatActivity {
    private static final String TAG = "AddFriendActivity";
    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_friend);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        EditText usernameField = findViewById(R.id.usernameField);
        Button addButton = findViewById(R.id.addButton);

        addButton.setOnClickListener(v -> {
            String username = usernameField.getText().toString().trim();
            if (!username.isEmpty()) {
                if (username.equals(currentUser.getDisplayName())) {
                    Toast.makeText(this, "You cannot send a request to yourself", Toast.LENGTH_SHORT).show();
                    return;
                }
                checkIfUsernameExistsAndSendRequest(username);
            } else {
                Toast.makeText(this, "Please enter a username", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkIfUsernameExistsAndSendRequest(String username) {
        db.collection("users")
                .whereEqualTo("username", username)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        String targetUserId = queryDocumentSnapshots.getDocuments().get(0).getId();
                        checkExistingRequestAndFriendship(targetUserId, username);
                    } else {
                        showUsernameNotFoundDialog(username);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error checking username: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void checkExistingRequestAndFriendship(String targetUserId, String username) {
        // First check if they're already friends
        db.collection("users").document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        java.util.List<String> friends = (java.util.List<String>) documentSnapshot.get("friends");
                        if (friends != null && friends.contains(targetUserId)) {
                            Toast.makeText(this, "You are already friends with " + username, Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // Check for existing requests
                        checkExistingRequests(targetUserId, username);
                    }
                });
    }

    private void checkExistingRequests(String targetUserId, String username) {
        db.collection("friendRequests")
                .whereEqualTo("fromUid", currentUser.getUid())
                .whereEqualTo("toUid", targetUserId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        // No existing request, proceed to send one
                        sendFriendRequest(targetUserId, username);
                    } else {
                        Toast.makeText(this, "You already sent a request to " + username, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void sendFriendRequest(String targetUserId, String username) {
        Map<String, Object> request = new HashMap<>();
        request.put("fromUid", currentUser.getUid());
        request.put("fromUsername", currentUser.getDisplayName());
        request.put("toUid", targetUserId);
        request.put("toUsername", username);
        request.put("timestamp", com.google.firebase.Timestamp.now());
        request.put("status", "pending");

        db.collection("friendRequests")
                .add(request)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(this, "Friend request sent to " + username, Toast.LENGTH_SHORT).show();
                    finish(); // Close the activity after successful request
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to send request: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void showUsernameNotFoundDialog(String username) {
        new AlertDialog.Builder(this)
                .setTitle("User Not Found")
                .setMessage("The username \"" + username + "\" does not exist. Please check the username and try again.")
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
    }
}