package Assign32starter;

import java.net.*;
import java.io.*;
import org.json.*;

/**
 * Minimal example: server requires "hello" first,
 * then requests name and age together, then proceeds.
 */
public class SockServer {
	private enum GameState { WAITING_FOR_HELLO, WAITING_FOR_NAME_AGE, WAITING_FOR_GUESS, FINISHED }
	private static GameState state = GameState.WAITING_FOR_HELLO;

	private static String playerName = "";
	private static int playerAge = 0;
	private static final String CORRECT_ANSWER = "colosseum"; // Example guess

	public static void main(String[] args) {
		try {
			ServerSocket serv = new ServerSocket(8888);
			System.out.println("Server ready for connection");

			while (true) {
				Socket sock = serv.accept(); // blocking wait
				ObjectInputStream in = new ObjectInputStream(sock.getInputStream());
				PrintWriter outWrite = new PrintWriter(sock.getOutputStream(), true);

				String inputStr = (String) in.readObject();
				JSONObject request = new JSONObject(inputStr);

				// Prepare JSON response
				JSONObject response = new JSONObject();
				String type = request.optString("type", "unknown");
				String value = request.optString("value", "").trim();

				switch (state) {
					case WAITING_FOR_HELLO:
						if (type.equals("hello")) {
							// Updated prompt message:
							response.put("type", "prompt");
							response.put("value", "Hello, \nPlease enter your name and age\n(name age, example: Nupur 21)");
							state = GameState.WAITING_FOR_NAME_AGE;
						} else {
							response.put("type", "error");
							response.put("message", "Expected 'hello' to start.");
						}
						break;

					case WAITING_FOR_NAME_AGE:
						if (type.equals("input") && !value.isEmpty()) {
							// Expect something like "Nupur 21"
							String[] tokens = value.split("\\s+");
							if (tokens.length < 2) {
								response.put("type", "error");
								response.put("message", "Please provide both name and age, e.g. \"Nupur 21\".");
							} else {
								playerName = tokens[0];
								try {
									playerAge = Integer.parseInt(tokens[1]);
									response.put("type", "prompt");
									response.put("value", "Thanks, " + playerName + " (age " + playerAge
											+ "). Now guess which wonder is shown. Hint: It's the Colosseum!");
									// Move to guess phase
									state = GameState.WAITING_FOR_GUESS;
								} catch (NumberFormatException nfe) {
									response.put("type", "error");
									response.put("message", "Age must be a valid integer. Try again.");
								}
							}
						} else {
							response.put("type", "error");
							response.put("message", "Please provide your name and age.");
						}
						break;

					case WAITING_FOR_GUESS:
						if (type.equals("input") && !value.isEmpty()) {
							if (value.equalsIgnoreCase(CORRECT_ANSWER)) {
								response.put("type", "result");
								response.put("value", "Correct! You guessed it right.");
								state = GameState.FINISHED;
							} else {
								response.put("type", "result");
								response.put("value", "Incorrect guess. Try again!");
							}
						} else {
							response.put("type", "error");
							response.put("message", "Please enter a guess.");
						}
						break;

					case FINISHED:
						response.put("type", "result");
						response.put("value", "Game over. Restart the application to play again.");
						break;

					default:
						response.put("type", "error");
						response.put("message", "Unknown state.");
				}

				// Send the JSON response back to the client
				outWrite.println(response.toString());
				sock.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}



