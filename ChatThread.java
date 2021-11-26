import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

// Luca Lombardo, mat. 546688

public class ChatThread implements Runnable {
	String ip;
	ConcurrentHashMap<String, ArrayList<Message>> chat; 	// Passata dal ClientMain, associa i nomi dei progetti alle rispettive chat
	MulticastSocket ms;

	// Costruttore
	public ChatThread(String ip, ConcurrentHashMap<String, ArrayList<Message>> chat, MulticastSocket ms) {
		this.ip = ip;
		this.chat = chat;
		this.ms = ms;
	}
	
	public void run() {
		try {
			InetAddress group = InetAddress.getByName(ip);
			ms.joinGroup(group);
			byte[] buffer = new byte[8192];
			DatagramPacket dp = new DatagramPacket(buffer, buffer.length);

			// Finche' il thread non viene interrotto, ricevi messaggi
			while (!Thread.currentThread().isInterrupted()) {
				ms.receive(dp);
				Message msg = new ObjectMapper().readValue(dp.getData(), new TypeReference<Message>(){});
				
				ArrayList<Message> ch = chat.get(ip);
					
				if (ch == null) {
					ch = new ArrayList<Message>();
				}
					
				ch.add(msg);
				chat.putIfAbsent(ip, ch);
			}
			
			ms.close();	// Chiudi il MulticastSocket
		} catch (SocketException e) {
			// Il MulticastSocket e' stato chiuso, quindi interrompi il thread
			Thread.currentThread().interrupt();
		} catch (IOException e) {
			System.err.printf("%s: errore I/O!\n", Thread.currentThread().getName());
		}
	}
	
	// Metodo getter per il MulticastSocket
	public MulticastSocket getMs() {
		return ms;
	}
}
