// Luca Lombardo, mat. 546688

import java.util.Date;

public class Message {
	private String author;
	private String text;
	private Date timestamp;
	
	public Message() {
		this.author = "";
		this.text = "";
		timestamp = new Date();
	}
	
	public Message(String author, String text) {
		this.author = author;
		this.text = text;
		timestamp = new Date();
	}
	
	// Getters & Setters
	public String getAuthor() {
		return author;
	}
	public void setAuthor(String author) {
		this.author = author;
	}
	public String getText() {
		return text;
	}
	public void setText(String text) {
		this.text = text;
	}
	public Date getTimestamp() {
		return timestamp;
	}
	public void setTimestamp(Date timestamp) {
		this.timestamp = timestamp;
	}
	
	@Override
	public String toString() {
		return timestamp.toString() + " " + author + ": " + text;
	}
}
