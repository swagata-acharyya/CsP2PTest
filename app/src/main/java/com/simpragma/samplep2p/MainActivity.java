package com.simpragma.samplep2p;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import com.cloudant.p2p.listener.HttpListener;
import com.cloudant.sync.datastore.BasicDocumentRevision;
import com.cloudant.sync.datastore.Datastore;
import com.cloudant.sync.datastore.DatastoreManager;
import com.cloudant.sync.datastore.DatastoreNotCreatedException;
import com.cloudant.sync.datastore.DocumentException;
import com.cloudant.sync.replication.Replicator;
import com.cloudant.sync.replication.ReplicatorBuilder;

import org.restlet.Component;
import org.restlet.Context;
import org.restlet.Server;
import org.restlet.data.Protocol;
import org.restlet.routing.Router;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class MainActivity extends AppCompatActivity {

    Button saveButton;
    Button refresh;
    EditText text;
    ListView list;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        saveButton = (Button) findViewById(R.id.save);
        refresh = (Button) findViewById(R.id.load);
        text = (EditText) findViewById(R.id.text);
        list = (ListView) findViewById(R.id.listView);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DAO dao = new DAO();
                Map<String, Object> map = new HashMap<String, Object>();
                map.put("name", text.getText().toString());
                try {
                    dao.save(map);
                    pushData(map);
                } catch (DocumentException e) {
                    e.printStackTrace();
                } catch (DatastoreNotCreatedException e) {
                    e.printStackTrace();
                }
            }
        });
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DAO dao = new DAO();
                List<String> names = null;
                try {
                    names = dao.getAll();
                    String[] namesArray = names.toArray(new String[names.size()]);
                    ArrayAdapter<String> adapter = new ArrayAdapter<String>(MainActivity.this,
                            android.R.layout.simple_list_item_1, android.R.id.text1, namesArray);
                    // Assign adapter to ListView
                    list.setAdapter(adapter);
                    adapter.notifyDataSetChanged();
                } catch (DatastoreNotCreatedException e) {
                    e.printStackTrace();
                }
            }
        });
        try {
            createServer(8182, "source");
            Thread.sleep(2000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void pushData(Map<String, Object> json) {

        BasicDocumentRevision sourceRev = null;

        // create a document in the source database

        try {
            URI dstUri = new URI("http://192.168.2.2:" + 8182 + "/source");

            DatastoreManager sourceManager = new DatastoreManager(AppUtil.databaseDirs.get(8182));
            Datastore sourceDs = sourceManager.openDatastore("source");
            Replicator replicator = ReplicatorBuilder.push().from(sourceDs).to(dstUri).build();
            waitForReplication(replicator);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    void createServer(final int port, final String dbname) throws Exception {

        final String databaseDir = getFilesDir().getPath();
        AppUtil.databaseDirs.put(port, databaseDir);

        DatastoreManager manager = new DatastoreManager(databaseDir);
        Datastore ds = manager.openDatastore(dbname);
        ds.close();

        Runnable r = new Runnable() {
            @Override
            public void run() {
                final Router router = new Router();
                router.attachDefault(HttpListener.class);

                org.restlet.Application myApp = new org.restlet.Application() {
                    @Override
                    public org.restlet.Restlet createInboundRoot() {
                        Context ctx = getContext();
                        ctx.getParameters().add("databaseDir", databaseDir);
                        ctx.getParameters().add("port", Integer.toString(port));
                        router.setContext(ctx);
                        return router;
                    }

                    ;
                };

                Component component = new Component();
                component.getDefaultHost().attach("/", myApp);
                try {
                    Log.d("SERVERCHK","Going to start the server ---------");
                    new Server(Protocol.HTTP, port, component).start();
                } catch (Exception e) {
                    Log.e("SERVERCHK","Problem starting the server ---------",e);
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
        };
        new Thread(r).start();
        Log.d("SERVERCHK", "Server started ---------");
    }

    protected void waitForReplication(Replicator replicator) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Listener listener = new Listener(latch);
        replicator.getEventBus().register(listener);
        replicator.start();
        latch.await();
        replicator.getEventBus().unregister(listener);
        if (replicator.getState() != Replicator.State.COMPLETE) {
            System.out.println("Error replicating TO remote");
            System.out.println(listener.error);
        } else {
            System.out.println(String.format("Replicated %d documents in %d batches",
                    listener.documentsReplicated, listener.batchesReplicated));
        }
    }

}
