import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;

public class ChatClient {

	// Attributes

   // GUI variables
   JFrame frame = new JFrame("Chat Client");
   private JTextField chatBox = new JTextField();
   private JTextArea chatArea = new JTextArea();

	// SocketChannel for communicating with server
	private SocketChannel clientSocket;

	// Encoder -- assume UTF-8
	static private final Charset charset = Charset.forName("UTF-8");
	static private final CharsetEncoder encoder = charset.newEncoder();

	// Construtor
	public ChatClient(String server, int port) throws IOException {

        clientSocket = SocketChannel.open();
        clientSocket.configureBlocking(true);
        clientSocket.connect(new InetSocketAddress(server, port));

        // GUI variables
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(chatBox);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.setSize(500, 300);
        frame.setVisible(true);
        chatArea.setEditable(false);
        chatBox.setEditable(true);
        chatBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    newMessage(chatBox.getText());
                } catch (IOException ex) {
                } finally {
                   chatBox.setText("");
                }
            }
        });
    }

	// Methods

	public void printMessage(String message) {
		String[] words = message.split(" ");
		switch(words[0]){
			case "NEWNICK":
				message = words[1] + " changed his nickname to: " + words[2];
				break;

			case "JOINED":
				message = words[1] + " joined the room";
				break;

			case "LEFT":
				message = words[1] + " left the room";
				break;

			case "MESSAGE":
				message = message.replaceFirst("MESSAGE","").replaceFirst(words[1],"");
				message = words[1] + ":" + message;
				break;

			case "PRIVATE":
				message = message.replaceFirst("PRIVATE","").replaceFirst(words[1],"");
				message = words[1] + ":" + message;
				break;
		}
      System.out.println("PRINTING TO CHAT: " + message);
      chatArea.append(message + "\n");
    }

	public void newMessage(String message) throws IOException {
		clientSocket.write(encoder.encode(CharBuffer.wrap(message + "\n")));
	}

	public void run() throws IOException {
		BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientSocket.socket().getInputStream()));

		while(true){
			String message = inFromServer.readLine();
			if (message == null){ break; }
         System.out.println("FROM SERVER: " + message);
         message = message.trim();

         printMessage(message);
         int len = chatArea.getDocument().getLength();
         chatArea.setCaretPosition(len);
     	}
      	clientSocket.close();
      	System.exit(0);
    }

    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }
}
