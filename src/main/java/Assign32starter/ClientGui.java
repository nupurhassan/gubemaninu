package Assign32starter;

import java.awt.Dimension;
import org.json.*;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import javax.swing.JDialog;
import javax.swing.WindowConstants;

/**
 * ClientGui.java
 *
 * - Sends user input as JSON ("type":"input","value":...).
 * - Receives server responses and displays them.
 * - Clears the input field after each submission.
 * - If "points" is in the JSON, updates the label "Current Points this round: X".
 * - If "quitting" is in the server message, exits the application.
 * - If "game over" is in the server message, prompts user to return to main menu.
 */
public class ClientGui implements OutputPanel.EventHandlers {
	JDialog frame;
	PicturePanel picPanel;
	OutputPanel outputPanel;

	Socket sock;
	OutputStream out;
	ObjectOutputStream os;
	BufferedReader bufferedReader;

	String host = "localhost";
	int port = 8888;

	public ClientGui(String host, int port) throws IOException {
		this.host = host;
		this.port = port;

		frame = new JDialog();
		frame.setLayout(new GridBagLayout());
		frame.setMinimumSize(new Dimension(500, 500));
		frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		// Setup the top picture panel
		picPanel = new PicturePanel();
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 0;
		c.weighty = 0.25;
		frame.add(picPanel, c);

		// Setup the input, button, and output area
		c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 1;
		c.weighty = 0.75;
		c.weightx = 1;
		c.fill = GridBagConstraints.BOTH;
		outputPanel = new OutputPanel();
		outputPanel.addEventHandlers(this);
		frame.add(outputPanel, c);

		// Initialize a 1x1 grid with a placeholder image
		picPanel.newGame(1);
		try {
			insertImage("img/hi.png", 0, 0);
		} catch (IOException e) {
			e.printStackTrace();
		}

		// Do an initial handshake with the server
		connectAndSayHello();
	}

	public void show(boolean makeModal) {
		frame.pack();
		frame.setModal(makeModal);
		frame.setVisible(true);
	}

	public boolean insertImage(String filename, int row, int col) throws IOException {
		System.out.println("[CLIENT] Inserting image: " + filename);
		try {
			if (picPanel.insertImage(filename, row, col)) {
				outputPanel.appendOutput("Displaying " + filename + " at (" + row + ", " + col + ")");
				return true;
			}
			outputPanel.appendOutput("File (" + filename + ") not found.");
		} catch (PicturePanel.InvalidCoordinateException e) {
			outputPanel.appendOutput(e.toString());
		}
		return false;
	}

	@Override
	public void submitClicked() {
		System.out.println("[CLIENT] Submit clicked.");
		try {
			open();

			String userInput = outputPanel.getInputText();
			JSONObject request = new JSONObject();
			request.put("type", "input");
			request.put("value", userInput);
			System.out.println("[CLIENT] Sending: " + request.toString());
			os.writeObject(request.toString());

			// Clear the input field after sending
			outputPanel.setInputText("");

			// Wait for server response
			String responseStr = bufferedReader.readLine();
			if (responseStr == null) {
				outputPanel.appendOutput("Server disconnected unexpectedly.");
				return;
			}
			System.out.println("[CLIENT] Received: " + responseStr);

			JSONObject jsonResponse = new JSONObject(responseStr);
			String serverMessage = jsonResponse.optString("value", "");
			outputPanel.appendOutput(serverMessage);

			// If server included "points", update the UI label
			if (jsonResponse.has("points")) {
				int points = jsonResponse.getInt("points");
				outputPanel.setPoints(points);  // "Current Points this round: X"
			}

			// If there's an image, display it
			if (jsonResponse.has("image")) {
				String imageStr = jsonResponse.optString("image");
				// e.g. "the image: img/Colosseum2.png"
				String[] tokens = imageStr.split(" ");
				String fileName = tokens[tokens.length - 1];
				picPanel.newGame(1);
				insertImage(fileName, 0, 0);
			}

			// If user chose to quit
			if (serverMessage.toLowerCase().contains("quitting")) {
				outputPanel.appendOutput("Exiting client...");
				System.exit(0);
			}

			// If game over, prompt for main menu
			if (serverMessage.toLowerCase().contains("game over")) {
				outputPanel.appendOutput("Returning to main menu.\nType '1' for Leaderboard, '2' to start new game, or '3' to quit.");
			}

			close();
		} catch (IOException e) {
			outputPanel.appendOutput("Network error: " + e.getMessage());
			e.printStackTrace();
		}
	}

	@Override
	public void inputUpdated(String input) {
		// optional
	}

	public void open() throws UnknownHostException, IOException {
		this.sock = new Socket(host, port);
		this.out = sock.getOutputStream();
		this.os = new ObjectOutputStream(out);
		this.bufferedReader = new BufferedReader(new InputStreamReader(sock.getInputStream()));
		System.out.println("[CLIENT] Connection opened to " + host + ":" + port);
	}

	public void close() {
		System.out.println("[CLIENT] Closing connection.");
		try {
			if (os != null) os.close();
			if (bufferedReader != null) bufferedReader.close();
			if (sock != null) sock.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void connectAndSayHello() {
		try {
			open();
			JSONObject helloReq = new JSONObject();
			helloReq.put("type", "hello");
			helloReq.put("value", "");
			System.out.println("[CLIENT] Sending initial hello: " + helloReq.toString());
			os.writeObject(helloReq.toString());

			String resp = bufferedReader.readLine();
			System.out.println("[CLIENT] Received handshake: " + resp);
			JSONObject jResp = new JSONObject(resp);
			outputPanel.appendOutput(jResp.optString("value", ""));
			close();
		} catch (IOException | JSONException e) {
			outputPanel.appendOutput("Error during handshake: " + e.getMessage());
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		try {
			ClientGui client = new ClientGui("localhost", 8888);
			client.show(true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
