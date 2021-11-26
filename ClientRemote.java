// Luca Lombardo, mat. 546688

import java.io.IOException;
import java.net.BindException;
import java.net.MulticastSocket;
import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

//import javafx.util.Pair;

public class ClientRemote extends RemoteObject implements ClientRemoteInterface {
	private static final long serialVersionUID = 1L;
	Object userSync;
	ArrayList<User> members;		// Lista degli utenti registrati al servizio
	ArrayList<User> loggedUsers;	// Lista degli utenti loggati
	ConcurrentHashMap<String, String> multicastIp; 			// Passata dal ClientMain, associa nomi dei progetti ai rispettivi indirizzi multicast
	ConcurrentHashMap<String, ArrayList<Message>> chat;		// Passata dal ClientMain, associa i nomi dei progetti alle rispettive chat
	ConcurrentHashMap<Thread, AbstractMap.SimpleEntry<String, MulticastSocket>> chatThreads;	// Passata dal ClientMain, associa ogni ChatThread al proprio nome e MulticastSocket

	// Costruttore
	public ClientRemote(Object userSync, ArrayList<User> members, ArrayList<User> loggedUsers, ConcurrentHashMap<String, String> multicastIp, 
			ConcurrentHashMap<String, ArrayList<Message>> chat, ConcurrentHashMap<Thread, AbstractMap.SimpleEntry<String, MulticastSocket>> chatThreads) throws RemoteException {
		super();
		this.userSync = userSync;
		this.members = members;
		this.loggedUsers = loggedUsers;
		this.multicastIp = multicastIp;
		this.chat = chat;
		this.chatThreads = chatThreads;
	}
	
	// Metodo invocato dal server per notificare il client remoto 
	public void notifyEvent(AbstractMap.SimpleEntry<ArrayList<User>, ArrayList<User>> pair) throws RemoteException {
		// Sincronizza l'accesso agli utenti registrati/loggati
		synchronized(userSync) {
			members.clear();
			members.addAll(pair.getKey());
			loggedUsers.clear();
			loggedUsers.addAll(pair.getValue());
		}
	}
	
	// Metodo invocato dal server quando il client viene aggiunto a un progetto
	public void notifyProject(String project, String ip) throws RemoteException {
		multicastIp.putIfAbsent(project, ip);
		int msPort = 11001;
											
		// Crea il thread per la chat						
		ChatThread ct = null;
			
		// Trova una porta disponibile per il MulticastSocket
		boolean msDone = false;
			
		while (!msDone) {
			try {
				ct = new ChatThread(multicastIp.get(project), chat, new MulticastSocket(msPort));
				msDone = true;
			} catch (BindException e) {
				msPort++;
			} catch (IOException e) {
				System.err.println("ClientRemote: errore I/O.");
			}
		}
			
		Thread t = new Thread(ct);
			
		// Aggiorna la struttura dati contenente i threads della chat
		AbstractMap.SimpleEntry<String, MulticastSocket> pair = new AbstractMap.SimpleEntry<String, MulticastSocket>(project, ct.getMs());
		chatThreads.putIfAbsent(t, pair);
		t.start();
	}
	
	// Metodo invocato dal server quando un progetto del quale il client e' membro viene cancellato
	public void notifyCancelProject(String project) throws RemoteException {
		multicastIp.remove(project);
		chat.remove(project);
		
		// Cerca il chatThread del progetto eliminato e chiudi il MulticastSocket corrispondente
		for (Thread t : chatThreads.keySet()) {
			if (chatThreads.get(t).getKey().toLowerCase().equals(project.toLowerCase())) {
				chatThreads.get(t).getValue().close();	// Chiudi la MulticastSocket
				//t.interrupt();						// Interrompi il ChatThread
				break;
			}
		}
	}
}
