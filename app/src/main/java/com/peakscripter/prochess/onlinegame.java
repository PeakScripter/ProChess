package com.peakscripter.prochess;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

public class onlinegame extends AppCompatActivity {

    private FirebaseFirestore db;
    private String gameId;
    private TextView gameIdDisplay;
    private EditText gameIdInput;
    private long selectedTime;
    private String selectedSide;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_online_game);

        db = FirebaseFirestore.getInstance();
        currentUserId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();

        gameIdDisplay = findViewById(R.id.gameIdDisplay);
        gameIdInput = findViewById(R.id.gameIdInput);
        Button createGameButton = findViewById(R.id.createGameButton);
        Button joinGameButton = findViewById(R.id.joinGameButton);

        createGameButton.setOnClickListener(v -> showTimeSelectionDialog());
        joinGameButton.setOnClickListener(v -> joinGame());
    }

    private void showTimeSelectionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Time Control:");

        String[] options = {"5 minutes", "10 minutes", "30 minutes", "No timer"};
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    selectedTime = 5 * 60 * 1000; // 5 minutes
                    break;
                case 1:
                    selectedTime = 10 * 60 * 1000; // 10 minutes
                    break;
                case 2:
                    selectedTime = 30 * 60 * 1000; // 30 minutes
                    break;
                case 3:
                    selectedTime = 0; // No timer
                    break;
            }
            showSideSelectionDialog();
        });

        builder.setCancelable(false);
        builder.show();
    }

    private void showSideSelectionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose your side:");

        String[] options = {"White", "Black", "Random"};
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    selectedSide = "WHITE";
                    break;
                case 1:
                    selectedSide = "BLACK";
                    break;
                case 2:
                    selectedSide = new Random().nextBoolean() ? "WHITE" : "BLACK";
                    break;
            }
            createGame();
        });

        builder.setCancelable(false);
        builder.show();
    }

    private void createGame() {
        gameId = UUID.randomUUID().toString().substring(0, 8);
        gameIdDisplay.setText("Game ID: " + gameId);

        Map<String, Object> gameData = new HashMap<>();
        gameData.put("status", "waiting");
        gameData.put("timeControl", selectedTime);
        gameData.put("creator_side", selectedSide);
        gameData.put("player1TimeRemaining", selectedTime);
        gameData.put("player2TimeRemaining", selectedTime);
        gameData.put("player1Id", currentUserId);

        db.collection("games").document(gameId)
                .set(gameData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Game created. Waiting for another player...", Toast.LENGTH_SHORT).show();
                    waitForPlayer();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to create game.", Toast.LENGTH_SHORT).show());
    }

    private void joinGame() {
        String enteredGameId = gameIdInput.getText().toString().trim();
        if (enteredGameId.isEmpty()) {
            Toast.makeText(this, "Please enter a Game ID.", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("games").document(enteredGameId).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        gameId = enteredGameId;
                        selectedSide = snapshot.getString("creator_side").equals("WHITE") ? "BLACK" : "WHITE";
                        selectedTime = snapshot.getLong("timeControl");

                        Map<String, Object> updates = new HashMap<>();
                        updates.put("status", "ongoing");
                        updates.put("joiner_side", selectedSide);
                        updates.put("player2Id", currentUserId);

                        db.collection("games").document(gameId)
                                .update(updates)
                                .addOnSuccessListener(aVoid -> startGame());
                    } else {
                        Toast.makeText(this, "Game ID not found.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void waitForPlayer() {
        db.collection("games").document(gameId)
                .addSnapshotListener((snapshot, error) -> {
                    if (snapshot != null && "ongoing".equals(snapshot.getString("status"))) {
                        startGame();
                    }
                });
    }

    private void startGame() {
        db.collection("games").document(gameId).get()
                .addOnSuccessListener(snapshot -> {
                    Intent intent = new Intent(this, ChessGameActivity.class);
                    intent.putExtra("gameId", gameId);
                    intent.putExtra("playerSide", selectedSide);
                    intent.putExtra("timeControl", selectedTime);
                    intent.putExtra("player1Id", snapshot.getString("player1Id"));
                    intent.putExtra("player2Id", snapshot.getString("player2Id"));
                    startActivity(intent);
                    finish();
                });
    }
}
