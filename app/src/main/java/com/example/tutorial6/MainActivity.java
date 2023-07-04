package com.example.tutorial6;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;


public class MainActivity extends AppCompatActivity implements FragmentManager.OnBackStackChangedListener {
    NotificationManagerCompat notificationManagerCompat;
    Notification notification;
    public static String prevStarted = "yes";

    Button contButton;
    EditText userName;
    EditText userWeight;
    EditText userActivity;

    EditText userSleepingHours;

    RadioGroup genderRadioGroup;

    public static boolean isFirstLaunch = true;

    public static String username;
    public static int weight;
    public static int numActivity;
    public static int sleepingHours;
    public static String gender;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportFragmentManager().addOnBackStackChangedListener(this);
        contButton = findViewById(R.id.contButton);
        userName = findViewById(R.id.EnterUserName);
        userActivity = findViewById(R.id.EnterUserActivity);
        userWeight = findViewById(R.id.EnterUserWeight);
        userSleepingHours = findViewById(R.id.EnterSleepingHours);
        genderRadioGroup = findViewById(R.id.genderRadioGroup);

        if (ContextCompat.checkSelfPermission(MainActivity.this,Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ){
            ActivityCompat.requestPermissions(MainActivity.this,new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},0);
        }

        if (! Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            NotificationChannel channel = new NotificationChannel("myCh2", "My Channel2", NotificationManager.IMPORTANCE_DEFAULT);

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
        contButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isFirstLaunch) {
                    if (userName.getText().toString().equals("") || userActivity.getText().toString().equals("") || userWeight.getText().toString().equals("") || userSleepingHours.getText().toString().equals("")) {
                        AlphaAnimation animation1 = new AlphaAnimation(0.2f, 1.0f);
                        animation1.setDuration(1500);
                        animation1.setStartOffset(500);
                        animation1.setFillAfter(true);
                        userName.startAnimation(animation1);
                        userActivity.startAnimation(animation1);
                        userWeight.startAnimation(animation1);
                        userSleepingHours.startAnimation(animation1);
                        genderRadioGroup.startAnimation(animation1);
                        Toast.makeText(getApplicationContext(), "Please Fill All the Fields", Toast.LENGTH_SHORT).show();
                    }
                    else{
                        SharedPreferences sharedpreferences = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = sharedpreferences.edit();
                        editor.putBoolean(prevStarted, false).apply();
                        isFirstLaunch = false;
                        username = userName.getText().toString();
                        weight = Integer.parseInt(userWeight.getText().toString());
                        numActivity = Integer.parseInt(userActivity.getText().toString());
                        sleepingHours = Integer.parseInt(userSleepingHours.getText().toString());
                        int selectedRadioBtnID = genderRadioGroup.getCheckedRadioButtonId();
                        if (selectedRadioBtnID != -1) {
                            RadioButton selectedBtn = findViewById(selectedRadioBtnID);
                            gender = selectedBtn.getText().toString();
                        }
                        editor.putString("userName", username).apply();
                        editor.putInt("userWeight", weight).apply();
                        editor.putInt("numActivity", numActivity).apply();
                        editor.putInt("sleepingHours", sleepingHours).apply();
                        editor.putString("userGender", gender).apply();
                        contButton.setVisibility(View.GONE);
                        userName.setVisibility(View.GONE);
                        userActivity.setVisibility(View.GONE);
                        userWeight.setVisibility(View.GONE);
                        userSleepingHours.setVisibility(View.GONE);
                        genderRadioGroup.setVisibility(View.GONE);

                        RelativeLayout rl = (RelativeLayout)findViewById(R.id.fragment);
                        rl.setBackgroundColor(getResources().getColor(R.color.waterBlue));

                        DevicesFragment devicesFragment = (DevicesFragment) getSupportFragmentManager().findFragmentByTag("devices");
                        if (devicesFragment == null) {
                            getSupportFragmentManager().beginTransaction().add(R.id.fragment, new DevicesFragment(), "devices").commit();
                        }

                    }
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences sharedpreferences = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
        isFirstLaunch = sharedpreferences.getBoolean(prevStarted, true);
        if (!isFirstLaunch) {
            username = sharedpreferences.getString("userName", "");
            numActivity = sharedpreferences.getInt("numActivity", 0);
            weight = sharedpreferences.getInt("userWeight", 0);
            sleepingHours = sharedpreferences.getInt("sleepingHours", sleepingHours);
            gender = sharedpreferences.getString("userGender", "");


            DevicesFragment devicesFragment = (DevicesFragment) getSupportFragmentManager().findFragmentByTag("devices");
            if (devicesFragment == null) {
                getSupportFragmentManager().beginTransaction().add(R.id.fragment, new DevicesFragment(), "devices").commit();
            }

            contButton.setVisibility(View.GONE);
            userName.setVisibility(View.GONE);
            userActivity.setVisibility(View.GONE);
            userWeight.setVisibility(View.GONE);
            userSleepingHours.setVisibility(View.GONE);
            genderRadioGroup.setVisibility(View.GONE);
            RelativeLayout rl = (RelativeLayout) findViewById(R.id.fragment);
            rl.setBackgroundColor(getResources().getColor(R.color.cardview_dark_background));
        }
    }

    @Override
    public void onBackStackChanged() {
        getSupportActionBar().setDisplayHomeAsUpEnabled(getSupportFragmentManager().getBackStackEntryCount()>0);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}