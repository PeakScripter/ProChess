package com.peakscripter.prochess;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;
import org.json.JSONObject;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import android.os.CountDownTimer;
import android.widget.TextView;

public class stockfishgame extends AppCompatActivity {

    private GridLayout gridLayout;
    private Board board;
    private Side playerSide;
    private Square selectedSquare = null;
    private List<Square> highlightedSquares = new ArrayList<>();
    private TextView playerTimerView;
    private TextView opponentTimerView;
    private CountDownTimer playerTimer;
    private CountDownTimer opponentTimer;
    private long playerTimeRemaining;
    private long opponentTimeRemaining;
    private long selectedTime; // in milliseconds


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stockfishgame);

        gridLayout = findViewById(R.id.chessboard);
        playerTimerView = findViewById(R.id.player_timer);
        opponentTimerView = findViewById(R.id.opponent_timer);

        gridLayout.setRowCount(8);
        gridLayout.setColumnCount(8);

        board = new Board();
        showTimeSelectionDialog();
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

    private void initializeTimers() {
        if (selectedTime == 0) return;

        playerTimeRemaining = selectedTime;
        opponentTimeRemaining = selectedTime;
        updateTimerDisplays();

        playerTimer = createTimer(playerTimerView, playerTimeRemaining, () ->
                showGameOverDialog("Time's up! " + (playerSide == Side.WHITE ? "Black" : "White") + " wins!"));

        opponentTimer = createTimer(opponentTimerView, opponentTimeRemaining, () ->
                showGameOverDialog("Time's up! " + (playerSide == Side.WHITE ? "White" : "Black") + " wins!"));

        if (playerSide == Side.WHITE) {
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
        if (selectedTime == 0) return;

        if (board.getSideToMove() == playerSide) {
            opponentTimer.cancel();
            playerTimer = createTimer(playerTimerView, playerTimeRemaining, () ->
                    showGameOverDialog("Time's up! " + (playerSide == Side.WHITE ? "Black" : "White") + " wins!"));
            playerTimer.start();
        } else {
            playerTimer.cancel();
            opponentTimer = createTimer(opponentTimerView, opponentTimeRemaining, () ->
                    showGameOverDialog("Time's up! " + (playerSide == Side.WHITE ? "White" : "Black") + " wins!"));
            opponentTimer.start();
        }
    }

    private void showSideSelectionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose your side:");

        String[] options = {"White", "Black", "Random"};
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    playerSide = Side.WHITE;
                    break;
                case 1:
                    playerSide = Side.BLACK;
                    break;
                case 2:
                    playerSide = new Random().nextBoolean() ? Side.WHITE : Side.BLACK;
                    break;
            }

            Toast.makeText(this, "You are playing as " + (playerSide == Side.WHITE ? "White" : "Black"), Toast.LENGTH_SHORT).show();
            renderChessboard();
            initializeTimers();
            Log.d("StockWhite", board.getFen());
            if (board.getSideToMove() != playerSide) {
                makeStockfishMove();
            }
        });

        builder.setCancelable(false);
        builder.show();
    }

    private void renderChessboard() {
        gridLayout.removeAllViews();

        Square kingSquare = getKingSquare(board.getSideToMove());
        boolean isInCheck = board.isKingAttacked();

        for (Square square : Square.values()) {
            if (square == Square.NONE) continue;

            ImageView cell = new ImageView(this);
            cell.setScaleType(ImageView.ScaleType.CENTER_CROP);

            // Modify row and column calculation based on player's side
            int row = playerSide == Side.WHITE ?
                    7 - square.getRank().ordinal() :
                    square.getRank().ordinal();
            int col = playerSide == Side.WHITE ?
                    square.getFile().ordinal() :
                    7 - square.getFile().ordinal();

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.rowSpec = GridLayout.spec(row, 1, 1f);
            params.columnSpec = GridLayout.spec(col, 1, 1f);
            params.width = 0;
            params.height = 0;
            cell.setLayoutParams(params);

            // Highlight logic remains the same
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

            cell.setOnClickListener(view -> handleSquareClick(square));
            gridLayout.addView(cell);
        }
    }

    private Square getKingSquare(Side side) {
        for (Square square : Square.values()) {
            if (board.getPiece(square) == (side == Side.WHITE ? Piece.WHITE_KING : Piece.BLACK_KING)) {
                return square;
            }
        }
        return null;
    }

    private void handleSquareClick(Square square) {
        if (board.getSideToMove() != playerSide) {
            Toast.makeText(this, "It's not your turn!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedSquare == null) {
            if (board.getPiece(square).getPieceSide() == playerSide) {
                selectedSquare = square;
                highlightedSquares = getLegalMoves(square);
                renderChessboard();
            } else {
                Toast.makeText(this, "Select one of your pieces!", Toast.LENGTH_SHORT).show();
            }
        } else {
            if (square == selectedSquare) {
                selectedSquare = null;
                highlightedSquares.clear();
                renderChessboard();
                return;
            }

            if (highlightedSquares.contains(square)) {
                Move move = new Move(selectedSquare, square);
                board.doMove(move);
                selectedSquare = null;
                highlightedSquares.clear();

                renderChessboard();
                switchTimers();

                if (board.isMated()) {
                    showGameOverDialog("Checkmate! " + (playerSide == Side.WHITE ? "White" : "Black") + " wins!");
                } else if (board.isStaleMate()) {
                    showGameOverDialog("Stalemate! It's a draw.");
                } else {
                    if (board.getSideToMove() != playerSide) {
                        makeStockfishMove();
                    }
                }
            }
        }
    }

    private void makeStockfishMove() {
        String fen = board.getFen();
        new Thread(() -> {
            try {
                String bestMoveResponse = getStockfishBestMove(fen, 12);
                if (bestMoveResponse != null) {
                    String[] responseParts = bestMoveResponse.split(" ");
                    String bestMove = responseParts[1];
                    if (bestMove.length() == 4) {
                        String fromSquareStr = bestMove.substring(0, 2).toUpperCase();
                        String toSquareStr = bestMove.substring(2, 4).toUpperCase();
                        try {
                            Square fromSquare = Square.valueOf(fromSquareStr);
                            Square toSquare = Square.valueOf(toSquareStr);

                            Move stockfishMove = new Move(fromSquare, toSquare);
                            board.doMove(stockfishMove);

                            runOnUiThread(() -> {
                                if (board.isMated()) {
                                    showGameOverDialog("Checkmate! Stockfish wins!");
                                } else {
                                    renderChessboard();
                                    switchTimers();
                                }
                            });
                        } catch (IllegalArgumentException e) {
                            Log.e("Stockfish", "Invalid square in best move: " + fromSquareStr + " or " + toSquareStr);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private String getStockfishBestMove(String fen, int depth) {
        try {
            String apiUrl = "https://stockfish.online/api/s/v2.php?fen=" + fen + "&depth=" + depth;
            URL url = new URL(apiUrl);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");

            InputStreamReader inputStreamReader = new InputStreamReader(urlConnection.getInputStream());
            StringBuilder response = new StringBuilder();
            int data = inputStreamReader.read();
            while (data != -1) {
                response.append((char) data);
                data = inputStreamReader.read();
            }

            JSONObject jsonResponse = new JSONObject(response.toString());
            if (jsonResponse.getBoolean("success")) {
                return jsonResponse.getString("bestmove");
            } else {
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void showGameOverDialog(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Game Over");
        builder.setMessage(message);
        builder.setPositiveButton("Restart", (dialog, which) -> restartGame());
        builder.setNegativeButton("Exit", (dialog, which) -> finish());
        builder.show();
    }

    private void restartGame() {
        board = new Board();
        showSideSelectionDialog();
    }

    private List<Square> getLegalMoves(Square square) {
        List<Square> legalMoves = new ArrayList<>();
        for (Move move : board.legalMoves()) {
            if (move.getFrom() == square) {
                legalMoves.add(move.getTo());
            }
        }
        return legalMoves;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (playerTimer != null) playerTimer.cancel();
        if (opponentTimer != null) opponentTimer.cancel();
    }
}