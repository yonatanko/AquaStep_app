package com.example.tutorial6;

import androidx.appcompat.app.AppCompatActivity;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.opencsv.CSVReader;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;

public class WeeklyStats extends AppCompatActivity {

    private static final String cups_file_path = "/sdcard/cups_data.csv";
    private static final String calories_file_path = "/sdcard/calories_data.csv";

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weekly_stats);
        Button backButton = findViewById(R.id.backButton);
        ArrayList<String[]> numCupsEachDay = new ArrayList<>();
        String[] daysOfWeek = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        // load data from cups csv file
        try {
            File cups_file = new File(cups_file_path);
            CSVReader reader = new CSVReader(new FileReader(cups_file));
            String[] nextLine;
            for (int i = 0; i < 7; i++) {
                if ((nextLine = reader.readNext()) != null) {
                    numCupsEachDay.add(new String[]{nextLine[0], nextLine[1]});
                }
                else {
                    numCupsEachDay.add(new String[]{daysOfWeek[i], "0"});
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }


        // build the bar chart
        BarChart barChart = (BarChart) findViewById(R.id.barChart);
        ArrayList<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < numCupsEachDay.size(); i++) {
            entries.add(new BarEntry(i, Float.parseFloat(numCupsEachDay.get(i)[1])));
        }
        BarDataSet barDataSet = new BarDataSet(entries, "Weekly Water Consumption");
        BarData barData = new BarData(barDataSet);
        barData.setBarWidth(0.9f);
        barChart.setData(barData);
        barChart.setFitBars(true);
        barChart.invalidate();
        barChart.animateY(1000);
        barChart.getDescription().setEnabled(false);
        barChart.getLegend().setEnabled(false);
        barChart.getAxisLeft().setAxisMinimum(0);
        barChart.getAxisRight().setAxisMinimum(0);
        barChart.getAxisLeft().setDrawGridLines(false);
        barChart.getAxisRight().setDrawGridLines(false);
        barChart.getXAxis().setDrawGridLines(false);
        barChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        barChart.getXAxis().setGranularity(1f);
        barChart.getXAxis().setValueFormatter(new ValueFormatter() {
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                return daysOfWeek[(int) value];
            }
        });
        // set y axis as integer
        barChart.getAxisLeft().setValueFormatter(new ValueFormatter() {
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                return String.valueOf((int) value);
            }
        });
        barChart.getAxisRight().setEnabled(false);
        // set color to be water blue color by rgb value of #49688C
        barDataSet.setColor(Color.rgb(73, 104, 140));

        // end build the bar chart

        // build the next bar chart - calories burned per day
        ArrayList<String[]> caloriesBurnedEachDay = new ArrayList<>();
        // load data from calories csv file
        try {
            File calories_file = new File(calories_file_path);
            CSVReader reader = new CSVReader(new FileReader(calories_file));
            String[] nextLine;
            for (int i = 0; i < 7; i++) {
                if ((nextLine = reader.readNext()) != null) {
                    caloriesBurnedEachDay.add(new String[]{nextLine[0], nextLine[1]});
                }
                else {
                    caloriesBurnedEachDay.add(new String[]{daysOfWeek[i], "0"});
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // build the bar chart
        BarChart barChart2 = (BarChart) findViewById(R.id.barChart2);
        ArrayList<BarEntry> entries2 = new ArrayList<>();
        for (int i = 0; i < caloriesBurnedEachDay.size(); i++) {
            entries2.add(new BarEntry(i, Float.parseFloat(caloriesBurnedEachDay.get(i)[1])));
        }
        BarDataSet barDataSet2 = new BarDataSet(entries2, "Weekly Calories Burned");
        BarData barData2 = new BarData(barDataSet2);
        barData2.setBarWidth(0.9f);
        barChart2.setData(barData2);
        barChart2.setFitBars(true);
        barChart2.invalidate();
        barChart2.animateY(1000);
        barChart2.getDescription().setEnabled(false);
        barChart2.getLegend().setEnabled(false);
        barChart2.getAxisLeft().setAxisMinimum(0);
        barChart2.getAxisRight().setAxisMinimum(0);
        barChart2.getAxisLeft().setDrawGridLines(false);
        barChart2.getAxisRight().setDrawGridLines(false);
        barChart2.getXAxis().setDrawGridLines(false);
        barChart2.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        barChart2.getXAxis().setGranularity(1f);

        // set x axis labels to be days of the week
        barChart2.getXAxis().setValueFormatter(new ValueFormatter() {
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                return daysOfWeek[(int) value];
            }
        });
        // set y axis as integer
        barChart2.getAxisLeft().setValueFormatter(new ValueFormatter() {
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                return String.valueOf((int) value);
            }
        });
        barChart2.getAxisRight().setEnabled(false);
        // set color to be water blue color by rgb value of #49688C
        barDataSet2.setColor(Color.rgb(73, 104, 140));
        // end build the bar chart

        // backButton to go back to TerminalFragment
        backButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });
    }

    private void ClickLineChart() {
        Intent intent = new Intent(this,MainActivity.class);
        startActivity(intent);
    }

    private void ClickOpenCSV() {
        Intent intent = new Intent(this,LoadCSV.class);
        startActivity(intent);
    }

    private ArrayList<String[]> CsvRead(String path){
        java.util.ArrayList<String[]> CsvData = new ArrayList<>();
        try {
            File file = new File(path);
            CSVReader reader = new CSVReader(new FileReader(file));
            String[] nextline;
            while((nextline = reader.readNext())!= null){
                if(nextline != null){
                    CsvData.add(nextline);

                }
            }

        }catch (Exception e){ e.printStackTrace();}
        return CsvData;
    }
}
