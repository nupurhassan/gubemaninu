package Assign32starter;

import java.net.*;
import java.io.*;
import java.util.*;
import org.json.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

/**
 * SockServer
 * ----------
 * Features:
 * 1) Multiple Wonders (4 hints each)
 * 2) Round-limited: user chooses # of rounds
 * 3) Supports commands: skip, next, remain, guess
 * 4) Score formula: (remainingHints * 5) + 5 for correct guess
 * 5) Persistent Leaderboard (stored in leaderboard.json)
 * 6) Displays "win.jpg" if user wins (>0 points) and "lose.jpg" if score is 0
 * 7) Option B: When "skip" is typed, a new wonder is chosen for the same round.
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

	// ---------------------------
	// LEADERBOARD
	// ---------------------------
	private static Map<String, Integer> leaderboard = new HashMap<>();
	private static final String LEADERBOARD_FILE = "leaderboard.json";

	// ---------------------------
	// WONDER DATA
	// ---------------------------
	private static class Wonder {
		List<String> images;
		String answer;
		Wonder(List<String> images, String answer) {
			this.images = images;
			this.answer = answer.toLowerCase();
		}
	}

	// List of wonders (with 4 hints each)
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

	// Current wonder/hint information
	private static Wonder currentWonder = null;
	private static int hintIndex = 0;  // 0..3 for 4 hints
	private static final int MAX_HINTS = 4;
	private static String currentCorrectAnswer = "";

	public static void main(String[] args) {
		// Load persistent leaderboard if available
		loadLeaderboard();

		try (ServerSocket serv = new ServerSocket(8888)) {
			System.out.println("Server ready for connection on port 8888");

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
	 * Handle a client connection by reading a JSON request and sending a JSON response.
	 */
	private static void handleClient(Socket sock) {
		try (ObjectInputStream in = new ObjectInputStream(sock.getInputStream());
			 PrintWriter outWrite = new PrintWriter(sock.getOutputStream(), true)) {

			String inputStr = (String) in.readObject();
			System.out.println("Server received: " + inputStr);

			// Parse JSON request
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

			// Prepare response JSON
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
		} else if (value.equals("2")) {
			// Ask how many rounds to play
			response.put("type", "prompt");
			response.put("value", "Please enter the number of rounds the game should last.");
			state = GameState.WAITING_FOR_ROUNDS;
		} else if (value.equals("3")) {
			response.put("type", "result");
			response.put("value", "Quitting. Thanks for visiting!\n(quitting)");
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
	 * - Type a guess (to be compared to the current wonder's answer)
	 * - Type "skip" to discard the current wonder and get a new one for the same round (Option B)
	 * - Type "next" to show the next hint if available
	 * - Type "remain" or "remaining" to see how many hints remain
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
			int remainingHints = (MAX_HINTS - 1) - hintIndex;
			int scoreThisRound = remainingHints * 5 + 5;
			int newTotal = updateScore(playerName, scoreThisRound);

			roundsLeft--;
			response.put("type", "result");
			response.put("value",
					"Correct! You earned " + scoreThisRound + " points this round."
			);
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

			// 2) Skip (Option B: Does NOT end the round; a new wonder is chosen for the same round)
		} else if (guess.equals("skip")) {
			response.put("type", "prompt");
			response.put("value", "You chose to skip this wonder (0 points awarded).");

			// Do NOT decrement roundsLeft or increment currentRound.
			// Instead, pick a new wonder for the same round.
			selectRandomWonder();
			response.put("image", "the image: " + getCurrentHintImage());
			response.put("value",
					"Here is a new wonder for round " + currentRound + " of " + totalRounds + ".\n"
							+ "Guess which wonder is shown."
			);

			// 3) Next => Show the next hint if available
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

			// 4) Remain / Remaining => Show how many hints remain
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
	 * Ends the game by showing the final score and then returning to the main menu.
	 * If the final score is 0, sends "lose.jpg"; otherwise, sends "win.jpg".
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
			response.put("image", "the image: img/lose.jpg");
		} else {
			response.put("type", "result");
			response.put("value",
					"Game over. You win! Your final score was " + finalScore + ".\n"
							+ "Returning to main menu...\n"
							+ "1) See Leaderboard\n2) Start the game\n3) Quit"
			);
			response.put("image", "the image: img/win.jpg");
		}
		state = GameState.WAITING_FOR_CHOICE;
	}

	/**
	 * Updates the user's score and saves the leaderboard to file.
	 */
	private static int updateScore(String name, int newScore) {
		int oldScore = leaderboard.getOrDefault(name, 0);
		int total = oldScore + newScore;
		leaderboard.put(name, total);
		System.out.println("Updated " + name + "'s total score to " + total);
		saveLeaderboard();
		return total;
	}

	/**
	 * Picks a random wonder from the list and resets hintIndex.
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
	 * Returns the current hint image path.
	 */
	private static String getCurrentHintImage() {
		return currentWonder.images.get(hintIndex);
	}

	/**
	 * Builds and returns a sorted leaderboard string.
	 */
	private static String getLeaderboardString() {
		List<Map.Entry<String, Integer>> entries = new ArrayList<>(leaderboard.entrySet());
		entries.sort((a, b) -> b.getValue() - a.getValue());
		StringBuilder sb = new StringBuilder("Leaderboard:\n");
		if (entries.isEmpty()) {
			sb.append("No scores yet!\n");
		} else {
			for (Map.Entry<String, Integer> e : entries) {
				sb.append(e.getKey()).append(" - ").append(e.getValue()).append(" points\n");
			}
		}
		sb.append("\nChoose an option:\n1) See Leaderboard\n2) Start the game\n3) Quit");
		return sb.toString();
	}

	/**
	 * Loads the leaderboard from the JSON file into the leaderboard map.
	 */
	private static void loadLeaderboard() {
		File file = new File(LEADERBOARD_FILE);
		if (!file.exists()) {
			System.out.println("No leaderboard file found, starting fresh.");
			return;
		}
		try {
			String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
			JSONObject obj = new JSONObject(content);
			for (String key : obj.keySet()) {
				leaderboard.put(key, obj.getInt(key));
			}
			System.out.println("Loaded leaderboard from " + LEADERBOARD_FILE);
		} catch (IOException | JSONException e) {
			e.printStackTrace();
			System.out.println("Failed to load leaderboard. Starting with empty data.");
		}
	}

	/**
	 * Saves the leaderboard map to the JSON file.
	 */
	private static void saveLeaderboard() {
		JSONObject obj = new JSONObject();
		for (Map.Entry<String, Integer> e : leaderboard.entrySet()) {
			obj.put(e.getKey(), e.getValue());
		}
		try {
			Files.write(Paths.get(LEADERBOARD_FILE), obj.toString(2).getBytes(StandardCharsets.UTF_8));
			System.out.println("Saved leaderboard to " + LEADERBOARD_FILE);
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Failed to save leaderboard.");
		}
	}

	/**
	 * Handles the case where the user provides only partial name/age input.
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


