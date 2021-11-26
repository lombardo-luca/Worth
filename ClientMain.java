// Luca Lombardo, mat. 546688

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

//import javafx.util.Pair;

public class ClientMain {	
	
	public static void main(String[] args) {
		System.setProperty("java.net.preferIPv4Stack" , "true");	// Disabilita IPv6
		
		String tn = Thread.currentThread().getName();
		ServerRemoteInterface serverObject = null;
		Remote remoteObject = null;
		ClientRemoteInterface stub = null;	// Stub
		Object user = new Object();
		ClientRemote callbackObj = null;
		String login = "";
		ArrayList<User> members = new ArrayList<User>();		// Lista degli utenti registrati
		ArrayList<User> loggedUsers = new ArrayList<User>();	// Lista degli utenti loggati
		ConcurrentHashMap<String, String> multicastIp = new ConcurrentHashMap<String, String>();	// Associa nomi dei progetti ai rispettivi indirizzi multicast
		ConcurrentHashMap<String, ArrayList<Message>> chat = new ConcurrentHashMap<String, ArrayList<Message>>(); 	// Associa i nomi dei progetti alle rispettive chat
		
		int dsPort = 11000;		// Porta di default per il DatagramSocket
		int msPort = 11001;		// Porta di default per il MulticastSocket, usata per i ChatThread
		
		DatagramSocket ds = null;
		boolean done = false;
		
		// Trova una porta libera per il DatagramSocket, utilizzato per le chat
		while (!done) {
			try {
				 ds = new DatagramSocket(dsPort);
				 done = true;
			} catch (BindException e) {
				// Porta gia' usata
				dsPort++;
			} catch (SocketException e) {
				System.err.printf("%S CLIENT: errore Socket.\n", tn);
			}
		}
				
		// Crea la struttura dati contenente i thread delle chat
		ConcurrentHashMap<Thread, AbstractMap.SimpleEntry<String, MulticastSocket>> chatThreads = new ConcurrentHashMap<Thread, AbstractMap.SimpleEntry<String, MulticastSocket>>();
		
		// Accedo al registro per RMI
		try {
			Registry r = LocateRegistry.getRegistry(6000);
			remoteObject = r.lookup("WORTH");
			serverObject = (ServerRemoteInterface) remoteObject;
			System.out.printf("%S CLIENT: ottenuto accesso al registry.\n", tn);
			callbackObj = new ClientRemote(user, members, loggedUsers, multicastIp, chat, chatThreads);
			
			// Creo lo stub
			stub = (ClientRemoteInterface) UnicastRemoteObject.exportObject(callbackObj, 0);
		} catch (RemoteException | NotBoundException e) {
			System.err.printf("%S CLIENT: errore remoto.\n", tn);
		}
		
		try (Socket socket = new Socket("localhost", 10000)) {
			System.out.printf("%S CLIENT: connesso al server.\n", tn);
	
			Scanner s = new Scanner(System.in);
			
			// Crea gli stream I/O per la connessione TCP
			BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
			BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
			ByteBuffer ok = ByteBuffer.allocate(4);
			ok.putInt(1);
			byte[] bufferIn = new byte[4096];
			boolean quit = false;
			int response;	// Variabile usata per ricevere il codice di risposta dal server
			
			// Loop di comandi
			while (!quit) {
				System.out.println("> ");
				
				String input = s.nextLine();	// Ottieni input dall'utente
				String[] split = input.trim().split("\\s+");	// Dividi l'input in parole
				
				// Controlla il tipo di comando
				switch (split[0]) {
					// Registrazione
					case "register":		
						// Se l'utente non ha rispettato la sintassi del comando
						if (split.length < 3) {
							System.out.println("Sintassi comando errata.");
						}
						
						else {
							int res = -1;
							res = serverObject.register(split[1], split[2]);
							
							switch (res) {
								// Successo
								case 0:
									System.out.println("Registrazione avvenuta con successo.");
									break;
									
								// Gestione degli errori
								case 1:
									System.out.println("Errore: il nickname esiste gia'.");
									break;
									
								case 2:
									System.out.println("Errore: la password inserita e' vuota.");
									break;
							}
						}
						
						break;
					
					// Login
					case "login":
						// Controlla se l'utente e' gia' loggato
						if (!login.equals("")) {
							System.out.println("Errore: sei gia' loggato.");
						}
						
						// Se l'utente non ha rispettato la sintassi del comando
						else if (split.length < 3) {
							System.out.println("Sintassi comando errata.");
						}
						
						else {
							// Invia comando al server
							if (!sendCommand(out, input)) {
								System.err.printf("%S CLIENT: errore invio comando!\n", tn);
							}
							
							else {
								// Ricevi messaggio di risposta dal server
								response = receiveResponse(in);
								
								// Invia conferma di ricezione al server
								out.write(ok.array(), 0, 4);
								out.flush();
								
								switch (response) {
									// Successo
									case 0:
										System.out.printf("Benvenuto %s.\n", split[1].trim());
										
										// Ricevi la lista degli utenti registrati
										bufferIn = new byte[4096];
										in.read(bufferIn, 0, 4096);
										
										ArrayList<User> membersIn = new ObjectMapper().readValue(bufferIn, new TypeReference<ArrayList<User>>(){});
										
										// Invia conferma di ricezione al server
										out.write(ok.array(), 0, 4);
										out.flush();
										
										// Ricevi la lista degli utenti loggati
										bufferIn = new byte[4096];
										in.read(bufferIn, 0, 4096);
										
										ArrayList<User> loggedUsersIn = new ObjectMapper().readValue(bufferIn, new TypeReference<ArrayList<User>>(){});
										
										// Invia conferma di ricezione al server
										out.write(ok.array(), 0, 4);
										out.flush();
										
										// Ricevi l'insieme degli indirizzi multicast
										bufferIn = new byte[4096];
										in.read(bufferIn, 0, 4096);
										
										HashMap<String, String> ips = new ObjectMapper().readValue(bufferIn, new TypeReference<HashMap<String, String>>(){});
										
										// Invia conferma di ricezione al server
										out.write(ok.array(), 0, 4);
										out.flush();
										
										// Aggiorna le strutture dati locali
										synchronized(user) {
											members.clear();
											members.addAll(membersIn);
											loggedUsers.clear();
											loggedUsers.addAll(loggedUsersIn);
										}
										
										multicastIp.putAll(ips);
										login = split[1].trim();	// Aggiorno la variabile login
																			
										// Crea i thread per la chat (uno per ogni progetto)							
										for (String prString : multicastIp.keySet()) {
											ChatThread ct = null;
											
											// Trova una porta disponibile per il MulticastSocket
											boolean msDone = false;
											
											while (!msDone) {
												try {
													ct = new ChatThread(multicastIp.get(prString), chat, new MulticastSocket(msPort));
													msDone = true;
												} catch (BindException e) {
													msPort++;
												}
											}
											
											Thread t = new Thread(ct);
											
											// Aggiorna la struttura dati locale contenente i threads della chat
											AbstractMap.SimpleEntry<String, MulticastSocket> pair = new AbstractMap.SimpleEntry<String, MulticastSocket>(prString, ct.getMs());
											chatThreads.put(t, pair);
											t.start();
										}
										
										// Registro alla callback
										System.out.printf("%S CLIENT: mi registro alle callback.\n", tn);
										serverObject.registerForCallback(stub, split[1].trim());
										
										break;
										
									// Gestione degli errori
									case 1:
										System.out.printf("Errore: utente %s non esistente.\n", split[1]);
										break;
									
									case 2:
										System.out.println("Errore: password errata.");
										break;
										
									case 3:
										System.out.println("Errore: client gia' loggato.");
										break;
										
									case 4:
										System.out.println("Errore: sintassi comando errata.");
										break;
										
									case 5:
										System.out.println("Errore: l'utente e' gia' loggato su un altro client.");
										break;
										
									case -1:
										System.err.println("CLIENT: errore ricezione risposta!");
										break;
								}	
							}
						}
						
						break;
						
					// Logout
					case "logout":		
						// Controlla che l'utente sia loggato
						if (login.equals("")) {
							System.out.println("Errore: non sei loggato.");
						}
						
						// Se l'utente non ha rispettato la sintassi del comando
						else if (split.length < 2) {
							System.out.println("Sintassi comando errata.");
						}
						
						else {
							// Invia comando al server
							if (!sendCommand(out, input)) {
								System.err.printf("%S CLIENT: errore invio comando!\n", tn);
							}

							else {
								// Ricevi messaggio di risposta dal server
								response = receiveResponse(in);
								
								// Invia conferma di ricezione al server
								out.write(ok.array(), 0, 4);
								out.flush();
								
								switch (response) {
									// Success
									case 0:
										System.out.printf("%S CLIENT: elimino registrazione alla callback.\n", tn);
										
										// Elimina registrazione alla callback
										serverObject.unregisterForCallback(stub, split[1].trim());
										
										// Aggiorna dati locali
										login = "";
										
										// Chiudi i MulticastSocket dei thread per la chat			
										for (AbstractMap.SimpleEntry<String, MulticastSocket> pair : chatThreads.values()) {
											pair.getValue().close();
										}
										
										// Resetta tutte le strutture dati locali
										members.clear();		
										loggedUsers.clear();	
										multicastIp.clear();	
										chat.clear();
										
										System.out.printf("Logout eseguito.\n");
										
										break;
										
									// Gestione degli errori
									case 1:
										System.out.printf("Errore: devi essere loggato per poter eseguire il logout.");
										break;
										
									case 2:
										System.out.println("Errore: utente non trovato.");
										break;
										
									case 3:
										System.out.println("Errore: sintassi comando errata.");
										break;
										
									case 4:
										System.out.printf("Errore: non sei loggato come: %s.\n", split[1]);
										break;
										
									case -1:
										System.err.printf("%S CLIENT: errore ricezione risposta!\n", tn);
										break;
								}
							}
						}
						
						break;
					
					// Stampa la lista degli utenti registrati
					case "listUsers":
						// Controlla che l'utente sia loggato
						if (login.equals("")) {
							System.out.printf("Errore: e' necessario eseguire il login.\n");
						}
							
						else {
							System.out.println("Utenti registrati: ");
							
							synchronized(user) {									
								for (User u : members) {
									boolean online = false;
									
									// Controlla se l'utente e' online o meno
									for (User u2 : loggedUsers) {
										if (u.getNickUtente().equals(u2.getNickUtente())) {
											online = true;
										}
									}
									
									if (online) {
										System.out.printf("%s: Online\n", u.getNickUtente());
									}
									
									else {
										System.out.printf("%s: Offline\n", u.getNickUtente());
									}
								}
							}
						}
						
						break;
						
					// Stampa la lista degli utenti loggati
					case "listOnlineUsers":
						// Controlla che l'utente sia loggato
						if (login.equals("")) {
							System.out.printf("Errore: e' necessario eseguire il login.\n");
						}
						
						else {
							System.out.println("Utenti loggati: ");
							
							// Sincronizza l'accesso alla lista di utenti loggati
							synchronized(user) {
								for (User u : loggedUsers) {
									System.out.printf("%s\n", u.getNickUtente());
								}
							}
						}
						
						break;
						
					// Crea un nuovo progetto
					case "createProject":
						// Controlla che l'utente sia loggato
						if (login.equals("")) {
							System.out.printf("Errore: e' necessario eseguire il login.\n");
						}
						
						// Se l'utente non ha rispettato la sintassi del comando
						else if (split.length < 2) {
							System.out.println("Sintassi comando errata.");
						}
						
						else {
							// Invia comando al server
							if (!sendCommand(out, input)) {
								System.err.printf("%S CLIENT: errore invio comando!\n", tn);
							}
							
							else {
								// Ricevi messaggio di risposta dal server
								response = receiveResponse(in);
								
								// Invia conferma di ricezione al server
								out.write(ok.array(), 0, 4);
								out.flush();
								
								switch (response) {
									// Successo
									case 0:
										System.out.printf("Progetto %s creato con successo.\n", split[1].trim());
										
										// Ricevi l'indirizzo multicast del progetto appena creato
										bufferIn = new byte[4096];
										in.read(bufferIn, 0, 4096);
										
										int[] ip = new ObjectMapper().readValue(bufferIn, new TypeReference<int[]>(){});
										
										// Invia conferma di ricezione al server
										out.write(ok.array(), 0, 4);
										out.flush();
										
										// Aggiungi l'indirizzo ricevuto alla struttura dati interna
										String ipString = String.valueOf(ip[0]) + "." + String.valueOf(ip[1]) + "." + String.valueOf(ip[2]) + "." + String.valueOf(ip[3]);
										multicastIp.put(split[1].trim(), ipString);
										
										// Creo il nuovo thread per la chat		

										// Trova una porta disponibile per il MulticastSocket
										boolean msDone = false;
										ChatThread ct = null;
										
										while (!msDone) {
											try {
												ct = new ChatThread(multicastIp.get(split[1].trim()), chat, new MulticastSocket(msPort));
												msDone = true;
											} catch (BindException e) {
												msPort++;
											}
										}
														
										// ChatThread ct = new ChatThread(multicastIp.get(split[1].trim()), chat, new MulticastSocket(11001));
										Thread t = new Thread(ct);
										AbstractMap.SimpleEntry<String, MulticastSocket> pair = new AbstractMap.SimpleEntry<String, MulticastSocket>(split[1].trim(), ct.getMs());
										chatThreads.put(t, pair);
										t.start();
										
										break;
									
									// Gestione degli errori
									case 1:
										System.out.printf("Errore: per creare un progetto e' necessario eseguire il login.\n");
										break;
										
									case 2:
										System.out.printf("Errore: inserire il nome del progetto.\n");
										break;
										
									case 3:
										System.out.printf("Errore: esiste gia' un progetto con nome %s\n", split[1].trim());
										break;
										
									case 4:
										System.out.printf("Errore: impossibile creare il progetto poiche' non sono disponibili altri indirizzi multicast.");
										break;
										
									case -1:
										System.err.printf("%S CLIENT: errore ricezione risposta!\n", tn);
										break;
								}
							}
						}
						
						break;
					
					// Stampa la lista dei progetti di cui l'utente e' membro
					case "listProjects":
						// Controlla che l'utente sia loggato
						if (login.equals("")) {
							System.out.printf("Errore: e' necessario eseguire il login.\n");
						}
						
						// Invia comando al server
						else if (!sendCommand(out, input)) {
							System.err.printf("%S CLIENT: errore invio comando!\n", tn);
						}
						
						else {
							// Ricevi messaggio di risposta dal server
							response = receiveResponse(in);
							
							// Invia conferma di ricezione al server
							out.write(ok.array(), 0, 4);
							out.flush();
							
							switch (response) {
								// Successo
								case 0:
									// Ricevi la lista dei progetti di cui l'utente e' membro
									bufferIn = new byte[4096];
									in.read(bufferIn, 0, 4096);
									
									ArrayList<Project> projectsIn = new ObjectMapper().readValue(bufferIn, new TypeReference<ArrayList<Project>>(){});
									
									// Invia conferma di ricezione al server
									out.write(ok.array(), 0, 4);
									out.flush();
									
									// Stampa la lista dei progetti di cui l'utente e' membro
									System.out.println("Progetti:");
									
									for (Project p : projectsIn) {
										System.out.printf("%s ", p.getName());
									}
									
									System.out.printf("\n");
									break;
									
								// Gestione degli errori
								case 1:
									System.out.printf("Errore: e' necessario eseguire il login.\n");
									break;
									
								case 2:
									System.err.printf("%S CLIENT: errore ricezione risposta!\n", tn);
									break;
							}
						}

						break;
					
					// Aggiungi un utente a un progetto
					case "addMember":
						// Controlla che l'utente sia loggato
						if (login.equals("")) {
							System.out.printf("Errore: e' necessario eseguire il login.\n");
						}
						
						// Se l'utente non ha rispettato la sintassi del comando
						else if (split.length < 3) {
							System.out.println("Sintassi comando errata.");
						}
						
						else {
							// Invia comando al server
							if (!sendCommand(out, input)) {
								System.err.printf("%S CLIENT: errore invio comando!\n", tn);
							}
							
							else {
								// Ricevi messaggio di risposta dal server
								response = receiveResponse(in);
								
								// Invia conferma di ricezione al server
								out.write(ok.array(), 0, 4);
								out.flush();
								
								switch (response) {
									// Successo
									case 0:
										System.out.printf("L'utente %s e' adesso un membro del progetto %s.\n", split[2].trim(), split[1].trim());
										break;
									
									// Gestione degli errori
									case 1:
										System.out.printf("Errore: e' necessario eseguire il login.\n");
										break;
										
									case 2:
										System.out.printf("Errore: non esiste alcun progetto con nome: %s.\n", split[1].trim());
										break;
										
									case 3:
										System.out.printf("Errore: devi essere membro del progetto per poter aggiungere altri utenti.\n");
										break;		
										
									case 4:
										System.out.printf("Errore: non esiste alcun utente registrato con nick: %s.\n", split[2].trim());
										break;
										
									case 5:
										System.out.printf("Errore: l'utente %s e' gia' membro del progetto %s.\n", split[2].trim(), split[1].trim());
										break;
										
									case 6:
										System.out.printf("Errore: sintassi comando errata.\n");
										break;
										
									case -1:
										System.err.printf("%S CLIENT: errore ricezione risposta!\n", tn);
										break;
								}
							}
						}
						
						break;
						
					// Recupera la lista dei membri del progetto
					case "showMembers":
						// Controlla che l'utente sia loggato
						if (login.equals("")) {
							System.out.printf("Errore: e' necessario eseguire il login.\n");
						}
						
						// Se l'utente non ha rispettato la sintassi del comando
						else if (split.length < 2) {
							System.out.println("Sintassi comando errata.");
						}
						
						else {
							// Invia comando al server
							if (!sendCommand(out, input)) {
								System.err.printf("%S CLIENT: errore invio comando!\n", tn);
							}
							
							else {
								// Ricevi messaggio di risposta dal server
								response = receiveResponse(in);
								
								// Invia conferma di ricezione al server
								out.write(ok.array(), 0, 4);
								out.flush();
								
								switch (response) {
									// Successo
									case 0:
										// Ricevi la lista dei membri del progetto
										bufferIn = new byte[4096];
										in.read(bufferIn, 0, 4096);
										
										ArrayList<String> mNames = new ObjectMapper().readValue(bufferIn, new TypeReference<ArrayList<String>>(){});
										
										// Invia conferma di ricezione al server
										out.write(ok.array(), 0, 4);
										out.flush();
										
										// Stampa la lista dei membri del progetto
										System.out.printf("Membri del progetto %s:\n", split[1].trim());
										
										for (String str : mNames) {
											System.out.printf("%s\n", str);
										}
										
										break;
									
									// Gestioni degli errori
									case 1:
										System.out.printf("Errore: e' necessario eseguire il login.\n");
										break;
										
									case 2:
										System.out.printf("Errore: sintassi comando errata.\n");
										break;
										
									case 3:
										System.out.printf("Errore: non esiste alcun progetto con nome: %s.\n", split[1].trim());
										break;
										
									case 4:
										System.out.printf("Errore: devi essere membro del progetto per poterne visualizzare i membri.\n");
										break;
										
									case -1:
										System.err.printf("%S CLIENT: errore ricezione risposta!\n", tn);
										break;
								}
							}
						}
						
						break;
						
					// Aggiungi una card a un progetto
					case "addCard":
						// Controlla che l'utente sia loggato
						if (login.equals("")) {
							System.out.printf("Errore: e' necessario eseguire il login.\n");
						}
						
						// Se l'utente non ha rispettato la sintassi del comando
						else if (split.length < 4) {
							System.out.println("Sintassi comando errata.");
						}
						
						else {
							// Invia comando al server
							if (!sendCommand(out, input)) {
								System.err.printf("%S CLIENT: errore invio comando!\n", tn);
							}
							
							else {
								// Ricevi messaggio di risposta dal server
								response = receiveResponse(in);
								
								// Invia conferma di ricezione al server
								out.write(ok.array(), 0, 4);
								out.flush();
								
								switch (response) {
									// Success
									case 0: 
										System.out.printf("La card %s e' stata aggiunta al progetto %s.\n", split[2].trim(), split[1].trim());
										break;
									
									// Gestione degli errori
									case 1:
										System.out.printf("Errore: e' necessario eseguire il login.\n");
										break;
										
									case 2:
										System.out.printf("Errore: sintassi comando errata.\n");
										break;
									
									case 3: 
										System.out.printf("Errore: non esiste alcun progetto con nome: %s.\n", split[1].trim());
										break;
										
									case 4:
										System.out.printf("Errore: devi essere membro del progetto per potervi aggiungere una card.\n");
										break;
									
									case 5:
										System.out.printf("Errore: esiste gia' una card con nome: %s all'interno del progetto: %s.\n", split[2].trim(), split[1].trim());
										break;
									
									case 6:
										System.out.printf("Errore: nome della card non valido.\n");
										break;
										
									case -1:
										System.err.printf("%S CLIENT: errore ricezione risposta!\n", tn);
										break;
								}
							}
						}
							
						break;
					
					// Recupera la lista di card associate a un progetto
					case "showCards":
						// Controlla che l'utente sia loggato
						if (login.equals("")) {
							System.out.printf("Errore: e' necessario eseguire il login.\n");
						}
						
						// Se l'utente non ha rispettato la sintassi del comando
						else if (split.length < 2) {
							System.out.println("Sintassi comando errata.");
						}
						
						else {
							// Invia comando al server
							if (!sendCommand(out, input)) {
								System.err.printf("%S CLIENT: errore invio comando!\n", tn);
							}
							
							else {
								// Ricevi messaggio di risposta dal server
								response = receiveResponse(in);
								
								// Invia conferma di ricezione al server
								out.write(ok.array(), 0, 4);
								out.flush();
								
								switch (response) {
									// Successo
									case 0:
										// Ricevi la lista delle card
										bufferIn = new byte[4096];
										in.read(bufferIn, 0, 4096);
										
										ArrayList<Card> cards = new ObjectMapper().readValue(bufferIn, new TypeReference<ArrayList<Card>>(){});
										
										// Invia conferma di ricezione al server
										out.write(ok.array(), 0, 4);
										out.flush();
										
										// Stampa la lista delle card
										System.out.printf("Card del progetto %s:\n", split[1].trim());
										
										for (Card c : cards) {
											ArrayList<String> cHistory = c.getHistory();
											System.out.printf("card nome: %s stato: %s\n", c.getName(), cHistory.get(cHistory.size() - 1));
										}
										
										System.out.printf("\n");
										
										break;
										
									// Gestione degli errori
									case 1:
										System.out.printf("Errore: e' necessario eseguire il login.\n");
										break;
										
									case 2:
										System.out.printf("Errore: sintassi comando errata.\n");
										break;
										
									case 3:
										System.out.printf("Errore: non esiste alcun progetto con nome: %s.\n", split[1].trim());
										break;
										
									case 4:
										System.out.printf("Errore: devi essere membro del progetto per poterne visualizzare le card.\n");
										break;
										
									case -1:
										System.err.printf("%S CLIENT: errore ricezione risposta!\n", tn);
										break;
								}
							}
						}
						
						break;
						
					// Recupera le informazioni di una card
					case "showCard":
						// Controlla che l'utente sia loggato
						if (login.equals("")) {
							System.out.printf("Errore: e' necessario eseguire il login.\n");
						}
						
						// Se l'utente non ha rispettato la sintassi del comando
						else if (split.length < 3) {
							System.out.println("Sintassi comando errata.");
						}
						
						else {
							// Invia comando al server
							if (!sendCommand(out, input)) {
								System.err.printf("%S CLIENT: errore invio comando!\n", tn);
							}
							
							else {
								// Ricevi messaggio di risposta dal server
								response = receiveResponse(in);
								
								// Invia conferma di ricezione al server
								out.write(ok.array(), 0, 4);
								out.flush();
								
								switch (response) {
									// Successo
									case 0:
										// Ricevi la card
										bufferIn = new byte[4096];
										in.read(bufferIn, 0, 4096);
										
										Card card = new ObjectMapper().readValue(bufferIn, new TypeReference<Card>(){});
										
										// Invia conferma di ricezione al server
										out.write(ok.array(), 0, 4);
										out.flush();
										
										// Stampa le informazioni della card
										ArrayList<String> cHistory = card.getHistory();
										System.out.printf("Card Info\n");
										System.out.printf("nome: %s\n", card.getName());
										System.out.printf("descrizione: %s\n", card.getDescription());
										System.out.printf("stato: %s\n", cHistory.get(cHistory.size() - 1));
										
										break;
										
									// Gestione degli errori
									case 1:
										System.out.printf("Errore: e' necessario eseguire il login.\n");
										break;
										
									case 2:
										System.out.printf("Errore: sintassi comando errata.\n");
										break;
										
									case 3:
										System.out.printf("Errore: non esiste alcun progetto con nome: %s.\n", split[1].trim());
										break;
										
									case 4:
										System.out.printf("Errore: devi essere membro del progetto per poter visualizzare una card.\n");
										break;
										
									case 5:
										System.out.printf("Errore: nel progetto: %s non esiste alcuna card con nome: %s.\n", split[1].trim(), split[2].trim());
										break;
										
									case -1:
										System.err.printf("%S CLIENT: errore ricezione risposta!\n", tn);
										break;
								}
							}
						}
					
						break;
						
					// Sposta una card da una lista a un'altra
					case "moveCard":
						// Controlla che l'utente sia loggato
						if (login.equals("")) {
							System.out.printf("Errore: e' necessario eseguire il login.\n");
						}
						
						// Se l'utente non ha rispettato la sintassi del comando
						else if (split.length < 5) {
							System.out.println("Sintassi comando errata.");
						}
						
						else {
							// Invia comando al server
							if (!sendCommand(out, input)) {
								System.err.printf("%S CLIENT: errore invio comando!\n", tn);
							}
							
							else {
								// Ricevi messaggio di risposta dal server
								response = receiveResponse(in);
								
								// Invia conferma di ricezione al server
								out.write(ok.array(), 0, 4);
								out.flush();
								
								switch (response) {
									// Successo
									case 0: 
										System.out.printf("La card: %s del progetto: %s e' stata spostata dalla lista: %s alla lista: %s\n", split[2].trim(), split[1].trim(), split[3].trim(), split[4].trim());
										
										// Ottieni l'indirizzo multicast del progetto
										String group = multicastIp.get(split[1].trim());	
										
										// Invia la notifica sulla chat del progetto
										if (group != null) {
											InetAddress ia = InetAddress.getByName(group);
											String notifica = "ho spostato la card " + split[2].trim() + " dalla lista " + split[3].trim() + " alla lista " + split[4].trim() + ".";
											Message msg = new Message(login, notifica);		// Crea l'oggetto di tipo Message
											ByteBuffer msgBuf = ByteBuffer.wrap(new ObjectMapper().writeValueAsBytes(msg));
											DatagramPacket dp = new DatagramPacket(msgBuf.array(), msgBuf.array().length, ia, msPort);
											
											ds.send(dp);	
										}
										
										break;
										
									// Gestione degli errori
									case 1:
										System.out.printf("Errore: e' necessario eseguire il login.\n");
										break;
										
									case 2:
										System.out.printf("Errore: sintassi comando errata.\n");
										break;
										
									case 3:
										System.out.printf("Errore: non esiste alcun progetto con nome: %s.\n", split[1].trim());
										break;
										
									case 4:
										System.out.printf("Errore: devi essere membro del progetto per poter spostare una card.\n");
										break;
										
									case 5:
										System.out.printf("Errore: nel progetto: %s non esiste alcuna card con nome: %s nella lista: %s.\n", split[1].trim(), split[2].trim(), split[3].trim());
										break;
									
									case 6:
										System.out.printf("Errore: la lista di partenza e' inesistente.\n");
										break;
										
									case 7:
										System.out.printf("Errore: la lista di destinazione e' inesistente.\n");
										break;
										
									case 8:
										System.out.printf("Errore: non e' possibile spostare una card dalla lista: %s.\n", split[3].trim());
										break;
										
									case 9:
										System.out.printf("Errore: non e' possibile spostare una card verso la lista: %s.\n", split[4].trim());
										break;
										
									case 10:
										System.out.printf("Errore: non e' possibile spostare una card dalla lista: %s alla lista: %s.\n", split[3].trim(), split[4].trim());
										break;
										
									case -1:
										System.err.printf("%S CLIENT: errore ricezione risposta!\n", tn);
										break;
								}
							}
						}
						
						break;	
						
					// Richiedi la sequenza di eventi di spostamento della card
					case "getCardHistory":
						// Controlla che l'utente sia loggato
						if (login.equals("")) {
							System.out.printf("Errore: e' necessario eseguire il login.\n");
						}
						
						// Se l'utente non ha rispettato la sintassi del comando
						else if (split.length < 3) {
							System.out.println("Sintassi comando errata.");
						}
						
						else {
							// Invia comando al server
							if (!sendCommand(out, input)) {
								System.err.printf("%S CLIENT: errore invio comando!\n", tn);
							}
							
							else {
								// Ricevi messaggio di risposta dal server
								response = receiveResponse(in);
								
								// Invia conferma di ricezione al server
								out.write(ok.array(), 0, 4);
								out.flush();
								
								switch (response) {
								// Successo
								case 0: 
									// Ricevi la storia della card
									bufferIn = new byte[4096];
									in.read(bufferIn, 0, 4096);
									
									ArrayList<String> history = new ObjectMapper().readValue(bufferIn, new TypeReference<ArrayList<String>>(){});
									
									// Invia conferma di ricezione al server
									out.write(ok.array(), 0, 4);
									out.flush();
									
									// Stampa la storia della card
									System.out.printf("Card: %s\n", split[2].trim());
									boolean first = true;
									
									for (String str : history) {
										if (!first) {
											System.out.printf(" -> ", str);
										}
										
										System.out.printf("%s", str);
										first = false;
									}
									
									System.out.printf("\n", split[1].trim());
									
									break;
									
								// Gestione degli errori
								case 1:
									System.out.printf("Errore: e' necessario eseguire il login.\n");
									break;
									
								case 2:
									System.out.printf("Errore: sintassi comando errata.\n");
									break;
									
								case 3:
									System.out.printf("Errore: non esiste alcun progetto con nome: %s.\n", split[1].trim());
									break;
									
								case 4:
									System.out.printf("Errore: devi essere membro del progetto per poter visualizzare la storia di una card.\n");
									break;
									
								case 5:
									System.out.printf("Errore: nel progetto: %s non esiste alcuna card con nome: %s.\n", split[1].trim(), split[2].trim());
									break;
									
								case -1:
									System.err.printf("%S CLIENT: errore ricezione risposta!\n", tn);
									break;
								}
							}
						}
						
						break;
						
					// Invia un messaggio alla chat di un progetto
					case "sendChatMsg":
						// Se l'utente non ha rispettato la sintassi del comando
						if (split.length < 3) {
							System.out.printf("Errore: sintassi comando errata.\n");
						}
						
						// Controlla che l'utente sia loggato
						else if (login.equals("")) {
							System.out.printf("Errore: e' necessario eseguire il login.\n");
						}
						
						else {
							// Ottieni l'indirizzo multicast del progetto
							String group = multicastIp.get(split[1].trim());
							
							// Crea e invia il datagramma 
							if (group != null) {
								InetAddress ia = InetAddress.getByName(group);
								Message msg = new Message(login, input.substring(split[0].length() + split[1].length() + 2, input.length()));
								ByteBuffer msgBuf = ByteBuffer.wrap(new ObjectMapper().writeValueAsBytes(msg));
								DatagramPacket dp = new DatagramPacket(msgBuf.array(), msgBuf.array().length, ia, msPort);
								
								ds.send(dp);	
								
								System.out.println("Messaggio inviato.");
							}
							
							else {
								System.out.printf("Errore: non sei membro di alcun progetto con nome: %s.\n", split[1].trim());
							}
						}
						
						break;
						
					// Stampa i messaggi della chat di un progetto
					case "readChat":
						// Se l'utente non ha rispettato la sintassi del comando
						if (split.length < 2) {
							System.out.printf("Errore: sintassi comando errata.\n");
						}
						
						// Controlla che l'utente sia loggato
						else if (login.equals("")) {
							System.out.printf("Errore: e' necessario eseguire il login.\n");
						}
						
						else {
							// Ottieni i messaggi del progetto
							synchronized(chat) {
								String ip = multicastIp.get(split[1].trim());
								if (ip != null) {
									ArrayList<Message> msgs = chat.get(ip);
									
									// Stampa la chat
									if (msgs != null && msgs.size() > 0) {
										for (Message m : msgs) {
											System.out.printf("%s\n", m);
										}
										
										msgs.clear();
									}
									
									else {
										System.out.printf("Non ci sono nuovi messaggi nella chat del progetto %s.\n", split[1].trim());
									}
								}
								
								else {
									System.out.printf("Errore: non sei membro di alcun progetto con nome: %s.\n", split[1].trim());
								}
							}
						}
						
						break;
					
					// Cancella un progetto
					case "cancelProject":
						// Controlla che l'utente sia loggato
						if (login.equals("")) {
							System.out.printf("Errore: e' necessario eseguire il login.\n");
						}
						
						// Se l'utente non ha rispettato la sintassi del comando
						else if (split.length < 2) {
							System.out.println("Sintassi comando errata.");
						}
						
						else {
							// Invia comando al server
							if (!sendCommand(out, input)) {
								System.err.printf("%S CLIENT: errore invio comando!\n", tn);
							}
							
							else {
								// Ricevi messaggio di risposta dal server
								response = receiveResponse(in);
								
								// Invia conferma di ricezione al server
								out.write(ok.array(), 0, 4);
								out.flush();
								
								switch(response) {
									// Successo
									case 0:
										System.out.printf("Il progetto %s e' stato eliminato.\n", split[1].trim());
										
										break;
										
									// Gestione degli errori
									case 1:
										System.out.printf("Errore: e' necessario eseguire il login.\n");
										break;
										
									case 2:
										System.out.printf("Errore: sintassi comando errata.\n");
										break;
										
									case 3:
										System.out.printf("Errore: non esiste alcun progetto con nome: %s.\n", split[1].trim());
										break;
										
									case 4:
										System.out.printf("Errore: devi essere membro del progetto per poterlo eliminare.\n");
										break;
										
									case 5:
										System.out.printf("Errore: nel progetto: %s sono presenti card ancora non terminate.\n", split[1].trim());
										break;
										
									case -1:
										System.err.printf("%S CLIENT: errore ricezione risposta!\n", tn);
										break;
								}
							}
						}
						
						break;
						
					// Chiudi il client
					case "quit":
						// Se l'utente e' ancora loggato, errore
						if (!login.equals("")) {
							System.out.println("Errore: esegui il logout prima di usare questo comando.");
						}
						
						// Altrimenti, esci dal loop dei comandi
						else {
							quit = true;
							
							// Invia comando al server
							if (!sendCommand(out, input)) {
								System.err.printf("%S CLIENT: errore invio comando!\n", tn);
							}
						}
						
						break;
						
					// Aiuto (mostra comandi disponibili)
					case "help":
						// Comandi disponibili se il client non e' loggato
						if (login.equals("")) {
							System.out.printf("Non sei loggato. Comandi disponibili:\n\n");
							System.out.printf("help\nVisualizza questa schermata.\n\n");
							System.out.printf("register [nickUtente] [password]\nRegistra un nuovo utente al servizio.\n\n");
							System.out.printf("login [nickUtente] [password]\nEffettua il login per accedere al servizio.\n\n");
							System.out.printf("quit\nChiude il client.\n\n");
						}
						
						// Comandi disponibili se il client e' loggato
						else {
							System.out.printf("Sei loggato. Comandi disponibili:\n\n");
							System.out.printf("help\nVisualizza questa schermata.\n\n");
							System.out.printf("register [nickUtente] [password]\nRegistra un nuovo utente al servizio.\n\n");
							System.out.printf("logout [nickUtente]\nEffettua il logout dell'utente dal servizio.\n\n");
							System.out.printf("listUsers\nVisualizza la lista degli utenti registrati al servizio e il loro stato (online/offline).\n\n");
							System.out.printf("listOnlineUsers\nVisualizza la lista degli utenti online in questo momento.\n\n");
							System.out.printf("listProjects\nVisualizza la lista dei progetti di cui sei membro.\n\n");
							System.out.printf("createProject [projectName]\nCrea un nuovo progetto.\n\n");
							System.out.printf("addMember [projectName] [nickUtente]\nAggiunge un utente a un progetto.\n\n");
							System.out.printf("showMembers [projectName]\nVisualizza la lista dei membri di un progetto.\n\n");
							System.out.printf("showCards [projectName]\nVisualizza la lista di card associate ad un progetto.\n\n");
							System.out.printf("showCard [projectName] [cardName]\nVisualizza le informazioni (nome, descrizione, lista) di una card associata a un progetto.\n\n");
							System.out.printf("moveCard [projectName] [cardName] [listaPartenza] [listaDestinazione]\nSposta una card di un progetto da una lista a un'altra.\n\n");
							System.out.printf("getCardHistory [projectName] [cardName]\nVisualizza la storia della card (sequenza di eventi di spostamento).\n\n");
							System.out.printf("readChat [projectName]\nVisualizza i messaggi della chat di un progetto.\n\n");
							System.out.printf("sendChatMsg [projectName] [messaggio]\nInvia un messaggio alla chat di un progetto.\n\n");
							System.out.printf("cancelProject [projectName]\nCancella un progetto; richiede che tutte le card del progetto siano nella lista \"done\".\n");
						}
						
						break;
						
					// Comando non riconosciuto
					default:
						System.out.println("Errore: comando non riconosciuto.\n");
				}
			}
			
			// Chiudi il registro RMI
			UnicastRemoteObject.unexportObject(callbackObj, true);
			// Chiudi lo scanner
			s.close();
			
			System.out.printf("%S CLIENT: uscita dal programma.\n", tn);
		} catch (RemoteException e2) {
			System.err.printf("%S CLIENT: errore registrazione.\n", tn);
		}  catch (ArrayIndexOutOfBoundsException e3) {
			System.err.printf("%S CLIENT: errore sintassi comando.\n", tn);
		} catch (IOException e) {
			System.err.printf("%S CLIENT: errore connessione.\n", tn);
		}
	}
	
	// Metodo statico che invia al server il comando dato come parametro
	private static boolean sendCommand(BufferedOutputStream out, String input) {
		byte[] cmd = input.getBytes();
		
		try {
			out.write(cmd, 0, cmd.length);
			out.flush();
		} catch (IOException e) {
			e.printStackTrace();
			
			return false;
		}
		
		return true;
	}
	
	// Metodo statico che riceve la risposta dal server
	private static int receiveResponse(BufferedInputStream in) {
		byte[] bufferIn = new byte[4096];
		
		try {
			in.read(bufferIn, 0, 4096);
		} catch (IOException e) {
			e.printStackTrace();
			return -1;
		}
		
		ByteBuffer wrapped = ByteBuffer.wrap(bufferIn);
		
		return wrapped.getInt();
	}
}
