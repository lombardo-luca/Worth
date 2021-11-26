// Luca Lombardo, mat. 546688

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.AbstractMap;
import java.util.ArrayList;

//import javafx.util.Pair;

public interface ClientRemoteInterface extends Remote {
	public void notifyEvent(AbstractMap.SimpleEntry<ArrayList<User>, ArrayList<User>> pair) throws RemoteException;
	
	public void notifyProject(String project, String ip) throws RemoteException;
	
	public void notifyCancelProject(String project) throws RemoteException;
}
