package edu.carleton.COMP2601;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import edu.carleton.COMP2601.communication.Event;
import edu.carleton.COMP2601.communication.EventHandler;
import edu.carleton.COMP2601.communication.EventSourceImpl;
import edu.carleton.COMP2601.communication.Fields;
import edu.carleton.COMP2601.communication.Reactor;
import edu.carleton.COMP2601.communication.ThreadWithReactor;

public class Server {
    public static final int PORT = 7001;
    private Reactor reactor;
    private EventSourceImpl es;
    private ThreadWithReactor twr;
    private ConcurrentHashMap<String, ThreadWithReactor> users;

    private ConcurrentHashMap<String,Game> gameTracker;

    public Server(){
        users = new ConcurrentHashMap<>();
        gameTracker = new ConcurrentHashMap<>();
        reactor = new Reactor();
        reactor.register("CONNECT_REQUEST", new EventHandler() {
            @Override
            public void handleEvent(Event event) {
                try {
                    Event response = new Event("CONNECTED_RESPONSE", es);
                    //get hashmap of users
                    ArrayList<String> allUsers = new ArrayList<String>();
                    JSONArray jsonUsers = new JSONArray();
                    for(Object user : ((Map)users).keySet()){
                        allUsers.add((String)user);
                        jsonUsers.put(user);
                    }
                    allUsers.add((String)event.get(Fields.ID));
                    jsonUsers.put(event.get(Fields.ID));
                    users.put((String) event.get(Fields.ID), twr);
                    es.putEvent(response);

                    //notify other users that a new user have been added
                    HashMap<String,Serializable> hm = new HashMap<String, Serializable>();
                    hm.put("users",jsonUsers.toString());
                    for (String username : allUsers){
                        Event newUserEvent = new Event("USERS_UPDATED",users.get(username).getEventSource());
                        newUserEvent.put(Fields.BODY,hm);
                        users.get(username).getEventSource().putEvent(newUserEvent);
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        });
        //No Specific JSON is needed since this is purely passing of one's ID
        reactor.register("PLAY_GAME_REQUEST", new EventHandler() {
            @Override
            public void handleEvent(Event event) {
                try {
                    JSONObject data = new JSONObject((String)event.get("data"));
                    String challenger = (String)data.get("challenger");
                    String player = (String) data.get("receiver");
                    Event request = new Event("PLAY_GAME_REQUEST", users.get(player).getEventSource());
                    JSONObject reciever = new JSONObject();
                    reciever.put("player",player);
                    reciever.put("challenger",challenger);
                    HashMap<String,Serializable> hm = new HashMap<String, Serializable>();
                    hm.put("data",reciever.toString());
                    request.put(Fields.BODY,hm);
                    users.get(player).getEventSource().putEvent(request);
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        });
        reactor.register("PLAY_GAME_RESPONSE", new EventHandler() {
            @Override
            public void handleEvent(Event event) {
                try {
                    String player = (String)event.get(Fields.RET_ID);
                    if((boolean)event.get("status") == true){
                        //start game
                        Game game = new Game(player,(String)event.get(Fields.ID));
                        gameTracker.put((String)event.get(Fields.ID),game);
                        gameTracker.put(player,game);
                    }
                    Event response = new Event("PLAY_GAME_RESPONSE",users.get(player).getEventSource());
                    response.put(Fields.ID,event.get(Fields.ID));
                    HashMap<String, Serializable> hm = new HashMap<String, Serializable>();
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("status", event.get("status"));
                    hm.put("data",jsonObject.toString());
                    response.put(Fields.BODY,hm);
                    users.get(player).getEventSource().putEvent(response);
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        });
        reactor.register("DISCONNECT_REQUEST", new EventHandler() {
            @Override
            public void handleEvent(Event event) {
                try {
                    users.remove((String)event.get(Fields.ID)).quit();
                    ArrayList<String> allUsers = new ArrayList<String>();
                    JSONArray jsonUsers = new JSONArray();
                    for (Object user : ((Map) users).keySet()) {
                        allUsers.add((String) user);
                        jsonUsers.put(user);
                    }
                    HashMap<String,Serializable> hm = new HashMap<String, Serializable>();
                    hm.put("users",jsonUsers.toString());
                    for (String username : allUsers) {
                        Event newUserEvent = new Event("USERS_UPDATED", users.get(username).getEventSource());
                        newUserEvent.put(Fields.BODY,hm);
                        users.get(username).getEventSource().putEvent(newUserEvent);
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        });
        //simple notify message, does not need extensive JSON
        reactor.register("GAME_ON", new EventHandler() {
            @Override
            public void handleEvent(Event event) {
                try {
                    Event gameOn = new Event("GAME_ON",es);
                    HashMap<String,Serializable> hm = new HashMap<>();
                    JSONObject data = new JSONObject((String)event.get("data"));
                    hm.put("data",data.toString());
                    gameOn.put(Fields.BODY,hm);
                    users.get(data.get("receiver")).getEventSource().putEvent(gameOn);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        reactor.register("MOVE_MESSAGE", new EventHandler() {
            @Override
            public void handleEvent(Event event) {
                JSONObject incData = new JSONObject((String)event.get("data"));
                if(gameTracker.get(event.get(Fields.ID)).place((int)incData.get("move"),(String)event.get(Fields.ID))){
                    try {
                        //if move succeeds then send result
                        String player2 = (String) event.get(Fields.RET_ID);
                        Event p1gameboardEvent = new Event("MOVE_MESSAGE", es);
                        Event p2gameboardEvent = new Event("MOVE_MESSAGE", users.get(player2).getEventSource());
                        HashMap<String, Serializable> hm = new HashMap<String, Serializable>();
                        JSONObject data = new JSONObject();
                        data.put("symbol", gameTracker.get(event.get(Fields.ID)).getPreviousSymbol());
                        data.put("location", incData.get("move"));
                        hm.put("data",data.toString());
                        p1gameboardEvent.put(Fields.BODY, hm);
                        p2gameboardEvent.put(Fields.BODY, hm);
                        p1gameboardEvent.put(Fields.ID,event.get(Fields.ID));
                        p2gameboardEvent.put(Fields.ID,event.get(Fields.ID));
                        users.get(event.get(Fields.ID)).getEventSource().putEvent(p1gameboardEvent);
                        users.get(player2).getEventSource().putEvent(p2gameboardEvent);
                        if(!gameTracker.get(event.get(Fields.ID)).getResult().equals("")) {
                            //game ended
                            Event p1gameoverEvent = new Event("GAME_OVER", es);
                            Event p2gameoverEvent = new Event("GAME_OVER", users.get(player2).getEventSource());
                            HashMap<String, Serializable> dataResults = new HashMap<>();
                            JSONObject results = new JSONObject();
                            results.put("winner",gameTracker.get(event.get(Fields.ID)).getResult());
                            dataResults.put("data",results.toString());
                            p1gameoverEvent.put(Fields.BODY,dataResults);
                            p2gameoverEvent.put(Fields.BODY,dataResults);
                            users.get(event.get(Fields.ID)).getEventSource().putEvent(p1gameoverEvent);
                            users.get(player2).getEventSource().putEvent(p2gameoverEvent);
                            Game g = new Game((String)event.get(Fields.ID),player2);
                            gameTracker.put((String)event.get(Fields.ID),g);
                            gameTracker.put(player2,g);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        //user ended game
        reactor.register("GAME_OVER", new EventHandler() {
            @Override
            public void handleEvent(Event event) {
                try {
                    String player1 = (String)event.get(Fields.ID);
                    String player2 = (String) event.get(Fields.RET_ID);
                    Event p2gameoverEvent = new Event("GAME_OVER", users.get(player2).getEventSource());
                    HashMap<String, Serializable> results = new HashMap<>();
                    JSONObject data = new JSONObject();
                    data.put("winner", "forced");
                    results.put("data",data.toString());
                    p2gameoverEvent.put(Fields.BODY,results);
                    users.get(player2).getEventSource().putEvent(p2gameoverEvent);
                    Game g = new Game(player1,player2);
                    gameTracker.put(player1,g);
                    gameTracker.put(player2,g);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
    public static void main(String[] args){
        Server ns = new Server();
        ns.run();
    }
    public void run(){
        ServerSocket listener;
        try{
            listener = new ServerSocket(PORT);
            while(true){
                Socket s = listener.accept();
                InputStream is = s.getInputStream();
                OutputStream os = s.getOutputStream();
                es = new EventSourceImpl(is,os);
                twr = new ThreadWithReactor(es, reactor);
                twr.start();
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
