package bnglist;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;


public class Games extends Thread {
	int nextId;
	HashMap<Integer, Game> games; //maps from game ID to game object
	HashMap<String, Integer> idFromHash; //maps from game hash to the game ID
	
	String gamelistString; //latest gamelist string that is sent to clients on GAMELIST
	ArrayList<GameUpdateListener> listeners;
	
	//config
	int maxGames; //maximum number of games to store
	long keepTime; //time to keep the game in our map; in ms
	
	public Games() {
		nextId = 0;
		games = new HashMap<Integer, Game>();
		idFromHash = new HashMap<String, Integer>();
		listeners = new ArrayList<GameUpdateListener>();
		
		//config
		maxGames = Config.getInt("max_games", 0);
		keepTime = Config.getInt("keep_time", 30000);
	}
	
	public void registerListener(GameUpdateListener listener) {
		listeners.add(listener);
	}
	
	public void deregisterListener(GameUpdateListener listener) {
		listeners.remove(listener);
	}
	
	public void addGame(Game game) {
		int gid = -1;
		
		synchronized(idFromHash) {
			//delete old game with the hash if existing
			
			//old game might still be null if we deleted it elsewhere at the same time somehow
			// this shouldn't happen but just to be safe we should check
			// if we deleted then this would be an ADD
			Game oldGame = null;
			if(idFromHash.containsKey(game.getHashString())) {
				//we don't delete the old entry because we might reuse the ID
				int oldGid = idFromHash.get(game.getHashString());
				
				synchronized(games) {
					oldGame = games.remove(oldGid);
				}
				
				//here we check if this is a replace or an update
				if(oldGame != null) {
					gid = oldGid; //reuse the game ID
					
					if(oldGame.hostCounter != game.hostCounter) {
						Main.println("[Games] REPLACE " + oldGid + " " + game.getOverviewString());
						
						for(int i = 0; i < listeners.size(); i++) {
							listeners.get(i).gameReplaced(gid, game);
						}
					}
				}
			}
			
			//check if this was an ADD
			// in this case, we create a new ID for the game
			if(oldGame == null) {
				gid = nextId;
				nextId++;
				
				Main.println("[Games] ADD " + gid + " " + game.getOverviewString());
				
				for(int i = 0; i < listeners.size(); i++) {
					listeners.get(i).gameAdded(gid, game);
				}
				
				idFromHash.put(game.getHashString(), gid);
			}
		}
		
		synchronized(games) {
			if(gid == -1) { //this should never occur
				Main.println("[Games] Error: gid=-1");
			}
			
			games.put(gid, game);
		}
	}
	
	public void addGame(String str) {
		Game game = Game.getGame(str, System.currentTimeMillis());
		if(game != null) addGame(Game.getGame(str, System.currentTimeMillis()));
	}
	
	public void deleteOldGames() {
		//todo: maybe create a priority queue based on seentime to make this more efficient
		// but there shouldn't be over 100 games, so we're good
		
		Main.println("[Games] Deleting old games");
		
		synchronized(idFromHash) { //always synchronize with idFromHash first
			Iterator<String> it = idFromHash.keySet().iterator();
			ArrayList<String> doDelete = new ArrayList<String>();
			
			while(it.hasNext()) {
				String hash = it.next();
				int gid = idFromHash.get(hash);
				Game game = games.get(gid); //make sure game is not null, which may occur if in middle of update
				
				if(game != null && System.currentTimeMillis() - game.seenTime > keepTime) {
					synchronized(games) {
						games.remove(gid); //update games first
					}
					
					doDelete.add(hash);
					Main.println("[Games] DELETE " + gid);

					for(int i = 0; i < listeners.size(); i++) {
						listeners.get(i).gameDeleted(gid);
					}
				}
			}
			
			for(String hash : doDelete) {
				idFromHash.remove(hash);
			}
		}
	}
	
	public void updateGamelistString() {
		StringBuilder list = new StringBuilder();
		
		synchronized(games) {
			Iterator<Integer> it = games.keySet().iterator();
			
			while(it.hasNext()) {
				int id = it.next();
				list.append("ID ").append(id).append(";")
					.append(games.get(id).getOverviewString())
					.append('\n');
			}
		}
		
		list.append("END");
		gamelistString = list.toString();
	}
	
	public String getGamelistString() {
		return gamelistString;
	}
	
	public String getGameDetails(int gid) {
		Game game = games.get(gid);
		
		if(game != null) {
			return game.getDetailString();
		} else {
			return "unknown game id";
		}
	}
	
	public Game getGame(int gid) {
		return games.get(gid);
	}
	
	public void run() {
		int count = 0; //use to delete old games every 10 iterations
		
		while(true) {
			updateGamelistString();
			count++;
			
			if(count >= 10) {
				deleteOldGames();
				count = 0;
			}
			
			try {
				Thread.sleep(1000);
			} catch(InterruptedException e) {}
		}
	}
}
