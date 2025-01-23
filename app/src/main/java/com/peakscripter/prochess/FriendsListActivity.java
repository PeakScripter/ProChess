package com.peakscripter.prochess;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class FriendsListActivity extends AppCompatActivity implements FriendsAdapter.OnFriendInteractionListener {
    private static final String TAG = "FriendsListActivity";

    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private RecyclerView recyclerView;
    private FriendsAdapter adapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar progressBar;
    private TextView emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friends_list);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initializeViews();
        setupRecyclerView();
        loadFriends();
    }
    @Override
    public void onRemoveFriend(FriendModel friend) {
        // Remove from current user's friends list
        db.collection("users").document(currentUser.getUid())
                .update("friends", com.google.firebase.firestore.FieldValue.arrayRemove(friend.getUserId()))
                .addOnSuccessListener(aVoid -> {
                    // Remove from other user's friends list
                    db.collection("users").document(friend.getUserId())
                            .update("friends", com.google.firebase.firestore.FieldValue.arrayRemove(currentUser.getUid()))
                            .addOnSuccessListener(aVoid1 -> {
                                Toast.makeText(this, friend.getUsername() + " removed from friends", Toast.LENGTH_SHORT).show();
                                loadFriends(); // Refresh the list
                            })
                            .addOnFailureListener(e -> handleError("Failed to update other user's friends list", e));
                })
                .addOnFailureListener(e -> handleError("Failed to remove friend", e));
    }

    private void initializeViews() {
        recyclerView = findViewById(R.id.friendsRecyclerView);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        progressBar = findViewById(R.id.progressBar);
        emptyView = findViewById(R.id.emptyView);

        swipeRefreshLayout.setOnRefreshListener(this::loadFriends);
    }

    private void setupRecyclerView() {
        adapter = new FriendsAdapter(new ArrayList<>(), this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void loadFriends() {
        progressBar.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);

        db.collection("users").document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    List<String> friendIds = (List<String>) documentSnapshot.get("friends");
                    if (friendIds != null && !friendIds.isEmpty()) {
                        fetchFriendsData(friendIds);
                    } else {
                        showEmptyState();
                    }
                })
                .addOnFailureListener(e -> handleError("Error loading friends list", e));
    }

    private void fetchFriendsData(List<String> friendIds) {
        List<FriendModel> friendsList = new ArrayList<>();

        db.collection("users")
                .whereIn("__name__", friendIds)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        FriendModel friend = new FriendModel(
                                document.getId(),
                                document.getString("username"),
                                document.getLong("rating") != null ? document.getLong("rating") : 1200,
                                document.getLong("wins") != null ? document.getLong("wins") : 0,
                                document.getLong("losses") != null ? document.getLong("losses") : 0
                        );
                        friendsList.add(friend);
                    }
                    updateUI(friendsList);
                })
                .addOnFailureListener(e -> handleError("Error fetching friends data", e));
    }

    private void updateUI(List<FriendModel> friendsList) {
        progressBar.setVisibility(View.GONE);
        swipeRefreshLayout.setRefreshing(false);

        if (friendsList.isEmpty()) {
            showEmptyState();
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            adapter.updateFriends(friendsList);
        }
    }

    private void showEmptyState() {
        progressBar.setVisibility(View.GONE);
        swipeRefreshLayout.setRefreshing(false);
        recyclerView.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onChallengeFriend(FriendModel friend) {
        // Create a new game challenge
        createGameChallenge(friend);
    }

    private void createGameChallenge(FriendModel friend) {
        String challengeId = db.collection("challenges").document().getId();

        // Create challenge data
        java.util.Map<String, Object> challenge = new java.util.HashMap<>();
        challenge.put("challengerId", currentUser.getUid());
        challenge.put("challengerUsername", currentUser.getDisplayName());
        challenge.put("receiverId", friend.getUserId());
        challenge.put("receiverUsername", friend.getUsername());
        challenge.put("status", "pending");
        challenge.put("timestamp", com.google.firebase.Timestamp.now());
        challenge.put("gameId", ""); // Will be filled when game starts

        db.collection("challenges").document(challengeId)
                .set(challenge)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Challenge sent to " + friend.getUsername(), Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> handleError("Failed to send challenge", e));
    }

    private void handleError(String message, Exception e) {
        Log.e(TAG, message, e);
        Toast.makeText(this, message + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
        progressBar.setVisibility(View.GONE);
        swipeRefreshLayout.setRefreshing(false);
    }
}