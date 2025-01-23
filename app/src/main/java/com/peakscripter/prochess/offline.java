package com.peakscripter.prochess;

import static androidx.core.util.TypedValueCompat.dpToPx;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.content.DialogInterface;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Rank;
import com.github.bhlangonijr.chesslib.move.Move;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.PieceType;
import java.util.ArrayList;
import java.util.List;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.content.DialogInterface;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.move.Move;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.List;

public class offline extends AppCompatActivity {

    private GridLayout gridLayout;
    private Board board;
    private Square selectedSquare = null;
    private List<Square> highlightedSquares = new ArrayList<>();
    private List<String> moveHistory = new ArrayList<>();
    private RequestQueue requestQueue;
    private static final String ANALYSIS_URL = "http://192.168.1.6:5000/analyze_game";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.offline);
        requestQueue = Volley.newRequestQueue(this);

        gridLayout = findViewById(R.id.chessboard2);
        gridLayout.setRowCount(8);
        gridLayout.setColumnCount(8);

        board = new Board();


        renderChessboard();
    }

    private void renderChessboard() {
        gridLayout.removeAllViews();

        Square kingSquare = getKingSquare(board.getSideToMove());
        boolean isInCheck = board.isKingAttacked();
        for (Square square : Square.values()) {
            if (square == Square.NONE) continue;

            ImageView cell = new ImageView(this);
            cell.setScaleType(ImageView.ScaleType.CENTER_CROP);

            int row = 7 - square.getRank().ordinal(); // Reverse for UI
            int col = square.getFile().ordinal();

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.rowSpec = GridLayout.spec(row, 1, 1f);
            params.columnSpec = GridLayout.spec(col, 1, 1f);
            params.width = 0;
            params.height = 0;
            cell.setLayoutParams(params);

            // Highlight logic
            if (square == kingSquare && isInCheck) {
                cell.setBackgroundColor(getResources().getColor(android.R.color.holo_red_light)); // Highlight king in red
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
                String resourceName = (piece.getPieceSide() == Side.WHITE ? "white_" : "black_") + piece.getPieceType().name().toLowerCase();
                int resourceId = getResources().getIdentifier(resourceName, "drawable", getPackageName());
                cell.setImageResource(resourceId);
            }

            // Add click listener for user interaction
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
        if (selectedSquare == null) {
            // Select a square
            Piece piece = board.getPiece(square);
            if (piece != Piece.NONE) {
                selectedSquare = square;
                highlightedSquares = getLegalMoves(square);
            }
        } else {
            // Attempt to make a move
            Move move = new Move(selectedSquare, square);

            if (isPawnPromotion(move)) {
                showPromotionDialog(selectedSquare, square);
            } else {
                executeMove(move);
            }

            // Reset selection and redraw board
            selectedSquare = null;
            highlightedSquares.clear();
        }

        renderChessboard();
    }

    private void executeMove(Move move) {
        if (board.isMoveLegal(move, true)) {
            board.doMove(move);
            moveHistory.add(move.toString());

            if (board.isKingAttacked()) {
                Toast.makeText(this, "Check!", Toast.LENGTH_SHORT).show();
            }
            if (board.isMated()) {
                analyzeGame();
                showCheckmateDialog();
            }

            Toast.makeText(this, "Move made: " + move, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Illegal move", Toast.LENGTH_SHORT).show();
        }
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
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            showAnalysisDialog(response);
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            Toast.makeText(offline.this,
                                    "Analysis failed: " + error.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
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

    private void showCheckmateDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Checkmate!");
        builder.setMessage("Game over! " + (board.getSideToMove() == Side.WHITE ? "Black" : "White") + " wins.");

        builder.setPositiveButton("Restart", (dialog, which) -> {
            board = new Board(); // Restart the game
            renderChessboard();
        });

        builder.setNegativeButton("Exit", (dialog, which) -> finish());

        builder.show();
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
            Piece promotedPiece;
            switch (which) {
                case 1:
                    promotedPiece = board.getSideToMove() == Side.WHITE ? Piece.WHITE_ROOK : Piece.BLACK_ROOK;
                    break;
                case 2:
                    promotedPiece = board.getSideToMove() == Side.WHITE ? Piece.WHITE_BISHOP : Piece.BLACK_BISHOP;
                    break;
                case 3:
                    promotedPiece = board.getSideToMove() == Side.WHITE ? Piece.WHITE_KNIGHT : Piece.BLACK_KNIGHT;
                    break;
                case 0: // Default to Queen
                default:
                    promotedPiece = board.getSideToMove() == Side.WHITE ? Piece.WHITE_QUEEN : Piece.BLACK_QUEEN;
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
}
