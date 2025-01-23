# ProChess

A feature-rich Android chess application that offers multiple game modes and a sophisticated chess-playing experience.

## Features

- *Multiple Game Modes*
  - Online multiplayer with real-time gameplay
  - Play against Stockfish AI engine
  - Offline two-player mode

- *Game Features*
  - Real-time chess board with legal move validation
  - Time controls (5, 10, 30 minutes, or no timer)
  - Player ratings and rankings
  - Move history tracking
  - Pawn promotion
  - Draw offers and resignation options
  - Check and checkmate detection

- *User Interface*
  - Beautiful and intuitive chessboard display
  - Real-time timers for both players
  - Player information display (username, rating)
  - Game control buttons (offer draw, resign)
  - Legal move highlighting
  - Check indication

## Technical Details

- Built for Android using Java
- Uses Firebase for real-time multiplayer functionality
- Integrates the chesslib library for chess logic
- Implements ELO rating system for player rankings
- Supports both portrait and landscape orientations

## Requirements

- Android device running Android 14
- Internet connection for online play and Stockfish mode
- Google Play Services for Firebase functionality

## Installation

1. Clone the repository
2. Open the project in Android Studio
3. Configure Firebase:
   - Add your google-services.json file
   - Enable Firebase Authentication and Firestore
4. Build and run the application

