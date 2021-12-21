package de.hohenpoelz.awattarprice;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.jakewharton.threetenabp.AndroidThreeTen;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.threeten.bp.Duration;
import org.threeten.bp.Instant;
import org.threeten.bp.LocalDate;
import org.threeten.bp.LocalDateTime;
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZoneOffset;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.temporal.ChronoUnit;
import org.threeten.bp.Period;

public class MainActivity extends AppCompatActivity {

    Button btn_left, btn_right;
    BarChart lc_chart;
    TextView tv_top;
    Switch sw_nettobrutto;
    Switch sw_country_deat;
    ZonedDateTime currentDate, today;
    Boolean is_brutto;
    String country;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidThreeTen.init(this);
        setContentView(R.layout.activity_main);

        btn_left = findViewById(R.id.btn_left);
        btn_right = findViewById(R.id.btn_right);
        lc_chart = findViewById(R.id.lc_chart);
        tv_top = findViewById(R.id.tv_top);
        sw_country_deat = findViewById(R.id.sw_country_deat);
        sw_nettobrutto = findViewById(R.id.sw_nettobrutto);

        currentDate = ZonedDateTime.now(ZoneId.systemDefault()).truncatedTo(ChronoUnit.DAYS);;
        today = currentDate;

        is_brutto = true;
        sw_nettobrutto.setChecked(is_brutto);
        country = "DE";
        sw_country_deat.setChecked(false);

        btn_right.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentDate = currentDate.plusDays(1);
                updateChart();
            }
        });

        btn_left.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentDate = currentDate.minusDays(1);
                updateChart();
            }
        });

        sw_country_deat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (sw_country_deat.isChecked()) {
                    country = "AT";
                } else {
                    country = "DE";
                }
                updateChart();
            }
        });

        sw_nettobrutto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                is_brutto = sw_nettobrutto.isChecked();
                updateChart();
            }
        });

        lc_chart.setDragEnabled(false);
        lc_chart.setDrawGridBackground(false);
        lc_chart.setDragEnabled(false);
        lc_chart.getDescription().setEnabled(false);
        lc_chart.getLegend().setEnabled(false);

        // data has AxisDependency.LEFT
        YAxis left = lc_chart.getAxisLeft();
        left.setAxisMinimum(-3.f);
        left.setAxisMaximum(40.f);
        left.setDrawLabels(false); // no axis labels
        left.setDrawAxisLine(false); // no axis line
        left.setDrawGridLines(false); // no grid lines
        left.setDrawZeroLine(true); // draw a zero line
        left.setZeroLineWidth(5);
        lc_chart.getAxisRight().setEnabled(false); // no right axis
        // Request a string response from the provided URL.
        updateChart();
    }

    protected void updateChart() {
        String url = "https://example.org/invalid-request-from-awattar-app";
        StringBuilder datestr_builder = new StringBuilder();
        datestr_builder.append(DateTimeFormatter.ofPattern("yyyy-MM-dd").format(currentDate));
        long day_offset = Duration.between(today, currentDate).toDays();
        if (day_offset == 0) {
            datestr_builder.append(" (today)");
        } else if (day_offset > 0) {
            datestr_builder.append(" (in " + day_offset + " days)");
        } else {
            datestr_builder.append(" (" +(-day_offset) +" days ago)");
        };
        String datestr = datestr_builder.toString();

        switch (country) {
            case "DE": url = "https://api.awattar.de/v1/marketdata"; break;
            case "AT": url = "https://api.awattar.at/v1/marketdata"; break;
        }
        long startOfDay = 1000 * currentDate.toInstant().getEpochSecond();
        url += "?start="+startOfDay;

        //Toast.makeText(MainActivity.this, url, Toast.LENGTH_LONG).show();
        Response.ErrorListener networkErrorHandler = new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(MainActivity.this, "Network failure", Toast.LENGTH_LONG).show();
            }
        };
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        JSONArray prices;
                        try {
                            prices = response.getJSONArray("data");
                        } catch (JSONException e) {
                            Toast.makeText(MainActivity.this, "JSON failure: "+ e.toString(), Toast.LENGTH_LONG).show();
                            return;
                        }
                        // now we have to update the chart
                        List<BarEntry> chartValues = new ArrayList<BarEntry>();
                        List<Integer>  chartColors = new ArrayList<Integer>();
                        for (int i=0; i<prices.length(); i++) {
                            JSONObject entry = null;
                            double marketprice = 0;
                            try {
                                entry = prices.getJSONObject(i);
                                // divide by 10 to get price per kWh
                                marketprice = entry.getDouble("marketprice") / 10.f;
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            if (marketprice > 20.0) { chartColors.add(Color.RED); }
                            else if (marketprice > 7.0) { chartColors.add(Color.YELLOW); }
                            else { chartColors.add(Color.GREEN); }
                            if (is_brutto) { marketprice *= 1.19; };
                            BarEntry barEntry = new BarEntry(i, (float) marketprice);
                            chartValues.add(barEntry);
                        }
                        BarDataSet barDataSet = new BarDataSet(chartValues, "");
                        barDataSet.setColors(chartColors);
                        lc_chart.setData(new BarData(barDataSet));
                        tv_top.setText(datestr);
                        lc_chart.invalidate();
                    }
                }, networkErrorHandler);


        // Add the request to the RequestQueue.
        RequestQueueSingleton.getInstance(MainActivity.this).addToRequestQueue(request);
    }
}