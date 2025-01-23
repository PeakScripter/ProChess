package com.peakscripter.prochess;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;

public class ProfileActivity extends AppCompatActivity {
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private TextView profileInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        profileInfo = findViewById(R.id.profileInfo);

        if (currentUser != null) {
            db.collection("users").document(currentUser.getUid())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            StringBuilder profileData = new StringBuilder();
                            profileData.append("Username: ").append(documentSnapshot.getString("username"));
                            profileData.append("\nRating: ").append(documentSnapshot.getLong("rating"));
                            profileData.append("\nGames Played: ").append(documentSnapshot.getLong("gamesPlayed"));
                            profileData.append("\nWins: ").append(documentSnapshot.getLong("wins"));
                            profileData.append("\nLosses: ").append(documentSnapshot.getLong("losses"));
                            profileData.append("\nDraws: ").append(documentSnapshot.getLong("draws"));
                            profileInfo.setText(profileData.toString());
                        }
                    });
        }
    }
}