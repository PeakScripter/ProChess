package com.peakscripter.prochess;

import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class FriendsAdapter extends RecyclerView.Adapter<FriendsAdapter.FriendViewHolder> {
    private List<FriendModel> friends;
    private OnFriendInteractionListener listener;

    public interface OnFriendInteractionListener {
        void onChallengeFriend(FriendModel friend);
        void onRemoveFriend(FriendModel friend);
    }

    public FriendsAdapter(List<FriendModel> friends, OnFriendInteractionListener listener) {
        this.friends = friends;
        this.listener = listener;
    }

    public void updateFriends(List<FriendModel> newFriends) {
        this.friends = newFriends;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FriendViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.friend_list_item, parent, false);
        return new FriendViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FriendViewHolder holder, int position) {
        FriendModel friend = friends.get(position);
        holder.bind(friend);
    }

    @Override
    public int getItemCount() {
        return friends.size();
    }

    class FriendViewHolder extends RecyclerView.ViewHolder {
        private TextView usernameText;
        private TextView ratingText;
        private TextView statsText;
        private Button challengeButton;
        private ImageButton removeButton;

        FriendViewHolder(@NonNull View itemView) {
            super(itemView);
            usernameText = itemView.findViewById(R.id.usernameText);
            ratingText = itemView.findViewById(R.id.ratingText);
            statsText = itemView.findViewById(R.id.statsText);
            challengeButton = itemView.findViewById(R.id.challengeButton);
            removeButton = itemView.findViewById(R.id.removeButton);
        }

        void bind(FriendModel friend) {
            usernameText.setText(friend.getUsername());
            ratingText.setText("Rating: " + friend.getRating());
            statsText.setText(String.format("Games: %d | Win Rate: %s",
                    friend.getGamesPlayed(), friend.getWinRate()));

            challengeButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onChallengeFriend(friend);
                }
            });

            removeButton.setOnClickListener(v -> {
                new AlertDialog.Builder(itemView.getContext())
                        .setTitle("Remove Friend")
                        .setMessage("Are you sure you want to remove " + friend.getUsername() + " from your friends list?")
                        .setPositiveButton("Remove", (dialog, which) -> {
                            if (listener != null) {
                                listener.onRemoveFriend(friend);
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }
    }
}