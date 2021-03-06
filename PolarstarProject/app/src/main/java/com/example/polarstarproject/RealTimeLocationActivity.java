package com.example.polarstarproject;


import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.polarstarproject.Domain.Connect;
import com.example.polarstarproject.Domain.DepartureArrivalStatus;
import com.example.polarstarproject.Domain.Disabled;
import com.example.polarstarproject.Domain.EmailVerified;
import com.example.polarstarproject.Domain.InOutStatus;
import com.example.polarstarproject.Domain.Range;
import com.example.polarstarproject.Domain.RealTimeLocation;
import com.example.polarstarproject.Domain.Route;
import com.example.polarstarproject.Domain.TrackingStatus;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Timer;
import java.util.TimerTask;

public class RealTimeLocationActivity extends AppCompatActivity implements OnMapReadyCallback { //?????????????????? ????????? ??? ???????????? ?????? ????????? ??????
    public static Context context_R; // ?????? ????????????????????? ????????? ?????? ??????

    private FirebaseDatabase database = FirebaseDatabase.getInstance();
    private DatabaseReference reference = database.getReference();
    private FirebaseAuth mAuth;
    private FirebaseUser user; //firebase ??????

    private static final String TAG = "RealTimeLocation";
    public GoogleMap map;
    private CameraPosition cameraPosition;

    private FusedLocationProviderClient fusedLocationProviderClient;

    private final LatLng defaultLocation = new LatLng(37.56, 126.97);
    public static final int DEFAULT_ZOOM = 15;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    public boolean locationPermissionGranted; //?????? ??????

    private Location lastKnownLocation;

    private static final String KEY_CAMERA_POSITION = "camera_position";
    private static final String KEY_LOCATION = "location";

    Marker myMarker;
    MarkerOptions myLocationMarker; //??? ?????? ??????
    Marker counterpartyMarker;
    MarkerOptions counterpartyLocationMarker; //????????? ?????? ??????

    LatLng counterpartyCurPoint; //????????? ??????

    public LocationManager manager;
    public GPSListener gpsListener;

    Connect myConnect;
    String counterpartyUID = "";
    public int classificationUserFlag = 0, count;//????????? ????????? ?????? (0: ?????????, 1: ?????????, 2: ?????????), ???????????? ????????? ?????????
    double routeLatitude, routeLongitude; //????????? ?????? ??????

    public double disabledAddressLatitude, disabledAddressLongitude; //????????? ??? ?????? ?????? ??????
    public double distance; //??????
    private final double DEFAULTDISTANCE= 1; //????????? ?????? ??????
    private final String DEFAULT = "DEFAULT";
    public boolean departureFlag, arrivalFlag, inFlag, outFlag = false; //??????, ??????, ??????, ?????? ?????????

    int permissionFlag = 0; //?????? ?????? ?????????
    
    String counterpartyName; //????????? ??????
    Intent notificationIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context_R = this;
        setContentView(R.layout.activity_realtime_location);

        if (savedInstanceState != null) {
            lastKnownLocation = savedInstanceState.getParcelable(KEY_LOCATION);
            cameraPosition = savedInstanceState.getParcelable(KEY_CAMERA_POSITION);
        }

        MapsInitializer.initialize(this);
        count = 0; //????????? ?????????
        
        createNotificationChannel(DEFAULT, "default channel", NotificationManager.IMPORTANCE_HIGH); //?????? ?????????
        setNotificationIntent();
        
        mAuth = FirebaseAuth.getInstance();
        user = mAuth.getCurrentUser();

        manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        gpsListener = new GPSListener();

        try {
            MapsInitializer.initialize(this);
        } catch (Exception e) {
            e.printStackTrace();
        }

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        counterpartyLocationScheduler();

