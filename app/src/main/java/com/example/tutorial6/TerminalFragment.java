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
import java.util.ArrayList;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.Calendar;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import android.content.Context;
import android.content.res.Resources;
import java.util.Random;


public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {

    private enum Connected { False, Pending, True }
    private String deviceAddress;
    private SerialService service;
    public static TextView drankCups;
    private TextView temperatureText;
    private TextView caloriesText;
    private TextUtil.HexWatcher hexWatcher;
    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private boolean hexEnabled = false;
    private boolean pendingNewline = false;
    private String newline = TextUtil.newline_crlf;

    ArrayList<Float> n_value_list = new ArrayList<>();
    int elements_to_avg = 10;
    int elements_to_remove = 4;
    ArrayList<String[]> rowsContainer = new ArrayList<>();
    float threshold = 10.1f;
    int num_of_steps = 0;
    float last_ten_N_avg = 0.0f;
    Python py =  Python.getInstance();
    PyObject pyobj = py.getModule("main");
    NotificationManagerCompat notificationManagerCompat;
    Notification notification;
    public int notificationCounter = 0;
    public static float counterProgressBar = 0.0f;
    public static ProgressBar progressBar;
    TextView userNameTextView;

    private static final String RESOURCE_NAME = "random_strings";

    private SharedPreferences sharedpreferences;

    public static int dailyTarget = 0;

    private int temperature;

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

    private static final String file_path = "/sdcard/csv_dir/data.csv";
    
    
    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceAddress = getArguments().getString("device");
        // Obtain the SharedPreferences object from the activity
       sharedpreferences = requireActivity().getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
    }

    @Override
    public void onDestroy() {
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
        progressBar.setProgress((int) Math.floor(counterProgressBar));
        drankCups.setText((int) Math.floor(counterProgressBar) + "/" + dailyTarget);
        temperatureText.setText("Current temperature\n\n" + String.valueOf(temperature) + " \u2103");
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
        drankCups = view.findViewById(R.id.drankCups);
        userNameTextView = view.findViewById(R.id.userNameText);
        progressBar = view.findViewById(R.id.progressBar4);
        TextView quoteText = view.findViewById(R.id.quoteText);
        temperatureText = view.findViewById(R.id.temperature_text);
        caloriesText = view.findViewById(R.id.calories_text);
        isFirstTemp = true;

        drankCups.setText("0" + "/" + dailyTarget);
        drankCups.setTextColor(getResources().getColor(R.color.white)); // set as default color to reduce number of spans

        String userName = MainActivity.username;
        int userWeight = MainActivity.weight;
        int userActivity = MainActivity.numActivity;
        int userSleepingHours = MainActivity.sleepingHours;

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
        progressBar.setProgress((int) Math.floor(counterProgressBar));
        drankCups.setText(String.valueOf((int) Math.floor(counterProgressBar)) + "/" + dailyTarget);
        temperatureText.setText("Current temperature\n\n" + String.valueOf(temperature) + " \u2103");

        return view;
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
            editor.putBoolean(MainActivity.prevStarted, true).apply();

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
            if (id == R.id.resetCups){
                notificationCounter = 0;
                counterProgressBar = 0.0f;
                progressBar.setProgress(0);
                SharedPreferences.Editor editor = sharedpreferences.edit();
                editor.putInt(KEY_NOTIFICATION_COUNTER, 0);
                editor.putFloat(KEY_COUNTER_PROGRESS_BAR, 0);
                editor.putInt(KEY_TEMPERATURE, temperature);
                editor.apply();
                drankCups.setText("0" + "/" + dailyTarget);
                return true;
            }
            else
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
                    temperatureText.setText("Current temperature\n\n" + String.valueOf(temperature)+ " \u2103");
                    isFirstTemp = false;
                }
                if (time % (intervalMinutes*60) == 0 && isFirstInFiveMinutes){
                    isFirstInFiveMinutes = false;
                    temperatureText.setText("Current temperature\n\n" + String.valueOf(temperature)+ " \u2103");
                    PyObject obj = pyobj.callAttr("get_preds" , file_path);
                    int activity = obj.asList().get(0).toInt();
                    int steps = obj.asList().get(1).toInt();
                    calculatedCups += baselineInterval + residualCups;
                    counterProgressBar += baselineInterval + residualCups;
                    if ((int) Math.floor(calculatedCups) >= 1){
                        residualCups = calculatedCups - (float)Math.floor(calculatedCups);
                        startNotification((int)Math.floor(calculatedCups), notificationCounter);
                        drankCups.setText((int)Math.floor(counterProgressBar) + "/" + dailyTarget);
                        progressBar.setProgress((int) Math.floor(counterProgressBar));
                        calculatedCups = 0;
                    }

                    // empty the csv
                    clearCsvFile();
                }
                if (time % (intervalMinutes*60) != 0)
                    isFirstInFiveMinutes = true;
            }
        }
    }

    private void startNotification(int numCups, int notificationCounter){
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getActivity(), "myCh2")
                .setSmallIcon(R.drawable.noification_logo)
                .setContentTitle("Drinking Time")
                .setContentText("Time to drink " + String.valueOf(numCups) + " cups");

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

    private void OpenLoadCSV(){
        Intent intent = new Intent(getContext(),LoadCSV.class);
        startActivity(intent);
    }

    public static int ounceToCups(double ounces) {
        return (int) Math.round(ounces * 0.125);
    }

    private void clearCsvFile() {
        String csvFile = Environment.getExternalStorageDirectory() + "/csv_dir/data.csv";

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