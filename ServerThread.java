// Luca Lombardo, mat. 546688

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ServerThread implements Runnable {
	private Socket socket;
	private Object userSync;			// Variabile per la sincronizzazione su utenti registrati e loggati
	private Object projectSync;			// Variabile per la sincronizzazione sui progetti
	ArrayList<User> members;			// Lista degli utenti registrati al servizio
	ArrayList<User> loggedUsers;		// Lista degli utenti loggati
	ArrayList<Project> projects;		// Lista dei progetti
	ServerRemote service;
	User login = null;		// Se != null, l'utente e' loggato
	//ArrayList<String> projectsIp = null;
	int[] ip;	// Ip multicast di partenza, usato per la creazione di nuovi progetti
	
	ServerThread(Socket socket, Object userSync, Object projectSync, ArrayList<User> members, ArrayList<User> loggedUsers, ArrayList<Project> projects, ServerRemote service, int[] ip) {
		this.socket = socket;
		this.userSync = userSync;
		this.projectSync = projectSync;
		this.members = members;
		this.loggedUsers = loggedUsers;
		this.projects = projects;
		this.service = service;
		this.ip = ip;
		//projectsIp = new ArrayList<String>();
	}
	
	public void run() {
		String tn = Thread.currentThread().getName();	// Thread attuale
		boolean quit = false;
		
		System.out.printf("%S: nuova connessione: %s\n", tn, socket);
		
		while (!quit) {
			// Leggo il comando dell'utente in un BufferedInputStream
			try { 
				BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
				BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
				byte[] bufferIn = new byte[4096]; 
				in.read(bufferIn, 0, 4096);
				String input = new String(bufferIn);
				String[] split = input.split(" ");	
				
				System.out.printf("%S: ricevuto comando: %s.\n", tn, split[0]);
				
				// Comando null
				if (split[0] == null) {
					System.out.printf("%S: errore comando!\n", tn);
				}
				
				// Controlla il tipo di comando ricevuto
				switch (split[0].trim()) {
					// Login
					case "login":				
						int errorMessage = 0;
						
						// Sincronizza l'accesso agli utenti registrati
						synchronized(userSync) {
							// Se l'utente non ha inserito nick e password, errore
							if (split.length < 3) {
								System.out.printf("%S: sintassi comando errata.\n", tn);
								errorMessage = 4;
							}
							
							// Se il client e' gia' loggato, errore
							else if (login != null) {
								System.out.printf("%S: il client e' gia' loggato.\n", tn);
								errorMessage = 3;
							}
							
							else {
								Iterator<User> it = members.iterator();
								User us = null;
								boolean found = false;
							
								// Controlla se esiste un utente registrato con quel nickname
								while (it.hasNext() && !found) {
									User u = it.next();
									
									// Se esiste, OK
									if (u.getNickUtente().equals(split[1].trim())) {
										us = u;
										found = true;
									}
								}
								
								// Se non esiste alcun utente con quel nick, errore
								if (!found) {
									System.out.printf("%S: un client ha tentato il login con un nick non registrato.\n", tn);
									errorMessage = 1;
								}
								
								else {
									
									// Controlla se l'utente e' gia' loggato su un altro client
									boolean alreadyLoggedIn = false;
								
									for (User logUs : loggedUsers) {
										if (logUs.getNickUtente().equals(split[1].trim())) {
											alreadyLoggedIn = true;
										}
									}
									
									// Se l'utente e' gia' loggato su un altro client, errore
									if (alreadyLoggedIn) {
										System.out.printf("%S: un client ha tentato il login di un utente gia' loggato.\n", tn);
										errorMessage = 5;
									}
									
									// Altrimenti...
									else {
										// Controlla se la password e' corretta
										if (us.getPassword().equals(split[2].trim())) {
											System.out.printf("%S: un client ha eseguito il login con successo.\n", tn);
											login = us;
											loggedUsers.add(login);
											service.update(members, loggedUsers);
											errorMessage = 0;
										}
										
										// Se la password e' errata, errore
										else {
											System.out.printf("%S: un client ha tentato il login con password errata.\n", tn);
											errorMessage = 2;
										}
									}
								}
							}	
							
							// Invia messaggio di successo/errore
							if (!sendMessage(out, errorMessage)) {
								System.err.printf("%S: errore invio messaggio!");
							}
							
							// Aspetta conferma di ricezione da parte del client
							if (!receiveConfirmation(in, bufferIn)) {
								System.err.printf("%S: errore invio messaggio!");
							}
							
							// Se il login e' andato a buon fine, invia le liste di utenti registrati e loggati
							if (errorMessage == 0) {
								// Invia lista degli utenti registrati
								ByteBuffer membersBuf = ByteBuffer.wrap(new ObjectMapper().writeValueAsBytes(members));
								out.write(membersBuf.array(), 0, membersBuf.array().length);
								out.flush();
													
								// Aspetta conferma di ricezione da parte del client
								if (!receiveConfirmation(in, bufferIn)) {
									System.err.printf("%S: errore invio messaggio!");
								}
								
								// Invia lista degli utenti loggati
								ByteBuffer loggedUsersBuf = ByteBuffer.wrap(new ObjectMapper().writeValueAsBytes(loggedUsers));
								out.write(loggedUsersBuf.array(), 0, loggedUsersBuf.array().length);
								out.flush();
								
								// Aspetta conferma di ricezione da parte del client
								if (!receiveConfirmation(in, bufferIn)) {
									System.err.printf("%S: errore invio messaggio!");
								}
								
								// Crea l'insieme degli indirizzi multicast dei progetti del quale l'utente e' membro
								HashMap<String, String> ips = new HashMap<String, String>();
								
								for (Project p : projects) {
									for (User u : p.getProjectMembers()) {
										if (u.getNickUtente().toLowerCase().equals(login.getNickUtente().toLowerCase())) {
											ips.put(p.getName(), p.getMulticastIp());
										}
									}
								}
								
								// Invia l'insieme degli indirizzi multicast
								ByteBuffer multicastBuf = ByteBuffer.wrap(new ObjectMapper().writeValueAsBytes(ips));
								out.write(multicastBuf.array(), 0, multicastBuf.array().length);
								out.flush();
								
								System.out.printf("%S: dati di login inviati.\n", tn);
								
								// Aspetta conferma di ricezione da parte del client
								if (!receiveConfirmation(in, bufferIn)) {
									System.err.printf("%S: errore invio messaggio!");
								}
							}
						}
						
						break;
					
					// Logout
					case "logout":		
						errorMessage = 0;
						
						// Sincronizza l'accesso agli utenti registrati
						synchronized(userSync) {	
							// Se l'utente non ha inserito il nickname, errore
							if (split.length < 2) {
								System.out.printf("%S: sintassi comando errata.\n", tn);
								errorMessage = 3;
							}
							
							// Se il client non e' loggato, errore
							else if (login == null) {
								System.out.printf("%S: il client non e' loggato.\n", tn);
								errorMessage = 1;
							}
							
							// Se l'utente ha inserito un nickname che non corrisponde all'utente loggato, errore
							else if (!split[1].trim().toLowerCase().equals(login.getNickUtente().toLowerCase())) {
								System.out.printf("%S: nickname errato.\n", tn);
								errorMessage = 4;
							}
							
							else {
								Iterator<User> it = loggedUsers.iterator();
								boolean found = false;
							
								// Controlla se esiste un utente registrato con quel nickname
								while (it.hasNext() && !found) {
									User u = it.next();
									
									// Se esiste, OK
									if (u.getNickUtente().equals(login.getNickUtente())) {
										found = true;
									}
								}
								
								if (!found) {
									System.out.printf("%S: errore logout! Utente non trovato.\n", tn);
									errorMessage = 2;
								}
								
								// Logout successful: aggiorna la lista degli utenti loggati
								else {
									loggedUsers.remove(login);
									service.update(members, loggedUsers); 
									login = null;
									errorMessage = 0;
								}
							}
						}
						
						// Invia messaggio di successo/errore
						if (!sendMessage(out, errorMessage)) {
							System.err.printf("%S: errore invio messaggio!");
						}
						
						// Aspetta conferma di ricezione da parte del client
						if (!receiveConfirmation(in, bufferIn)) {
							System.err.printf("%S: errore invio messaggio!");
						}
							
						break;
					
					// Crea un nuovo progetto
					case "createProject":
						errorMessage = 0;
						
						// Se il client non e' loggato, errore
						if (login == null) {
							System.out.printf("%S: il client non e' loggato.\n", tn);
							errorMessage = 1;
						}
						
						else {
							// Se l'utente non ha inserito il nome del progetto, errore
							if (split.length < 2) {
								System.out.printf("%S: sintassi comando errata.\n", tn);
								errorMessage = 2;
							}
							
							else {
								// Sincronizza l'accesso ai progetti
								synchronized(projectSync) {
									boolean found = false;
									
									// Controlla se esiste gia' un progetto con lo stesso nome
									for (Project p : projects) {
										if (p.getName().toLowerCase().equals(split[1].trim().toLowerCase())) {
											found = true;
										}
									}
									
									// Se esiste gia', errore
									if (found) {
										System.out.printf("%S: progetto gia' esistente.\n", tn);
										errorMessage = 3;
									}
									
									// Altrimenti...
									else {
										String projectPath = "projects/" + split[1].trim();
										File projectDirectory = new File(projectPath);
										
										if (projectDirectory.exists()) {
											System.err.printf("%S: errore! Cartella gia' esistente.en", tn);
												
											throw new Exception();
										}
											
										else {	
											// Incrementa l'ip da utilizzare per il progetto
											if (ip[3] < 255) {
												ip[3]++;
											}
											
											else {
												if (ip[2] < 255) {
													ip[2]++;
													ip[3] = 1;
												}
												
												else {
													if (ip[1] < 255) {
														ip[1]++;
														ip[2] = 0;
														ip[3] = 0;
													}
													
													// Non sono piu' disponibili ip multicast
													else {
														 System.out.printf("%S: indirizzi multicast terminati!\n", Thread.currentThread().getName());
														 errorMessage = 4;
													}
												}
											}
											
											if (errorMessage == 0) {
												// Crea la cartella del progetto
												projectDirectory.mkdir();
												System.out.printf("%S: cartella del progetto creata.\n", tn);
												
												// Crea il progetto
												Project p = new Project(split[1].trim(), ip);
													
												// Aggiungi l'utente come membro del progetto appena creato
												if (!p.addMember(login)) {
													System.err.printf("%S: errore! Membro gia' esistente.\n", tn);
												}
													
												// Aggiorna la lista dei progetti
												projects.add(p);
												
												// Crea il file dei membri
												File memFile = new File(projectDirectory + "/" + p.getName() + "Members.json");
												FileOutputStream fos = null;
												ByteBuffer buffer = null;

												try {
													memFile.createNewFile();
												    fos = new FileOutputStream(memFile);
												} catch (IOException e) {
													System.err.printf("%S: errore creazione file projectMembers.\n", Thread.currentThread().getName());
												    e.printStackTrace();
												}
												    
												FileChannel fc = fos.getChannel();

												// Scrivi sul file dei membri
												try {
													buffer = ByteBuffer.wrap(new ObjectMapper().writeValueAsBytes(p.getProjectMembers()));
												    fc.write(buffer);
												    buffer.clear();
												} catch (IOException e) {
												    System.err.printf("%S: errore scrittura file projectMembers.\n", Thread.currentThread().getName());
												    e.printStackTrace();
												    fos.close();
												}
												
												fos.close();		// Chiudi FileOutputStream
												
												// Crea il file contenente l'indirizzo multicast
												File ipFile = new File(projectDirectory + "/" + p.getName() + "Multicast.json");

												try {
													ipFile.createNewFile();
												    fos = new FileOutputStream(ipFile);
												} catch (IOException e) {
													System.err.printf("%S: errore creazione file projectMulticast.\n", Thread.currentThread().getName());
												    e.printStackTrace();
												    fos.close();
												}
												    
												fc = fos.getChannel();

												// Scrivi sul file del multicast
												try {
													buffer = ByteBuffer.wrap(new ObjectMapper().writeValueAsBytes(ip));
												    fc.write(buffer);
												    buffer.clear();
												} catch (IOException e) {
												    System.err.printf("%S: errore scrittura file projectMulticast.\n", Thread.currentThread().getName());
												    e.printStackTrace();
												    fos.close();
												}											
													
												errorMessage = 0;
												fos.close();	// Chiudi FileOutputStream
											}
										}
									}
								}
							}
						}
						
						// Invia messaggio di successo/errore
						if (!sendMessage(out, errorMessage)) {
							System.err.printf("%S: errore invio messaggio!");
						}
						
						// Aspetta conferma di ricezione da parte del client
						if (!receiveConfirmation(in, bufferIn)) {
							System.err.printf("%S: errore invio messaggio!");
						}
						
						// Se l'operazione e' andata a buon fine, manda l'indirizzo multicast del progetto appena creato
						if (errorMessage == 0) {
							ByteBuffer projectsBuf = ByteBuffer.wrap(new ObjectMapper().writeValueAsBytes(ip));
							out.write(projectsBuf.array(), 0, projectsBuf.array().length);
							out.flush();
							
							// Aspetta conferma di ricezione da parte del client
							if (!receiveConfirmation(in, bufferIn)) {
								System.err.printf("%S: errore invio messaggio!");
							}
						}
						
						break;
						
					// Recupera la lista dei progetti di cui l'utente e' membro
					case "listProjects":
						errorMessage = 0;
						
						// Crea la lista da restituire come risultato
						ArrayList<String> pNames = new ArrayList<String>();
						
						// Se il client non e' loggato, errore
						if (login == null) {
							System.out.printf("%S: il client non e' loggato.\n", tn);
							errorMessage = 1;
						}
						
						else {
							// Sincronizza l'accesso ai progetti
							synchronized(projectSync) {
								// Popola la lista con i nomi dei progetti di cui l'utente e' membro
								for (Project p : projects) {
									for (User up : p.getProjectMembers()) {
										if (up.getNickUtente().equals(login.getNickUtente())) {
											pNames.add(p.getName());
										}
									}
								}
							}
						}
						
						// Invia messaggio di successo/errore
						if (!sendMessage(out, errorMessage)) {
							System.err.printf("%S: errore invio messaggio!");
						}
						
						// Aspetta conferma di ricezione da parte del client
						if (!receiveConfirmation(in, bufferIn)) {
							System.err.printf("%S: errore invio messaggio!");
						}
						
						// Se l'operazione e' andata a buon fine, manda la lista risultato
						if (errorMessage == 0) {
							ByteBuffer projectsBuf = ByteBuffer.wrap(new ObjectMapper().writeValueAsBytes(pNames));
							out.write(projectsBuf.array(), 0, projectsBuf.array().length);
							out.flush();
							
							// Aspetta conferma di ricezione da parte del client
							if (!receiveConfirmation(in, bufferIn)) {
								System.err.printf("%S: errore invio messaggio!");
							}
						}
						
						break;
						
					// Aggiungi un utente a un progetto
					case "addMember":
						errorMessage = 0;
						
						// Se il client non e' loggato, errore
						if (login == null) {
							System.out.printf("%S: il client non e' loggato.\n", tn);
							errorMessage = 1;
						}
						
						// Se l'utente non ha seguito correttamente la sintassi del comando, errore
						else if (split.length < 3) {
							System.out.printf("%S: sintassi comando errata.\n", tn);
							errorMessage = 6;
						}
						
						else {			
							boolean found = false;
							Project progetto = null;
							
							// Sincronizza l'accesso ai progetti
							synchronized(projectSync) {
								// Cerca il progetto
								for (Project p : projects) {
									if (p.getName().equals(split[1].trim())) {
										found = true;
										progetto = p;
									}
								}
								
								// Se non esiste alcun progetto con questo nome, errore
								if (!found) {
									System.out.printf("%S: progetto non esistente.\n", tn);
									errorMessage = 2;
								}
								
								else {
									found = false;
									
									// Controlla se l'utente e' membro del progetto
									for (User u : progetto.getProjectMembers()) {
										if (u.getNickUtente().equals(login.getNickUtente())) {
											found = true;
										}
									}
									
									// Se l'utente non e' membro del progetto, errore
									if (!found) {
										System.out.printf("%S: l'utente non e' membro del progetto.\n", tn);
										errorMessage = 3;
									}
									
									else {
										// Sincronizza l'accesso agli utenti registrati
										synchronized(userSync) {
											Iterator<User> it = members.iterator();
											found = false;
										
											// Controlla se l'utente da aggiungere e' registrato
											while (it.hasNext() && !found) {
												User u = it.next();
												
												// Se e' registrato, aggiungilo alla lista dei membri del progetto
												if (u.getNickUtente().equals(split[2].trim())) {
													found = false;
													
													// Controlla se e' gia' presente nella lista dei membri del progetto
													for (User user : progetto.getProjectMembers()) {
														if (user.getNickUtente().equals(split[2].trim())) {
															found = true;
														}
													}
													
													// Se l'utente era gia' membro del progetto, errore
													if (found) {
														System.out.printf("%S: l'utente e' gia' membro del progetto.\n", tn);
														errorMessage = 5;
													}
													
													// Altrimenti, aggiorna la lista dei membri e sovrascrivi il file projectMembers
													else {
														progetto.addMember(u);
														
														String projectPath = "projects/" + split[1].trim();
														File projectDirectory = new File(projectPath);
														File memFile = new File(projectDirectory + "/" + progetto.getName() + "Members.json");
														FileOutputStream fos = null;
														ByteBuffer buffer = null;

														try {
															memFile.createNewFile();
														    fos = new FileOutputStream(memFile);
														} catch (IOException e) {
															System.err.printf("%S: errore creazione file projectMembers.\n", Thread.currentThread().getName());
														    e.printStackTrace();
														}
														    
														FileChannel fc = fos.getChannel();

														try {
															buffer = ByteBuffer.wrap(new ObjectMapper().writeValueAsBytes(progetto.getProjectMembers()));
														    fc.write(buffer);
														    buffer.clear();
														} catch (IOException e) {
														    System.err.printf("%S: errore scrittura file projectMembers.\n", Thread.currentThread().getName());
														    e.printStackTrace();
															fos.close();
														}
														
														fos.close(); // Chiudi il FileOutputStream
														
														// Fai la callback all'utente aggiunto al progetto
														service.addMemberCallback(split[2].trim(), progetto.getName(), progetto.getMulticastIp());
													}
													
													found = true;
												}
											}
										}
										
										// Se non esiste alcun utente registrato con quel nick, errore
										if (!found) {
											System.out.printf("%S: utente inesistente.\n", tn);
											errorMessage = 4;
										}
									}
								}
							}
						}
						
						// Invia messaggio di successo/errore
						if (!sendMessage(out, errorMessage)) {
							System.err.printf("%S: errore invio messaggio!");
						}
						
						// Aspetta conferma di ricezione da parte del client
						if (!receiveConfirmation(in, bufferIn)) {
							System.err.printf("%S: errore invio messaggio!");
						}
						
						break;
						
					// Recupera la lista dei membri di un progetto
					case "showMembers":	
						errorMessage = 0;
						// Crea la lista da restituire come risultato
						ArrayList<String> mNames = new ArrayList<String>();
						
						// Se il client non e' loggato, errore
						if (login == null) {
							System.out.printf("%S: il client non e' loggato.\n", tn);
							errorMessage = 1;
						}
						
						// Se l'utente non ha seguito correttamente la sintassi del comando, errore
						else if (split.length < 2) {
							System.out.printf("%S: sintassi comando errata.\n", tn);
							errorMessage = 2;
						}
						
						else {
							boolean found = false;
							Project progetto = null;
							
							// Sincronizza l'accesso ai progetti
							synchronized(projectSync) {
								// Cerca il progetto
								for (Project p : projects) {
									if (p.getName().equals(split[1].trim())) {
										found = true;
										progetto = p;
									}
								}
								
								// Se non esiste alcun progetto con questo nome, errore
								if (!found) {
									System.out.printf("%S: progetto inesistente.\n", tn);
									errorMessage = 3;
								}
								
								else {
									found = false;
									
									// Controlla se l'utente e' membro del progetto
									for (User u : progetto.getProjectMembers()) {
										if (u.getNickUtente().equals(login.getNickUtente())) {
											found = true;
										}
									}
									
									// Se l'utente non e' membro del progetto, errore
									if (!found) {
										System.out.printf("%S: l'utente non e' membro del progetto.\n", tn);
										errorMessage = 4;
									}
									
									// Altrimenti, popola la lista risultato
									else {
										for (User u : progetto.getProjectMembers()) {
											mNames.add(u.getNickUtente());
										}
									}
								}
							}
						}
									
						// Invia messaggio di successo/errore
						if (!sendMessage(out, errorMessage)) {
							System.err.printf("%S: errore invio messaggio!");
						}
						
						// Aspetta conferma di ricezione da parte del client
						if (!receiveConfirmation(in, bufferIn)) {
							System.err.printf("%S: errore invio messaggio!");
						}
						
						// Se non ci sono errori, invia la lista dei membri del progetto
						if (errorMessage == 0) {
							// Invia lista dei membri
							ByteBuffer projMembBuf = ByteBuffer.wrap(new ObjectMapper().writeValueAsBytes(mNames));
							out.write(projMembBuf.array(), 0, projMembBuf.array().length);
							out.flush();
												
							// Aspetta conferma di ricezione da parte del client
							if (!receiveConfirmation(in, bufferIn)) {
								System.err.printf("%S: errore invio messaggio!");
							}
						}
						
						break;
						
					// Aggiungi una card a un progetto
					case "addCard":
						errorMessage = 0;
						
						// Se il client non e' loggato, errore
						if (login == null) {
							System.out.printf("%S: il client non e' loggato.\n", tn);
							errorMessage = 1;
						}
						
						// Se l'utente non ha seguito correttamente la sintassi del comando, errore
						else if (split.length < 4) {
							System.out.printf("%S: sintassi comando errata.\n", tn);
							errorMessage = 2;
						}
						
						else {
							boolean found = false;
							Project progetto = null;
							
							// Sincronizza l'accesso ai progetti
							synchronized(projectSync) {
								// Cerca il progetto
								for (Project p : projects) {
									if (p.getName().equals(split[1].trim())) {
										found = true;
										progetto = p;
									}
								}
								
								// Se non esiste alcun progetto con questo nome, errore
								if (!found) {
									System.out.printf("%S: progetto non esistente.\n", tn);
									errorMessage = 3;
								}
								
								else {
									found = false;
									
									// Controlla se l'utente e' membro del progetto
									for (User u : progetto.getProjectMembers()) {
										if (u.getNickUtente().equals(login.getNickUtente())) {
											found = true;
										}
									}
									
									// Se l'utente non e' membro del progetto, errore
									if (!found) {
										System.out.printf("%S: l'utente non e' membro del progetto.\n", tn);
										errorMessage = 4;
									}
									
									else {
										ArrayList<Card> todo = progetto.getToDo();
										found = false;
										
										// Controlla se esiste gia' una card con questo nome
										for (Card c : todo) {
											if (c.getName().toLowerCase().equals(split[2].trim().toLowerCase())) {
												found = true;
											}
										}
										
										// Se esiste, errore
										if (found) {
											System.out.printf("%S: esiste gia' una card con questo nome.\n", tn);
											errorMessage = 5;
										}
										
										// Se il nome della card e' uguale al nome del file dei membri, errore
										else if (split[2].trim().toLowerCase().equals((split[1].trim() + "Members").toLowerCase()) || 
												split[2].trim().toLowerCase().equals((split[1].trim() + "Multicast").toLowerCase())) {
											System.out.printf("%S: nome card non valido.\n", tn);
											errorMessage = 6;
										}
										
										else {
											// Crea la nuova card e aggiungila al progetto
											Card card = new Card(split[2].trim(), input.substring(split[0].length() + split[1].length() + split[2].length() + 3, input.trim().length()));
											//Card card = new Card(split[2].trim(), split[3].trim());
											todo.add(card);
											progetto.setToDo(todo);
											
											// Crea il file per persistere la card
											String fpath = "projects/" + split[1].trim() + "/" + split[2].trim() + ".json";
											File f = new File(fpath);
											FileOutputStream fos = null;
											ByteBuffer buffer = null;
											    
											try {
												f.createNewFile();
											    fos = new FileOutputStream(f);
											} catch (IOException e) {
												System.err.printf("%S: errore creazione file della card.\n", Thread.currentThread().getName());
											    e.printStackTrace();
											}
											    
											FileChannel fc = fos.getChannel();

											try {
												buffer = ByteBuffer.wrap(new ObjectMapper().writeValueAsBytes(card));
											    fc.write(buffer);
											    buffer.clear();
											} catch (IOException e) {
											    System.err.printf("%S: errore scrittura file della card.\n", Thread.currentThread().getName());
											    e.printStackTrace();
												fos.close();
											}
											
											fos.close(); // Chiudi il FileOutputStream
										}
									}
								}
							}
						}
						
						// Invia messaggio di successo/errore
						if (!sendMessage(out, errorMessage)) {
							System.err.printf("%S: errore invio messaggio!");
						}
						
						// Aspetta conferma di ricezione da parte del client
						if (!receiveConfirmation(in, bufferIn)) {
							System.err.printf("%S: errore invio messaggio!");
						}
						
						break;
						
					// Recupera la lista di card associate a un progetto
					case "showCards":
						errorMessage = 0;
						ArrayList<Card> result = new ArrayList<Card>();
						
						// Se il client non e' loggato, errore
						if (login == null) {
							System.out.printf("%S: il client non e' loggato.\n", tn);
							errorMessage = 1;
						}
						
						// Se l'utente non ha seguito correttamente la sintassi del comando, errore
						else if (split.length < 2) {
							System.out.printf("%S: sintassi comando errata.\n", tn);
							errorMessage = 2;
						}
						
						else {
							boolean found = false;
							Project progetto = null;
							
							// Sincronizza l'accesso ai progetti
							synchronized(projectSync) {
								// Cerca il progetto
								for (Project p : projects) {
									if (p.getName().equals(split[1].trim())) {
										found = true;
										progetto = p;
									}
								}
								
								// Se non esiste alcun progetto con questo nome, errore
								if (!found) {
									System.out.printf("%S: progetto non esistente.\n", tn);
									errorMessage = 3;
								}
								
								else {
									found = false;
									
									// Controlla se l'utente e' membro del progetto
									for (User u : progetto.getProjectMembers()) {
										if (u.getNickUtente().equals(login.getNickUtente())) {
											found = true;
										}
									}
									
									// Se l'utente non e' membro del progetto, errore
									if (!found) {
										System.out.printf("%S: l'utente non e' membro del progetto.\n", tn);
										errorMessage = 4;
									}
									
									else {
										// Aggiungi tutte le card alla lista risultato
										result.addAll(progetto.getToDo());
										result.addAll(progetto.getInProgress());
										result.addAll(progetto.getToBeRevised());
										result.addAll(progetto.getDone());
									}
								}
							}
						}
						
						// Invia messaggio di successo/errore
						if (!sendMessage(out, errorMessage)) {
							System.err.printf("%S: errore invio messaggio!");
						}
						
						// Aspetta conferma di ricezione da parte del client
						if (!receiveConfirmation(in, bufferIn)) {
							System.err.printf("%S: errore invio messaggio!");
						}

						// Se non ci sono errori, invia la lista delle card
						if (errorMessage == 0) {
							// Invia lista delle card
							ByteBuffer cardBuf = ByteBuffer.wrap(new ObjectMapper().writeValueAsBytes(result));
							out.write(cardBuf.array(), 0, cardBuf.array().length);
							out.flush();
												
							// Aspetta conferma di ricezione da parte del client
							if (!receiveConfirmation(in, bufferIn)) {
								System.err.printf("%S: errore invio messaggio!");
							}
						}
						
						break;
						
					// Recupera le informazioni di una card
					case "showCard":
						errorMessage = 0;
						Card cardResult = null;
						
						// Se il client non e' loggato, errore
						if (login == null) {
							System.out.printf("%S: il client non e' loggato.\n", tn);
							errorMessage = 1;
						}
						
						// Se l'utente non ha seguito correttamente la sintassi del comando, errore
						else if (split.length < 3) {
							System.out.printf("%S: sintassi comando errata.\n", tn);
							errorMessage = 2;
						}
						
						else {
							boolean found = false;
							Project progetto = null;
							
							// Sincronizza l'accesso ai progetti
							synchronized(projectSync) {
								// Cerca il progetto
								for (Project p : projects) {
									if (p.getName().equals(split[1].trim())) {
										found = true;
										progetto = p;
									}
								}
								
								// Se non esiste alcun progetto con questo nome, errore
								if (!found) {
									System.out.printf("%S: progetto non esistente.\n", tn);
									errorMessage = 3;
								}
								
								else {
									found = false;
									
									// Controlla se l'utente e' membro del progetto
									for (User u : progetto.getProjectMembers()) {
										if (u.getNickUtente().equals(login.getNickUtente())) {
											found = true;
										}
									}
									
									// Se l'utente non e' membro del progetto, errore
									if (!found) {
										System.out.printf("%S: l'utente non e' membro del progetto.\n", tn);
										errorMessage = 4;
									}
									
									// Cerca la card con questo nome
									else {	
										ArrayList<Card> todo = progetto.getToDo();
										ArrayList<Card> inprogress = progetto.getInProgress();
										ArrayList<Card> toberevised = progetto.getToBeRevised();
										ArrayList<Card> done = progetto.getDone();
										found = false;
										
										// Cerca nella lista ToDo
										for (Card c : todo) {
											if (c.getName().toLowerCase().equals(split[2].trim().toLowerCase())) {
												found = true;
												cardResult = c;
											}
										}
										
										// Cerca nella lista InProgress
										if (!found) {
											for (Card c : inprogress) {
												if (c.getName().toLowerCase().equals(split[2].trim().toLowerCase())) {
													found = true;
													cardResult = c;
												}
											}
										}
										
										// Cerca nella lista ToBeRevised
										if (!found) {
											for (Card c : toberevised) {
												if (c.getName().toLowerCase().equals(split[2].trim().toLowerCase())) {
													found = true;
													cardResult = c;
												}
											}
										}
										
										// Cerca nella lista Done
										if (!found) {
											for (Card c : done) {
												if (c.getName().toLowerCase().equals(split[2].trim().toLowerCase())) {
													found = true;
													cardResult = c;
												}
											}
										}
										
										// Se non esiste una carta con questo nome, errore
										if (!found) {
											System.out.printf("%S: non esiste alcuna card con questo nome.\n", tn);
											errorMessage = 5;
										}
									}
								}
							}
						}
						
						// Invia messaggio di successo/errore
						if (!sendMessage(out, errorMessage)) {
							System.err.printf("%S: errore invio messaggio!");
						}
						
						// Aspetta conferma di ricezione da parte del client
						if (!receiveConfirmation(in, bufferIn)) {
							System.err.printf("%S: errore invio messaggio!");
						}

						// Se non ci sono errori, invia la card
						if (errorMessage == 0) {
							// Invia la card
							ByteBuffer cardResultBuf = ByteBuffer.wrap(new ObjectMapper().writeValueAsBytes(cardResult));
							out.write(cardResultBuf.array(), 0, cardResultBuf.array().length);
							out.flush();
												
							// Aspetta conferma di ricezione da parte del client
							if (!receiveConfirmation(in, bufferIn)) {
								System.err.printf("%S: errore invio messaggio!");
							}
						}
						
						break;
						
					// Sposta una card da una lista a un'altra
					case "moveCard":
						errorMessage = 0;
						
						// Se il client non e' loggato, errore
						if (login == null) {
							System.out.printf("%S: il client non e' loggato.\n", tn);
							errorMessage = 1;
						}
						
						// Se l'utente non ha seguito correttamente la sintassi del comando, errore
						else if (split.length < 5) {
							System.out.printf("%S: sintassi comando errata.\n", tn);
							errorMessage = 2;
						}
						
						else {
							boolean found = false;
							Project progetto = null;
							
							// Sincronizza l'accesso ai progetti
							synchronized(projectSync) {
								// Cerca il progetto
								for (Project p : projects) {
									if (p.getName().equals(split[1].trim())) {
										found = true;
										progetto = p;
									}
								}
								
								// Se non esiste alcun progetto con questo nome, errore
								if (!found) {
									System.out.printf("%S: progetto non esistente.\n", tn);
									errorMessage = 3;
								}
								
								else {
									found = false;
									
									// Controlla se l'utente e' membro del progetto
									for (User u : progetto.getProjectMembers()) {
										if (u.getNickUtente().equals(login.getNickUtente())) {
											found = true;
										}
									}
									
									// Se l'utente non e' membro del progetto, errore
									if (!found) {
										System.out.printf("%S: l'utente non e' membro del progetto.\n", tn);
										errorMessage = 4;
									}
									
									else {
										ArrayList<Card> todo = progetto.getToDo();
										ArrayList<Card> inprogress = progetto.getInProgress();
										ArrayList<Card> toberevised = progetto.getToBeRevised();
										Card toBeMoved = null;
										String partenza = "none";
										
										// Cerca la card con questo nome nella lista specificata dall'utente
										switch (split[3].trim().toLowerCase()) {
											case "todo":
												// Cerca nella lista ToDo
												for (Card c : todo) {
													if (c.getName().toLowerCase().equals(split[2].trim().toLowerCase())) {
														toBeMoved = c;
														partenza = "todo";
													}
												}
												
												break;
												
											case "inprogress":
												// Cerca nella lista InProgress
												for (Card c : inprogress) {
													if (c.getName().toLowerCase().equals(split[2].trim().toLowerCase())) {
														toBeMoved = c;
														partenza = "inprogress";
													}
												}
												
												break;
												
											case "toberevised":
												// Cerca nella lista ToBeRevised
												for (Card c : toberevised) {
													if (c.getName().toLowerCase().equals(split[2].trim().toLowerCase())) {
														toBeMoved = c;
														partenza = "toberevised";
													}
												}
												
												break;
												
											case "done":
												// Cerca nella lista Done
												System.out.printf("%S: la lista di partenza non puo' essere done.\n", tn);
												errorMessage = 8;
												partenza = "done";
												
												break;
												
											default:
												System.out.printf("%S: lista di partenza inesistente.\n", tn);
												errorMessage = 6;
												
												break;
										}
										
										// Se non c'e' una card con questo nome nella lista specificata dall'utente, errore
										if (partenza.equals("none")) {
											System.out.printf("%S: non esiste una card con questo nome nella lista specificata.\n", tn);
											errorMessage = 5;
										}
										
										// Se c'e' la card, spostala
										else if (errorMessage == 0){
											// Controlla la lista destinazione
											switch (split[4].trim().toLowerCase()) {
												// Nessuna card puo' essere spostata in toDo: errore
												case "todo":
													System.out.printf("%S: la lista di destinazione non puo' essere toDo.\n", tn);
													errorMessage = 9;
													
													break;
													
												case "inprogress":
													if (partenza.equals("todo") || partenza.equals("toberevised")) {
														toBeMoved.move("inProgress");
														progetto.moveCard(toBeMoved, partenza, "inprogress");
													}
													
													else {
														System.out.printf("%S: operazione di move non permessa.\n", tn);
														errorMessage = 10;
													}
													
													break;
													
												case "toberevised":
													if (partenza.equals("inprogress")) {
														toBeMoved.move("toBeRevised");
														progetto.moveCard(toBeMoved, partenza, "toberevised");
													}
													
													else {
														System.out.printf("%S: operazione di move non permessa.\n", tn);
														errorMessage = 10;
													}
													
													break;
													
												case "done":
													if (partenza.equals("toberevised") || partenza.equals("inprogress")) {
														toBeMoved.move("done");
														progetto.moveCard(toBeMoved, partenza, "done");
													}
													
													else {
														System.out.printf("%S: operazione di move non permessa.\n", tn);
														errorMessage = 10;
													}
													
													break;
													
												default:
													System.out.printf("%S: lista di destinazione inesistente.\n", tn);
													errorMessage = 7;
													
													break;
											}
											
											// Se non ci son stati errori, sovrascrivi il file della card
											if (errorMessage == 0) {
												String fpath = "projects/" + split[1].trim() + "/" + split[2].trim() + ".json";
												File f = new File(fpath);
												FileOutputStream fos = null;
												ByteBuffer buffer = null;
												    
												try {
													f.createNewFile();
												    fos = new FileOutputStream(f);
												} catch (IOException e) {
													System.err.printf("%S: errore creazione file della card.\n", Thread.currentThread().getName());
												    e.printStackTrace();
												}
												    
												FileChannel fc = fos.getChannel();

												try {
													buffer = ByteBuffer.wrap(new ObjectMapper().writeValueAsBytes(toBeMoved));
												    fc.write(buffer);
												    buffer.clear();
												} catch (IOException e) {
												    System.err.printf("%S: errore scrittura file della card.\n", Thread.currentThread().getName());
												    e.printStackTrace();
													fos.close();
												}
												
												fos.close(); // Chiudi il FileOutputStream
											}
										}
									}
								}
							}
						}
						
						// Invia messaggio di successo/errore
						if (!sendMessage(out, errorMessage)) {
							System.err.printf("%S: errore invio messaggio!");
						}
						
						// Aspetta conferma di ricezione da parte del client
						if (!receiveConfirmation(in, bufferIn)) {
							System.err.printf("%S: errore invio messaggio!");
						}
						
						break;
						
					// Richiedi la sequenza di eventi di spostamento della card
					case "getCardHistory":
						errorMessage = 0;
						ArrayList<String> historyResult = new ArrayList<String>();
						cardResult = null;
						
						// Se il client non e' loggato, errore
						if (login == null) {
							System.out.printf("%S: il client non e' loggato.\n", tn);
							errorMessage = 1;
						}
						
						// Se l'utente non ha seguito correttamente la sintassi del comando, errore
						else if (split.length < 3) {
							System.out.printf("%S: sintassi comando errata.\n", tn);
							errorMessage = 2;
						}
						
						else {
							boolean found = false;
							Project progetto = null;
							
							// Sincronizza l'accesso ai progetti
							synchronized(projectSync) {
								// Cerca il progetto
								for (Project p : projects) {
									if (p.getName().equals(split[1].trim())) {
										found = true;
										progetto = p;
									}
								}
								
								// Se non esiste alcun progetto con questo nome, errore
								if (!found) {
									System.out.printf("%S: progetto non esistente.\n", tn);
									errorMessage = 3;
								}
								
								else {
									found = false;
									
									// Controlla se l'utente e' membro del progetto
									for (User u : progetto.getProjectMembers()) {
										if (u.getNickUtente().equals(login.getNickUtente())) {
											found = true;
										}
									}
									
									// Se l'utente non e' membro del progetto, errore
									if (!found) {
										System.out.printf("%S: l'utente non e' membro del progetto.\n", tn);
										errorMessage = 4;
									}
									
									// Cerca la card con questo nome
									else {	
										ArrayList<Card> todo = progetto.getToDo();
										ArrayList<Card> inprogress = progetto.getInProgress();
										ArrayList<Card> toberevised = progetto.getToBeRevised();
										ArrayList<Card> done = progetto.getDone();
										found = false;
										
										// Cerca nella lista ToDo
										for (Card c : todo) {
											if (c.getName().toLowerCase().equals(split[2].trim().toLowerCase())) {
												found = true;
												cardResult = c;
											}
										}
										
										// Cerca nella lista InProgress
										if (!found) {
											for (Card c : inprogress) {
												if (c.getName().toLowerCase().equals(split[2].trim().toLowerCase())) {
													found = true;
													cardResult = c;
												}
											}
										}
										
										// Cerca nella lista ToBeRevised
										if (!found) {
											for (Card c : toberevised) {
												if (c.getName().toLowerCase().equals(split[2].trim().toLowerCase())) {
													found = true;
													cardResult = c;
												}
											}
										}
										
										// Cerca nella lista Done
										if (!found) {
											for (Card c : done) {
												if (c.getName().toLowerCase().equals(split[2].trim().toLowerCase())) {
													found = true;
													cardResult = c;
												}
											}
										}
										
										// Se non esiste una carta con questo nome, errore
										if (!found) {
											System.out.printf("%S: non esiste alcuna card con questo nome.\n", tn);
											errorMessage = 5;
										}
										
										else {
											historyResult = cardResult.getHistory();
										}
									}
								}
							}
						}
						
						// Invia messaggio di successo/errore
						if (!sendMessage(out, errorMessage)) {
							System.err.printf("%S: errore invio messaggio!");
						}
						
						// Aspetta conferma di ricezione da parte del client
						if (!receiveConfirmation(in, bufferIn)) {
							System.err.printf("%S: errore invio messaggio!");
						}
						
						// Se non ci sono errori, invia l'history
						if (errorMessage == 0) {
							// Invia la card
							ByteBuffer cardHistoryBuf = ByteBuffer.wrap(new ObjectMapper().writeValueAsBytes(historyResult));
							out.write(cardHistoryBuf.array(), 0, cardHistoryBuf.array().length);
							out.flush();
												
							// Aspetta conferma di ricezione da parte del client
							if (!receiveConfirmation(in, bufferIn)) {
								System.err.printf("%S: errore invio messaggio!");
							}
						}
						
						break;
						
					// Cancella un progetto
					case "cancelProject":
						errorMessage = 0;
						
						// Se il client non e' loggato, errore
						if (login == null) {
							System.out.printf("%S: il client non e' loggato.\n", tn);
							errorMessage = 1;
						}
						
						// Se l'utente non ha inserito il nome del progetto, errore
						else if (split.length < 2) {
							System.out.printf("%S: sintassi comando errata.\n", tn);
							errorMessage = 2;
						}
						
						else {
							boolean found = false;
							Project progetto = null;
							
							// Sincronizza l'accesso ai progetti
							synchronized(projectSync) {
								// Cerca il progetto
								for (Project p : projects) {
									if (p.getName().equals(split[1].trim())) {
										found = true;
										progetto = p;
									}
								}
								
								// Se non esiste alcun progetto con questo nome, errore
								if (!found) {
									System.out.printf("%S: progetto non esistente.\n", tn);
									errorMessage = 3;
								}
								
								else {
									found = false;
									
									// Controlla se l'utente e' membro del progetto
									for (User u : progetto.getProjectMembers()) {
										if (u.getNickUtente().equals(login.getNickUtente())) {
											found = true;
										}
									}
									
									// Se l'utente non e' membro del progetto, errore
									if (!found) {
										System.out.printf("%S: l'utente non e' membro del progetto.\n", tn);
										errorMessage = 4;
									}
									
									else {
										// Se sono presenti card in liste diverse da Done, errore
										if (progetto.getToDo().size() > 0 || progetto.getInProgress().size() > 0 || progetto.getToBeRevised().size() > 0) {
											System.out.printf("%S: ci sono card ancora non completate.\n", tn);
											errorMessage = 5;
										}
											
										else {
											// Localizza la cartella del progetto
											String pName = ("projects/" + progetto.getName());
											File pFile = new File(pName);
											String[] entries = pFile.list();
											
											// Elimina i file del progetto
											for (String s : entries) {
												File currentFile = new File(pFile.getPath(), s);
												
												if (!currentFile.delete()) {
													System.err.printf("%S: impossibile eliminare file %s.\n", tn, currentFile.getName());
												}
											}
											
											// Elimina la cartella del progetto
											if (!pFile.delete()) {
												System.err.printf("%S: impossibile eliminare cartella %s.\n", tn, pFile.getName());
											}
											
											// Rimuovi il progetto dalla struttura dati del server
											projects.remove(progetto);
											
											// Manda le callbacks agli utenti membri del progetto
											service.cancelProjectCallback(progetto.getName(), progetto);
										}
									}
								}
							}
						}
						
						// Invia messaggio di successo/errore
						if (!sendMessage(out, errorMessage)) {
							System.err.printf("%S: errore invio messaggio!");
						}
						
						// Aspetta conferma di ricezione da parte del client
						if (!receiveConfirmation(in, bufferIn)) {
							System.err.printf("%S: errore invio messaggio!");
						}
						
						break;
						
					// Il client ha chiuso la connessione
					case "quit":
						quit = true;
						System.out.printf("%S: un client ha chiuso la connessione.\n", tn);
						break;
						
					// Comando non riconosciuto
					default: 
						System.out.printf("%S: un client ha richiesto un'operazione non riconosciuta.\n", tn);
						
						if (login != null) {
							loggedUsers.remove(login);
						}
						
						// Mando le callback (perche' il client che ha chiuso la connessione poteva essere loggato)
						try {
							service.update(members, loggedUsers);
						} catch (RemoteException e1) {
							System.out.printf("%S: errore remoto.\n", tn); 
							e1.printStackTrace();
						}
						
						quit = true;
						System.out.printf("%S: termino.\n", tn);
						
						break;
				}
							
			} catch (SocketException e) { 
				System.out.printf("%S: eccezione %s\n", tn, socket); 
				
				if (login != null) {
					loggedUsers.remove(login);
				}
				
				// Mando le callback (perche' il client che ha chiuso la connessione poteva essere loggato)
				try {
					service.update(members, loggedUsers);
				} catch (RemoteException e1) {
					System.out.printf("%S: errore remoto.\n", tn); 
					e1.printStackTrace();
				}
				
				quit = true;
				
				break;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		System.out.printf("%S: termino.\n", tn);
	}
	
	// Metodo privato che invia al client il messaggio di risposta (successo/errore)
	private boolean sendMessage(BufferedOutputStream out, int errorMessage) {
		ByteBuffer response = ByteBuffer.allocate(4);
		response.putInt(errorMessage);
		try {
			out.write(response.array());
			out.flush();
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	
		return true;
	}
	
	// Metodo privato che aspetta fino a che non riceve la conferma di ricezione dal client
	private boolean receiveConfirmation(BufferedInputStream in, byte[] bufferIn) {
		try {
			in.read(bufferIn, 0, 4096);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		ByteBuffer wrapped = ByteBuffer.wrap(bufferIn);
		int ok = wrapped.getInt();
		
		if (ok != 1) {
			System.err.printf("%S: Errore! Il client ha ricevuto in maniera errata.\n", Thread.currentThread().getName());
			return false;
		}
		
		return true;
	}
}
