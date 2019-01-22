package com.example.hsipa.amby;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.directions.route.AbstractRouting;
import com.directions.route.Route;
import com.directions.route.RouteException;
import com.directions.route.Routing;
import com.directions.route.RoutingListener;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.paypal.android.sdk.payments.PayPalConfiguration;
import com.paypal.android.sdk.payments.PayPalPayment;
import com.paypal.android.sdk.payments.PayPalService;
import com.paypal.android.sdk.payments.PaymentActivity;
import com.paypal.android.sdk.payments.PaymentConfirmation;

import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HistorySingleActivity extends AppCompatActivity implements OnMapReadyCallback , RoutingListener {
    private GoogleMap mMap;
    private SupportMapFragment mMapFragment;
    private String rideId, currentUserId,customerId, driverId,userDriverorCCustomer;
    private DatabaseReference historyRideInfoDb;
    private Boolean customerPaid=false;

    private TextView rideLocation, rideDistance, rideDate, userName, userPhone;
    private LatLng destinationLatLng, pickupLatLng;
    private String distance;
    private Double ridePrice;

    private ImageView userImage;
    private RatingBar mRatingBar;
    private Button mPay;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history_single);

        Intent intent = new Intent(this,PayPalService.class);
        intent.putExtra(PayPalService.EXTRA_PAYPAL_CONFIGURATION,config);
        startService(intent);
        rideId = getIntent().getExtras().getString("rideId");
        mMapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        polylines= new ArrayList<>();
        mMapFragment.getMapAsync(this);
        mRatingBar= (RatingBar) findViewById(R.id.ratingBar);
        mPay= findViewById(R.id.pay);

        rideLocation= (TextView) findViewById(R.id.rideLocation);
        rideDistance= (TextView) findViewById(R.id.rideDistance);
        rideDate  = (TextView) findViewById(R.id.rideDate);
        userName= (TextView) findViewById(R.id.userName);
        userPhone= (TextView) findViewById(R.id.userPhone);

        userImage= (ImageView) findViewById(R.id.userImage);
        currentUserId= FirebaseAuth.getInstance().getCurrentUser().getUid();

        historyRideInfoDb= FirebaseDatabase.getInstance().getReference().child("History").child(rideId);
        getRideInformation();
    }

    private void getRideInformation() {
        historyRideInfoDb.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()){
                    for(DataSnapshot child: dataSnapshot.getChildren()){
                        if(child.getKey().equals("customer")){
                            customerId= child.getValue().toString();
                            if(!customerId.equals(currentUserId)){
                                userDriverorCCustomer= "Drivers";
                                getUserInformation("Customers", customerId);
                            }
                        }
                        if(child.getKey().equals("driver")){
                            driverId= child.getValue().toString();
                            if(!driverId.equals(currentUserId)){
                                userDriverorCCustomer= "Customers";
                                getUserInformation("Drivers", driverId);
                                displayCustomerRelatedObject();
                            }
                        }
                        if(child.getKey().equals("timestamp")){
                            rideDate.setText(getDate(Long.valueOf(child.getValue().toString())));
                        }
                        if(child.getKey().equals("rating")){
                            mRatingBar.setRating(Integer.valueOf(child.getValue().toString()));
                        }
                        if(child.getKey().equals("distance")){
                            distance= child.getValue().toString();
                            rideDistance.setText(distance.substring(0, Math.min(distance.length(),5))+" km");
                            ridePrice = Double.valueOf(distance)*0.5;

                        }
                        if(child.getKey().equals("customerPaid")){
                            customerPaid = true;
                        }
                        if(child.getKey().equals("destination")){
                            rideLocation.setText(getDate(Long.valueOf(child.getValue().toString())));
                        }
                        if(child.getKey().equals("location")){
                            rideLocation.setText(getDate(Long.valueOf(child.getValue().toString())));
                            pickupLatLng = new LatLng(Double.valueOf(child.child("from").child("lat").getValue().toString()),Double.valueOf(child.child("from").child("lng").getValue().toString()));
                            destinationLatLng = new LatLng(Double.valueOf(child.child("to").child("lat").getValue().toString()),Double.valueOf(child.child("to").child("lng").getValue().toString()));
                            if(destinationLatLng!= new LatLng(0,0)){
                                getRouteToMarker();
                            }
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private void displayCustomerRelatedObject() {
        mRatingBar.setVisibility(View.VISIBLE);
        mPay.setVisibility(View.VISIBLE);
        mRatingBar.setOnRatingBarChangeListener(new RatingBar.OnRatingBarChangeListener() {
            @Override
            public void onRatingChanged(RatingBar ratingBar, float v, boolean b) {
                historyRideInfoDb.child("rating").setValue(v);
                DatabaseReference mDriverRatingDb= FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverId).child("rating");
                mDriverRatingDb.child(rideId).setValue(v);
            }
        });
        if(customerPaid){
            mPay.setEnabled(false);
        }
        else{
            mPay.setEnabled(true);
        }
        mPay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                payPalPayment();
            }
        });
    }
    private int PAYPAL_REQUEST_CODE = 1;
    private static PayPalConfiguration config = new PayPalConfiguration()
            .environment(PayPalConfiguration.ENVIRONMENT_SANDBOX)
            .clientId(PayPalConfig.PAYPAL_CLIENT_ID);


    private void payPalPayment() {
        PayPalPayment payment = new PayPalPayment(new BigDecimal(ridePrice),"INR", "Amby Ride",PayPalPayment.PAYMENT_INTENT_SALE);

        Intent intent = new Intent(this,PaymentActivity.class);

        intent.putExtra(PayPalService.EXTRA_PAYPAL_CONFIGURATION, config);
        intent.putExtra(PaymentActivity.EXTRA_PAYMENT, payment);

        startActivityForResult(intent,PAYPAL_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode== PAYPAL_REQUEST_CODE){
            if(resultCode==Activity.RESULT_OK){
                PaymentConfirmation confirm = data.getParcelableExtra(PaymentActivity.EXTRA_RESULT_CONFIRMATION);

                if(confirm!=null){
                    try{
                        JSONObject jsonObj= new JSONObject(confirm.toJSONObject().toString());

                        String paymentResponse = jsonObj.getJSONObject("response").getString("state");

                        if(paymentResponse.equals("approved")){
                            Toast.makeText(getApplicationContext(),"Payment Successful",Toast.LENGTH_LONG).show();
                            historyRideInfoDb.child("customerPaid").setValue(true);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

            }else {
                    Toast.makeText(getApplicationContext(),"Payment unsuccessful",Toast.LENGTH_SHORT).show();
        }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopService(new Intent(this,PayPalService.class));
    }

    private void getUserInformation(String otherUserDriverorCustomer, String otherUserId) {
        DatabaseReference mOtherUserDB = FirebaseDatabase.getInstance().getReference().child("Users").child(otherUserDriverorCustomer).child(otherUserId);
        mOtherUserDB.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()){
                    Map<String, Object> map= (Map<String,Object>) dataSnapshot.getValue();
                    if(map.get("name") != null){
                        userName.setText(map.get("name").toString());
                    }
                    if(map.get("phone") != null){
                        userPhone.setText(map.get("phone").toString());
                    }
                    if(map.get("profileImageUrl") != null){
                        Glide.with(getApplication()).load(map.get("profileImageUrl").toString()).into(userImage);
                    }
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
    private void getRouteToMarker() {
        Routing routing= new Routing.Builder()
                .travelMode(AbstractRouting.TravelMode.DRIVING)
                .withListener(this)
                .alternativeRoutes(true)
                .waypoints(pickupLatLng,destinationLatLng)
                .build();
        routing.execute();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap=googleMap;
    }

    private List<Polyline> polylines;
    private static  final int[] COLORS = new int[] {R.color.primary_dark_material_light};



    @Override
    public void onRoutingFailure(RouteException e) {
        if(e != null) {
            Toast.makeText(this, "Error: "+ e.getMessage(), Toast.LENGTH_LONG).show();
        }
        else{
            Toast.makeText(this, "Something went wrong, Try again", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRoutingStart() {

    }

    @Override
    public void onRoutingSuccess(ArrayList<Route> route, int shortestRouteIndex) {

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(pickupLatLng);
        builder.include(destinationLatLng);
        LatLngBounds bounds = builder.build();

        int width = getResources().getDisplayMetrics().widthPixels;
        int padding = (int) (width*0.2);

        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding);

        mMap.animateCamera(cameraUpdate);

        mMap.addMarker(new MarkerOptions().position(pickupLatLng).title("pickup location").icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_pickup)));
        mMap.addMarker(new MarkerOptions().position(destinationLatLng).title("destination"));
        if(polylines.size()>0){
            for(Polyline poly : polylines){
                poly.remove();
            }
        }
        polylines= new ArrayList<>();
        for(int i=0; i<route.size();i++){
            int colorIndex= i % COLORS.length;

            PolylineOptions polyOptions= new PolylineOptions();
            polyOptions.color(getResources().getColor(COLORS[colorIndex]));
            polyOptions.width(10 + i * 3);
            polyOptions.addAll(route.get(i).getPoints());
            Polyline polyline = mMap.addPolyline(polyOptions);
            polylines.add(polyline);

            Toast.makeText(getApplicationContext(),"Route " +(i+1)+ ": distance -" + route.get(i).getDistanceValue()+ ": duration -"+ route.get(i).getDurationValue(),Toast.LENGTH_SHORT).show();        }
    }

    @Override
    public void onRoutingCancelled() {

    }
    private void erasePolylines(){
        for(Polyline line : polylines){
            line.remove();
        }
        polylines.clear();
    }
}
