// Luca Lombardo, mat. 546688

import java.io.Serializable;
import java.util.ArrayList;

public class Card implements Serializable {
	private static final long serialVersionUID = 1L;
	
	String name;
	String description;
	ArrayList<String> history;
	
	// Costruttori
	public Card() {
		this.name = "";
		this.description = "";
		this.history = new ArrayList<String>();
	}

	public Card(String name, String description) {
		this.name = name;
		this.description = description;
		this.history = new ArrayList<String>();
		history.add("toDo");
	}
	
	public void move(String destinazione) {
		history.add(destinazione);
	}
	
	// Getters & Setters
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
	
	public ArrayList<String> getHistory() {
		return history;
	}

	public void setHistory(ArrayList<String> history) {
		this.history = history;
	}
}
