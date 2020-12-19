import java.util.*;
import java.nio.channels.*;

// Class to store chat room name and set of current users
public class ChatRoom{

	// Attributes
	private String roomName;
	private HashSet<SelectionKey> curUsers = new HashSet<>();

	// Constructor
	ChatRoom(String name){
		 roomName = name;
	}
	String getName(){
		return roomName;
	}

	void addUser(SelectionKey sk){
		curUsers.add(sk);
	}

	void removeUser(SelectionKey sk){
		curUsers.remove(sk);
	}

	HashSet<SelectionKey> getUsers(){
		return curUsers;
	}

}
