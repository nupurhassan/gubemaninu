package Assign32starter;

import java.net.*;
import java.io.*;
import java.util.*;
import org.json.*;

/**
 * SockServer
 * ----------
 * Demonstrates:
 * - "next" command: move to the next hint (up to 4 hints per wonder)
 * - "remain"/"remaining" command: shows how many hints are left
 * - guess, skip, scoreboard, etc.
 */
public class SockServer {

	// ---------------------------
	// GAME STATES
	// ---------------------------
	private enum GameState {
		WAITING_FOR_HELLO,
		WAITING_FOR_NAME_AGE,
		WAITING_FOR_CHOICE,
		WAITING_FOR_ROUNDS,
		WAITING_FOR_GUESS
	}

	private static GameState state = GameState.WAITING_FOR_HELLO;

	// ---------------------------
	// PLAYER / ROUND INFO
	// ---------------------------
	private static String playerName = "";
	private static int playerAge = 0;
	private static int totalRounds = 0;
	private static int roundsLeft = 0;
	private static int currentRound = 0;

	// Leaderboard storing total points per player
	private static Map<String,Integer> leaderboard = new HashMap<>();

	// Each wonder has 4 hints (images) and an answer
	private static class Wonder {
		List<String> images;
		String answer;
		Wonder(List<String> images, String answer) {
			this.images = images;
			this.answer = answer.toLowerCase();
		}
	}

	// Our wonders
	private static List<Wonder> wonderList = new ArrayList<>(Arrays.asList(
			new Wonder(
					Arrays.asList("img/Colosseum1.png", "img/Colosseum2.png", "img/Colosseum3.png", "img/Colosseum4.png"),
					"colosseum"
			),
			new Wonder(
					Arrays.asList("img/GrandCanyon1.png", "img/GrandCanyon2.png", "img/GrandCanyon3.png", "img/GrandCanyon4.png"),
					"grand canyon"
			),
			new Wonder(
					Arrays.asList("img/Stonehenge1.png", "img/Stonehenge2.png", "img/Stonehenge3.png", "img/Stonehenge4.png"),
					"stonehenge"
			)
	));

	// Current wonder/hint
	private static Wonder currentWonder = null;
	private static int hintIndex = 0;  // 0..3 for 4 hints
	private static final int MAX_HINTS = 4;
	private static String currentCorrectAnswer = "";

