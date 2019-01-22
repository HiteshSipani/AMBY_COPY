package com.example.hsipa.amby;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.os.Build;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.directions.route.AbstractRouting;
import com.directions.route.Route;
import com.directions.route.RouteException;
import com.directions.route.Routing;
import com.directions.route.RoutingListener;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DriverMapActivity extends FragmentActivity implements OnMapReadyCallback,RoutingListener {

    private GoogleMap mMap;
    private FusedLocationProviderClient mFusedLocationClient;
    private SupportMapFragment mapFragment;
    Location mLastLocation;
    LocationRequest mLocationRequest;
    Location mCurrentLocation = null;
    private Button mLogout, mSettings, mRideStatus,mHistory;
    private int status=0;
    private float rideDistance;
    private Boolean isLoggingOut=false;
    private String customerId= "", destination;
    private LatLng destinationLatLng, pickupLatLng;
    private LinearLayout mCustomerInfo;
    private ImageView mCustomerProfileImage;
    private TextView mCustomerName;
    private TextView mCustomerPhone, mCustomerDestination;
    private List<Polyline> polylines;
    private Switch mWorkingSwitch;
    private static final int[] COLORS= new int[]{R.color.primary_dark_material_light,R.color.colorAccent};

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_map2);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.

        polylines = new ArrayList<>();
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);

        mapFragment.getMapAsync(this);

        mWorkingSwitch= (Switch) findViewById(R.id.workingSwitch);
        mWorkingSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if(b){
                    connectDriver();
                }
                else{
                    disconnectDriver();
                }
            }
        });



        mCustomerInfo= (LinearLayout) findViewById(R.id.customerInfo);

        mCustomerProfileImage= (ImageView) findViewById(R.id.customerProfileImage);

        mCustomerPhone= (TextView) findViewById(R.id.customerPhone);
        mCustomerDestination = (TextView) findViewById(R.id.customerDestination);

        mSettings= (Button) findViewById(R.id.settings);
        mHistory= (Button) findViewById(R.id.history);
        mRideStatus =(Button) findViewById(R.id.rideStatus);
        mRideStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switch(status){
                    case 1:
                            status=2;
                            erasePolylines();
                            if(destinationLatLng.latitude != 0.0 && destinationLatLng.longitude!=0.0)
                            {
                                getRouteToMarker(destinationLatLng);
                            }
                            mRideStatus.setText("Arrived Destination");
                        break;
                    case 2:
                        recordRide();
                        endRide();
                        break;
                }
            }
        });

        mCustomerName= (TextView) findViewById(R.id.customerName);
        mLogout= (Button) findViewById(R.id.logout);
        mLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isLoggingOut=true;
                disconnectDriver();
                FirebaseAuth.getInstance().signOut();
                Intent intent= new Intent(DriverMapActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
                return;
            }
        });
        mSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent= new Intent(DriverMapActivity.this, DriverSettingsActivity.class);
                startActivity(intent);
                finish();
                return;
            }
        });
        mHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent= new Intent(DriverMapActivity.this, HistoryActivity.class);
                intent.putExtra("customerOrDriver", "Drivers");
                startActivity(intent);
                return;
            }
        });
        getAssignedCustomer();
    }
    private void getAssignedCustomer(){
        String driverId= FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference assignedCustomerRef= FirebaseDatabase.getInstance().getReference().child("Drivers").child(driverId).child("customerRequest").child("CustomerRideId");
        assignedCustomerRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()){
                    status=1;
                        customerId=dataSnapshot.getValue().toString();
                        getAssignedCustomerPickupLocation();
                    getAssignedCustomerInfo();
                    getAssignedCustomerDestination();

                }
                else{
                    endRide();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }


    Marker pickupMarker;
    private  DatabaseReference assignedCustomerPickupLocationRef;
    private ValueEventListener assignedCustomerPickupLocationRefListener;
    private void getAssignedCustomerPickupLocation(){

         assignedCustomerPickupLocationRef= FirebaseDatabase.getInstance().getReference().child("customerRequest").child(customerId).child("l");
        assignedCustomerPickupLocationRefListener=assignedCustomerPickupLocationRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()&& !customerId.equals("")){
                    List<Object> map = (List<Object>) dataSnapshot.getValue();
                    double locationLat =0;
                    double locationLng =0;
                    if (map.get(0)!= null){
                        locationLat = Double.parseDouble(map.get(0).toString());
                    }
                    if (map.get(1)!= null){
                        locationLng = Double.parseDouble(map.get(1).toString());
                    }
                    LatLng pickupLatLng= new LatLng(locationLat,locationLng);
                    pickupMarker=mMap.addMarker(new MarkerOptions().position(pickupLatLng).title("pickup Location").icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_pickup)));
                    getRouteToMarker(pickupLatLng);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

    }

    private void getRouteToMarker(LatLng pickupLatLng) {
        if(pickupLatLng!= null && mLastLocation!=null) {
            Routing routing = new Routing.Builder()
                    .travelMode(AbstractRouting.TravelMode.DRIVING)
                    .withListener(this)
                    .alternativeRoutes(true)
                    .waypoints(new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude()), pickupLatLng)
                    .build();
            routing.execute();
        }
    }

    private void getAssignedCustomerDestination(){
        String driverId= FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference assignedCustomerRef= FirebaseDatabase.getInstance().getReference().child("Drivers").child(driverId).child("customerRequest");
        assignedCustomerRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {

                    Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();
                    if(map.get("destination") != null){
                        destination=map.get("destination").toString();
                        mCustomerDestination.setText("Destination"+ destination);
                    }
                    else{
                        mCustomerDestination.setText("Destination: -- ");
                    }
                    Double destinationLat=0.0;
                    Double destinationLng=0.0;
                    if(map.get("destinationLat")!= null){
                        destinationLat = Double.valueOf(map.get("destinationLat").toString());
                    }
                    if(map.get("destinationLng")!= null){
                        destinationLng = Double.valueOf(map.get("destinationLng").toString());
                        destinationLatLng= new LatLng(destinationLat, destinationLng);
                    }

                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }
    private void getAssignedCustomerInfo(){
        mCustomerInfo.setVisibility(View.VISIBLE);
        DatabaseReference mCustomerDatabase= FirebaseDatabase.getInstance().getReference().child("Child").child("Customers").child(customerId);
        mCustomerDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()&& dataSnapshot.getChildrenCount()>0){
                    Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();
                    if(map.get("name")!= null){

                        mCustomerName.setText(map.get("name").toString());
                    }
                    if(map.get("phone")!= null){

                        mCustomerPhone.setText(map.get("phone").toString());
                    }
                    if(map.get("profileImageUrl")!= null){
                        Glide.with(getApplication()).load(map.get("profileImageUrl").toString()).into(mCustomerProfileImage);
                    }

                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }


    private void endRide(){
        mRideStatus.setText("Ongoing Ride");
        erasePolylines();
        String userId= FirebaseAuth.getInstance().getCurrentUser().getUid();

            DatabaseReference driverRef= FirebaseDatabase.getInstance().getReference().child("Drivers").child(userId).child("Customer Request");
            driverRef.removeValue();


        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Customer Request");
        GeoFire geoFire= new GeoFire(ref);
        geoFire.removeLocation(customerId);

        customerId= "";
        rideDistance=0;

        if(pickupMarker != null){
            pickupMarker.remove();

        }
        if(assignedCustomerPickupLocationRef != null) {
            assignedCustomerPickupLocationRef.removeEventListener(assignedCustomerPickupLocationRefListener);
        }
        mCustomerInfo.setVisibility(View.GONE);
        mCustomerName.setText("");
        mCustomerPhone.setText("");
        mCustomerDestination.setText("Destination:-- ");
        mCustomerProfileImage.setImageResource(R.mipmap.ic_defaultimage_round);

    }
    private void recordRide(){
        String userId= FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference driverRef= FirebaseDatabase.getInstance().getReference().child("Drivers").child(userId).child("History");
        DatabaseReference customerRef= FirebaseDatabase.getInstance().getReference().child("Customers").child(customerId).child("History");
        DatabaseReference historyRef= FirebaseDatabase.getInstance().getReference().child("History");
        String requestId = historyRef.push().getKey();
        driverRef.child(requestId).setValue(true);
        customerRef.child(requestId).setValue(true);

        HashMap map= new HashMap();
        map.put("driver", userId);
        map.put("customer", customerId);
        map.put("rating", 0);
        map.put("timestamp", getCurrentTimeStamp());
        map.put("destination", destination);
        map.put("location/from/lat",pickupLatLng.latitude);
        map.put("location/from/lng",pickupLatLng.longitude);
        map.put("location/to/lat",destinationLatLng.latitude);
        map.put("location/to/lat",destinationLatLng.latitude);
        map.put("distance",rideDistance);
        historyRef.child(requestId).updateChildren(map);


    }

    private Long getCurrentTimeStamp() {
        Long timestamp = System.currentTimeMillis()/1000;
        return timestamp;

    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);


        if(Build.VERSION.SDK_INT >=Build.VERSION_CODES.M ){
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)== PackageManager.PERMISSION_GRANTED){

            }
            else{
                checkLocationPermission();
            }
        }
    }

    LocationCallback mLocationCallback= new LocationCallback(){
        @Override
        public void onLocationResult(LocationResult locationResult) {
            super.onLocationResult(locationResult);
            for (Location location: locationResult.getLocations()){
                LatLng latlng = new LatLng(location.getLatitude(), location.getLongitude());


                if(!customerId.equals("")){
                    rideDistance+= mLastLocation.distanceTo(location)/1000;
                }
                mLastLocation=location;
                mMap.moveCamera(CameraUpdateFactory.newLatLng(latlng));
                mMap.animateCamera(CameraUpdateFactory.zoomTo(19));
                String userId= FirebaseAuth.getInstance().getCurrentUser().getUid();
                DatabaseReference refAvailable= FirebaseDatabase.getInstance().getReference("drivers Available");
                DatabaseReference refWorking= FirebaseDatabase.getInstance().getReference("drivers Working");
                GeoFire geoFireAvailable = new GeoFire(refAvailable);
                GeoFire geoFireWorking= new GeoFire(refWorking);

                switch (customerId){
                    case "":

                        geoFireWorking.removeLocation(userId);
                        geoFireAvailable.setLocation(userId, new GeoLocation(location.getLatitude(),location.getLongitude()));
                        break;

                    default:
                        geoFireAvailable.removeLocation(userId);
                        geoFireWorking.setLocation(userId, new GeoLocation(location.getLatitude(),location.getLongitude()));
                        break;
                }
            }
        }
    };

    private void checkLocationPermission() {
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)!= PackageManager.PERMISSION_GRANTED){
            if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)){
                new AlertDialog.Builder(this)
                        .setTitle("Give Permission")
                        .setMessage("Give Permission Message")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {

                                ActivityCompat.requestPermissions(DriverMapActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},1);
                            }
                        })
                .create()
                .show();
            }
            else{
                ActivityCompat.requestPermissions(DriverMapActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},1);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        switch(requestCode){
            case 1: {
                if(grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED){
                    if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){
                        mFusedLocationClient.requestLocationUpdates(mLocationRequest,mLocationCallback, Looper.myLooper());
                        mMap.setMyLocationEnabled(true);
                    }
                }
                else{
                    Toast.makeText(getApplicationContext(), "Please provide the permission", Toast.LENGTH_SHORT).show();
                }
                break;
            }
        }
    }

    private void connectDriver(){
        checkLocationPermission();
        mFusedLocationClient.requestLocationUpdates(mLocationRequest,mLocationCallback, Looper.myLooper());
        mMap.setMyLocationEnabled(true);
    }

    private  void disconnectDriver(){
        if(mFusedLocationClient != null){
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
        }
        String userId= FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference ref= FirebaseDatabase.getInstance().getReference("driversAvailable");
        GeoFire geoFire = new GeoFire (ref);
        geoFire.removeLocation(userId);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(!isLoggingOut){
            disconnectDriver();
        }

    }

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



