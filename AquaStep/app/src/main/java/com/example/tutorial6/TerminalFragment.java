package com.example.tutorial6;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.MarkerImage;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.opencsv.CSVWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.Calendar;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import android.content.Context;
import android.content.res.Resources;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;

import java.util.Date;
import java.util.Random;


public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {

    private enum Connected { False, Pending, True }
    private String deviceAddress;
    private SerialService service;
    public static TextView drankCupsText;
    private TextView temperatureText;
    private TextView caloriesText;
    private TextUtil.HexWatcher hexWatcher;
    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private String newline = TextUtil.newline_crlf;

    Python py =  Python.getInstance();
    PyObject pyobj = py.getModule("main");
    NotificationManagerCompat notificationManagerCompat;
    Notification notification;
    public static int notificationCounter = 0;
    public static float counterProgressBar = 0.0f;
    public static ProgressBar progressBar;
    public TextView userNameTextView;

    public Button sleepModeButton;

    private static final String RESOURCE_NAME = "random_strings";

    private SharedPreferences sharedpreferences;

    public static int dailyTarget = 0;
    public static int temperature;
    public static int calories;

    public static String gender;
    private boolean isFirstTemp = true;
    private boolean isFirstInFiveMinutes = true;

    public static float baselineInterval;

    public static int intervalMinutes = 5;

    public static float residualCups = 0;

    private int fullCups = 0;

    public static float calculatedCups = 0.0f;

    public static final String SHARED_PREFS_NAME = "MyPrefs";
    private static final String KEY_NOTIFICATION_COUNTER = "notification_counter";
    public static final String KEY_COUNTER_PROGRESS_BAR = "counter_progress_bar";
    private static final String KEY_TEMPERATURE = "temperature";
    private static final String KEY_CALORIES = "calories";
    private static final String file_path = "/sdcard/data.csv";
    private static final String cups_file_path = "/sdcard/cups_data.csv";
    private static final String calories_file_path = "/sdcard/calories_data.csv";

    public static MediaPlayer hotTempBeep;
    public static MediaPlayer hotTempTextualSound;
    public static MediaPlayer reachedTargetBeep;


    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceAddress = getArguments().getString("device");
        sharedpreferences = requireActivity().getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
    }

    @Override
    public void onDestroy() {
        Log.d("blabla", "in destroy");
        if (connected != Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        SharedPreferences sharedPreferences = requireActivity().getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(KEY_NOTIFICATION_COUNTER, notificationCounter);
        editor.putFloat(KEY_COUNTER_PROGRESS_BAR, counterProgressBar);
        editor.putInt(KEY_TEMPERATURE, temperature);
        editor.apply();
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if(service != null)
            service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if(service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation") // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try { getActivity().unbindService(this); } catch(Exception ignored) {}
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(initialStart && service != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }

        SharedPreferences sharedPreferences = requireActivity().getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        notificationCounter = sharedPreferences.getInt(KEY_NOTIFICATION_COUNTER, 0);
        counterProgressBar = sharedPreferences.getFloat(KEY_COUNTER_PROGRESS_BAR, 0.0f);
        temperature = sharedPreferences.getInt(KEY_TEMPERATURE, 25);
        calories = sharedPreferences.getInt(KEY_CALORIES, 0);
        gender = MainActivity.gender;
        progressBar.setProgress((int) Math.floor(counterProgressBar));
        drankCupsText.setText((int) Math.floor(counterProgressBar) + "/" + dailyTarget);
        temperatureText.setText("Current temperature\n\n" + temperature + " \u2103");
        caloriesText.setText("Calories burned\n\n" + calories + " kcal");
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if(initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    /*
     * UI
     */
    public void setBackground(int hour, String userName, View view){
        if (hour >= 5 && hour < 12){
            userNameTextView.setText("Good morning, " + userName);
            view.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.morning_background));
        }
        if(hour >= 12 && hour < 19){
            userNameTextView.setText("Good afternoon, " + userName);
            view.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.noon_background));
        }

        if(hour >= 19 || hour < 5){
            userNameTextView.setText("Good night, " + userName);
            view.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.night_background));
        }
    }
    public String getRandomString() {
        Resources res = getContext().getResources();
        int resourceId = res.getIdentifier(RESOURCE_NAME, "array", getContext().getPackageName());
        String[] strings = res.getStringArray(resourceId);

        Random random = new Random();
        int index = random.nextInt(strings.length);

        return strings[index];
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.main_screen, container, false);
        drankCupsText = view.findViewById(R.id.drankCups);
        userNameTextView = view.findViewById(R.id.userNameText);
        progressBar = view.findViewById(R.id.progressBar4);
        TextView quoteText = view.findViewById(R.id.quoteText);
        temperatureText = view.findViewById(R.id.temperature_text);
        caloriesText = view.findViewById(R.id.calories_text);
        isFirstTemp = true;
        sleepModeButton = view.findViewById(R.id.sleepModeButton);

        drankCupsText.setText("0" + "/" + dailyTarget);
        drankCupsText.setTextColor(getResources().getColor(R.color.white)); // set as default color to reduce number of spans

        String userName = MainActivity.username;
        int userWeight = MainActivity.weight;
        int userActivity = MainActivity.numActivity;
        int userSleepingHours = MainActivity.sleepingHours;
        gender = MainActivity.gender;

        dailyTarget = ounceToCups(userWeight*2.205*(1/2.0) + userActivity*60/210.0*12);
        float numIntervals = Math.round((24-userSleepingHours)*60.0/intervalMinutes);
        baselineInterval = dailyTarget/numIntervals;
        Log.d("baselineInterval", String.valueOf(baselineInterval) + ", " + String.valueOf(numIntervals) + ", " + String.valueOf(dailyTarget));
        progressBar.setMax(dailyTarget);

        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);

        setBackground(hour, userName, view);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getActivity(), "myCh2")
                .setSmallIcon(R.drawable.noification_logo)
                .setContentTitle("Drinking Time")
                .setContentText("Time to drink " + String.valueOf(calculatedCups) + " cups");

        notification = builder.build();
        notificationManagerCompat = NotificationManagerCompat.from(getActivity());

        quoteText.setText(getRandomString());

        SharedPreferences sharedPreferences = requireActivity().getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        notificationCounter = sharedPreferences.getInt(KEY_NOTIFICATION_COUNTER, 0);
        counterProgressBar = sharedPreferences.getFloat(KEY_COUNTER_PROGRESS_BAR, 0.0f);
        temperature = sharedPreferences.getInt(KEY_TEMPERATURE, 25);
        calories = sharedPreferences.getInt(KEY_CALORIES, 0);
        progressBar.setProgress((int) Math.floor(counterProgressBar));
        drankCupsText.setText((int) Math.floor(counterProgressBar) + "/" + dailyTarget);
        temperatureText.setText("Current temperature\n\n" + temperature + " \u2103");
        caloriesText.setText("Calories burned\n\n" + calories + " kcal");

        sleepModeButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                SimpleDateFormat sdf = new SimpleDateFormat("EEEE");
                Date d = new Date();
                String dayOfTheWeek = sdf.format(d);
                Log.d("dayOfTheWeek", dayOfTheWeek);
                try {
                    CSVWriter csvWriterCups = new CSVWriter(new FileWriter(cups_file_path, true));
                    String[] dataCups = {dayOfTheWeek, String.valueOf((int) progressBar.getProgress())};
                    csvWriterCups.writeNext(dataCups);
                    csvWriterCups.close();

                    CSVWriter csvWriterCalories = new CSVWriter(new FileWriter(calories_file_path, true));
                    String[] dataCalories = {dayOfTheWeek, String.valueOf(calories)};
                    csvWriterCalories.writeNext(dataCalories);
                    csvWriterCalories.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (dayOfTheWeek.equals("Saturday")){
                    clearCsvFile("cups_data.csv");
                    clearCsvFile("calories_data.csv");
                }
                onDestroy();
                getActivity().finishAffinity(); // Close all activities of the app
                System.exit(0); // Terminate the app process
            }
        });

        hotTempBeep = MediaPlayer.create(getContext(), R.raw.beep_warning);
        hotTempTextualSound = MediaPlayer.create(getContext(), R.raw.textual_sound);
        reachedTargetBeep = MediaPlayer.create(getContext(), R.raw.reached_sound);

        return view;
    }

    public static void play(MediaPlayer player){
        if (player != null){
            player.start();
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    player.pause();
                }
            });
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.EditInput) {
            // Reset the isFirstLaunch flag to true
            SharedPreferences.Editor editor = sharedpreferences.edit();
            editor.putBoolean("firstRun", true).apply();

            // Clear the saved user data
            editor.remove("userName").apply();
            editor.remove("userWeight").apply();
            editor.remove("numActivity").apply();

            // Restart the activity
            Intent intent = requireActivity().getIntent();
            requireActivity().finish();
            startActivity(intent);

            return true;
        } else {
            if (id == R.id.WeeklyReport){
                Intent intent = new Intent(getActivity(), WeeklyStats.class);
                startActivity(intent);
                return true;
            }
            else
                if (id == R.id.headToInstructions2){
                    Intent intent = new Intent(getActivity(), Instructions.class);
                    startActivity(intent);
                    return true;
                }
                return super.onOptionsItemSelected(item);
        }
    }

    /*
     * Serial + UI
     */
    public static String[] clean_str(String[] stringsArr){
        for (int i = 0; i < stringsArr.length; i++)  {
            stringsArr[i]=stringsArr[i].replaceAll(" ","");
        }

        return stringsArr;
    }

    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
