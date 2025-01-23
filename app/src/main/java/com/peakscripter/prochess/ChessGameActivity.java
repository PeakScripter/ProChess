package com.peakscripter.prochess;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Rank;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import android.os.CountDownTimer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.List;

public class ChessGameActivity extends AppCompatActivity {

    private GridLayout gridLayout;
    private Board board;
    private Square selectedSquare = null;
    private List<Square> highlightedSquares = new ArrayList<>();
    private FirebaseFirestore db;
    private String gameId;
    private String playerSide;
    private DocumentReference gameRef;
    private ListenerRegistration moveListener;
    private long timeControl;
    private TextView playerTimerView;
    private TextView opponentTimerView;
    private CountDownTimer playerTimer;
    private CountDownTimer opponentTimer;
    private long playerTimeRemaining;
    private long opponentTimeRemaining;
    private String lastProcessedFen = null;
    private String lastProcessedMove = null;
    private String gameStatus = null;
    // Rating system components
    private String player1Id, player2Id;
    private int player1Rating, player2Rating;
    private long player1games, player2games;
    private TextView playerUsernameView;
    private TextView playerRatingView;
    private TextView opponentUsernameView;
    private TextView opponentRatingView;
    private Button offerDrawButton;
    private String currentUserId;
    private Button resignButton;
    private RequestQueue requestQueue;
    private List<String> moveHistory = new ArrayList<>();
    private static final String ANALYSIS_URL = "http://192.168.1.6:5000/analyze_game";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chess_game);

        // Initialize UI components
        gridLayout = findViewById(R.id.chessboard);
        playerTimerView = findViewById(R.id.player_timer);
        opponentTimerView = findViewById(R.id.opponent_timer);
        gridLayout.setRowCount(8);
        gridLayout.setColumnCount(8);

        // Get game information from intent
        gameId = getIntent().getStringExtra("gameId");
        playerSide = getIntent().getStringExtra("playerSide");
        timeControl = getIntent().getLongExtra("timeControl", 0);
        player1Id = getIntent().getStringExtra("player1Id");
        player2Id = getIntent().getStringExtra("player2Id");
        player2games = getIntent().getLongExtra("player2games",0);
        player1games = getIntent().getLongExtra("player1games", 0);
        // Initialize views
        playerUsernameView = findViewById(R.id.player_username);
        playerRatingView = findViewById(R.id.player_rating);
        opponentUsernameView = findViewById(R.id.opponent_username);
        opponentRatingView = findViewById(R.id.opponent_rating);
        offerDrawButton = findViewById(R.id.offer_draw_button);
        resignButton = findViewById(R.id.resign_button);