        //????????? ?????? ????????? ???????????? ??????
        Button goSet = (Button) findViewById(R.id.goSet);
        goSet.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                Intent intent = new Intent(getApplicationContext(), RangeSettingActivity.class);
                startActivity(intent);
            }
        });
    }

    public void setNotificationIntent(){
        notificationIntent = new Intent(RealTimeLocationActivity.this, RealTimeLocationActivity.class); // ????????? ????????? activity??? ??????
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    /////////////////////////////////////////??????????????? ????????????////////////////////////////////////////
    @Override
    protected void onStart(){ //Activity??? ??????????????? ????????????
        super.onStart();

        //????????? ????????? ??????
        if(user.isEmailVerified()) {
            EmailVerified emailVerified = new EmailVerified(true);
            reference.child("emailverified").child(user.getUid()).setValue(emailVerified); //????????? ????????? true

            Log.d(TAG, "?????? ?????? ??????");
        }
        else{
            EmailVerified emailVerified = new EmailVerified(false);
            reference.child("emailverified").child(user.getUid()).setValue(emailVerified); //????????? ????????? false

            Toast.makeText(RealTimeLocationActivity.this, "????????? ????????? ???????????????.", Toast.LENGTH_SHORT).show(); //????????? ?????? ?????? ????????? ??????

            Log.d(TAG, "?????? ?????? ??????");
        }
    }

    /*@Override
    protected void onResume(){ //Activity??? ???????????? ??????????????????
        super.onResume();

        stopLocationService(); //??????????????? ????????? ??????
    }

    @Override
    protected void onPause(){ //Activity??? ?????? ?????????
        super.onPause();


        startLocationService(); //??????????????? ????????? ??????
    }

    @Override
    protected void onStop(){ //Activity??? ??????????????? ????????? ?????????
        super.onStop();

        startLocationService(); //??????????????? ????????? ??????
    }*/


    @Override
    protected void onSaveInstanceState(Bundle outState) { //?????? ???????????? ???, ????????????
        if (map != null) {
            outState.putParcelable(KEY_CAMERA_POSITION, map.getCameraPosition());
            outState.putParcelable(KEY_LOCATION, lastKnownLocation);
        }
        super.onSaveInstanceState(outState);
    }

    /////////////////////////////////////////??????????????? ?????????////////////////////////////////////////
    /*private boolean isLocationServiceRunning() {
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager != null) {
            for (ActivityManager.RunningServiceInfo service : activityManager.getRunningServices(Integer.MAX_VALUE)) {
                if (LocationService.class.getName().equals(service.service.getClassName())) {
                    if (service.foreground) {
                        return true;
                    }
                }
            }
            return false;
        }
        return false;
    }

    private void startLocationService() { //????????? ??????
        if (!isLocationServiceRunning()) {
            Intent intent = new Intent(getApplicationContext(), LocationService.class);
            intent.setAction(Constants.ACTION_START_LOCATION_SERVICE);
            startService(intent);
            Toast.makeText(this, "Location service started", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopLocationService() { //????????? ??????
        if (isLocationServiceRunning()) {
            Intent intent = new Intent(getApplicationContext(), LocationService.class);
            intent.setAction(Constants.ACTION_STOP_LOCATION_SERVICE);
            startService(intent);
            Toast.makeText(this, "Location service stopped", Toast.LENGTH_SHORT).show();
        }
    }*/

    public void realTimeDeviceLocationBackground(FirebaseUser user, double latitude, double longitude) { //??????????????? ????????? ?????? ??????
        firebaseUpdateLocation(user, latitude, longitude); //firebase ????????? ?????? ??????
    }

    /////////////////////////////////////////?????? ?????? ??????////////////////////////////////////////
    @Override
    public void onMapReady(GoogleMap map) { //??? ?????? ???, map ??????
        this.map = map;

        getLocationPermission(); //?????? ?????? ??????
        updateLocationUI(); //UI ????????????
        defaultDeviceLocation(); //?????? ?????? ??????
    }

    private void getLocationPermission() { //?????? ?????? ?????? ??? ??????
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            locationPermissionGranted = true;
        }
        else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) { //?????? ?????? ?????? ??????
        locationPermissionGranted = false;
        if (requestCode
                == PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationPermissionGranted = true;
                //startLocationService();
            }
        }
        else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
        updateLocationUI();
    }


    @SuppressLint("MissingPermission")
    private void updateLocationUI() { //????????? ?????? UI ??? ?????? ?????? ?????? ????????????
        if (map == null) {
            return;
        }
        try {
            if (locationPermissionGranted) {
                map.setMyLocationEnabled(true); //????????? ??????
                map.getUiSettings().setMyLocationButtonEnabled(true);
            }
            else {
                map.setMyLocationEnabled(false);
                map.getUiSettings().setMyLocationButtonEnabled(false);
                lastKnownLocation = null;
                getLocationPermission();
            }
        } catch (SecurityException e)  {
            Log.e("Exception: %s", e.getMessage());
        }
    }

    private void defaultDeviceLocation() { //????????? ?????? ?????? ?????? ????????????
        try {
            if (locationPermissionGranted) { //?????? ?????? ?????? ??????
                @SuppressLint("MissingPermission") Task<Location> locationResult = fusedLocationProviderClient.getLastLocation();
                locationResult.addOnCompleteListener(this, new OnCompleteListener<Location>() {
                    @RequiresApi(api = Build.VERSION_CODES.O)
                    @SuppressLint("MissingPermission")
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful()) {
                            lastKnownLocation = task.getResult();
                            if (lastKnownLocation != null) { //?????? ?????? ?????? ??????
                                if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                                    LatLng curPoint = new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
                                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(curPoint, DEFAULT_ZOOM)); //?????? ????????? ????????? ??????

                                    //firebaseUpdateLocation(user, lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude()); //firebase??? ????????? ?????? ??????
                                    defaultMyMarker(curPoint); //?????? ?????? ??????
                                    realTimeDeviceLocation(); //????????? ?????? ?????? ??????
                                }
                                else if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                                    LatLng curPoint = new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
                                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(curPoint, DEFAULT_ZOOM)); //?????? ????????? ????????? ??????

                                    //firebaseUpdateLocation(user, lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude()); //firebase??? ????????? ?????? ??????
                                    defaultMyMarker(curPoint); //?????? ?????? ??????
                                    realTimeDeviceLocation(); //????????? ?????? ?????? ??????
                                }
                            }
                        }
                        else { //?????? ?????? ?????? ?????? ???, ????????? ????????? ????????? ??????
                            Log.d(TAG, "????????? ?????? ?????? ??????. ????????? ?????? ??????.");
                            Log.e(TAG, "Exception: %s", task.getException());
                            map.moveCamera(CameraUpdateFactory
                                    .newLatLngZoom(defaultLocation, DEFAULT_ZOOM));
                            map.getUiSettings().setMyLocationButtonEnabled(false);
                        }
                    }
                });
            }
        } catch (SecurityException e)  {
            Log.e("Exception: %s", e.getMessage(), e);
        }
    }

    public void defaultMyMarker(LatLng curPoint){ //????????? ?????? ??????
        if (myLocationMarker == null) {
            myLocationMarker = new MarkerOptions();
            myLocationMarker.position(curPoint);

            int height = 300;
            int width = 300;
            BitmapDrawable bitmapdraw=(BitmapDrawable)getResources().getDrawable((R.drawable.my_gps));
            Bitmap b=bitmapdraw.getBitmap();
            Bitmap smallMarker = Bitmap.createScaledBitmap(b, width, height, false); //?????? ????????????

            myLocationMarker.icon(BitmapDescriptorFactory.fromBitmap(smallMarker));
            myMarker = map.addMarker(myLocationMarker);
        }
        else if (myLocationMarker != null){
            myMarker.remove(); // ????????????
            myLocationMarker.position(curPoint);
            myMarker = map.addMarker(myLocationMarker);
        }

        else if (counterpartyMarker != null){
            counterpartyMarker.remove(); // ????????????
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @SuppressLint("MissingPermission")
    public void realTimeDeviceLocation() { //????????? ?????? ??????
        try {
            Location location = null;

            long minTime = 0;        // 0????????? ?????? - ???????????? ??????
            float minDistance = 0;

            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                location = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

                if (location != null) {
                    double latitude = location.getLatitude();
                    double longitude = location.getLongitude();
                    LatLng curPoint = new LatLng(latitude, longitude);

                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(curPoint, DEFAULT_ZOOM));
                    Log.w(TAG, "GPS_PROVIDER: " + latitude + " " + longitude);
                    firebaseUpdateLocation(user, latitude, longitude); //firebase ????????? ?????? ??????
                    showCurrentLocation(latitude, longitude);
                }

                //?????? ????????????
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTime, minDistance, gpsListener);
            }
            else if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                location = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (location != null) {
                    double latitude = location.getLatitude();
                    double longitude = location.getLongitude();
                    LatLng curPoint = new LatLng(latitude, longitude);

                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(curPoint, DEFAULT_ZOOM));
                    Log.w(TAG, "NETWORK_PROVIDER: " + latitude + " " + longitude);
                    firebaseUpdateLocation(user, latitude, longitude); //firebase ????????? ?????? ??????
                    showCurrentLocation(latitude,longitude);
                }

                //?????? ????????????
                manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, minTime, minDistance, gpsListener);
            }

        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private void showCurrentLocation(double latitude, double longitude) {
        LatLng curPoint = new LatLng(latitude, longitude);
        showMyLocationMarker(curPoint);
    }

    class GPSListener implements LocationListener {
        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void onLocationChanged(Location location) { // ?????? ?????? ??? ??????
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();
            LatLng curPoint = new LatLng(latitude, longitude);

            Log.w(TAG, "GPSListener: " + latitude + " " + longitude);
            firebaseUpdateLocation(user, latitude, longitude); //firebase ????????? ?????? ??????
            showMyLocationMarker(curPoint);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }
        @Override
        public void onProviderEnabled(String provider) {

        }
        @Override
        public void onProviderDisabled(String provider) {

        }
    }

    private void showMyLocationMarker(LatLng curPoint) { //?????? ??????
        if (myLocationMarker == null) { //????????? ????????? ??????
            myLocationMarker.position(curPoint);
            myMarker = map.addMarker(myLocationMarker);
        }
        else { //????????? ???????????? ??????
            myMarker.remove(); // ????????????
            myLocationMarker.position(curPoint);
            myMarker = map.addMarker(myLocationMarker);
        }
    }

    private void firebaseUpdateLocation(FirebaseUser user, double latitude, double longitude) { //firebase??? ????????? ?????? ??????
        routeLatitude = latitude;
        routeLongitude = longitude;
        
        RealTimeLocation realTimeLocation = new RealTimeLocation(latitude,longitude);

        Log.w(TAG, "firebaseUpdate: " + latitude + " " + longitude);
        reference.child("realtimelocation").child(user.getUid()).setValue(realTimeLocation)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {

                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        // Write failed
                        Log.d(TAG,"firebase ????????? ?????? ?????? ??????");
                    }
                });
    }

    private void routeScheduler(){ //?????? ????????? ????????????
        Log.d(TAG,"?????? ????????? ???????????? ??????");
        if(classificationUserFlag == 1){
            Timer timer = new Timer();

            TimerTask timerTask = new TimerTask() {
                @RequiresApi(api = Build.VERSION_CODES.O)
                @Override
                public void run() {
                    //2????????? ??????
                    firebaseUpdateRoute(user, routeLatitude, routeLongitude);
                }
            };
            timer.schedule(timerTask,0,2000);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void firebaseUpdateRoute(FirebaseUser user, double latitude, double longitude) { //firebase??? ????????? ?????? ??????
        if(latitude != 0 && longitude != 0){
            LocalTime localTime = LocalTime.now(ZoneId.of("Asia/Seoul"));
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
            String nowTime = localTime.format(formatter); //?????? ?????? ?????????

            Route route = new Route(nowTime, latitude,longitude);

            LocalDate localDate = LocalDate.now(ZoneId.of("Asia/Seoul")); //?????? ?????? ?????????
            String nowDate = localDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            reference.child("route").child(user.getUid()).child(nowDate).child(nowTime).setValue(route)
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {

                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            // Write failed
                            Log.d(TAG,"firebase ????????? ?????? ?????? ??????");
                        }
                    });
        }
    }



    /////////////////////////////////////////????????? ??????////////////////////////////////////////
    private void counterpartyLocationScheduler(){ //1????????? ????????? DB ?????? ???, ?????? ?????????
        Timer timer = new Timer();

        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                //1????????? ??????
                classificationUser(user.getUid());
            }
        };
        timer.schedule(timerTask,0,2000);
    }

    /////////////////////////////////////////????????? ??????////////////////////////////////////////
    private void classificationUser(String uid){ //firebase select ?????? ??????, ??? connect ????????? ??????
        Query disabledQuery = reference.child("connect").child("disabled").orderByKey().equalTo(uid); //????????? ????????? ??????
        disabledQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                myConnect = new Connect();
                for(DataSnapshot ds : dataSnapshot.getChildren()){
                    myConnect = ds.getValue(Connect.class);
                }

                if(myConnect.getMyCode() != null && !myConnect.getMyCode().isEmpty()){
                    classificationUserFlag = 1;
                    if(count == 0){
                        routeScheduler(); //????????? ?????? ?????? ?????? ??????
                    }
                    count++;
                    getOtherUID();

                    if(!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) { //?????? ?????? ?????? ??????
                        TrackingStatus trackingStatus = new TrackingStatus(false);
                        reference.child("trackingstatus").child(user.getUid()).setValue(trackingStatus);
                    }

                    else {
                        TrackingStatus trackingStatus = new TrackingStatus(true); //?????? ?????? ?????? ??????
                        reference.child("trackingstatus").child(user.getUid()).setValue(trackingStatus);
                    }
                }
                
                else {

                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

        Query guardianQuery = reference.child("connect").child("guardian").orderByKey().equalTo(uid); //????????? ????????? ??????
        guardianQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                myConnect = new Connect();
                for(DataSnapshot ds : dataSnapshot.getChildren()){
                    myConnect = ds.getValue(Connect.class);
                }

                if(myConnect.getMyCode() != null && !myConnect.getMyCode().isEmpty()){
                    classificationUserFlag = 2;
                    getOtherUID();
                }
                else {

                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    /////////////////////////////////////////????????? UID ????????????////////////////////////////////////////
    private void getOtherUID(){
        if(classificationUserFlag == 1) { //?????? ???????????????, ???????????? ???????????? ??????
            Query query = reference.child("connect").child("guardian").orderByChild("myCode").equalTo(myConnect.getCounterpartyCode());
            query.addListenerForSingleValueEvent(new ValueEventListener() { //????????? ????????? ????????? uid ????????????
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {

                    for(DataSnapshot ds : dataSnapshot.getChildren()){
                        counterpartyUID = ds.getKey();
                    }

                    if(counterpartyUID != null  && !counterpartyUID.isEmpty()){
                        counterpartyMarker();
                    }
                    else {
                        Toast.makeText(RealTimeLocationActivity.this, "??????", Toast.LENGTH_SHORT).show();
                        Log.w(TAG, "????????? ???????????? ?????? ??????");
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            });
        }
        else if(classificationUserFlag == 2) { //?????? ????????????, ???????????? ???????????? ??????
            Query query = reference.child("connect").child("disabled").orderByChild("myCode").equalTo(myConnect.getCounterpartyCode());
            query.addListenerForSingleValueEvent(new ValueEventListener() { //????????? ????????? ????????? uid ????????????
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    for(DataSnapshot ds : dataSnapshot.getChildren()){
                        counterpartyUID = ds.getKey();
                    }

                    if(counterpartyUID != null  && !counterpartyUID.isEmpty()){
                        counterpartyMarker();
                    }
                    else {
                        Toast.makeText(RealTimeLocationActivity.this, "??????", Toast.LENGTH_SHORT).show();
                        Log.w(TAG, "????????? ???????????? ?????? ??????");
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            });
        }
        else { //???????????? ?????? ?????????
            Log.w(TAG, "????????? ???????????? ?????? ??????");
        }
    }

    /////////////////////////////////////////????????? ?????? ??????////////////////////////////////////////
    private void counterpartyMarker() {
        reference.child("realtimelocation").orderByKey().equalTo(counterpartyUID). //????????? ????????? ?????? ??????
                addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                RealTimeLocation realTimeLocation = new RealTimeLocation();
                for(DataSnapshot ds : snapshot.getChildren()){
                    realTimeLocation = ds.getValue(RealTimeLocation.class);
                }
                if (!snapshot.exists()) {
                    Log.w(TAG, "????????? ????????? ?????? ??????");
                }
                else {
                    counterpartyCurPoint = new LatLng(realTimeLocation.latitude, realTimeLocation.longitude);
                    return;
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });

        if(counterpartyCurPoint != null){
            if (counterpartyLocationMarker == null) { //????????? ????????? ??????
                counterpartyLocationMarker = new MarkerOptions();
                counterpartyLocationMarker.position(counterpartyCurPoint);

                int height = 300;
                int width = 300;
                BitmapDrawable bitmapdraw=(BitmapDrawable)getResources().getDrawable((R.drawable.other_gps));
                Bitmap b=bitmapdraw.getBitmap();
                Bitmap smallMarker = Bitmap.createScaledBitmap(b, width, height, false); //?????? ????????????

                counterpartyLocationMarker.icon(BitmapDescriptorFactory.fromBitmap(smallMarker));
                Log.w(TAG, "??????");
                counterpartyMarker = map.addMarker(counterpartyLocationMarker);
            }
            else if(counterpartyLocationMarker != null){ //????????? ???????????? ??????
                counterpartyMarker.remove(); // ????????????
                counterpartyLocationMarker.position(counterpartyCurPoint);
                Log.w(TAG, "??????");
                counterpartyMarker = map.addMarker(counterpartyLocationMarker);
            }

            if(classificationUserFlag == 2){ //???????????? ?????? //////////////////////////////????????? ?????? ??????????????? ???????????????
                reference.child("disabled").orderByKey().equalTo(counterpartyUID). //????????? ?????? ????????????
                        addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Disabled disabled = new Disabled();
                        for(DataSnapshot ds : snapshot.getChildren()){
                            disabled = ds.getValue(Disabled.class);
                        }
                        if (disabled.getName()!= null && !disabled.getName().isEmpty()) {
                            counterpartyName = disabled.getName();
                            departureArrivalNotification(); //????????? ????????? ??????
                            trackingStatusCheck(); //???????????? ??????
                            inOutCheck(); // ???????????? ??????
                        }
                        else {
                            Log.w(TAG, "????????? ?????? ???????????? ??????");
                            return;
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {

                    }
                });
            }
        }
    }

    /////////////////////////////////////////????????? ??? ??????&?????? ??????////////////////////////////////////////
    public void departureArrivalNotification(){
        reference.child("disabled").child(counterpartyUID).orderByKey().equalTo("address"). //????????? ??? ?????? ????????????
                addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String address = null;
                for(DataSnapshot ds : snapshot.getChildren()){
                    address = ds.getValue().toString();
                }
                if (!snapshot.exists()) {
                    Log.w(TAG, "????????? ??? ?????? ??????");
                }
                else { //????????? ??? ?????? ????????????
                    String finalAddress = address.substring(7);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            geoCoding(finalAddress);
                        }
                    }).start();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void geoCoding(String address) {
        try{
            BufferedReader bufferedReader;
            StringBuilder stringBuilder = new StringBuilder();

            String query = "https://naveropenapi.apigw.ntruss.com/map-geocode/v2/geocode?query=" + URLEncoder.encode(address, "UTF-8");
            URL url = new URL(query);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            if(conn != null) {
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("X-NCP-APIGW-API-KEY-ID", BuildConfig.CLIENT_ID);
                conn.setRequestProperty("X-NCP-APIGW-API-KEY", BuildConfig.CLIENT_SECRET);
                conn.setDoInput(true);

                int responseCode = conn.getResponseCode();

                if (responseCode == 200) {
                    bufferedReader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                } else {
                    bufferedReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                }

                String line = null;
                while ((line = bufferedReader.readLine()) != null) {
                    stringBuilder.append(line + "\n");
                }

                int indexFirst;
                int indexLast;

                indexFirst = stringBuilder.indexOf("\"x\":\"");
                indexLast = stringBuilder.indexOf("\",\"y\":");
                disabledAddressLongitude = Double.parseDouble(stringBuilder.substring(indexFirst + 5, indexLast));

                indexFirst = stringBuilder.indexOf("\"y\":\"");
                indexLast = stringBuilder.indexOf("\",\"distance\":");
                disabledAddressLatitude = Double.parseDouble(stringBuilder.substring(indexFirst + 5, indexLast));

                bufferedReader.close();
                conn.disconnect();
        }
    } catch (ProtocolException e) {
            e.printStackTrace();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        departureArrivalCheck(); //????????? ??????
    }

    public void departureArrivalCheck(){ //?????? ?????? ?????? ??? ??????
        Query query = reference.child("departurearrivalstatus").orderByKey().equalTo(counterpartyUID);
        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                DepartureArrivalStatus departureArrivalStatus = new DepartureArrivalStatus();
                for(DataSnapshot ds : dataSnapshot.getChildren()){
                    departureArrivalStatus = ds.getValue(DepartureArrivalStatus.class);
                }

                if(departureArrivalStatus != null){
                    departureFlag = departureArrivalStatus.departureStatus;
                    arrivalFlag = departureArrivalStatus.arrivalStatus; //??? ????????????
                }
                else { //?????? ??????

                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

        //??????(longitude)??? X, ??????(latitude)??? Y
        distance = Math.sqrt(((counterpartyCurPoint.longitude-disabledAddressLongitude)*(counterpartyCurPoint.longitude-disabledAddressLongitude))+((counterpartyCurPoint.latitude-disabledAddressLatitude)*(counterpartyCurPoint.latitude-disabledAddressLatitude)));

        if(disabledAddressLatitude != 0.0 && disabledAddressLongitude != 0.0){
            if(!departureFlag){ //?????? ?????? ????????? ??????
                if(distance*1000 > DEFAULTDISTANCE) { //1000????????? ????????? ?????????
                    if(counterpartyCurPoint.longitude != -122.0840064 && counterpartyCurPoint.latitude != 37.4219965){
                        departureNotification(DEFAULT, 1); //?????? ?????? ?????????
                        DepartureArrivalStatus departureArrivalStatus = new DepartureArrivalStatus(true, false); //?????? true, ?????? ????????? ?????????
                        reference.child("departurearrivalstatus").child(counterpartyUID).setValue(departureArrivalStatus); //????????? ????????? ?????????
                    }
                }
            }

            if(!arrivalFlag){ //?????? ??????????????? ??????
                if(departureFlag){ //?????????
                    if(distance*1000 < DEFAULTDISTANCE) {
                        if(counterpartyCurPoint.longitude != -122.0840064 && counterpartyCurPoint.latitude != 37.4219965){
                            arrivalNotification(DEFAULT, 2); //?????? ?????? ?????????
                            DepartureArrivalStatus departureArrivalStatus = new DepartureArrivalStatus(false, true); //?????? true, ?????? ????????? ?????????
                            reference.child("departurearrivalstatus").child(counterpartyUID).setValue(departureArrivalStatus); //????????? ????????? ?????????
                        }
                    }
                }
            }
        }
    }
    
    /////////////////////////////////////////????????? ???????????? ??????////////////////////////////////////////
    private void trackingStatusCheck() {
        Query disabledQuery = reference.child("trackingstatus").orderByKey().equalTo(counterpartyUID); //???????????? ?????? ??????
        disabledQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                TrackingStatus trackingStatus = new TrackingStatus();
                for(DataSnapshot ds : dataSnapshot.getChildren()){
                    trackingStatus = ds.getValue(TrackingStatus.class);
                }

                if(!trackingStatus.getStatus()){ //?????? ?????? ??????
                    if(permissionFlag == 0){
                        trackingImpossibleNotification(DEFAULT, 3);
                        permissionFlag = 1;
                    }
                }
                else { //?????? ??????
                    if(permissionFlag == 1){
                        trackingPossibleNotification(DEFAULT, 4);
                        permissionFlag = 0;
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }
    public void inOutCheck(){ //?????? ?????? ?????? ??? ??????
        Query query = reference.child("inoutstatus").orderByKey().equalTo(counterpartyUID);
        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                InOutStatus inOutStatus = new InOutStatus();
                for(DataSnapshot ds : dataSnapshot.getChildren()){
                    inOutStatus = ds.getValue(InOutStatus.class);
                }

                if(inOutStatus != null){
                    outFlag = inOutStatus.outStatus;
                    inFlag = inOutStatus.inStatus; //??? ????????????
                }
                else { //?????? ??????

                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
        alertNotification();
    }
    ////?????????????????? ???????????? ?????? ?????? ???????????? ????????? ?????????
    private void alertNotification(){
        if(reference.child("range").child(user.getUid()).orderByKey().equalTo("????????????") != null){
            reference.child("range").child(user.getUid()).orderByKey().equalTo("????????????"). //????????? ??? ?????? ????????????
                    addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Range myRangeP = new Range();
                    for (DataSnapshot ds : snapshot.getChildren()) {
                        myRangeP = ds.getValue(Range.class);
                    }
                    if (!snapshot.exists()) {
                        Log.w(TAG, "???????????? ???????????? ??????");
                    }
                    else { //????????? ??? ?????? ????????????
                        double sDis = myRangeP.distance;
                        //??????(longitude)??? X, ??????(latitude)??? Y
                        //double nDis = Math.sqrt(((counterpartyCurPoint.longitude-myRangeP.longitude)*(counterpartyCurPoint.longitude-myRangeP.longitude))+((counterpartyCurPoint.latitude-myRangeP.latitude)*(counterpartyCurPoint.latitude-myRangeP.latitude)));
                        double nDis = cDistance(counterpartyCurPoint.latitude, counterpartyCurPoint.longitude, myRangeP.latitude, myRangeP.longitude);
                        Log.w(TAG, "????????????: " + nDis + "????????????: "+sDis);

                        if(myRangeP.latitude != 0.0 && myRangeP.longitude != 0.0){
                            if(!outFlag) { //?????? ?????? ????????? ??????
                                if (nDis > sDis) { //1000????????? ????????? ?????????
                                    outNotification(DEFAULT, 3);//?????? ?????? ?????????
                                    InOutStatus inOutStatus = new InOutStatus(true, false); //?????? true, ?????? ????????? ?????????
                                    reference.child("inoutstatus").child(counterpartyUID).setValue(inOutStatus); //???????????? ????????? ?????????
                                }
                            }
                            if(outFlag){ //?????? ??? ????????? ??????
                                if(outFlag){ //?????????
                                    if(nDis < sDis) {
                                        inNotification(DEFAULT, 4); //?????? ?????? ?????????
                                        InOutStatus inOutStatus = new InOutStatus(false, true); //?????? true, ?????? ????????? ?????????
                                        reference.child("inoutstatus").child(counterpartyUID).setValue(inOutStatus); //???????????? ????????? ?????????
                                    }
                                }
                            }
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {

                }
            });
        }
    }
    
    /////////////////////////////////////////??????////////////////////////////////////////
    public void createNotificationChannel(String channelId, String channelName, int importance) { //?????? ?????????
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(new NotificationChannel(channelId, channelName, importance));
        }
    }

    public void departureNotification(String channelId, int id) { //?????? ??????
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(R.drawable.ic_stat_polaris_smallicon) //?????? ?????????
                .setContentTitle("?????????")
                .setContentText(counterpartyName + "?????? ????????? ?????????????????????.")
                .setContentIntent(pendingIntent)    // ????????? ????????? PendingIntent??? ????????????
                .setAutoCancel(true)                // true?????? ????????? ????????? ????????????
                //.setTimeoutAfter(1000)
                //.setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE);

        NotificationManager notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(id, builder.build());
    }

    public void arrivalNotification(String channelId, int id) { //?????? ??????
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(R.drawable.ic_stat_polaris_smallicon) //?????? ?????????
                .setContentTitle("?????????")
                .setContentText(counterpartyName + "?????? ????????? ?????????????????????.")
                .setContentIntent(pendingIntent)    // ????????? ????????? PendingIntent??? ????????????
                .setAutoCancel(true)                // true?????? ????????? ????????? ????????????
                //.setTimeoutAfter(1000)
                //.setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE);

        NotificationManager notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(id, builder.build());
    }
    ////?????? ?????? ?????? ?????????
    private void outNotification(String channelId, int id) { //?????? ??????
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(R.drawable.ic_stat_polaris_smallicon) //?????? ?????????
                .setContentTitle("?????????")
                .setContentText(counterpartyName + "?????? ??????????????? ??????????????????.")
                .setContentIntent(pendingIntent)    // ????????? ????????? PendingIntent??? ????????????
                .setAutoCancel(true)                // true?????? ????????? ????????? ????????????
                //.setTimeoutAfter(1000)
                //.setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE);

        NotificationManager notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(id, builder.build());
    }
    private void inNotification(String channelId, int id) { //????????? ??????
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(R.drawable.ic_stat_polaris_smallicon) //?????? ?????????
                .setContentTitle("?????????")
                .setContentText(counterpartyName + "?????? ??????????????? ??????????????????.")
                .setContentIntent(pendingIntent)    // ????????? ????????? PendingIntent??? ????????????
                .setAutoCancel(true)                // true?????? ????????? ????????? ????????????
                //.setTimeoutAfter(1000)
                //.setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE);

        NotificationManager notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(id, builder.build());
    }

    private void trackingImpossibleNotification(String channelId, int id){ //?????? ?????? ??????
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(R.drawable.ic_stat_polaris_smallicon) //?????? ?????????
                .setContentTitle("?????????")
                .setContentText(counterpartyName + "?????? ??????(GPS) ????????? ??????????????????.")
                .setContentIntent(pendingIntent)    // ????????? ????????? PendingIntent??? ????????????
                .setAutoCancel(true)                // true?????? ????????? ????????? ????????????
                //.setTimeoutAfter(1000)
                //.setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE);

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(id, builder.build());
    }

    private void trackingPossibleNotification(String channelId, int id){ //?????? ?????? ??????
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(R.drawable.ic_stat_polaris_smallicon) //?????? ?????????
                .setContentTitle("?????????")
                .setContentText(counterpartyName + "?????? ??????(GPS) ????????? ??????????????????.")
                .setContentIntent(pendingIntent)    // ????????? ????????? PendingIntent??? ????????????
                .setAutoCancel(true)                // true?????? ????????? ????????? ????????????
                //.setTimeoutAfter(1000)
                //.setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE);

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(id, builder.build());
    }

    /////////////////////////////////////////?????? ?????? ?????? ??????////////////////////////////////////////
    // ??? ?????? ????????? ?????? ?????? ??????
    private static double cDistance(double lat1, double lon1, double lat2, double lon2){
        double theta = lon1 - lon2;
        double dist = Math.sin(deg2rad(lat1))* Math.sin(deg2rad(lat2)) + Math.cos(deg2rad(lat1))*Math.cos(deg2rad(lat2))*Math.cos(deg2rad(theta));
        dist = Math.acos(dist);
        dist = rad2deg(dist);
        dist = dist * 60*1.1515*1609.344;

        return dist; //?????? meter
    }
    //10????????? radian(?????????)?????? ??????
    private static double deg2rad(double deg){
        return (deg * Math.PI/180.0);
    }
    //radian(?????????)??? 10????????? ??????
    private static double rad2deg(double rad){
        return (rad * 180 / Math.PI);
    }
}