//            status("connecting...");
            connected = Connected.Pending;
            SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), device);
            service.connect(socket);
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        service.disconnect();
    }

    private void writeToCsv(ArrayList<String[]> rowsContainer, CSVWriter csvWriter){
        for (int i = 0; i < rowsContainer.size(); i++){
            csvWriter.writeNext(rowsContainer.get(i));
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void receive(byte[] message) {
        String msg = new String(message);
        Log.d("message", msg);
        if(newline.equals(TextUtil.newline_crlf) && msg.length() > 0) {
            String msg_to_save = msg.replace(TextUtil.newline_crlf, TextUtil.emptyString);
            if (msg_to_save.length() > 1){
                String[] parts = clean_str(msg_to_save.split(","));
                // Saving to CSV
                try{
                    CSVWriter csvWriter = new CSVWriter(new FileWriter(file_path, true));
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
                        csvWriter.writeNext(row);
                        csvWriter.close();
                    }
                    catch (NumberFormatException e){Log.d("NumberFormatException", "NumberFormatException");}
                } catch (IOException e) {
                    e.printStackTrace();
                }

                int time = (int) Math.round((Float.parseFloat(parts[3])/1000.0));

                float f_temperature = Float.parseFloat(parts[4]);
                temperature = Math.round(f_temperature);

                if (isFirstTemp){
                    temperatureText.setText("Current temperature\n\n" + temperature + " \u2103");
                    caloriesText.setText("Calories burned\n\n" + calories + " kcal");
                    isFirstTemp = false;
                }
                Log.d("temperatureApp", time + " seconds" + " " + isFirstInFiveMinutes + " " + "interval minutes" + intervalMinutes);
                if (time % (intervalMinutes*60) == 0 && isFirstInFiveMinutes && time != 0) {
                    Log.d("temperatureApp", temperature + " \u2103");
                    Log.d("temperatureApp", time + " seconds");
                    Log.d("temperatureApp", isFirstInFiveMinutes + "");
                    notificationCounter++;
                    SharedPreferences.Editor editor = sharedpreferences.edit();
                    editor.putInt(KEY_NOTIFICATION_COUNTER, notificationCounter);
                    isFirstInFiveMinutes = false;
                    temperatureText.setText("Current temperature\n\n" + temperature+ " \u2103");
                    PyObject obj = pyobj.callAttr("get_preds" , file_path);
                    int activity = obj.asList().get(0).toInt();
                    if (temperature > 30 && activity == 1) { // Running while too hot
                        play(hotTempBeep);
                        play(hotTempTextualSound);
                    }
                    int steps;
                    if (activity == 2) // Rest
                        steps = 0;
                    else // walk or run
                        steps = obj.asList().get(1).toInt();
                    float calculatedWaterIntake = calculatedWater(baselineInterval, temperature, steps, activity, gender);
                    calories += calculateCalories(MainActivity.weight, gender, activity, steps);
                    caloriesText.setText("Calories burned\n\n" + calories + " kcal");
                    calculatedCups += calculatedWaterIntake + residualCups;
                    counterProgressBar += calculatedWaterIntake + residualCups;
                    dailyTarget += calculatedWaterIntake - baselineInterval;
                    if ((int) Math.floor(counterProgressBar) == dailyTarget) // reached target
                        play(reachedTargetBeep);
                    if ((int) Math.floor(calculatedCups) >= 1){
                        residualCups = calculatedCups - (float)Math.floor(calculatedCups);
                        startNotification((int)Math.floor(calculatedCups), notificationCounter);
                        calculatedCups = 0;
                    }

                    // empty the csv of the Data
                    clearCsvFile("/data.csv");
                }
                if (time % (intervalMinutes*60) != 0)
                    isFirstInFiveMinutes = true;
            }
        }
    }

    private void startNotification(int numCups, int notificationCounter){
        Intent cancelIntent = new Intent(getContext(), updateProgressBar.class);
        cancelIntent.setAction(updateProgressBar.ACTION_CANCEL);
        PendingIntent cancelPendingIntent =
                PendingIntent.getBroadcast(getContext(), notificationCounter,
                        cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getActivity(), "myCh2")
                .setSmallIcon(R.drawable.noification_logo)
                .setContentTitle("Drinking Time")
                .setContentText("Time to drink " + String.valueOf(numCups) + " cups")
                .addAction(new NotificationCompat.Action(R.color.colorPrimary, "Done!", cancelPendingIntent));

        notification = builder.build();
        notificationManagerCompat = NotificationManagerCompat.from(getActivity());
        notificationManagerCompat.notify(notificationCounter, notification);
    }


    private void status(String str) {
        Toast.makeText(getActivity(), "Connection To Bluetooth device failed.\nTry again later.", Toast.LENGTH_SHORT).show();
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
//        status("connected");
        connected = Connected.True;
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnect();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onSerialRead(byte[] data) {
        try {
            receive(data);}
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        disconnect();
    }

    private ArrayList<Entry> emptyDataValues()
    {
        ArrayList<Entry> dataVals = new ArrayList<Entry>();
        return dataVals;
    }

    public static int ounceToCups(double ounces) {
        return (int) Math.round(ounces * 0.125);
    }

    private void clearCsvFile(String file_path){
        String csvFile = Environment.getExternalStorageDirectory() + file_path;

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

    public static float calculatedWater(float baselineInterval, int temperature, int numSteps, int activityType, String gender){
        float wTemperature;
        float wSteps;
        float wActivity;

        // temperature weight - w2
        if (temperature <= 25)
            wTemperature = 1.0f;
        else if (temperature <= 30)
            wTemperature = 1.1f;
        else wTemperature = 1.2f;

        // activity weight - w1
        if (activityType == 2) // rest
            wActivity = 0.0f;
        else if (activityType == 0) // walk
            wActivity = 1.1f;
        else wActivity = 1.2f; // run

        // steps weight - w3
        if (activityType == 2) // rest
            wSteps = 0.0f;
        else if (activityType == 0) { // walk
            if (gender.equals("Male"))
                wSteps = numSteps / 382.0f;
            else wSteps = numSteps / 436.0f;
        }

        else{
            if (gender.equals("Male"))
                wSteps = numSteps/720.0f;
            else wSteps = numSteps/809.0f;
        }

        return (wActivity*wSteps + wTemperature)*baselineInterval;
    }

    public static int calculateCalories(int weight, String gender, int activityType, int numSteps){
        Log.d("blabla", weight + ", " + gender + ", " + activityType + ", " + numSteps);
        float met; // metabolic equivalent of task
        float speed; // speed in m/s

        if (gender.equals("Male")){
            speed = (float) (numSteps*0.762/1000.0) * (60.0f/intervalMinutes); // speed in m/s
        }
        else{
            speed = (float) (numSteps*0.67/1000.0) * (60.0f/intervalMinutes); // speed in m/s
        }

        if (activityType == 0) { // walk
            if (speed <= 4.0f)
                met = 3.0f;
            else if (speed <= 6.0f) {
                met = 4.0f;
            }
            else met = 5.0f;
        }
        else if (activityType == 1) // run
            if (speed <= 8.0f)
                met = 8.3f;
            else if (speed <= 12.0f) {
                met = 10.5f;
            }
            else met = 13.0f;

        else met = 1.3f; // rest

        return (int) Math.round(intervalMinutes*met*3.5*weight/200.0);
    }

}