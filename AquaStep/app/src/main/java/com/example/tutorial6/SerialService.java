package com.example.tutorial6;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

import java.io.FileWriter;



/**
 * create notification and queue serial data while activity is not in the foreground
 * use listener chain: SerialSocket -> SerialService -> UI fragment
 */
public class SerialService extends Service implements SerialListener {

    class SerialBinder extends Binder {
        SerialService getService() { return SerialService.this; }
    }

    private enum QueueType {Connect, ConnectError, Read, IoError}

    private static class QueueItem {
        QueueType type;
        byte[] data;
        Exception e;

        QueueItem(QueueType type, byte[] data, Exception e) { this.type=type; this.data=data; this.e=e; }
    }

    private final Handler mainLooper;
    private final IBinder binder;
    private final Queue<QueueItem> queue1, queue2;

    private SerialSocket socket;
    private SerialListener listener;
    private boolean connected;

    NotificationManagerCompat notificationManagerCompat;

    Notification notification;

    int counter;

    private final ArrayList<String[]> rowsContainer;

    private boolean isFirstInFiveMinutes = true;

    Python py =  Python.getInstance();
    PyObject pyobj = py.getModule("main");

    private static final String file_path = "/sdcard/csv_dir/data.csv";

    /**
     * Lifecylce
     */
    public SerialService() {
        mainLooper = new Handler(Looper.getMainLooper());
        binder = new SerialBinder();
        queue1 = new LinkedList<>();
        queue2 = new LinkedList<>();
        rowsContainer = new ArrayList<>();
    }

