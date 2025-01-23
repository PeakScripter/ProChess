package com.peakscripter.prochess;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class GameModeActivity extends AppCompatActivity {
    private Button offlineButton, onlineButton, aiButton, viewProfileButton, addFriendButton,
            acceptRequestButton, friendListButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gamemode);

        offlineButton = findViewById(R.id.offlineButton);
        onlineButton = findViewById(R.id.onlineButton);
        aiButton = findViewById(R.id.aiButton);
        viewProfileButton = findViewById(R.id.viewProfileButton);
        addFriendButton = findViewById(R.id.addFriendButton);
        acceptRequestButton = findViewById(R.id.acceptRequestButton);
        friendListButton = findViewById(R.id.friendListButton);

        offlineButton.setOnClickListener(v -> {
            Intent intent = new Intent(GameModeActivity.this, offline.class);
            intent.putExtra("gameMode", "offline");
            startActivity(intent);
        });

        onlineButton.setOnClickListener(v -> {
            Intent intent = new Intent(GameModeActivity.this, onlinegame.class);
            intent.putExtra("gameMode", "online");
            startActivity(intent);
        });

        aiButton.setOnClickListener(v -> {
            Intent intent = new Intent(GameModeActivity.this, stockfishgame.class);
            intent.putExtra("gameMode", "ai");
            startActivity(intent);
        });

        viewProfileButton.setOnClickListener(v -> {
            Intent intent = new Intent(GameModeActivity.this, ProfileActivity.class);
            startActivity(intent);
        });

        addFriendButton.setOnClickListener(v -> {
            Intent intent = new Intent(GameModeActivity.this, AddFriendActivity.class);
            startActivity(intent);
        });

        acceptRequestButton.setOnClickListener(v -> {
            Intent intent = new Intent(GameModeActivity.this, AcceptRequestActivity.class);
            startActivity(intent);
        });

        friendListButton.setOnClickListener(v -> {
            Intent intent = new Intent(GameModeActivity.this, FriendsListActivity.class);
            startActivity(intent);
        });
    }
}