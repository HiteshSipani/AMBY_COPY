package com.example.hsipa.amby;

import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.TextView;

import com.example.hsipa.amby.historyRecyclerView.HistoryAdapter;
import com.example.hsipa.amby.historyRecyclerView.HistoryObject;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;


public class HistoryActivity extends AppCompatActivity {
    private String customerOrDriver, userId;
    private RecyclerView mHistoryRecyclerView;
    private TextView mBalance;
    private Double Balance = 0.0;
    private RecyclerView.Adapter mHistoryAdapter;
    private RecyclerView.LayoutManager mHistoryLayoutManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        mBalance = findViewById(R.id.balance);

        mHistoryRecyclerView = (RecyclerView) findViewById(R.id.historyRecyclerView);
        mHistoryRecyclerView.setNestedScrollingEnabled(false);
        mHistoryRecyclerView.setHasFixedSize(true);
        mHistoryLayoutManager = new LinearLayoutManager(HistoryActivity.this);
        mHistoryRecyclerView.setLayoutManager(mHistoryLayoutManager);
        mHistoryAdapter= new HistoryAdapter(getDataSetHistory(), HistoryActivity.this);
        mHistoryRecyclerView.setAdapter(mHistoryAdapter);



        customerOrDriver= getIntent().getExtras().getString("customerOrDriver");
        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        getUserHistoryIds();

        if(customerOrDriver.equals("Drivers")){
            mBalance.setVisibility(View.VISIBLE);
        }








    }

    private void getUserHistoryIds() {
        DatabaseReference userHistoryDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child(customerOrDriver).child(userId).child("History");
        userHistoryDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()){
                    for(DataSnapshot History : dataSnapshot.getChildren()){
                        FetchRideInformation(History.getKey());
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private void FetchRideInformation(String rideKey) {
        DatabaseReference HistoryDatabase = FirebaseDatabase.getInstance().getReference().child("History").child(rideKey);
        HistoryDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()){
                    String rideId = dataSnapshot.getKey();
                    Long timestamp = 0L;
                    String distance="";
                    Double ridePrice=0.0;

                    if(dataSnapshot.child("timestamp")!=null){
                        timestamp= Long.valueOf(dataSnapshot.child("timestamp").getValue().toString());
                    }

                    if(dataSnapshot.child("customerPaid")!=null && dataSnapshot.child("driverPaidOut")==null){
                        if(dataSnapshot.child("distance").getValue()==null){
                            distance = dataSnapshot.child("distance").getValue().toString();
                            ridePrice = (Double.valueOf(distance)*0.4);
                            Balance+= ridePrice;
                            mBalance.setText("Balance: ₹"+String.valueOf(Balance));
                        }

                    }


                    HistoryObject obj = new HistoryObject(rideId, getDate(timestamp));
                    resultsHistory.add(obj);

                mHistoryAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private String getDate(Long timestamp) {
        Calendar cal = Calendar.getInstance(Locale.getDefault());
        cal.setTimeInMillis(timestamp*1000);
        String date = DateFormat.format("dd-MM-yyyy  hh:mm", cal).toString();
        return date;

    }
    

    private ArrayList resultsHistory = new ArrayList<HistoryObject>();
    private ArrayList<HistoryObject> getDataSetHistory() {
        return resultsHistory;
    }
}
