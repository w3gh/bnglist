package bnglist.worker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;

import bnglist.Games;
import bnglist.Main;


public class WorkerProtocol {
	public static final int STATE_INITIATED = 0;
	public static final int STATE_HELLO = 1;
	
	BufferedReader in;
	PrintStream out;
	WorkerConnection connection;
	int state;
	
	public WorkerProtocol(BufferedReader in, PrintStream out, WorkerConnection connection) {
		this.in = in;
		this.out = out;
		this.connection = connection;
	}
	
	public void sendHello() {
		out.println("HELLO " + Main.BNGLIST_VERSION);
	}
	
	public void sendSay(int realm, String message) {
		out.println("SAY " + realm + " " + message);
	}
	
	public boolean handleCommand() throws IOException {
		String line = in.readLine();
		
		if(line == null) {
			throw new IOException("end of stream reached");
		}
		
		String[] parts = line.split(" ", 2);
		String command = parts[0];
		
		String payload = "";
		if(parts.length > 1)
			payload = parts[1];
		
		if(state == STATE_INITIATED) {
			if(command.equalsIgnoreCase("HELLO")) {
				Main.println("[WorkerProtocol] Accepted HELLO; client version is " + payload);
				
				sendHello();
				state = STATE_HELLO;
				
				return true;
			}
		} else if(state == STATE_HELLO) {
			if(command.equalsIgnoreCase("PUSH")) {
				connection.server.games.addGame(payload);
				return true;
			} else if(command.equalsIgnoreCase("EVENT")) {
				//todo
				return true;
			} else if(command.equalsIgnoreCase("SAY")) {
				synchronized(connection.sayWait) {
					connection.sayResponse = payload;
					connection.sayWait.notify();
				}
				
				return true;
			}
		}
		
		return false;
	}
}
