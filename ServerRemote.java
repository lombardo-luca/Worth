// Luca Lombardo, mat. 546688

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.rmi.ConnectException;
import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ServerRemote extends RemoteObject implements ServerRemoteInterface {
	private static final long serialVersionUID = 1L;
	
	public ConcurrentHashMap<String, ClientRemoteInterface> clients; 	// Mappa che associa i nomi degli utenti alle rispettive ClientRemoteInterface
	Object userSync;				// Variabile per la sincronizzazione su utenti registrati e loggati
	ArrayList<User> members;		// Lista degli utenti registrati al servizio
	ArrayList<User> loggedUsers;	// Lista degli utenti loggati
	
	// Costruttore
	public ServerRemote(Object user, ArrayList<User> members, ArrayList<User> loggedUsers) throws RemoteException {
		super();
		this.userSync = user;
		//clients = new ArrayList<ClientRemoteInterface>();
		clients = new ConcurrentHashMap<String, ClientRemoteInterface>();
		this.members = members;
		this.loggedUsers = loggedUsers;
		
		System.out.printf("%S REMOTE: creato ServerRemote.\n", Thread.currentThread().getName());
	}
	
	// Registrazione alla callback
	public synchronized void registerForCallback (ClientRemoteInterface ClientInterface, String nickUtente) throws RemoteException {
		if (!clients.containsKey(nickUtente)) { 
				clients.putIfAbsent(nickUtente, ClientInterface);
				System.out.printf("%S REMOTE: client registrato alla callback.\n", Thread.currentThread().getName());
		}
			
		else {
			System.err.printf("%S REMOTE: utente gia' presente!\n", Thread.currentThread().getName());
		}
		
	}
	
	// Cancella registrazione alla callback
	public synchronized void unregisterForCallback (ClientRemoteInterface ClientInterface, String nickUtente) throws RemoteException {
		clients.remove(nickUtente);
	}
	
	// Chiama il metodo doCallbacks
	public void update(ArrayList<User> members, ArrayList<User> loggedUsers) throws RemoteException {
		AbstractMap.SimpleEntry<ArrayList<User>, ArrayList<User>> pair = new AbstractMap.SimpleEntry<ArrayList<User>, ArrayList<User>>(members, loggedUsers);
		doCallbacks(pair);
	}
	
	// Manda callback a tutti i client registrati
	public synchronized void doCallbacks(AbstractMap.SimpleEntry<ArrayList<User>, ArrayList<User>> pair) throws RemoteException {
		System.out.printf("%S REMOTE: mando le callbacks.\n", Thread.currentThread().getName());
		Iterator<String> i = clients.keySet().iterator();
		ArrayList<String> remove = new ArrayList<String>();
		
		while (i.hasNext()) {
			String clientName = i.next();
			ClientRemoteInterface client = (ClientRemoteInterface) clients.get(clientName);
				
			try {
				client.notifyEvent(pair);	// Invoca il metodo remoto del client
			} catch (ConnectException e) {
				// Se un client ha chiuso la connessione senza prima fare il logout...
				remove.add(clientName);
			}
		}
			
		// ...eliminalo dalla lista dei client
		for (String s : remove) {
			clients.remove(s);
		}
		
		System.out.printf("%S REMOTE: callbacks completate.\n", Thread.currentThread().getName());
	}
	
	// Manda la callback all'utente che e' stato aggiunto a un progetto, se e' loggato
	public synchronized void addMemberCallback(String nickUtente, String project, String ip) throws RemoteException {
		boolean logged = false;
		
		// Sincronizza l'accesso agli utenti registrati/loggati
		synchronized(userSync) {
			// Controlla che l'utente aggiunto al progetto sia loggato
			for (User u : loggedUsers) {
				if (u.getNickUtente().toLowerCase().equals(nickUtente.toLowerCase())) {
					logged = true;
					
					break;
				}
			}
		}
		
		// Fai la callback solo se l'utente aggiunto al progetto e' loggato
		if (logged) {
			for (String nick : clients.keySet()) {
				if (nick.toLowerCase().equals(nickUtente.toLowerCase())) {
					ClientRemoteInterface client = clients.get(nick);
						
					try {
						client.notifyProject(project, ip);	// Invoca il metodo remoto del client
						System.out.printf("%S REMOTE: mando la callback dell'addMember.\n", Thread.currentThread().getName());
					} catch (ConnectException e) {
						// Se un client ha chiuso la connessione senza prima fare il logout, eliminalo dalla lista dei client
						clients.remove(nick);
					}
						
					break;
				}
			}
		}
	}
	
	// Fai le callback a tutti gli utenti loggati che erano membri di un progetto appena cancellato
	public synchronized void cancelProjectCallback(String projectName, Project p) throws RemoteException {
		for (User u : p.getProjectMembers()) {
			boolean logged = false;
			
			// Sincronizza l'accesso agli utenti registrati/loggati
			synchronized(userSync) {
				for (User ul : loggedUsers) {
					if (ul.getNickUtente().toLowerCase().equals(u.getNickUtente().toLowerCase())) {
						logged = true;
						
						break;
					}
				}
			}
			
			// Fai la callback solo se l'utente e' loggato
			if (logged) {
				for (String nick : clients.keySet()) {
					if (nick.toLowerCase().equals(u.getNickUtente().toLowerCase())) {
						ClientRemoteInterface client = clients.get(nick);
							
						try {
							client.notifyCancelProject(projectName);
							System.out.printf("%S REMOTE: mando la callback della CancelProject all'utente %s.\n", Thread.currentThread().getName(), u.getNickUtente());
						} catch (ConnectException e) {
							// Se un client ha chiuso la connessione senza prima fare il logout, eliminalo dalla lista dei client
							clients.remove(nick);
						}
							
						break;
					}
				}
			}
		}
	}
	
	// Operazione di registrazione
	public int register(String nickUtente, String password) throws RemoteException {
		ObjectMapper objectMapper = new ObjectMapper();
		String fpath = "members.json";
		File f = new File(fpath);
			
		// Se la password inserita e' vuota, errore
		if (password.equals("")) {
			return 2;
		}
		
		// Sincronizza l'accesso agli utenti registrati/loggati
		synchronized(userSync) {	
			Iterator<User> it = members.iterator();
				
			// Controlla se esiste gia' un utente registrato con quel nickname
			while (it.hasNext()) {
				User u = it.next();
					
				// Se esiste, ritorna un errore al client
				if (u.getNickUtente().toLowerCase().equals(nickUtente.toLowerCase())) {
					System.out.printf("%S REMOTE: Un client ha provato a registrarsi con un nick gia' presente.\n", Thread.currentThread().getName());
					
					return 1;
				}
			}
				
			User u = new User(nickUtente, password);
				
			// Aggiungi il nuovo utente alla lista dei registrati
			members.add(u);
				
			FileOutputStream fos = null;
			ByteBuffer buffer = null;
			    
			// Crea il nuovo file degli utenti registrati
			try {
				f.createNewFile();
			    fos = new FileOutputStream(f);
			} catch (IOException e) {
				System.err.printf("%S REMOTE: errore creazione file degli utenti registrati.\n", Thread.currentThread().getName());
			    e.printStackTrace();
			}
			    
			FileChannel fc = fos.getChannel();

			try {
				buffer = ByteBuffer.wrap(objectMapper.writeValueAsBytes(members));
			    fc.write(buffer);
			    buffer.clear();
			} catch (IOException e) {
			    System.err.printf("%S REMOTE: errore scrittura file degli utenti registrati.\n", Thread.currentThread().getName());
			    e.printStackTrace();
			}
			
			// Chiudi il FileOutputStream
			try {
				fos.close();
			} catch (IOException e) {
				System.err.printf("%S REMOTE: errore chiusura fos.\n", Thread.currentThread().getName());
				e.printStackTrace();
			}
			
			System.out.printf("%S REMOTE: Un client si e' registrato con successo.\n", Thread.currentThread().getName());
			update(members, loggedUsers); 	// Manda callback
		}	
    
		return 0;
	}
}
