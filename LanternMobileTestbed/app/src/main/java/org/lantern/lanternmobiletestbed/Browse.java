package org.lantern.lanternmobiletestbed;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import org.lantern.mobilesdk.Lantern;
import org.lantern.pubsub.Client;
import org.lantern.pubsub.Message;
import org.lantern.pubsub.MessageHandler;
import org.lantern.pubsub.PubSub;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class Browse extends AppCompatActivity {
    private static final String TAG = "Browse";
    private static final String GEO_LOOKUP = "http://ipinfo.io/ip";
    private static final int[] BUTTON_IDS = new int[]{R.id.onButton, R.id.onServiceButton, R.id.offButton};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browse);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        refreshIP(null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_browse, menu);
        PubSub.subscribe(getApplicationContext(), Client.utf8("topic"), new MessageHandler() {
            @Override
            public void onMessage(Message message) {
                String body = Client.fromUTF8(message.getBody());
                Log.i(TAG, "Message: " + body);
                NotificationCompat.Builder mBuilder =
                        new NotificationCompat.Builder(getApplicationContext())
                                .setSmallIcon(R.drawable.lantern_icon)
                                .setContentTitle("Lantern Notification")
                                .setContentText(body);
                // Creates an explicit intent for an Activity in your app
                Intent resultIntent = new Intent(getApplicationContext(), Browse.class);
                PendingIntent resultPendingIntent = null;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    // The stack builder object will contain an artificial back stack for the
                    // started Activity.
                    // This ensures that navigating backward from the Activity leads out of
                    // your application to the Home screen.
                    TaskStackBuilder stackBuilder = TaskStackBuilder.create(getApplicationContext());
                    // Adds the back stack for the Intent (but not the Intent itself)
                    stackBuilder.addParentStack(Browse.class);
                    // Adds the Intent that starts the Activity to the top of the stack
                    stackBuilder.addNextIntent(resultIntent);
                    resultPendingIntent = stackBuilder.getPendingIntent(
                            0,
                            PendingIntent.FLAG_UPDATE_CURRENT);
                } else {
                    resultPendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                }
                mBuilder.setContentIntent(resultPendingIntent);

                NotificationManager mNotificationManager =
                        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                // mId allows you to update the notification later on.
                mNotificationManager.notify(5, mBuilder.build());

                Log.i(TAG, "Notified");
            }
        });
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

    public void on(View view) {
        toggle(view, true, false);
    }

    public void onAsService(View view) {
        toggle(view, true, true);
    }

    public void off(View view) {
        toggle(view, false, false);
    }

    private void toggle(final View view, boolean on, final boolean asService) {
        view.setEnabled(false);
        getIPAddressView().setText("Toggling Lantern ...");
        getIPAddressView().setEnabled(false);
        new AsyncTask<Boolean, Void, String>() {
            @Override
            protected String doInBackground(Boolean... params) {
                boolean on = params[0];
                try {
                    if (on) {
                        Log.i(TAG, "Turning on proxy");
                        int startupTimeoutMillis = 30000;
                        String trackingId = "UA-21815217-17";
                        if (asService) {
                            Lantern.enableAsService(getApplicationContext(), startupTimeoutMillis, trackingId);
                        } else {
                            Lantern.enable(getApplicationContext(), startupTimeoutMillis, trackingId);
                        }
                        Log.i(TAG, "Turned on proxy");
                    } else {
                        Log.i(TAG, "Turning off proxy");
                        Lantern.disable(getApplicationContext());
                        Log.i(TAG, "Turned off proxy");
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return null;
            }

            @Override
            protected void onPostExecute(String ipAddress) {
                for (int id : BUTTON_IDS) {
                    if (id != view.getId()) {
                        findViewById(id).setEnabled(true);
                    }
                }
                refreshIP(null);
            }
        }.execute(on);
    }

    public void refreshIP(View view) {
        getIPAddressView().setText("refreshing ...");
        getIPAddressView().setEnabled(false);
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                try {
                    Log.i(TAG, "Opening connection to " + GEO_LOOKUP);
                    URL url = new URL(GEO_LOOKUP);
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    // Need to force closing so that old connections (with old proxy settings) don't get reused.
                    urlConnection.setRequestProperty("Connection", "close");
                    try {
                        InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                        Scanner s = new Scanner(in).useDelimiter("\\A");
                        return s.hasNext() ? s.next() : "";
                    } finally {
                        urlConnection.disconnect();
                        Log.i(TAG, "Finished doing geolookup");
                    }
                } catch (Exception e) {
                    return "Unable to refresh IP: " + e.getMessage();
                }
            }

            @Override
            protected void onPostExecute(String ipAddress) {
                Log.i(TAG, "Setting IP Address to: " + ipAddress);
                getIPAddressView().setText(ipAddress);
                getIPAddressView().setEnabled(true);
            }
        }.execute();
    }

    private TextView getIPAddressView() {
        return (TextView) findViewById(R.id.ipAddress);
    }
}