    @Override
    public void onDestroy() {
        cancelNotification();
        disconnect();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /**
     * Api
     */
    public void connect(SerialSocket socket) throws IOException {
        socket.connect(this);
        this.socket = socket;
        connected = true;
    }

    public void disconnect() {
        connected = false; // ignore data,errors while disconnecting
        cancelNotification();
        if(socket != null) {
            socket.disconnect();
            socket = null;
        }
    }

    public void write(byte[] data) throws IOException {
        if(!connected)
            throw new IOException("not connected");
        socket.write(data);
    }

    public void attach(SerialListener listener) {
        if(Looper.getMainLooper().getThread() != Thread.currentThread())
            throw new IllegalArgumentException("not in main thread");
        cancelNotification();
        // use synchronized() to prevent new items in queue2
        // new items will not be added to queue1 because mainLooper.post and attach() run in main thread
        synchronized (this) {
            this.listener = listener;
        }
        for(QueueItem item : queue1) {
            switch(item.type) {
                case Connect:       listener.onSerialConnect      (); break;
                case ConnectError:  listener.onSerialConnectError (item.e); break;
                case Read:          listener.onSerialRead         (item.data); break;
                case IoError:       listener.onSerialIoError      (item.e); break;
            }
        }
        for(QueueItem item : queue2) {
            switch(item.type) {
                case Connect:       listener.onSerialConnect      (); break;
                case ConnectError:  listener.onSerialConnectError (item.e); break;
                case Read:          listener.onSerialRead         (item.data); break;
                case IoError:       listener.onSerialIoError      (item.e); break;
            }
        }
        queue1.clear();
        queue2.clear();
        rowsContainer.clear();
    }

    public void detach() {
        if(connected) {
            createNotification();
        }
        // items already in event queue (posted before detach() to mainLooper) will end up in queue1
        // items occurring later, will be moved directly to queue2
        // detach() and mainLooper.post run in the main thread, so all items are caught
        listener = null;
    }


    private void cancelNotification() {
        stopForeground(true);
    }

    /**
     * SerialListener
     */
    public void onSerialConnect() {
        if(connected) {
            synchronized (this) {
                if (listener != null) {
                    mainLooper.post(() -> {
                        if (listener != null) {
                            listener.onSerialConnect();
                        } else {
                            queue1.add(new QueueItem(QueueType.Connect, null, null));
                        }
                    });
                } else {
                    queue2.add(new QueueItem(QueueType.Connect, null, null));
                }
            }
        }
    }

    public void onSerialConnectError(Exception e) {
        if(connected) {
            synchronized (this) {
                if (listener != null) {
                    mainLooper.post(() -> {
                        if (listener != null) {
                            listener.onSerialConnectError(e);
                        } else {
                            queue1.add(new QueueItem(QueueType.ConnectError, null, e));
                            cancelNotification();
                            BTDisconnectedNotification();
                            disconnect();
                        }
                    });
                } else {
                    queue2.add(new QueueItem(QueueType.ConnectError, null, e));
                    cancelNotification();
                    BTDisconnectedNotification();
                    disconnect();
                }
            }
        }
    }

    public void onSerialRead(byte[] data) {
        if (connected) {
            synchronized (this) {
                if (listener != null) {
                    mainLooper.post(() -> {
                        Log.d("queue1", new String(data));
                        if (listener != null) {
                            listener.onSerialRead(data);
                        } else {
                            queue1.add(new QueueItem(QueueType.Read, data, null));
                            Log.d("queue1", new String(data));
                        }
                    });
                } else {
//                    queue2.add(new QueueItem(QueueType.Read, data, null));
                    Log.d("queue2", new String(data));
                    String[] parts = new String(data).split(",");
                    parts = clean_str(parts);
                    String currentCsv = "/sdcard/csv_dir/data.csv";
                    try {
                        CSVWriter csvWriter = new CSVWriter(new FileWriter(currentCsv, true));
                        if (parts.length == 5)
                        {
                            try{
                                float x = Float.parseFloat(parts[0]);
                                float y = Float.parseFloat(parts[1]);
                                float z = Float.parseFloat(parts[2]);
                                String[] row = {parts[0], parts[1], parts[2]};
                                // save the row only if x, y, z all have a dot in them
                                if(parts[0].contains(".") && parts[1].contains(".") && parts[2].contains("."))
                                {
                                    csvWriter.writeNext(row);
                                    csvWriter.close();
                                }
                                else{
                                    Log.d("blabla", "Jump in x/y/z");
                                }
                            }
                            catch (NumberFormatException e){Log.d("blabla", "string appeared");}
                            int time = (int) Math.round((Float.parseFloat(parts[3])/1000.0));
                            float f_temperature = Float.parseFloat(parts[4]);
                            TerminalFragment.temperature = Math.round(f_temperature);

                            if (time % (TerminalFragment.intervalMinutes*60) == 0 && isFirstInFiveMinutes && time != 0){
                                TerminalFragment.notificationCounter++;
                                isFirstInFiveMinutes = false;
                                PyObject obj = pyobj.callAttr("get_preds" , file_path);
                                int activity = obj.asList().get(0).toInt();
                                if (TerminalFragment.temperature > 30 && activity == 1) { // Running while too hot
                                    TerminalFragment.play(TerminalFragment.hotTempBeep);
                                    TerminalFragment.play(TerminalFragment.hotTempTextualSound);
                                }
                                int steps;
                                if (activity == 2) // Rest
                                    steps = 0;
                                else
                                    steps = obj.asList().get(1).toInt();
                                float calculatedWaterIntake = TerminalFragment.calculatedWater(TerminalFragment.baselineInterval, TerminalFragment.temperature, steps, activity, TerminalFragment.gender);
                                TerminalFragment.calculatedCups += calculatedWaterIntake + TerminalFragment.residualCups;
                                TerminalFragment.counterProgressBar += calculatedWaterIntake + TerminalFragment.residualCups;
                                TerminalFragment.dailyTarget += calculatedWaterIntake - TerminalFragment.baselineInterval;

                                if ((int) Math.floor(TerminalFragment.counterProgressBar) == TerminalFragment.dailyTarget) // reached target
                                    TerminalFragment.play(TerminalFragment.reachedTargetBeep);
                                if ((int) Math.floor(TerminalFragment.calculatedCups) >= 1){
                                    TerminalFragment.residualCups = TerminalFragment.calculatedCups - (float)Math.floor(TerminalFragment.calculatedCups);
                                    startNotification((int)Math.floor(TerminalFragment.calculatedCups), TerminalFragment.notificationCounter);
                                    TerminalFragment.calculatedCups = 0;
                                }
                                // empty the csv file
                                clearCsvFile();
                            }
                            else if (time % (TerminalFragment.intervalMinutes*60) != 0){
                                isFirstInFiveMinutes = true;
                            }
                        }
                    }
                    catch (IOException e) {
                        Toast.makeText(this, "Error in writing to csv file", Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    }
                }
            }
        }
    }


    public void onSerialIoError(Exception e) {
        if(connected) {
            synchronized (this) {
                if (listener != null) {
                    mainLooper.post(() -> {
                        if (listener != null) {
                            listener.onSerialIoError(e);
                        } else {
                            queue1.add(new QueueItem(QueueType.IoError, null, e));
                            cancelNotification();
                            disconnect();
                        }
                    });
                } else {
                    queue2.add(new QueueItem(QueueType.IoError, null, e));
                    cancelNotification();
                    disconnect();
                }
            }
        }
    }

    public String[] clean_str(String[] stringsArr){
        for (int i = 0; i < stringsArr.length; i++)  {
            stringsArr[i]=stringsArr[i].replaceAll(" ","");
        }

        return stringsArr;
    }

    private void startNotification(int numCups, int notificationCounter){
        Intent cancelIntent = new Intent(this, updateProgressBar.class);
        cancelIntent.setAction(updateProgressBar.ACTION_CANCEL);
        PendingIntent cancelPendingIntent =
                PendingIntent.getBroadcast(this, notificationCounter,
                        cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "myCh2")
                .setSmallIcon(R.drawable.noification_logo)
                .setContentTitle("Drinking Time")
                .setContentText("Time to drink " + String.valueOf(numCups) + " cups")
                .addAction(new NotificationCompat.Action(R.color.colorPrimary, "Done!", cancelPendingIntent));

        notification = builder.build();
        notificationManagerCompat = NotificationManagerCompat.from(this);
        notificationManagerCompat.notify(notificationCounter, notification);
    }

    private void calculatedNotification(int activity, int steps, float calculatedCups, int calories, int notificationCounter){
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "myCh2")
                .setSmallIcon(R.drawable.noification_logo)
                .setContentTitle("Calculations")
                .setContentText("Activity: " + activity + " Steps: " + steps + " Calculated Cups: " + calculatedCups + " Calories: " + calories);

        notification = builder.build();
        notificationManagerCompat = NotificationManagerCompat.from(this);
        notificationManagerCompat.notify(notificationCounter, notification);
    }

    private void createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel nc = new NotificationChannel(Constants.NOTIFICATION_CHANNEL, "Background service", NotificationManager.IMPORTANCE_LOW);
            nc.setShowBadge(false);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(nc);
        }
        Intent disconnectIntent = new Intent()
                .setAction(Constants.INTENT_ACTION_DISCONNECT);
        Intent restartIntent = new Intent()
                .setClassName(this, Constants.INTENT_CLASS_MAIN_ACTIVITY)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER);
        PendingIntent disconnectPendingIntent = PendingIntent.getBroadcast(this, 1, disconnectIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent restartPendingIntent = PendingIntent.getActivity(this, 1, restartIntent,  PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(getResources().getColor(R.color.colorPrimary))
                .setContentTitle(getResources().getString(R.string.app_name))
                .setContentText(socket != null ? "Connected to "+ socket.getName() : "Background Service")
                .setContentIntent(restartPendingIntent)
                .setOngoing(true)
                .addAction(new NotificationCompat.Action(R.drawable.ic_clear_white_24dp, "Disconnect", disconnectPendingIntent));
        // @drawable/ic_notification created with Android Studio -> New -> Image Asset using @color/colorPrimaryDark as background color
        // Android < API 21 does not support vectorDrawables in notifications, so both drawables used here, are created as .png instead of .xml
        Notification notification = builder.build();
        startForeground(Constants.NOTIFY_MANAGER_START_FOREGROUND_SERVICE, notification);
    }

    public void BTDisconnectedNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "myCh2")
                .setSmallIcon(R.drawable.noification_logo)
                .setContentTitle(getResources().getString(R.string.app_name))
                .setContentText("Bluetooth device is disconnected. open the app to reconnect");

        Notification notification = builder.build();

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        counter += 1;
        notificationManager.notify(counter, notification);
    }



    private void clearCsvFile() {
        String csvFile = Environment.getExternalStorageDirectory() + "/data.csv";

        File file = new File(csvFile);
        if (file.exists()) {
            file.delete();
        }

        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
