// Luca Lombardo, mat. 546688

import java.io.Serializable;

public class User implements Serializable {
	private static final long serialVersionUID = 1L;
	
	String nickUtente;
	String password;
	
	// Costruttori
	public User() {
		this.nickUtente = "";
		this.password = "";
	}
	
	public User(String nickUtente, String password) {
		this.nickUtente = nickUtente;
		this.password = password;
	}
	
	// Getters & Setters
	public String getNickUtente() {
		return nickUtente;
	}
	
	public void setNickUtente(String nickUtente) {
		this.nickUtente = nickUtente;
	}
	
	public String getPassword() {
		return password;
	}
	
	public void setPassword(String password) {
		this.password = password;
	}
}
