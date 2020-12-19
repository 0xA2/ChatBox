// Class to store current nickname and state ('init', 'outside' or 'inside') of each user
public class User{

	// Attributes
	private String curName;
	private String curState;
	private String curRoom;

	// Constructor
	User(){
		curState = "init";
	}

	// Methods

	String getName(){
		return curName;
	}

	String getState(){
		return curState;
	}

	String getRoom(){
		return curRoom;
	}

	void setName(String name){
		curName = name;
	}

	void setState(String state){
		curState = state;
	}

	void setRoom(String room){
		curRoom = room;
	}

}
