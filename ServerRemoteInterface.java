// Luca Lombardo, mat. 546688

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ServerRemoteInterface extends Remote {
	public int register(String nickUtente, String password) throws RemoteException;
	
	public void registerForCallback (ClientRemoteInterface ClientInterface, String nickUtente) throws RemoteException;
	
	public void unregisterForCallback (ClientRemoteInterface ClientInterface, String nickUtente) throws RemoteException;
}
