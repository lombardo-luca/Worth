// Luca Lombardo, mat. 546688

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ServerMain {
	public static void main(String args[]) {
		Object userSync = new Object();		// Variabile per la sincronizzazione su utenti registrati e loggati
		Object projectSync = new Object();	// Variabile per la sincronizzazione sui progetti
		ServerRemote service = null;
		ArrayList<User> members = new ArrayList<User>();			// Lista degli utenti registrati al servizio
		ArrayList<User> loggedUsers = new ArrayList<User>();		// Lista degli utenti loggati
		ArrayList<Project> projects = new ArrayList<Project>();		// Lista dei progetti
		int[] ip = {224, 0, 0, 1};		// Ip multicast di partenza, passato ai ServerThread e usato nella creazione di nuovi progetti
		String tn = Thread.currentThread().getName();	// Thread attuale
		
		// Se esiste il file degli utenti registrati, leggine i contenuti e popola la lista di membri
		String fpath = "members.json";
		File f = new File(fpath);
		
		// Sincronizza l'accesso agli utenti registrati
		synchronized(userSync) {
			// Cerca il file degli utenti registrati
			if(f.isFile()) { 
				System.out.printf("%S SERVER: file members trovato.\n", tn);
				
				String read = "";
				try {
					read = new String(Files.readAllBytes(Paths.get(fpath)));
				    members = new ObjectMapper().readValue(read, new TypeReference<ArrayList<User>>(){});
				} catch (IOException e) {
					System.err.printf("%S SERVER: errore lettura file.\n", tn);
				    e.printStackTrace();
				}
			}
			
			else {
				System.out.printf("%S SERVER: file members non trovato. Verra' creato con la registrazione.\n", tn);
			}
		}
		
		// Cerca la directory projects
		String projectsPath = "projects";
		File projectDirectory = new File(projectsPath);
		
		// Sincronizza l'accesso ai progetti
		synchronized(projectSync) {
			if (projectDirectory.exists()) {
				System.out.printf("%S SERVER: cartella projects trovata.\n", tn);
				
				// Cerca i progetti
				File[] pDirectories = new File("projects/").listFiles(File::isDirectory);
				
				// Aggiorna la lista interna dei progetti
				for (File pf : pDirectories) {	
					projects.add(new Project(pf.getName()));
					File[] pFiles = pf.listFiles();
					
					if (pFiles != null) {
						for (File ch : pFiles) {
							// Se il file e' la lista dei membri...
							if (ch.getName().equals(pf.getName() + "Members.json")) {
								String read = "";
								
								try {
									String fileName = ("projects/" + pf.getName() + "/" + pf.getName() + "Members.json");
									read = new String(Files.readAllBytes(Paths.get(fileName)));
									Project progetto = null;
									
								    for (Project p : projects) {
								    	if (p.getName().equals(pf.getName())) {
								    		progetto = p;
								    	}	
								    }
								    
								    // Leggi la lista dei membri dal file .json e aggiorna la struttura dati interna
									progetto.setProjectMembers(new ObjectMapper().readValue(read, new TypeReference<ArrayList<User>>(){}));
								} catch (IOException e) {
									System.err.printf("%S SERVER: errore lettura file projectMembers.\n", tn);
								    e.printStackTrace();
								}
							}
							
							// Se il file e' l'indirizzo multicast...
							else if (ch.getName().equals(pf.getName() + "Multicast.json")) {
								String read = "";
								
								try {
									String fileName = ("projects/" + pf.getName() + "/" + pf.getName() + "Multicast.json");
									read = new String(Files.readAllBytes(Paths.get(fileName)));
									Project progetto = null;
									
								    for (Project p : projects) {
								    	if (p.getName().equals(pf.getName())) {
								    		progetto = p;
								    	}	
								    }
									
								    // Leggi l'indirizzo ip multicast dal file json
									int[] currentIp = new ObjectMapper().readValue(read, new TypeReference<int[]>(){});
									progetto.setMulticastIp(currentIp);
									
									System.out.printf("%S SERVER: ho trovato il progetto %s con ip %s.\n", tn, progetto.getName(), progetto.getMulticastIp());
									
									// Tieni in memoria l'ip multicast maggiore: serve per la creazione di nuovi progetti
									if (currentIp[1] > ip[1]) {
										ip = currentIp.clone();
									}
									
									else if (currentIp[2] > ip[2]) {
										ip = currentIp.clone();
									}
									
									else if (currentIp[3] > ip[3]) {
										ip =currentIp.clone();
									}	
									
								} catch (IOException e) {
									System.err.printf("%S SERVER: errore lettura file projectMulticast.\n", tn);
								    e.printStackTrace();
								}
							}
							
							// Se il file e' una card...
							else {
								String read = "";
								
								try {
									String fileName = ("projects/" + pf.getName() + "/" + ch.getName());
									read = new String(Files.readAllBytes(Paths.get(fileName)));
									Project progetto = null;
									
								    for (Project p : projects) {						    	
								    	if (p.getName().equals(pf.getName())) {
								    		progetto = p;
								    	}	
								    }
								    
								    // Leggi la card dal file .json
								    Card card = new ObjectMapper().readValue(read, new TypeReference<Card>(){});
								    ArrayList<String> history = card.getHistory();
								    
								    // Trova a quale lista appartiene la card
								    String lastList = history.get(history.size() -1);
								    
								    // Aggiorna la corrispondente lista del progetto
								    switch (lastList.toLowerCase()) {
								    	case "todo":
								    		ArrayList<Card> todo = progetto.getToDo();
								    		todo.add(card);
								    		progetto.setToDo(todo);
								    		
								    		break;
								    	
								    	case "inprogress":
								    		ArrayList<Card> inprogress = progetto.getInProgress();
								    		inprogress.add(card);
								    		progetto.setInProgress(inprogress);
								    		
								    		break;
								    		
								    	case "toberevised":
								    		ArrayList<Card> toberevised = progetto.getToBeRevised();
								    		toberevised.add(card);
								    		progetto.setToBeRevised(toberevised);
								    		
								    		break;
								    		
								    	case "done":
								    		ArrayList<Card> done = progetto.getDone();
								    		done.add(card);
								    		progetto.setDone(done);
								    		
								    		break;
								    	
								    	default:
								    		System.err.printf("%S SERVER: errore! La card non appartiene ad alcuna lista.\n", tn);
								    		
								    		break;
								    }
								    
								} catch (IOException e) {
									System.err.printf("%S SERVER: errore lettura file projectMembers.\n", tn);
								    e.printStackTrace();
								}
							}
						}
					}
					
					else {
						System.err.printf("%S SERVER: errore lettura directory!\n", tn);
					}
				}	
			}
			
			// Se la cartella projects non esiste, creala
			else {
				System.out.printf("%S SERVER: cartella projects non trovata.\n", tn);
				projectDirectory.mkdir();
				System.out.printf("%S SERVER: cartella projects creata.\n", tn);
			}
		}
		
		// Crea il registro per RMI
		try {
			service = new ServerRemote(userSync, members, loggedUsers);
			ServerRemoteInterface stub = (ServerRemoteInterface) UnicastRemoteObject.exportObject(service, 0);
			LocateRegistry.createRegistry(6000);
			Registry r = LocateRegistry.getRegistry(6000);
			r.rebind("WORTH", stub);
			System.out.printf("%S SERVER: registro pronto.\n", tn);
		} catch (RemoteException e) {
			System.err.printf("%S SERVER: errore creazione registry.\n", tn);
	    	e.printStackTrace();
		}
		
		// Crea la threadpool
		try (ServerSocket listener = new ServerSocket(10000)) {
			System.out.printf("%S SERVER: creo la ThreadPool...\n", tn);
			int numThreads = 8;
			
			// Se il server e' stato avviato senza nessun argomento, utilizza un numero di default come dimensione della FixedThreadPool
			if (args.length == 0) {
				System.out.printf("%S SERVER: nessun argomento passato, lascio parametro al valore di default (8).\n", tn);
			}
			
			// Altrimenti, imposta la dimensione della pool uguale all'argomento passato
			else {
				try {
					numThreads = Integer.parseInt(args[0]);
				} catch (NumberFormatException e) {
					System.out.printf("%S SERVER: l'argomento passato non e' valido. Lascio parametro al valore di default (8).\n", tn);
				}
			}
			
			ThreadPoolExecutor pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(numThreads);			
			
			// Faccio partire i thread
			while (true) {
				pool.execute(new ServerThread(listener.accept(), userSync, projectSync, members, loggedUsers, projects, service, ip));
			}
			
		} catch (IOException e) {
			System.err.printf("%S SERVER: errore IO.\n", tn);
			e.printStackTrace();
		}
	}
}
