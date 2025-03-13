package Assign32starter;

import java.net.*;
import java.io.*;
import java.util.*;
import org.json.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

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
	// LEADERBOARD (Persistent)
	// ---------------------------
	private static Map<String, Integer> leaderboard = new HashMap<>();
	private static final String LEADERBOARD_FILE = "leaderboard.json";

	// ---------------------------
	// NEW: HINT CLASS
	// ---------------------------
	private static class Hint {
		String image;
		String funnyText;
		Hint(String image, String funnyText) {
			this.image = image;
			this.funnyText = funnyText;
		}
	}

	// ---------------------------
	// WONDER DATA – now with a list of Hint objects
	// ---------------------------
	private static class Wonder {
		List<Hint> hints;
		String answer;
		Wonder(List<Hint> hints, String answer) {
			this.hints = hints;
			this.answer = answer.toLowerCase();
		}
	}

	// Define wonders with 4 funny hints each.
	private static List<Wonder> wonderList = new ArrayList<>(Arrays.asList(
			new Wonder(
					Arrays.asList(
							new Hint("img/Colosseum1.png", "Hint 1: Looks like a giant pizza oven in ancient Rome!"),
							new Hint("img/Colosseum2.png", "Hint 2: An arena where gladiators once battled (and maybe joked around)."),
							new Hint("img/Colosseum3.png", "Hint 3: Imagine gladiators doing stand-up comedy here!"),
							new Hint("img/Colosseum4.png", "Hint 4: Once hosted wild chariot races—faster than today's NASCAR!")
					),
					"colosseum"
			),
			new Wonder(
					Arrays.asList(
							new Hint("img/GrandCanyon1.png", "Hint 1: A giant crack in the Earth—nature’s artwork!"),
							new Hint("img/GrandCanyon2.png", "Hint 2: Not a swimming pool, but massive and awe-inspiring."),
							new Hint("img/GrandCanyon3.png", "Hint 3: Even the birds seem to pause and take a look."),
							new Hint("img/GrandCanyon4.png", "Hint 4: Carved over millions of years, it's simply breathtaking.")
					),
					"grand canyon"
			),
			new Wonder(
					Arrays.asList(
							new Hint("img/Stonehenge1.png", "Hint 1: Massive stones arranged like nature’s rock concert stage!"),
							new Hint("img/Stonehenge2.png", "Hint 2: Looks like a prehistoric parking lot for giant rocks."),
							new Hint("img/Stonehenge3.png", "Hint 3: The ultimate stone puzzle – nature’s Lego without instructions!"),
							new Hint("img/Stonehenge4.png", "Hint 4: Mysterious and ancient; a timeless gathering of stones.")
					),
					"stonehenge"
			)
	));

	// ---------------------------
	// CURRENT WONDER & HINT INFO
	// ---------------------------
	private static Wonder currentWonder = null;
	private static int hintIndex = 0;  // Valid values 0..3 for 4 hints.
	private static final int MAX_HINTS = 4;
	private static String currentCorrectAnswer = "";

	// ---------------------------
	// For random operations
	// ---------------------------
	private static final Random random = new Random();

	// ---------------------------
	// NEW: Flags for placeholder behavior
	// ---------------------------
	private static boolean placeholderActive = false;
	private static String pendingCommand = "";

	public static void main(String[] args) {
		// Load persistent leaderboard if available.
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
	 * Handle a client connection by reading a JSON request and sending responses.
	 * Some commands (like skip/next) may send two responses.
	 */
	private static void handleClient(Socket sock) {
		try (ObjectInputStream in = new ObjectInputStream(sock.getInputStream());
			 PrintWriter outWrite = new PrintWriter(sock.getOutputStream(), true)) {

			String inputStr = (String) in.readObject();
			System.out.println("Server received: " + inputStr);

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

			JSONObject response = new JSONObject();
			String type = request.optString("type", "unknown");
			String value = request.optString("value", "").trim();

			try {
				switch (state) {
					case WAITING_FOR_HELLO:
						if (type.equals("hello")) {
							response.put("type", "prompt");
							response.put("value", "Hello,\nPlease enter your name and age\n(name age, example: Nupur 21)");
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
						handleGuess(response, type, value, outWrite);
						if (response.length() == 0) return;
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
			response.put("value", "Hello " + playerName + " (" + playerAge + ")\n"
					+ "Choose an option:\n1) See Leaderboard\n2) Start the game\n3) Quit");
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
			String lb = getLeaderboardString();
			response.put("type", "prompt");
			response.put("value", lb);
		} else if (value.equals("2")) {
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
				response.put("value", "Great! We'll play for " + totalRounds + " rounds.\n"
						+ "Now starting round " + currentRound + " of " + totalRounds + ".\n"
						+ "Guess which wonder is shown.\n"
						+ getCurrentHintText());
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
	 * In the guessing state the user may:
	 * - Type a guess.
	 * - Type "skip" to discard the current wonder.
	 *   When "skip" is first typed, a placeholder is sent and the command is pending.
	 *   The user must type "next" to reveal a new wonder.
	 * - Type "next" similarly for showing the next hint.
	 * - Type "remain" or "remaining" to see how many hints remain.
	 */
	private static void handleGuess(JSONObject response, String type, String value, PrintWriter outWrite) {
		if (!type.equals("input") || value.isEmpty()) {
			response.put("type", "error");
			response.put("value", "Please enter a guess (or 'skip', 'next', 'remain').");
			return;
		}
		String command = value.toLowerCase();

		// If a placeholder is active, only "next" is allowed to clear it.
		if (placeholderActive) {
			if (!command.equals("next")) {
				response.put("type", "error");
				response.put("value", "Please type 'next' to reveal the wonder.");
				return;
			} else {
				if (pendingCommand.equals("skip")) {
					selectRandomWonder();
					JSONObject newWonderResp = new JSONObject();
					newWonderResp.put("type", "prompt");
					newWonderResp.put("image", "the image: " + getCurrentHintImage());
					newWonderResp.put("value", "Here is a new wonder for round " + currentRound + " of " + totalRounds + ".\nGuess which wonder is shown.\n" + getCurrentHintText());
					outWrite.println(newWonderResp.toString());
				} else if (pendingCommand.equals("next")) {
					if (hintIndex < MAX_HINTS - 1) {
						hintIndex++;
						JSONObject nextHintResp = new JSONObject();
						nextHintResp.put("type", "prompt");
						nextHintResp.put("value", "Showing next hint for the same wonder. (Hint " + (hintIndex + 1) + "/" + MAX_HINTS + ")\n" + getCurrentHintText());
						nextHintResp.put("image", "the image: " + getCurrentHintImage());
						outWrite.println(nextHintResp.toString());
					} else {
						JSONObject noMoreHintsResp = new JSONObject();
						noMoreHintsResp.put("type", "prompt");
						noMoreHintsResp.put("value", "No more hints for this wonder.");
						outWrite.println(noMoreHintsResp.toString());
					}
				}
				placeholderActive = false;
				pendingCommand = "";
				return;
			}
		}

		// If no placeholder is active, check if command is "skip" or "next".
		if (command.equals("skip") || command.equals("next")) {
			placeholderActive = true;
			pendingCommand = command;
			JSONObject placeholderResp = new JSONObject();
			if (command.equals("skip")) {
				placeholderResp.put("value", "You chose to skip this wonder (0 points awarded).");
			} else {
				placeholderResp.put("value", "Preparing to show the next hint...");
			}
			placeholderResp.put("type", "prompt");
			placeholderResp.put("image", "the image: img/placeholder.png");
			outWrite.println(placeholderResp.toString());
			return;
		}

		// "remain" or "remaining": show number of hints remaining.
		if (command.equals("remain") || command.equals("remaining")) {
			int hintsLeft = (MAX_HINTS - 1) - hintIndex;
			response.put("type", "prompt");
			response.put("value", "Hints remaining for this wonder: " + hintsLeft
					+ "\n(You are currently on hint #" + (hintIndex + 1) + ")");
			return;
		}

		// Otherwise, treat input as a guess.
		if (command.equals(currentCorrectAnswer)) {
			int remainingHints = (MAX_HINTS - 1) - hintIndex;
			int scoreThisRound = remainingHints * 5 + 5;
			int newTotal = updateScore(playerName, scoreThisRound);
			roundsLeft--;
			response.put("type", "result");
			response.put("value", "Correct! You earned " + scoreThisRound + " points this round.");
			response.put("points", newTotal);
			if (roundsLeft > 0) {
				currentRound++;
				selectRandomWonder();
				response.put("image", "the image: " + getCurrentHintImage());
				response.put("value", "Correct! You earned " + scoreThisRound + " points.\nNow starting round " + currentRound + " of " + totalRounds
						+ ".\nRounds remaining: " + roundsLeft + "\nGuess which wonder is shown.\n" + getCurrentHintText());
			} else {
				endGame(response);
			}
		} else {
			response.put("type", "result");
			response.put("value", "Incorrect guess. Try again!\nOr type 'skip', 'next', or 'remain'.");
		}
	}

	// -----------------------------
	// END GAME
	// -----------------------------
	private static void endGame(JSONObject response) {
		int finalScore = leaderboard.getOrDefault(playerName, 0);
		if (finalScore == 0) {
			response.put("type", "result");
			response.put("value", "Game over. You lose! Your final score was 0.\nReturning to main menu...\n1) See Leaderboard\n2) Start the game\n3) Quit");
			response.put("image", "the image: img/lose.jpg");
		} else {
			response.put("type", "result");
			response.put("value", "Game over. You win! Your final score was " + finalScore + ".\nReturning to main menu...\n1) See Leaderboard\n2) Start the game\n3) Quit");
			response.put("image", "the image: img/win.jpg");
		}
		state = GameState.WAITING_FOR_CHOICE;
	}

	// -----------------------------
	// UPDATE SCORE & SAVE LEADERBOARD
	// -----------------------------
	private static int updateScore(String name, int newScore) {
		int oldScore = leaderboard.getOrDefault(name, 0);
		int total = oldScore + newScore;
		leaderboard.put(name, total);
		System.out.println("Updated " + name + "'s total score to " + total);
		saveLeaderboard();
		return total;
	}

	// -----------------------------
	// SELECT RANDOM WONDER & SHUFFLE HINTS
	// -----------------------------
	private static void selectRandomWonder() {
		int index = random.nextInt(wonderList.size());
		Wonder base = wonderList.get(index);
		// Create a new list from the base hints and shuffle to randomize order.
		List<Hint> shuffled = new ArrayList<>(base.hints);
		Collections.shuffle(shuffled, random);
		currentWonder = new Wonder(shuffled, base.answer);
		currentCorrectAnswer = currentWonder.answer;
		hintIndex = 0;
		System.out.println("** The correct answer is: " + currentCorrectAnswer + " **");
	}

	// -----------------------------
	// GET CURRENT HINT IMAGE & TEXT
	// -----------------------------
	private static String getCurrentHintImage() {
		return currentWonder.hints.get(hintIndex).image;
	}

	private static String getCurrentHintText() {
		return currentWonder.hints.get(hintIndex).funnyText;
	}

	// -----------------------------
	// GET LEADERBOARD STRING
	// -----------------------------
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

	// -----------------------------
	// LOAD & SAVE LEADERBOARD
	// -----------------------------
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

	// -----------------------------
	// HANDLE PARTIAL NAME/AGE INPUT
	// -----------------------------
	private static void handlePartialNameAge(JSONObject response, String[] tokens) {
		if (tokens.length == 1) {
			try {
				Integer.parseInt(tokens[0]);
				response.put("type", "error");
				response.put("value", "You have only typed age. Please provide both name and age, e.g. \"Nupur 21\".");
			} catch (NumberFormatException nfe) {
				response.put("type", "error");
				response.put("value", "You have only typed name. Please provide both name and age, e.g. \"Nupur 21\".");
			}
		} else {
			response.put("type", "error");
			response.put("value", "Please provide both name and age, e.g. \"Nupur 21\".");
		}
	}
}

