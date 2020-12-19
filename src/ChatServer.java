import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

public class ChatServer{

	// A pre-allocated buffer for the received data
	static private final ByteBuffer buffer = ByteBuffer.allocate( 16384 );

	// Encoder and Decoder -- assume UTF-8
	static private final Charset charset = Charset.forName("UTF8");
	static private final CharsetEncoder encoder = charset.newEncoder();
	static private final CharsetDecoder decoder = charset.newDecoder();


	// Map and Set to store current named users and current rooms in use respectively
	static private Map<String,SelectionKey> userMap = new TreeMap<String, SelectionKey>();
	static private Map<String,ChatRoom> roomMap = new TreeMap<String, ChatRoom>();


	// Check if command line arguments are used correctly
	static private boolean checkArgs(String[] args){
		if(args.length != 1){
			System.out.println("Usage:$ java Server [PORT]");
			return false;
		}
		else{
			for(char c: args[0].toCharArray()){
				if(!Character.isDigit(c)){
					System.out.println("Usage:$ java Server [PORT]");
					return false;
				}
			}
		}
		return true;
	}

	// Auxiliary function for sending messages to a given client
	static private void send(SelectionKey sk, String message) throws IOException{
		SocketChannel sc = (SocketChannel) sk.channel();
		sc.write(encoder.encode(CharBuffer.wrap(message)));
	}

	// Send message to everyone in given room, set "flag" to prevent sending message to oneself
	static private void sendRoom(HashSet<SelectionKey> userSet, SelectionKey self, String message, boolean flag) throws IOException{
		if(!flag){
			for(SelectionKey sk : userSet){
				send(sk,message);
			}
		}
		else{
			for(SelectionKey sk : userSet){
				if(sk.equals(self)){ continue;}
				send(sk,message);
			}
		}
	}

	// Set user's nickname
	static private void setNickname(SelectionKey sk, String name) throws IOException{

		User curUser = (User)sk.attachment();

		// If name is taken immediately send an error message
		if(userMap.containsKey(name)){ send(sk,"ERROR\n"); return; }
		else{

			// Current user state -> "init"
			if(curUser.getState().equals("init")){
				curUser.setName(name);
				curUser.setState("outside");
				userMap.put(name,sk);
				send(sk,"OK\n");
				return;
			}

			// Current user state -> "outside"
			if(curUser.getState().equals("outside")){
				userMap.remove(curUser.getName());
				userMap.put(name,sk);
				send(sk,"OK\n");
				return;
			}

			// Current user state -> "inside"
			if(curUser.getState().equals("inside")){
				String curName = curUser.getName();
				userMap.remove(curName);
				userMap.put(name,sk);
				send(sk,"OK\n");
				sendRoom(roomMap.get(curUser.getRoom()).getUsers(),sk, "NEWNICK " + curName + " " + name + "\n", false);
				return;
			}
		}
	}

	// Send private message to given user
	static private void sendPrivateMessage(SelectionKey sk, String sendTo, String message) throws IOException{
		User curUser = (User)sk.attachment();
		SelectionKey receiver = userMap.get(sendTo);

		// Check if both sender and receiver have a nickname assigned
		if(receiver != null && curUser.getState() != "init"){
			send(receiver, "PRIVATE " + curUser.getName() + " "  + message + "\n");
			return;
		}
		send(sk,"ERROR\n");
	}

	// Add user to the given chat room
	static private void joinRoom(SelectionKey sk, String roomName) throws IOException{
		User curUser = (User)sk.attachment();

		// Check if room exists
		if(!roomMap.containsKey(roomName)){
			roomMap.put(roomName, new ChatRoom(roomName));
		}

		// Current user state -> "init"
		if(curUser.getState().equals("init")){ send(sk,"ERROR\n"); return;}

		// Current user state -> "outside"
		if(curUser.getState().equals("outside")){
			roomMap.get(roomName).addUser(sk);
			curUser.setRoom(roomName);
			curUser.setState("inside");
			send(sk,"OK\n");
			sendRoom(roomMap.get(roomName).getUsers(), sk, "JOINED " + curUser.getName() + "\n", false);
			return;
		}

		// Current user state -> "inside"
		if(curUser.getState().equals("inside")){
			leave(sk);
			roomMap.get(roomName).addUser(sk);
			curUser.setRoom(roomName);
			curUser.setState("inside");
			send(sk,"OK\n");
			sendRoom(roomMap.get(roomName).getUsers(), sk, "JOINED " + curUser.getName() + "\n", false);
			return;
		}

	}

	// Remove user from the chat room they are currently in
	private static boolean leave(SelectionKey sk) throws IOException{
		User curUser = (User)sk.attachment();

		// Send error if user state is "init" or "outside"
		if(!curUser.getState().equals("inside")){
			send(sk,"ERROR\n");
			return false;
		}

		ChatRoom curRoom = roomMap.get(curUser.getRoom());

		// Remove user from room and update user object accordingly
		curRoom.removeUser(sk);
		curUser.setRoom(null);
		curUser.setState("outside");

		// Check if room is empty and if so delete it
		if(curRoom.getUsers().isEmpty()){
			roomMap.remove(curRoom.getName());
			return true;
		}
		sendRoom(curRoom.getUsers(),sk,"LEFT " + curUser.getName() + "\n", true);
		return true;
	}