// Set up button listeners
        offerDrawButton.setOnClickListener(v -> offerDraw());
        resignButton.setOnClickListener(v -> showResignConfirmation());
        // Initialize game
        db = FirebaseFirestore.getInstance();
        gameRef = db.collection("games").document(gameId);
        board = new Board();
        currentUserId = playerSide.equals("WHITE") ? player1Id : player2Id;

        // Fetch player ratings and setup game
        // Add this after initializing the views
        fetchPlayerInfo();
        fetchPlayerRatings();
        setupInitialGame();
        requestQueue = Volley.newRequestQueue(this);
    }
    private void fetchPlayerRatings() {
        db.collection("users").document(player1Id).get().addOnSuccessListener(snapshot -> {
            player1Rating = Objects.requireNonNull(snapshot.getLong("rating")).intValue();
            db.collection("users").document(player2Id).get().addOnSuccessListener(snapshot2 -> {
                player2Rating = Objects.requireNonNull(snapshot2.getLong("rating")).intValue();
            });
        });
    }
    private int calculateNewRating(int currentRating, int opponentRating, double actualScore, long numGamesPlayed) {
        int k;
        if (numGamesPlayed < 30) {
            k = 40; // Provisional player
        } else if (currentRating >= 2400) {
            k = 10; // Top players
        } else {
            k = 20; // Standard
        }

        double expectedScore = 1.0 / (1.0 + Math.pow(10, (opponentRating - currentRating) / 400.0));
        return (int) Math.round(currentRating + k * (actualScore - expectedScore));
    }

    private void updateRatingsAfterGame(String winnerId) {
        double player1Score, player2Score;
        if (winnerId.equals(player1Id)) {
            player1Score = 1.0;
            player2Score = 0.0;
        } else if (winnerId.equals(player2Id)) {
            player1Score = 0.0;
            player2Score = 1.0;
        } else {
            player1Score = 0.5;
            player2Score = 0.5;
        }

        int newPlayer1Rating = calculateNewRating(player1Rating, player2Rating, player1Score, player1games);
        int newPlayer2Rating = calculateNewRating(player2Rating, player1Rating, player2Score, player2games);

        Map<String, Object> player1Updates = new HashMap<>();
        player1Updates.put("rating", newPlayer1Rating);
        player1Updates.put("games", player1games + 1);

        Map<String, Object> player2Updates = new HashMap<>();
        player2Updates.put("rating", newPlayer2Rating);
        player2Updates.put("games", player2games + 1);

        db.collection("users").document(player1Id).update(player1Updates)
                .addOnSuccessListener(aVoid -> {
                    db.collection("users").document(player2Id).update(player2Updates)
                            .addOnSuccessListener(aVoid2 -> {
                                Toast.makeText(this, "Ratings Updated!", Toast.LENGTH_SHORT).show();
                            });
                });
    }
    private void analyzeGame() {
        try {
            JSONObject requestBody = new JSONObject();
            JSONArray movesArray = new JSONArray(moveHistory);
            requestBody.put("moves", movesArray);

            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.POST,
                    ANALYSIS_URL,
                    requestBody,
                    response -> showAnalysisDialog(response),
                    error -> Toast.makeText(ChessGameActivity.this,
                            "Analysis failed: " + error.getMessage(),
                            Toast.LENGTH_LONG).show()
            );

            requestQueue.add(request);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void showAnalysisDialog(JSONObject analysis) {
        try {
            JSONObject summary = analysis.getJSONObject("summary");
            JSONArray moveAnalysis = analysis.getJSONArray("analysis");

            StringBuilder message = new StringBuilder();
            message.append("Game Summary:\n");
            message.append("Blunders: ").append(summary.getInt("total_blunders")).append("\n");
            message.append("Mistakes: ").append(summary.getInt("total_mistakes")).append("\n");
            message.append("Inaccuracies: ").append(summary.getInt("total_inaccuracies")).append("\n\n");

            message.append("Move Analysis:\n");
            for (int i = 0; i < moveAnalysis.length(); i++) {
                JSONObject move = moveAnalysis.getJSONObject(i);
                message.append("Move ").append(i + 1).append(": ")
                        .append(move.getString("move")).append("\n");
                message.append("Best move was: ").append(move.getString("best_move")).append("\n");

                JSONObject currentEval = move.getJSONObject("current_evaluation");
                message.append("Position evaluation: ")
                        .append(currentEval.getInt("value") / 100.0)
                        .append("\n\n");
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Game Analysis");
            builder.setMessage(message.toString());
            builder.setPositiveButton("OK", null);
            builder.show();
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error parsing analysis", Toast.LENGTH_SHORT).show();
        }
    }


    private void setupMoveListener() {
        moveListener = gameRef.addSnapshotListener((snapshot, error) -> {
            if (error != null || snapshot == null || !snapshot.exists()) {
                System.err.println("Error in move listener: " + (error != null ? error.getMessage() : "No data"));
                return;
            }

            try {
                String lastMove = snapshot.getString("lastMove");
                String fenState = snapshot.getString("boardFen");
                String drawOfferId = snapshot.getString("drawOffer");

                if (drawOfferId != null && !drawOfferId.equals(currentUserId)) {
                    showDrawOfferDialog();
                }

                // Only process if we have both FEN and move, and they're new
                if (fenState != null && !fenState.isEmpty() && lastMove != null && !lastMove.isEmpty()) {
                    // Skip if we've already processed this exact state
                    if (lastMove.equals(lastProcessedMove) && fenState.equals(lastProcessedFen)) {
                        return;
                    }

                    // If this is our own move, skip processing
                    if (board.getFen().equals(fenState)) {
                        return;
                    }

                    // Load the new state
                    board.loadFromFen(fenState);
                    lastProcessedFen = fenState;
                    lastProcessedMove = lastMove;

                    runOnUiThread(() -> {
                        renderChessboard();
                        checkGameStatus();
                        // Only switch timers if the game is still ongoing
                        if (gameStatus == null) {
                            switchTimers();
                        }
                    });
                }

                // Update opponent's time without resetting timers
                if (timeControl > 0) {
                    Long opponentTime = snapshot.getLong("opponentTimeRemaining");
                    if (opponentTime != null) {
                        opponentTimeRemaining = opponentTime;
                        runOnUiThread(() ->
                                updateTimerDisplay(opponentTimerView, opponentTimeRemaining));
                    }
                }
            } catch (Exception e) {
                System.err.println("Error processing board state from Firebase: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    private void showDrawOfferDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Draw Offer")
                .setMessage("Your opponent has offered a draw. Do you accept?")
                .setPositiveButton("Accept", (dialog, which) -> acceptDraw())
                .setNegativeButton("Decline", (dialog, which) -> declineDraw())
                .show();
    }

    private void acceptDraw() {
        gameStatus = "Draw";
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", gameStatus);
        updates.put("drawOffer", null);

        gameRef.update(updates)
                .addOnSuccessListener(aVoid -> {
                    updateRatingsAfterGame("draw");
                    showGameEndDialog("Game Over - Draw agreed");
                });
    }

    private void declineDraw() {
        gameRef.update("drawOffer", null);
    }

    private void executeMove(Move move) {
        try {
            if (board.isMoveLegal(move, true)) {
                // Do the move locally
                board.doMove(move);
                moveHistory.add(move.toString());  // Add this line to record the move
                String newFen = board.getFen();
                lastProcessedFen = newFen;
                lastProcessedMove = move.toString();

                // Update Firestore with the move and new FEN state
                Map<String, Object> updates = new HashMap<>();
                updates.put("lastMove", move.toString());
                updates.put("boardFen", newFen);
                updates.put("playerTimeRemaining", playerTimeRemaining);
                updates.put("opponentTimeRemaining", opponentTimeRemaining);

                gameRef.update(updates)
                        .addOnSuccessListener(aVoid -> {
                            System.out.println("Move updated successfully");
                            switchTimers();
                        })
                        .addOnFailureListener(e -> {
                            System.err.println("Error updating move: " + e.getMessage());
                            Toast.makeText(ChessGameActivity.this,
                                    "Error updating move: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        });
                switchTimers();
                checkGameStatus();
            } else {
                Toast.makeText(this, "Illegal move", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error making move: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            System.err.println("Error in executeMove: " + e.getMessage());
            e.printStackTrace();
        } finally {
            selectedSquare = null;
            highlightedSquares.clear();
            renderChessboard();
        }
    }

    // Modify the checkGameStatus method to trigger analysis
    private void checkGameStatus() {
        if (board.isMated()) {
            gameStatus = board.getSideToMove() == Side.WHITE ? "Black wins" : "White wins";
            // Determine winner ID based on game result
            String winnerId = (gameStatus.equals("Black wins") && playerSide.equals("BLACK")) ||
                    (gameStatus.equals("White wins") && playerSide.equals("WHITE")) ?
                    player1Id : player2Id;
            analyzeGame();  // Add this line to trigger analysis
            updateRatingsAfterGame(winnerId);
        } else if (board.isDraw()) {
            gameStatus = "Draw";
            analyzeGame();  // Add this line to trigger analysis
            updateRatingsAfterGame("draw");
        } else if (board.isKingAttacked()) {
            Toast.makeText(this, "Check!", Toast.LENGTH_SHORT).show();
        }

        if (gameStatus != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", gameStatus);

            gameRef.update(updates)
                    .addOnSuccessListener(aVoid -> System.out.println("Game status updated: " + gameStatus))
                    .addOnFailureListener(e -> System.err.println("Failed to update game status: " + e.getMessage()));

            showGameEndDialog(gameStatus);
        }
    }

    // Modified setupInitialGame to handle initial state better
    private void setupInitialGame() {
        // Initialize timers if time control is enabled
        if (timeControl > 0) {
            playerTimeRemaining = timeControl;
            opponentTimeRemaining = timeControl;
            initializeTimers();
        }

        // Set up initial board state in Firebase if we're the creator
        if (playerSide.equals("WHITE")) {
            String initialFen = board.getFen();
            lastProcessedFen = initialFen;  // Set initial FEN as processed

            Map<String, Object> initialState = new HashMap<>();
            initialState.put("boardFen", initialFen);
            initialState.put("status", "ongoing");

            gameRef.update(initialState)
                    .addOnSuccessListener(aVoid -> System.out.println("Initial state set successfully"))
                    .addOnFailureListener(e -> System.err.println("Error setting initial state: " + e.getMessage()));
        }

        // Setup move listener
        setupMoveListener();
        // Render initial board
        renderChessboard();
    }

    private void initializeTimers() {
        if (timeControl == 0) return;
        playerTimeRemaining = timeControl;
        opponentTimeRemaining = timeControl;
        updateTimerDisplays();
        playerTimer = createTimer(playerTimerView, playerTimeRemaining, () ->
                showGameEndDialog("Time's up! " + (playerSide.equals("WHITE") ? "Black" : "White") + " wins!"));
        opponentTimer = createTimer(opponentTimerView, opponentTimeRemaining, () ->
                showGameEndDialog("Time's up! " + (playerSide.equals("WHITE") ? "White" : "Black") + " wins!"));
        if (playerSide.equals("WHITE")) {
            playerTimer.start();
        } else {
            opponentTimer.start();
        }
    }

    private CountDownTimer createTimer(TextView timerView, long millisInFuture, Runnable onFinish) {
        return new CountDownTimer(millisInFuture, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (timerView == playerTimerView) {
                    playerTimeRemaining = millisUntilFinished;
                } else {
                    opponentTimeRemaining = millisUntilFinished;
                }
                updateTimerDisplay(timerView, millisUntilFinished);
            }

            @Override
            public void onFinish() {
                updateTimerDisplay(timerView, 0);
                onFinish.run();
            }
        };
    }

    private void updateTimerDisplays() {
        updateTimerDisplay(playerTimerView, playerTimeRemaining);
        updateTimerDisplay(opponentTimerView, opponentTimeRemaining);
    }

    private void updateTimerDisplay(TextView timerView, long timeInMillis) {
        int minutes = (int) (timeInMillis / 1000) / 60;
        int seconds = (int) (timeInMillis / 1000) % 60;
        timerView.setText(String.format("%02d:%02d", minutes, seconds));
    }

    private void switchTimers() {
        if (timeControl == 0) return;
        if (board.getSideToMove().name().equals(playerSide)) {
            opponentTimer.cancel();
            playerTimer = createTimer(playerTimerView, playerTimeRemaining, () ->
                    showGameEndDialog("Time's up! " + (playerSide.equals("WHITE") ? "Black" : "White") + " wins!"));
            playerTimer.start();
        } else {
            playerTimer.cancel();
            opponentTimer = createTimer(opponentTimerView, opponentTimeRemaining, () ->
                    showGameEndDialog("Time's up! " + (playerSide.equals("WHITE") ? "White" : "Black") + " wins!"));
            opponentTimer.start();
        }
    }

    private void renderChessboard() {
        gridLayout.removeAllViews();
        Square kingSquare = getKingSquare(board.getSideToMove());
        boolean isInCheck = board.isKingAttacked();
        for (Square square : Square.values()) {
            if (square == Square.NONE) continue;
            ImageView cell = new ImageView(this);
            cell.setScaleType(ImageView.ScaleType.CENTER_CROP);
            // Adjust row orientation based on player's side
            int row = playerSide.equals("WHITE") ?
                    7 - square.getRank().ordinal() :
                    square.getRank().ordinal();
            int col = playerSide.equals("WHITE") ?
                    square.getFile().ordinal() :
                    7 - square.getFile().ordinal();
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.rowSpec = GridLayout.spec(row, 1, 1f);
            params.columnSpec = GridLayout.spec(col, 1, 1f);
            params.width = 0;
            params.height = 0;
            cell.setLayoutParams(params);
            // Highlight logic
            if (square == kingSquare && isInCheck) {
                cell.setBackgroundColor(getResources().getColor(android.R.color.holo_red_light));
            } else if (highlightedSquares.contains(square)) {
                cell.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_light));
            } else {
                cell.setBackgroundColor((row + col) % 2 == 0 ?
                        getResources().getColor(android.R.color.darker_gray) :
                        getResources().getColor(android.R.color.white));
            }
            // Set piece image
            Piece piece = board.getPiece(square);
            if (piece != Piece.NONE) {
                String resourceName = (piece.getPieceSide() == Side.WHITE ? "white_" : "black_") +
                        piece.getPieceType().name().toLowerCase();
                int resourceId = getResources().getIdentifier(resourceName, "drawable", getPackageName());
                cell.setImageResource(resourceId);
            }
            // Add click listener only if it's player's turn
            if (board.getSideToMove().name().equals(playerSide)) {
                cell.setOnClickListener(view -> handleSquareClick(square));
            }
            gridLayout.addView(cell);
        }
    }

    private void handleSquareClick(Square square) {
        try {
            if (selectedSquare == null) {
                // First click - selecting a piece
                Piece piece = board.getPiece(square);
                // Validate piece selection
                if (piece == Piece.NONE) {
                    Toast.makeText(this, "No piece at this position", Toast.LENGTH_SHORT).show();
                    return;
                }
                // Convert playerSide string to Side enum for proper comparison
                Side currentPlayerSide = Side.valueOf(playerSide);
                // Debug logs
                System.out.println("Piece Side: " + piece.getPieceSide());
                System.out.println("Player Side: " + currentPlayerSide);
                if (piece.getPieceSide() != currentPlayerSide) {
                    Toast.makeText(this, "Not your piece", Toast.LENGTH_SHORT).show();
                    return;
                }
                // Valid piece selected
                selectedSquare = square;
                highlightedSquares = getLegalMoves(square);
                if (highlightedSquares.isEmpty()) {
                    Toast.makeText(this, "No legal moves for this piece", Toast.LENGTH_SHORT).show();
                    selectedSquare = null;
                }
            } else {
                // Second click - making a move
//                haha=true;
                if (square == selectedSquare) {
                    // Deselect if same square clicked
                    selectedSquare = null;
                    highlightedSquares.clear();
//                    haha=false;
                } else {
                    // Check if the target square is a legal move
                    if (!highlightedSquares.contains(square)) {
                        Toast.makeText(this, "Invalid move", Toast.LENGTH_SHORT).show();
//                        selectedSquare = null;
//                        highlightedSquares.clear();
                        return;
                    }
                    Move move = new Move(selectedSquare, square);
                    if (isPawnPromotion(move)) {
                        showPromotionDialog(selectedSquare, square);
                    } else {
                        executeMove(move);
//                        haha=false;
                    }
                }
            }

            renderChessboard();
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            System.err.println("Error in handleSquareClick: " + e.getMessage());
            e.printStackTrace();
            // Reset state on error
            selectedSquare = null;
            highlightedSquares.clear();
            renderChessboard();
        }
    }
//    private void checkGameStatus() {
//        if (board.isMated()) {
//            gameStatus = board.getSideToMove() == Side.WHITE ? "Black wins" : "White wins";
//            // Determine winner ID based on game result
//            String winnerId = (gameStatus.equals("Black wins") && playerSide.equals("BLACK")) ||
//                    (gameStatus.equals("White wins") && playerSide.equals("WHITE")) ?
//                    player1Id : player2Id;
//            updateRatingsAfterGame(winnerId);
//        } else if (board.isDraw()) {
//            gameStatus = "Draw";
//            updateRatingsAfterGame("draw");
//        } else if (board.isKingAttacked()) {
//            Toast.makeText(this, "Check!", Toast.LENGTH_SHORT).show();
//        }
//
//        if (gameStatus != null) {
//            Map<String, Object> updates = new HashMap<>();
//            updates.put("status", gameStatus);
//
//            gameRef.update(updates)
//                    .addOnSuccessListener(aVoid -> System.out.println("Game status updated: " + gameStatus))
//                    .addOnFailureListener(e -> System.err.println("Failed to update game status: " + e.getMessage()));
//
//            showGameEndDialog(gameStatus);
//        }
//    }


    private void showGameEndDialog(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Game Over")
                .setMessage(message)
                .setPositiveButton("Exit", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private Square getKingSquare(Side side) {
        for (Square square : Square.values()) {
            if (board.getPiece(square) == (side == Side.WHITE ? Piece.WHITE_KING : Piece.BLACK_KING)) {
                return square;
            }
        }
        return null;
    }

    private boolean isPawnPromotion(Move move) {
        Piece piece = board.getPiece(move.getFrom());
        return piece.getPieceType() == PieceType.PAWN &&
                (move.getTo().getRank() == Rank.RANK_8 || move.getTo().getRank() == Rank.RANK_1);
    }

    private void showPromotionDialog(Square from, Square to) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Promote Pawn to:");
        String[] options = {"Queen", "Rook", "Bishop", "Knight"};
        builder.setItems(options, (dialog, which) -> {
            Side side = Side.valueOf(playerSide);
            Piece promotedPiece;
            switch (which) {
                case 1:
                    promotedPiece = side == Side.WHITE ? Piece.WHITE_ROOK : Piece.BLACK_ROOK;
                    break;
                case 2:
                    promotedPiece = side == Side.WHITE ? Piece.WHITE_BISHOP : Piece.BLACK_BISHOP;
                    break;
                case 3:
                    promotedPiece = side == Side.WHITE ? Piece.WHITE_KNIGHT : Piece.BLACK_KNIGHT;
                    break;
                default:
                    promotedPiece = side == Side.WHITE ? Piece.WHITE_QUEEN : Piece.BLACK_QUEEN;
                    break;
            }
            Move promotionMove = new Move(from, to, promotedPiece);
            executeMove(promotionMove);
            renderChessboard();
        });
        builder.show();
    }

    private List<Square> getLegalMoves(Square square) {
        List<Square> legalSquares = new ArrayList<>();
        for (Move move : board.legalMoves()) {
            if (move.getFrom() == square) {
                legalSquares.add(move.getTo());
            }
        }
        return legalSquares;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (moveListener != null) {
            moveListener.remove();
        }
    }
    private void offerDraw() {
        Map<String, Object> updates = new HashMap<>();
        updates.put("drawOffer", currentUserId);

        gameRef.update(updates)
                .addOnSuccessListener(aVoid ->
                        Toast.makeText(this, "Draw offered", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to offer draw", Toast.LENGTH_SHORT).show());
    }
    private void fetchPlayerInfo() {
        db.collection("users").document(player1Id).get()
                .addOnSuccessListener(snapshot -> {
                    String username = snapshot.getString("username");
                    long rating = snapshot.getLong("rating");

                    if (playerSide.equals("WHITE")) {
                        playerUsernameView.setText(username);
                        playerRatingView.setText(String.valueOf(rating));
                    } else {
                        opponentUsernameView.setText(username);
                        opponentRatingView.setText(String.valueOf(rating));
                    }
                });

        db.collection("users").document(player2Id).get()
                .addOnSuccessListener(snapshot -> {
                    String username = snapshot.getString("username");
                    long rating = snapshot.getLong("rating");

                    if (playerSide.equals("BLACK")) {
                        playerUsernameView.setText(username);
                        playerRatingView.setText(String.valueOf(rating));
                    } else {
                        opponentUsernameView.setText(username);
                        opponentRatingView.setText(String.valueOf(rating));
                    }
                });
    }

    private void showResignConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Resign Game")
                .setMessage("Are you sure you want to resign?")
                .setPositiveButton("Yes", (dialog, which) -> resignGame())
                .setNegativeButton("No", null)
                .show();
    }

    private void resignGame() {
        String winner = playerSide.equals("WHITE") ? "Black wins" : "White wins";
        gameStatus = winner;
        String winnerId = playerSide.equals("WHITE") ? player2Id : player1Id;

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", gameStatus);
        updates.put("winner", winnerId);

        gameRef.update(updates)
                .addOnSuccessListener(aVoid -> {
                    updateRatingsAfterGame(winnerId);
                    showGameEndDialog("Game Over - " + winner + " by resignation");
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to resign", Toast.LENGTH_SHORT).show());
    }
}