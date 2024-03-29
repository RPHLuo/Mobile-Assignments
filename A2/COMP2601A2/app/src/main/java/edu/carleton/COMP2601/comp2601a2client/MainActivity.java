package edu.carleton.COMP2601.comp2601a2client;

import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.IBinder;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;

import edu.carleton.COMP2601.communication.Event;
import edu.carleton.COMP2601.communication.EventHandler;
import edu.carleton.COMP2601.communication.EventSourceImpl;
import edu.carleton.COMP2601.communication.Fields;
import edu.carleton.COMP2601.communication.Reactor;

public class MainActivity extends AppCompatActivity {

    public static final String[] NAMES = new String[]{"Bob","Alice","Charlie", "Joe", "Grace", "Eric", "Mike", "Dan", "Ben", "Greg", "Stewart", "Brianna", "Charles", "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z"};

    String userid, opponent;
    Reactor reactor;
    ListView userlist;
    CustomAdapter listAdapter;
    NetworkingService ns;
    ArrayList<String> users;
    public static MainActivity mainActivity;
    //service connection manager
    protected ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            NetworkingService.MyBinder b = (NetworkingService.MyBinder)service;
            ns = b.getService();
            connectToServer();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            ns = null;
        }
    };

    public static MainActivity getInstance(){return mainActivity;}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mainActivity = this;
        userid = NAMES[(int)(Math.random() * NAMES.length)];
        userlist = (ListView)findViewById(R.id.lstUsers);
        users = new ArrayList<String>();
        listAdapter = new CustomAdapter(this,users);
        userlist.setAdapter(listAdapter);
        setup();
    }

    protected void setup(){
        Intent network = new Intent(getApplicationContext(),NetworkingService.class);
        startService(network);
        bindService(network,serviceConnection, Context.BIND_AUTO_CREATE);
        reactor = new Reactor();
        reactor.register("CONNECTED_RESPONSE", new EventHandler() {
            @Override
            public void handleEvent(Event event) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(),"Connected!",Toast.LENGTH_SHORT).show();
                        ((ViewGroup)findViewById(R.id.progSpinner).getParent()).removeView(findViewById(R.id.progSpinner));
                    }
                });
            }
        });
        reactor.register("USERS_UPDATED", new EventHandler() {
            @Override
            public void handleEvent(Event event) {
                //fill players
                try {
                    users.clear();
                    JSONArray allUsers = new JSONArray((String)event.get("users"));
                    for(int i=0;i<allUsers.length();i++){
                        if(!allUsers.get(i).equals(userid)){
                            users.add((String)allUsers.get(i));
                        }
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            listAdapter.notifyDataSetChanged();
                        }
                    });
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        reactor.register("PLAY_GAME_REQUEST", new EventHandler() {
            @Override
            public void handleEvent(Event event) {
                try {
                    //create response event
                    final AlertDialog.Builder confirmDialog = new AlertDialog.Builder(MainActivity.getInstance());
                    //ID is the challenger and RET_ID(this user) is the receiver
                    JSONObject data = new JSONObject((String) event.get("data"));
                    final String challenger = (String)data.get("challenger");
                    confirmDialog.setMessage(challenger + " wants to play");
                    confirmDialog.setPositiveButton("YES", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            Event response = new Event("PLAY_GAME_RESPONSE",ns.es);
                            response.put(Fields.ID,userid);
                            response.put(Fields.RET_ID,challenger);
                            HashMap<String, Serializable> hm = new HashMap<String, Serializable>();
                            hm.put("status",true);
                            response.put(Fields.BODY,hm);
                            ns.es.putEvent(response);
                            //start game
                            opponent = challenger;
                            Intent gameIntent = new Intent(getApplicationContext(),GameActivity.class);
                            startActivity(gameIntent);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
                confirmDialog.setNegativeButton("NO", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            Event response = new Event("PLAY_GAME_RESPONSE",ns.es);
                            response.put(Fields.ID,userid);
                            response.put(Fields.RET_ID,challenger);
                            HashMap<String, Serializable> hm = new HashMap<String, Serializable>();
                            hm.put("status",false);
                            response.put(Fields.BODY,hm);
                            ns.es.putEvent(response);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        }
                });
                  runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        confirmDialog.show();
                    }
                });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        reactor.register("PLAY_GAME_RESPONSE", new EventHandler() {
            @Override
            public void handleEvent(final Event event) {
                try {
                    JSONObject data = new JSONObject((String)event.get("data"));
                    if((boolean)data.get("status")){
                        Intent gameIntent = new Intent(getApplicationContext(),GameActivity.class);
                        opponent = (String)event.get(Fields.ID);
                        startActivity(gameIntent);
                    }else{
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                String result = (String)event.get(Fields.ID) + " Does not want to play";
                                ((TextView)findViewById(R.id.txtResult)).setText(result);
                            }
                        });
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    //send first connect message
    protected void connectToServer() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ns.connect(userid);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
    //Request a game with this player
    public void request(String name){
        try {
            ns.request(name);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy(){
        try {
            ns.disconnect();
            unbindService(serviceConnection);
            super.onDestroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