	// Close connection to client.
	static private void bye(SelectionKey sk) throws IOException{
		User curUser = (User)sk.attachment();

		// If user is in a chat room, make sure they leave before disconnecting
		if(curUser.getState().equals("inside")){ leave(sk); }

		// Check if user was ever assigned a nick name and if so remove it
		if(!curUser.getState().equals("init")){ userMap.remove(curUser.getName()); }

		send(sk,"BYE\n");

		SocketChannel sc = (SocketChannel)sk.channel();
		Socket s = null;
		try{
			s = sc.socket();
     		System.out.println( "Closing connection to "+s );
     		s.close();
		}
		catch(IOException e){
   		System.err.println( "Error closing socket "+s+": "+e );
    	}

	}

	// Process user commands
	static private void processCommand(SelectionKey sk, String[] command, String message) throws IOException{
		switch(command[0].strip()){
			case "/nick":
				if(command.length != 2){ send(sk,"ERROR\n"); break; }
				setNickname(sk,command[1].strip());
				break;
			case "/priv":
				if(command.length < 3){ send(sk,"ERROR\n"); break; }
				sendPrivateMessage(sk,command[1].strip(), message.substring(command[1].length()+7));
				break;
			case "/join":
				if(command.length != 2){ send(sk,"ERROR\n"); break; }
				joinRoom(sk,command[1].strip());
				break;
			case "/leave":
				if(command.length != 1){ send(sk,"ERROR\n"); break; }
				if(leave(sk)){send(sk,"OK\n");}
				break;
			case "/bye":
				if(command.length != 1){ send(sk,"ERROR\n"); break; }
				bye(sk);
				break;
			default:
				send(sk,"ERROR\n");
				break;
			}
	}

	// Read the message from the socket and process it accordingly
	static private boolean processInput(SelectionKey sk, SocketChannel sc) throws IOException {

		// Get current user object
		User curUser = (User)sk.attachment();

		// Read the message to the buffer
		buffer.clear();
		sc.read( buffer );
		buffer.flip();

		// If no data, close the connection
		if (buffer.limit()==0) {
			return false;
		}

		// Decode and print the message to stdout
		String message = decoder.decode(buffer).toString();
		System.out.print( message );

		String[] commands = message.split("\n");

		for(String line : commands){
			String[] splitLine = line.split(" ");
			if(line.charAt(0) == '/'){ processCommand(sk,splitLine,line);}
			else{
				if(curUser.getState().equals("inside")){sendRoom(roomMap.get(curUser.getRoom()).getUsers(),sk,"MESSAGE " + curUser.getName()  + " " + line + "\n",false);}
				else{ send(sk,"ERROR\n"); }
			}
		}

		return true;
	}

	static public void main( String args[] ) throws Exception {

		if(!checkArgs(args)){ return; }

			// Parse port from command line
   		int port = Integer.parseInt( args[0] );

   		try {

	     		// Instead of creating a ServerSocket, create a ServerSocketChannel
   	  		ServerSocketChannel ssc = ServerSocketChannel.open();

     			// Set it to non-blocking, so we can use select
     			ssc.configureBlocking(false);

     			// Get the Socket connected to this channel, and bind it to the
     			// listening port
     			ServerSocket ss = ssc.socket();
     			InetSocketAddress isa = new InetSocketAddress( port );
     			ss.bind( isa );

     			// Create a new Selector for selecting
	     		Selector selector = Selector.open();

   	  		// Register the ServerSocketChannel, so we can listen for incoming
     			// connections
     			ssc.register( selector, SelectionKey.OP_ACCEPT );
     			System.out.println( "Listening on port "+port );

     			while (true) {

       				// See if we've had any activity -- either an incoming connection,
		       		// or incoming data on an existing connection
       				int num = selector.select();

       				// If we don't have any activity, loop around and wait again
       				if (num == 0) {
    		     			continue;
       				}

       				// Get the keys corresponding to the activity that has been
       				// detected, and process them one by one
	       			Set<SelectionKey> keys = selector.selectedKeys();
   	    			Iterator<SelectionKey> it = keys.iterator();

	       			while (it.hasNext()) {

   	      			// Get a key representing one of bits of I/O activity
      	   			SelectionKey sk = it.next();

         				// What kind of activity is it?
         				if (sk.isAcceptable()) {

	           				// It's an incoming connection.  Register this socket with
   	        				// the Selector so we can listen for input on it
      	     				Socket s = ss.accept();
         	  				System.out.println( "Got connection from "+s );

           					// Make sure to make it non-blocking, so we can use a selector
           					// on it.
           					SocketChannel sc = s.getChannel();
	           				sc.configureBlocking( false );

   	        				// Register it with the selector, for reading
      	     				sc.register( selector, SelectionKey.OP_READ );

         				}
         				else if (sk.isReadable()) {

           					SocketChannel sc = null;

           					try {

									//  Attach new 'User' object to key in case of new user
									if(sk.attachment() == null){ sk.attach(new User());}

         	   				// It's incoming data on a connection -- process it
            					sc = (SocketChannel)sk.channel();
            					boolean ok = processInput(sk,sc);

            					// If the connection is dead, remove it from the selector
            					// and close it
            					if (!ok) {
            						bye(sk);
              						sk.cancel();

								}

						} catch( IOException ie ) {

							// On exception, remove this channel from the selector
							sk.cancel();

							try {
                			sc.close();
							} catch( IOException ie2 ) { System.out.println( ie2 ); }

							System.out.println( "Closed "+sc );
						}
					}
				}

					// We remove the selected keys, because we've dealt with them.
					keys.clear();
			}
		} catch( IOException ie ) {
			System.err.println( ie );
		}
	}
}
