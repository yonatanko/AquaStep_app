package com.example.tutorial6;


import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import com.opencsv.CSVReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import java.util.List;


public class LoadCSV extends AppCompatActivity {
    String fileNameValue;

    private TextView numStepsText;

    int elements_to_avg = 10;
    int elements_to_remove = 4;
    ArrayList<Float> n_value_list = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_load_csv);
        Button BackButton = (Button) findViewById(R.id.button_back);
        Button showButton = (Button) findViewById(R.id.button_submit);
        LineChart lineChart = (LineChart) findViewById(R.id.line_chart);
        final EditText fileName = findViewById(R.id.fileNameInput);

        numStepsText = findViewById(R.id.numStepsText);
        numStepsText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans
        numStepsText.setMovementMethod(ScrollingMovementMethod.getInstance());

        BackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClickBack();
            }
        });

        showButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                fileNameValue = fileName.getText().toString();
                ArrayList<String[]> csvData = new ArrayList<>();
                csvData = CsvRead("/sdcard/csv_dir/" + fileNameValue + ".csv");
                ArrayList<ILineDataSet> dataSets = new ArrayList<>();

                LineDataSet lineDataSet =  new LineDataSet(DataValues(csvData),"N");
                dataSets.add(lineDataSet);
                lineDataSet.setLineWidth(1.5f);
                lineDataSet.setColor(Color.RED);
                lineDataSet.setCircleColor(Color.RED);

                LineData data = new LineData(dataSets);
                lineChart.setData(data);
                lineChart.invalidate();

                String text_to_show = "Number of steps: " + csvData.get(4)[1];
                numStepsText.setText(text_to_show);
            }
        });
    }

    private void ClickBack(){
        finish();

    }

    private ArrayList<String[]> CsvRead(String path){
        ArrayList<String[]> CsvData = new ArrayList<>();
        try {
            File file = new File(path);
            CSVReader reader = new CSVReader(new FileReader(file));
            String[]nextline;
            while((nextline = reader.readNext())!= null){
                if(nextline != null){
                    CsvData.add(nextline);

                }
            }

        }catch (Exception e){}
        return CsvData;
    }

    private ArrayList<Entry> DataValues(ArrayList<String[]> csvData){
        Python py =  Python.getInstance();
        PyObject pyobj = py.getModule("test");
        ArrayList<Entry> dataVals = new ArrayList<Entry>();
        for (int i = 7; i < csvData.size(); i++){
            float x = Float.parseFloat(csvData.get(i)[1]);
            float y = Float.parseFloat(csvData.get(i)[2]);
            float z = Float.parseFloat(csvData.get(i)[3]);
            PyObject obj = pyobj.callAttr("main", x,y,z);
            float n_value = obj.toFloat();
            n_value_list.add(n_value);
            if (n_value_list.size()==elements_to_avg){
                float sum = 0.0f;
                for (int j = 0; j < elements_to_avg; j++){
                    sum += n_value_list.get(j);
                }
                float last_20_avg = sum/elements_to_avg;
                dataVals.add(new Entry(Float.parseFloat(csvData.get(i)[0]), last_20_avg));
                n_value_list.subList(0, elements_to_remove).clear();
            }
        }
        return dataVals;
    }

}