package com.peakscripter.prochess;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

public class AcceptRequestActivity extends AppCompatActivity {
    private static final String TAG = "AcceptRequestActivity";

    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private LinearLayout requestsLayout;
    private SwipeRefreshLayout swipeRefreshLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_accept_request);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        requestsLayout = findViewById(R.id.requestsLayout);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        if (currentUser == null) {
            showError("Error: User not logged in.");
            finish();
            return;
        }

        setupSwipeRefresh();
        fetchFriendRequests();
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(this::fetchFriendRequests);
    }

    private void fetchFriendRequests() {
        db.collection("friendRequests")
                .whereEqualTo("toUid", currentUser.getUid())
                .whereEqualTo("status", "pending")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    requestsLayout.removeAllViews();
                    if (!querySnapshot.isEmpty()) {
                        querySnapshot.getDocuments().forEach(doc -> {
                            String requestId = doc.getId();
                            String fromUserId = doc.getString("fromUid");
                            String fromUsername = doc.getString("fromUsername");
                            if (fromUserId != null && fromUsername != null) {
                                createRequestView(requestId, fromUserId, fromUsername);
                            }
                        });
                    } else {
                        showEmptyState();
                    }
                    swipeRefreshLayout.setRefreshing(false);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching friend requests: ", e);
                    Toast.makeText(this, "Error fetching requests: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    swipeRefreshLayout.setRefreshing(false);
                });
    }

    private void showEmptyState() {
        TextView emptyStateText = new TextView(this);
        emptyStateText.setText("No pending friend requests");
        emptyStateText.setPadding(16, 16, 16, 16);
        requestsLayout.addView(emptyStateText);
    }

    private void createRequestView(String requestId, String fromUserId, String fromUsername) {
        View requestView = getLayoutInflater().inflate(R.layout.friend_request_item, null);

        TextView fromUserTextView = requestView.findViewById(R.id.fromUserTextView);
        Button acceptButton = requestView.findViewById(R.id.acceptButton);
        Button declineButton = requestView.findViewById(R.id.declineButton);

        fromUserTextView.setText(String.format("Friend request from %s", fromUsername));

        acceptButton.setOnClickListener(v -> acceptFriendRequest(requestId, fromUserId, fromUsername));
        declineButton.setOnClickListener(v -> declineFriendRequest(requestId));

        requestsLayout.addView(requestView);
    }

    private void acceptFriendRequest(String requestId, String fromUserId, String fromUsername) {
        // First update the request status
        db.collection("friendRequests").document(requestId)
                .update("status", "accepted")
                .addOnSuccessListener(aVoid -> {
                    // Add to current user's friends list
                    db.collection("users").document(currentUser.getUid())
                            .update("friends", com.google.firebase.firestore.FieldValue.arrayUnion(fromUserId))
                            .addOnSuccessListener(aVoid1 -> {
                                // Add to other user's friends list
                                db.collection("users").document(fromUserId)
                                        .update("friends", com.google.firebase.firestore.FieldValue.arrayUnion(currentUser.getUid()))
                                        .addOnSuccessListener(aVoid2 -> {
                                            Toast.makeText(this, "You are now friends with " + fromUsername, Toast.LENGTH_SHORT).show();
                                            fetchFriendRequests(); // Refresh the list
                                        })
                                        .addOnFailureListener(e -> handleError("Failed to update other user's friends list", e));
                            })
                            .addOnFailureListener(e -> handleError("Failed to update your friends list", e));
                })
                .addOnFailureListener(e -> handleError("Failed to accept friend request", e));
    }

    private void declineFriendRequest(String requestId) {
        db.collection("friendRequests").document(requestId)
                .update("status", "declined")
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Friend request declined", Toast.LENGTH_SHORT).show();
                    fetchFriendRequests(); // Refresh the list
                })
                .addOnFailureListener(e -> handleError("Failed to decline friend request", e));
    }

    private void handleError(String message, Exception e) {
        Log.e(TAG, message, e);
        Toast.makeText(this, message + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        Log.e(TAG, message);
    }
}