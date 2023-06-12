package com.example.tutorial6;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;

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
    String prevStarted = "yes";

    Button contButton;
    EditText userName;
    EditText userWeight;
    EditText userActivity;

    ImageView logoImage;

    private boolean isFirstLaunch = true;

    public static String username;
    public static String weight;
    public static String numActivity;

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
        logoImage = findViewById(R.id.imageView);


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
                    SharedPreferences sharedpreferences = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedpreferences.edit();
                    editor.putBoolean(prevStarted, false).apply();
                    isFirstLaunch = false;
                    username = userName.getText().toString();
                    weight= userWeight.getText().toString();
                    numActivity = userActivity.getText().toString();
                    getSupportFragmentManager().beginTransaction().add(R.id.fragment, new DevicesFragment(), "devices").commit();
                    contButton.setVisibility(View.GONE);
                    userName.setVisibility(View.GONE);
                    userActivity.setVisibility(View.GONE);
                    userWeight.setVisibility(View.GONE);
                    logoImage.setVisibility(View.GONE);

                    RelativeLayout rl = (RelativeLayout)findViewById(R.id.fragment);
                    rl.setBackgroundColor(getResources().getColor(R.color.cardview_dark_background));
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences sharedpreferences = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
        isFirstLaunch = sharedpreferences.getBoolean(prevStarted, true);
        Log.d("isFirstLaunch", String.valueOf(isFirstLaunch));
        if (!isFirstLaunch) {
            onBackStackChanged();
            getSupportFragmentManager().beginTransaction().add(R.id.fragment, new DevicesFragment(), "devices").commit();
            contButton.setVisibility(View.GONE);
            userName.setVisibility(View.GONE);
            userActivity.setVisibility(View.GONE);
            userWeight.setVisibility(View.GONE);
            logoImage.setVisibility(View.GONE);
            RelativeLayout rl = (RelativeLayout)findViewById(R.id.fragment);
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