package com.example.tutorial6;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.ListFragment;

import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;

import androidx.fragment.app.FragmentManager;

public class InstructionsFragment extends ListFragment {
    Button contBtn;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        View header = getLayoutInflater().inflate(R.layout.instructions, getListView(), false);
        contBtn = header.findViewById(R.id.contButton2);
        contBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Fragment fragment = new DevicesFragment();
                getFragmentManager().beginTransaction().replace(R.id.fragment, fragment, "devices").addToBackStack(null).commit();
            }
        });
        getListView().addHeaderView(header);
    }



    @Override
    public void onResume() {
        super.onResume();
    }
}