	public static void main(String[] args) {
		try (ServerSocket serv = new ServerSocket(8888)) {
			System.out.println("Server ready for connection");

			while (true) {
				try {
					Socket sock = serv.accept();
					handleClient(sock);
				} catch (IOException e) {
					System.err.println("Error accepting connection: " + e.getMessage());
				}
			}
		} catch (IOException e) {
			System.err.println("Failed to start server on port 8888: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Handle each client connection in a separate method
	 */
	private static void handleClient(Socket sock) {
		try (ObjectInputStream in = new ObjectInputStream(sock.getInputStream());
			 PrintWriter outWrite = new PrintWriter(sock.getOutputStream(), true)) {

			String inputStr = (String) in.readObject();
			System.out.println("Server received: " + inputStr);

			// Attempt to parse JSON
			JSONObject request;
			try {
				request = new JSONObject(inputStr);
			} catch (JSONException je) {
				JSONObject resp = new JSONObject();
				resp.put("type", "error");
				resp.put("value", "Invalid JSON received.");
				outWrite.println(resp.toString());
				return;
			}

			// Prepare response
			JSONObject response = new JSONObject();
			String type = request.optString("type", "unknown");
			String value = request.optString("value", "").trim();

			try {
				switch (state) {
					case WAITING_FOR_HELLO:
						if (type.equals("hello")) {
							response.put("type", "prompt");
							response.put("value",
									"Hello,\nPlease enter your name and age\n(name age, example: Nupur 21)"
							);
							state = GameState.WAITING_FOR_NAME_AGE;
						} else {
							response.put("type", "error");
							response.put("value", "Expected 'hello' to start.");
						}
						break;

					case WAITING_FOR_NAME_AGE:
						handleNameAge(response, type, value);
						break;

					case WAITING_FOR_CHOICE:
						handleChoice(response, type, value);
						break;

					case WAITING_FOR_ROUNDS:
						handleRounds(response, type, value);
						break;

					case WAITING_FOR_GUESS:
						handleGuess(response, type, value);
						break;
				}
			} catch (Exception e) {
				// Catch any unexpected runtime error
				response.put("type", "error");
				response.put("value", "An unexpected error occurred: " + e.getMessage());
				e.printStackTrace();
			}

			outWrite.println(response.toString());
		} catch (IOException e) {
			System.err.println("Network error handling client: " + e.getMessage());
		} catch (ClassNotFoundException e) {
			System.err.println("Unknown object type from client: " + e.getMessage());
		}
	}

	// -----------------------------
	// WAITING_FOR_NAME_AGE
	// -----------------------------
	private static void handleNameAge(JSONObject response, String type, String value) {
		if (!type.equals("input") || value.isEmpty()) {
			response.put("type", "error");
			response.put("value", "Please provide your name and age.");
			return;
		}
		String[] tokens = value.split("\\s+");
		if (tokens.length < 2) {
			handlePartialNameAge(response, tokens);
			return;
		}
		playerName = tokens[0];
		try {
			playerAge = Integer.parseInt(tokens[1]);
			response.put("type", "prompt");
			response.put("value",
					"Hello " + playerName + " (" + playerAge + ")\n"
							+ "Choose an option:\n"
							+ "1) See Leaderboard\n"
							+ "2) Start the game\n"
							+ "3) Quit"
			);
			state = GameState.WAITING_FOR_CHOICE;
		} catch (NumberFormatException nfe) {
			response.put("type", "error");
			response.put("value", "Age must be a valid integer. Try again.");
		}
	}

	// -----------------------------
	// WAITING_FOR_CHOICE
	// -----------------------------
	private static void handleChoice(JSONObject response, String type, String value) {
		if (!type.equals("input") || value.isEmpty()) {
			response.put("type", "error");
			response.put("value", "Please enter 1, 2 or 3.");
			return;
		}
		if (value.equals("1")) {
			// Show leaderboard
			String lb = getLeaderboardString();
			response.put("type", "prompt");
			response.put("value", lb);
			// remain in WAITING_FOR_CHOICE
		} else if (value.equals("2")) {
			// Ask how many rounds
			response.put("type", "prompt");
			response.put("value", "Please enter the number of rounds the game should last.");
			state = GameState.WAITING_FOR_ROUNDS;
		} else if (value.equals("3")) {
			// Quit
			response.put("type", "result");
			response.put("value", "Quitting. Thanks for visiting!\n(quitting)");
			// remain in WAITING_FOR_CHOICE
		} else {
			response.put("type", "error");
			response.put("value", "Please enter 1, 2 or 3.");
		}
	}

	// -----------------------------
	// WAITING_FOR_ROUNDS
	// -----------------------------
	private static void handleRounds(JSONObject response, String type, String value) {
		if (!type.equals("input") || value.isEmpty()) {
			response.put("type", "error");
			response.put("value", "Please enter a valid number of rounds.");
			return;
		}
		try {
			int num = Integer.parseInt(value);
			if (num <= 0) {
				response.put("type", "error");
				response.put("value", "Rounds must be an integer greater than 0, please try again.");
			} else {
				totalRounds = num;
				roundsLeft = num;
				currentRound = 1;
				selectRandomWonder();

				response.put("type", "prompt");
				response.put("image", "the image: " + getCurrentHintImage());
				response.put("value",
						"Great! We'll play for " + totalRounds + " rounds.\n"
								+ "Now starting round " + currentRound + " of " + totalRounds + ".\n"
								+ "Guess which wonder is shown."
				);
				// Move to guess state
				state = GameState.WAITING_FOR_GUESS;
			}
		} catch (NumberFormatException nfe) {
			response.put("type", "error");
			response.put("value", "Rounds must be an integer greater than 0, please try again.");
		}
	}

	// -----------------------------
	// WAITING_FOR_GUESS
	// -----------------------------
	/**
	 * The user can:
	 * - Type a guess (compare to currentCorrectAnswer)
	 * - Type "skip" (forfeit round, 0 points)
	 * - Type "next" (show next hint if available)
	 * - Type "remain"/"remaining" (show how many hints left)
	 */
	private static void handleGuess(JSONObject response, String type, String value) {
		if (!type.equals("input") || value.isEmpty()) {
			response.put("type", "error");
			response.put("value", "Please enter a guess (or 'skip', 'next', 'remain').");
			return;
		}
		String guess = value.toLowerCase();

		// 1) Correct guess
		if (guess.equals(currentCorrectAnswer)) {
			int remainingHints = (MAX_HINTS - 1) - hintIndex;  // e.g. if hintIndex=0 => 3 remain
			int scoreThisRound = remainingHints * 5 + 5;
			int newTotal = updateScore(playerName, scoreThisRound);

			roundsLeft--;
			response.put("type", "result");
			response.put("value",
					"Correct! You earned " + scoreThisRound + " points this round."
			);
			// Send total points to the client
			response.put("points", newTotal);

			if (roundsLeft > 0) {
				currentRound++;
				selectRandomWonder();
				response.put("image", "the image: " + getCurrentHintImage());
				response.put("value",
						"Correct! You earned " + scoreThisRound + " points.\n"
								+ "Now starting round " + currentRound + " of " + totalRounds + ".\n"
								+ "Rounds remaining: " + roundsLeft + "\n"
								+ "Guess which wonder is shown."
				);
			} else {
				endGame(response);
			}

			// 2) Skip => 0 points
		} else if (guess.equals("skip")) {
			roundsLeft--;
			response.put("type", "prompt");
			response.put("value", "You chose to skip this round (0 points).");

			if (roundsLeft > 0) {
				currentRound++;
				selectRandomWonder();
				response.put("image", "the image: " + getCurrentHintImage());
				response.put("value",
						"Now starting round " + currentRound + " of " + totalRounds + ".\n"
								+ "Rounds remaining: " + roundsLeft + "\n"
								+ "Guess which wonder is shown."
				);
			} else {
				endGame(response);
				response.put("type", "result");
				response.put("value",
						"You chose to skip the last round. Game over."
				);
			}

			// 3) Next => Show next hint if available
		} else if (guess.equals("next")) {
			if (hintIndex < MAX_HINTS - 1) {
				hintIndex++;
				response.put("type", "prompt");
				response.put("value",
						"Showing next hint for the same wonder. (Hint " + (hintIndex + 1) + "/" + MAX_HINTS + ")"
				);
				response.put("image", "the image: " + getCurrentHintImage());
			} else {
				response.put("type", "prompt");
				response.put("value", "No more hints for this wonder.");
			}

			// 4) Remain / Remaining => Show how many hints left
		} else if (guess.equals("remain") || guess.equals("remaining")) {
			int hintsLeft = (MAX_HINTS - 1) - hintIndex;
			response.put("type", "prompt");
			response.put("value",
					"Hints remaining for this wonder: " + hintsLeft
							+ "\n(You are currently on hint #" + (hintIndex + 1) + ")"
			);

			// 5) Incorrect guess
		} else {
			response.put("type", "result");
			response.put("value",
					"Incorrect guess. Try again!\n"
							+ "Or type 'skip', 'next', or 'remain'."
			);
		}
	}

	/**
	 * End the game, show final score, then return to WAITING_FOR_CHOICE (main menu).
	 */
	private static void endGame(JSONObject response) {
		int finalScore = leaderboard.getOrDefault(playerName, 0);
		if (finalScore == 0) {
			response.put("type", "result");
			response.put("value",
					"Game over. You lose! Your final score was 0.\n"
							+ "Returning to main menu...\n"
							+ "1) See Leaderboard\n2) Start the game\n3) Quit"
			);
		} else {
			response.put("type", "result");
			response.put("value",
					"Game over. You win! Your final score was " + finalScore + ".\n"
							+ "Returning to main menu...\n"
							+ "1) See Leaderboard\n2) Start the game\n3) Quit"
			);
		}
		// Return to main menu
		state = GameState.WAITING_FOR_CHOICE;
	}

	/**
	 * Updates the user's total score
	 */
	private static int updateScore(String name, int newScore) {
		int oldScore = leaderboard.getOrDefault(name, 0);
		int total = oldScore + newScore;
		leaderboard.put(name, total);
		System.out.println("Updated " + name + "'s total score to " + total);
		return total;
	}

	/**
	 * Picks a random wonder, resets hintIndex=0
	 */
	private static void selectRandomWonder() {
		Random rand = new Random();
		int index = rand.nextInt(wonderList.size());
		currentWonder = wonderList.get(index);
		currentCorrectAnswer = currentWonder.answer;
		hintIndex = 0;
		System.out.println("** The correct answer is: " + currentCorrectAnswer + " **");
	}

	/**
	 * Returns the current hint image path
	 */
	private static String getCurrentHintImage() {
		return currentWonder.images.get(hintIndex);
	}

	/**
	 * Builds and returns a sorted leaderboard string
	 */
	private static String getLeaderboardString() {
		List<Map.Entry<String,Integer>> entries = new ArrayList<>(leaderboard.entrySet());
		// Sort by total points descending
		entries.sort((a,b)-> b.getValue() - a.getValue());

		StringBuilder sb = new StringBuilder("Leaderboard:\n");
		if (entries.isEmpty()) {
			sb.append("No scores yet!\n");
		} else {
			for (Map.Entry<String,Integer> e : entries) {
				sb.append(e.getKey()).append(" - ").append(e.getValue()).append(" points\n");
			}
		}
		sb.append("\nChoose an option:\n1) See Leaderboard\n2) Start the game\n3) Quit");
		return sb.toString();
	}

	/**
	 * Handle partial name/age input
	 */
	private static void handlePartialNameAge(JSONObject response, String[] tokens) {
		if (tokens.length == 1) {
			try {
				Integer.parseInt(tokens[0]);
				response.put("type", "error");
				response.put("value",
						"You have only typed age. Please provide both name and age, e.g. \"Nupur 21\"."
				);
			} catch (NumberFormatException nfe) {
				response.put("type", "error");
				response.put("value",
						"You have only typed name. Please provide both name and age, e.g. \"Nupur 21\"."
				);
			}
		} else {
			response.put("type", "error");
			response.put("value",
					"Please provide both name and age, e.g. \"Nupur 21\"."
			);
		}
	}
}

