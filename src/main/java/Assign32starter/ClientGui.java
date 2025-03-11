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
 * Minimal example client that sends 'hello' first,
 * then sends user input for name+age, then guess.
 */
public class ClientGui implements OutputPanel.EventHandlers {
	JDialog frame;
	PicturePanel picPanel;
	OutputPanel outputPanel;

	Socket sock;
	OutputStream out;
	ObjectOutputStream os;
	BufferedReader bufferedReader;

	// Adjust these as needed
	String host = "localhost";
	int port = 8888;

	public ClientGui(String host, int port) throws IOException {
		this.host = host;
		this.port = port;

		frame = new JDialog();
		frame.setLayout(new GridBagLayout());
		frame.setMinimumSize(new Dimension(500, 500));
		frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		// Setup top picture panel
		picPanel = new PicturePanel();
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 0;
		c.weighty = 0.25;
		frame.add(picPanel, c);

		// Setup input, button, and output area
		c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 1;
		c.weighty = 0.75;
		c.weightx = 1;
		c.fill = GridBagConstraints.BOTH;
		outputPanel = new OutputPanel();
		outputPanel.addEventHandlers(this);
		frame.add(outputPanel, c);

		// Initialize a small 1x1 grid with a placeholder image
		picPanel.newGame(1);
		insertImage("img/Colosseum1.png", 0, 0);

		// Connect to server, send "hello", read the response
		open();
		try {
			JSONObject helloRequest = new JSONObject();
			helloRequest.put("type", "hello");
			helloRequest.put("value", "");
			os.writeObject(helloRequest.toString());
		} catch (IOException e) {
			e.printStackTrace();
		}

		String response = this.bufferedReader.readLine();
		System.out.println("Got a connection to server");
		JSONObject json = new JSONObject(response);
		outputPanel.appendOutput(json.optString("value"));
		close();
	}

	/**
	 * Show the GUI
	 */
	public void show(boolean makeModal) {
		frame.pack();
		frame.setModal(makeModal);
		frame.setVisible(true);
	}

	public void newGame(int dimension) {
		picPanel.newGame(dimension);
		outputPanel.appendOutput("Started new game with a " + dimension + "x" + dimension + " board.");
	}

	public boolean insertImage(String filename, int row, int col) throws IOException {
		try {
			if (picPanel.insertImage(filename, row, col)) {
				outputPanel.appendOutput("Inserting " + filename + " in position (" + row + ", " + col + ")");
				return true;
			}
			outputPanel.appendOutput("File(\"" + filename + "\") not found.");
		} catch (PicturePanel.InvalidCoordinateException e) {
			outputPanel.appendOutput(e.toString());
		}
		return false;
	}

	/**
	 * Called when the Submit button is clicked in the GUI.
	 * We simply send whatever the user typed to the server as "type":"input".
	 */
	@Override
	public void submitClicked() {
		try {
			open();
			String userInput = outputPanel.getInputText();

			JSONObject request = new JSONObject();
			request.put("type", "input");
			request.put("value", userInput);
			os.writeObject(request.toString());

			String responseStr = bufferedReader.readLine();
			JSONObject jsonResponse = new JSONObject(responseStr);
			outputPanel.appendOutput(jsonResponse.optString("value"));

			close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void inputUpdated(String input) {
		// Optionally do something if needed
	}

	public void open() throws UnknownHostException, IOException {
		this.sock = new Socket(host, port);
		this.out = sock.getOutputStream();
		this.os = new ObjectOutputStream(out);
		this.bufferedReader = new BufferedReader(new InputStreamReader(sock.getInputStream()));
	}

	public void close() {
		try {
			if (out != null) out.close();
			if (bufferedReader != null) bufferedReader.close();
			if (sock != null) sock.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws IOException {
		try {
			String host = "localhost";
			int port = 8888;
			ClientGui main = new ClientGui(host, port);
			main.show(true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
