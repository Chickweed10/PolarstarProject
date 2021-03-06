package com.example.polarstarproject;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.polarstarproject.Domain.Connect;
import com.example.polarstarproject.Domain.DepartureArrivalStatus;
import com.example.polarstarproject.Domain.Disabled;
import com.example.polarstarproject.Domain.RealTimeLocation;
import com.example.polarstarproject.Domain.TrackingStatus;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
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

public class LocationService extends Service {
    private static final String TAG = "LocationService";

    private FirebaseDatabase database = FirebaseDatabase.getInstance();
    private DatabaseReference reference = database.getReference();
    private FirebaseAuth mAuth;
    private FirebaseUser user; //firebase ??????

    int classificationUserFlag = 0; //????????? ?????? ?????????
    String counterpartyUID; //????????? UID
    LatLng counterpartyCurPoint; //????????? ????????? ??????

    Connect myConnect;

    public double distance; //??????
    private final double DEFAULTDISTANCE= 1; //????????? ?????? ??????
    private final String DEFAULT = "DEFAULT";
    double disabledAddressLongitude, disabledAddressLatitude; //????????? ??? ??????
    boolean departureFlag, arrivalFlag = false; //????????? ?????????
    String counterpartyName;

    RealTimeLocationActivity realTimeLocationActivity;

    Intent notificationIntent;

    public LocationService(){
        realTimeLocationActivity = new RealTimeLocationActivity();
    }

    private LocationCallback mLocationCallback = new LocationCallback() {
        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void onLocationResult(LocationResult locationResult) {
            super.onLocationResult(locationResult);

            if (locationResult != null && locationResult.getLastLocation() != null) {
                double latitude = locationResult.getLastLocation().getLatitude();
                double longitude = locationResult.getLastLocation().getLongitude();
                Log.v(TAG, latitude + ", " + longitude);

                mAuth = FirebaseAuth.getInstance();
                user = mAuth.getCurrentUser();
                realTimeLocationActivity.realTimeDeviceLocationBackground(user, latitude, longitude); //firebase??? ????????? ?????? ????????????

                classificationUserBackground(); //????????? ??????

                if(classificationUserFlag == 1){
                    realTimeLocationActivity.firebaseUpdateRoute(user, latitude, longitude); //firebase??? ?????? ??????
                }
                else if(classificationUserFlag == 2){      // ????????? ????????? activity??? ??????
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
                                if(counterpartyCurPoint != null){
                                    departureArrivalNotification(); //????????? ??????
                                }
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
    };

    /////////////////////////////////////////????????? ??????////////////////////////////////////////
    private void classificationUserBackground(){
        Query disabledQuery = reference.child("connect").child("disabled").orderByKey().equalTo(user.getUid()); //????????? ????????? ??????
        disabledQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                myConnect = new Connect();
                for(DataSnapshot ds : dataSnapshot.getChildren()){
                    myConnect = ds.getValue(Connect.class);
                }

                if(myConnect.getMyCode() != null && !myConnect.getMyCode().isEmpty()){
                    classificationUserFlag = 1;
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

        Query guardianQuery = reference.child("connect").child("guardian").orderByKey().equalTo(user.getUid()); //????????? ????????? ??????
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
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    /////////////////////////////////////////????????? UID ????????????////////////////////////////////////////
    private void getOtherUID(){
        Query query = reference.child("connect").child("disabled").orderByChild("myCode").equalTo(myConnect.getCounterpartyCode());
        query.addListenerForSingleValueEvent(new ValueEventListener() { //????????? ????????? ????????? uid ????????????
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for(DataSnapshot ds : dataSnapshot.getChildren()){
                    counterpartyUID = ds.getKey();
                }

                if(counterpartyUID != null  && !counterpartyUID.isEmpty()){
                    counterpartyLocation();
                }
                else {
                    Log.w(TAG, "????????? ???????????? ?????? ??????");
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    /////////////////////////////////////////????????? ????????? ??????////////////////////////////////////////
    private void counterpartyLocation() {
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
                if(distance*1000 > DEFAULTDISTANCE) {
                    if(counterpartyCurPoint.longitude != -122.0840064 && counterpartyCurPoint.latitude != 37.4219965) {
                        departureNotification(DEFAULT, 1); //?????? ?????? ?????????
                        DepartureArrivalStatus departureArrivalStatus = new DepartureArrivalStatus(true, false); //?????? true, ?????? ????????? ?????????
                        reference.child("departurearrivalstatus").child(counterpartyUID).setValue(departureArrivalStatus); //????????? ????????? ?????????
                    }
                }
            }

            if(!arrivalFlag){ //?????? ??????????????? ??????
                if(departureFlag){ //?????????
                    if(distance*1000 < DEFAULTDISTANCE) {
                        if(counterpartyCurPoint.longitude != -122.0840064 && counterpartyCurPoint.latitude != 37.4219965) {
                            arrivalNotification(DEFAULT, 2); //?????? ?????? ?????????
                            DepartureArrivalStatus departureArrivalStatus = new DepartureArrivalStatus(false, true); //?????? true, ?????? ????????? ?????????
                            reference.child("departurearrivalstatus").child(counterpartyUID).setValue(departureArrivalStatus); //????????? ????????? ?????????
                        }
                    }
                }
            }
        }
    }

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

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @SuppressLint("MissingPermission")
    private void startLocationService() {
        String channelId = "location_notification_channel";
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Intent resultIntent = new Intent();
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), channelId);
        builder.setSmallIcon(R.drawable.ic_stat_polaris_smallicon);
        builder.setContentTitle("?????? ?????????");
        builder.setDefaults(NotificationCompat.DEFAULT_ALL);
        builder.setContentText("?????????");
        builder.setContentIntent(pendingIntent);
        builder.setAutoCancel(false);
        builder.setPriority(NotificationCompat.PRIORITY_MAX);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager != null && notificationManager.getNotificationChannel(channelId) == null) {
                NotificationChannel notificationChannel = new NotificationChannel(channelId, "Location Service", NotificationManager.IMPORTANCE_HIGH);
                notificationChannel.setDescription("This channel is used by location service");
                notificationManager.createNotificationChannel(notificationChannel);
            }
        }

        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(4000);
        locationRequest.setFastestInterval(2000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        LocationServices.getFusedLocationProviderClient(this).requestLocationUpdates(locationRequest, mLocationCallback, Looper.getMainLooper());
        startForeground(Constants.LOCATION_SERVICE_ID, builder.build());
    }

    private void stopLocationService() {
        LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(mLocationCallback);
        stopForeground(true);
        stopSelf();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel(DEFAULT, "default channel", NotificationManager.IMPORTANCE_HIGH); //?????? ?????????
        notificationIntent = new Intent(this, RealTimeLocationActivity.class); // ????????? ????????? activity??? ??????
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        if (intent != null) {
            String action = intent.getAction();
            if (action != null) {
                if (action.equals(Constants.ACTION_START_LOCATION_SERVICE)) {
                    startLocationService();
                } else if (action.equals(Constants.ACTION_STOP_LOCATION_SERVICE)) {
                    stopLocationService();
                }
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }
}