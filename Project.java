// Luca Lombardo, mat. 546688

import java.io.Serializable;
import java.util.ArrayList;

public class Project implements Serializable {
	private static final long serialVersionUID = 1L;

	String name;
	String multicastIp;
	ArrayList<User> projectMembers;
	ArrayList<Card> toDo;
	ArrayList<Card> inProgress;
	ArrayList<Card> toBeRevised;
	ArrayList<Card> done;
	
	// Costruttori
	public Project() {
		this.name = "";
		multicastIp = "";
		this.projectMembers = new ArrayList<User>();
		this.toDo = new ArrayList<Card>();
		this.inProgress = new ArrayList<Card>();
		this.toBeRevised = new ArrayList<Card>();
		this.done = new ArrayList<Card>();
	}
	
	public Project(String name) {
		this.name = name;
		this.multicastIp = "";
		this.projectMembers = new ArrayList<User>();
		this.toDo = new ArrayList<Card>();
		this.inProgress = new ArrayList<Card>();
		this.toBeRevised = new ArrayList<Card>();
		this.done = new ArrayList<Card>();
	}
	
	public Project(String name, String multicastIp) {
		this.name = name;
		this.multicastIp = multicastIp;
		this.projectMembers = new ArrayList<User>();
		this.toDo = new ArrayList<Card>();
		this.inProgress = new ArrayList<Card>();
		this.toBeRevised = new ArrayList<Card>();
		this.done = new ArrayList<Card>();
	}
	
	public Project(String name, int[] ip) {
		this.name = name;
		setMulticastIp(ip);
		this.projectMembers = new ArrayList<User>();
		this.toDo = new ArrayList<Card>();
		this.inProgress = new ArrayList<Card>();
		this.toBeRevised = new ArrayList<Card>();
		this.done = new ArrayList<Card>();
	}
	
	public Project(String name, String multicastIp, ArrayList<User> projectMembers, ArrayList<Card> toDo, ArrayList<Card> inProgress, ArrayList<Card> toBeRevised, ArrayList<Card> done) {
		this.name = name;
		this.multicastIp = multicastIp;
		this.projectMembers = projectMembers;
		this.toDo = toDo;
		this.inProgress = inProgress;
		this.toBeRevised = toBeRevised;
		this.done = done;
	}

	// Aggiungi un utente alla lista dei membri del progetto
	public boolean addMember(User member) {
		return projectMembers.add(member);
	}
	
	// Sposta una carta da una lista all'altra
	public void moveCard(Card card, String from, String to) {
		switch (from.toLowerCase()) {
			case "todo":
				for (Card c : toDo) {
					if (c.getName().toLowerCase().equals(card.getName().toLowerCase())) {
						toDo.remove(c);
						break;
					}
				}
				
				break;
				
			case "inprogress":
				for (Card c : inProgress) {
					if (c.getName().toLowerCase().equals(card.getName().toLowerCase())) {
						inProgress.remove(c);
						break;
					}
				}
				
				break;
				
			case "toberevised":
				for (Card c : toBeRevised) {
					if (c.getName().toLowerCase().equals(card.getName().toLowerCase())) {
						toBeRevised.remove(c);
						break;
					}
				}
				
				break;
				
			case "done":
				for (Card c : done) {
					if (c.getName().toLowerCase().equals(card.getName().toLowerCase())) {
						done.remove(c);
						break;
					}
				}
				
				break;
				
			default:
				System.err.println("PROJECT: errore moveCard!");
				break;
		}
		
		switch (to.toLowerCase()) {
			case "todo":
				toDo.add(card);
				
				break;
				
			case "inprogress":
				inProgress.add(card);
				
				break;
				
			case "toberevised":
				toBeRevised.add(card);
				
				break;
				
			case "done":
				done.add(card);
				
				break;
				
			default:
				System.err.println("PROJECT: errore moveCard!");
				break;
		}
	}
	
	// Getters & Setters
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public String getMulticastIp() {
		return multicastIp;
	}

	public void setMulticastIp(String multicastIp) {
		this.multicastIp = multicastIp;
	}
	
	public void setMulticastIp(int[] ip) {
		String ipString = String.valueOf(ip[0]) + "." + String.valueOf(ip[1]) + "." + String.valueOf(ip[2]) + "." + String.valueOf(ip[3]);
		
		this.multicastIp = ipString;
	}
	
	public ArrayList<User> getProjectMembers() {
		return projectMembers;
	}

	public void setProjectMembers(ArrayList<User> projectMembers) {
		this.projectMembers = projectMembers;
	}
	
	public ArrayList<Card> getToDo() {
		return toDo;
	}
	
	public void setToDo(ArrayList<Card> toDo) {
		this.toDo = toDo;
	}
	
	public ArrayList<Card> getInProgress() {
		return inProgress;
	}
	
	public void setInProgress(ArrayList<Card> inProgress) {
		this.inProgress = inProgress;
	}
	
	public ArrayList<Card> getToBeRevised() {
		return toBeRevised;
	}
	
	public void setToBeRevised(ArrayList<Card> toBeRevised) {
		this.toBeRevised = toBeRevised;
	}
	
	public ArrayList<Card> getDone() {
		return done;
	}
	
	public void setDone(ArrayList<Card> done) {
		this.done = done;
	}
}
