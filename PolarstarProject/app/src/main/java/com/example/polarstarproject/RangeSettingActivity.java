package com.example.polarstarproject;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.polarstarproject.Domain.Connect;
import com.example.polarstarproject.Domain.Range;
import com.example.polarstarproject.Domain.RealTimeLocation;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
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
import java.util.Timer;
import java.util.TimerTask;

public class RangeSettingActivity extends AppCompatActivity implements OnMapReadyCallback, View.OnClickListener {
    private FirebaseDatabase database = FirebaseDatabase.getInstance();
    private DatabaseReference reference = database.getReference();
    private FirebaseAuth mAuth;
    private FirebaseUser user; //firebase ??????

    private static final String TAG = "RangeSetting";
    private GoogleMap map;
    private CameraPosition cameraPosition;

    private FusedLocationProviderClient fusedLocationProviderClient;

    private final LatLng defaultLocation = new LatLng(37.56, 126.97);
    private static final int DEFAULT_ZOOM = 15;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    public boolean locationPermissionGranted;

    private Location lastKnownLocation;

    private static final String KEY_CAMERA_POSITION = "camera_position";
    private static final String KEY_LOCATION = "location";
    private static final int SEARCH_ADDRESS_ACTIVITY = 10000;

    Marker counterpartyMarker;
    MarkerOptions counterpartyLocationMarker;

    Circle counterpartyCir;
    CircleOptions cir;
    public int rad = 0;

    LatLng counterpartyCurPoint;
    public LatLng rPoint;

    LocationManager manager;

    Connect myConnect;
    String counterpartyUID = "";

    double disabledAddressLat, disabledAddressLng; //????????? ??? ?????? ?????? ??????

    TextView rName;
    TextView rangeAddress;
    Button btnSet, btnAdd;
    SeekBar seekBar;
    TextView tvDis;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rangesetting);

        rName = findViewById(R.id.rName);
        rName.setText("????????????");
        seekBar = findViewById(R.id.seekBar);
        tvDis = findViewById(R.id.tvDis);
        rangeAddress = findViewById(R.id.rangeAddress);
        btnSet = findViewById(R.id.btnSet);
        btnAdd = findViewById(R.id.btnAdd);

        btnSet.setOnClickListener(this);
        btnAdd.setOnClickListener(this);

        if (savedInstanceState != null) {
            lastKnownLocation = savedInstanceState.getParcelable(KEY_LOCATION);
            cameraPosition = savedInstanceState.getParcelable(KEY_CAMERA_POSITION);
        }

        MapsInitializer.initialize(this);

        mAuth = FirebaseAuth.getInstance();
        user = mAuth.getCurrentUser();

        manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        try {
            MapsInitializer.initialize(this);
        } catch (Exception e) {
            e.printStackTrace();
        }

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.gMap);
        mapFragment.getMapAsync(this);

        // ?????? ?????? ????????????
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                rad = seekBar.getProgress();
                tvDis.setText(String.format("%d M", rad));

                if(counterpartyCir != null){ //?????? ???????????? ??????
                    counterpartyCir.remove();
                }
                //?????? ???
                cir = new CircleOptions().center(counterpartyCurPoint) //??????
                        .radius(rad) //????????? ?????? = ??????
                        .strokeWidth(0f) //????????? 0f=?????????
                        .fillColor(Color.parseColor("#880000ff")); //?????????
                counterpartyCir = map.addCircle(cir);

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                reference.child("range").child(user.getUid()).child(rName.getText().toString()).child("distance").setValue(rad);

            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) { //?????? ???????????? ???, ????????????
        if (map != null) {
            outState.putParcelable(KEY_CAMERA_POSITION, map.getCameraPosition());
            outState.putParcelable(KEY_LOCATION, lastKnownLocation);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onMapReady(GoogleMap map) { //??? ?????? ???, map ??????
        this.map = map;

        getLocationPermission(); //?????? ?????? ??????
        classificationUser(user.getUid()); //????????? ????????? ?????? ?????????
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
            }
        }
        else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }


    /////////////////////////////////////////????????? ??????////////////////////////////////////////
    private void classificationUser(String uid){ //firebase select ?????? ??????, ??? connect ????????? ??????
        Query guardianQuery = reference.child("connect").child("guardian").orderByKey().equalTo(uid); //????????? ????????? ??????
        guardianQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                myConnect = new Connect();
                for(DataSnapshot ds : dataSnapshot.getChildren()){
                    myConnect = ds.getValue(Connect.class);
                }

                if(myConnect.getMyCode() != null && !myConnect.getMyCode().isEmpty()){
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
                    Toast.makeText(RangeSettingActivity.this, "??????", Toast.LENGTH_SHORT).show();
                    Log.w(TAG, "????????? ???????????? ?????? ??????");
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
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
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(counterpartyCurPoint, DEFAULT_ZOOM)); //?????? ????????? ????????? ??????
                    Log.w(TAG, "??? ????????? ?????? "+ counterpartyCurPoint);
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
                            counterpartyMarker = map.addMarker(counterpartyLocationMarker);
                            Log.w(TAG, "??? ?????? ?????? "+ counterpartyCurPoint);

                        }
                        else if(counterpartyLocationMarker != null){ //????????? ???????????? ??????
                            counterpartyMarker.remove(); // ????????????
                            counterpartyLocationMarker.position(counterpartyCurPoint);
                            counterpartyMarker = map.addMarker(counterpartyLocationMarker);
                        }
                    }
                    return;
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }
    ////////////////////////////////////????????? ???????????? ????????? ??????/////////////////////////////////////////////
    private void mapMarker() {
        if(reference.child("range").child(user.getUid()).orderByKey().equalTo(rName.getText().toString()) != null){
            reference.child("range").child(user.getUid()).orderByKey().equalTo(rName.getText().toString()). //??????????????? ??????
                    addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Range myRangeP = new Range();
                    for (DataSnapshot ds : snapshot.getChildren()) {
                        myRangeP = ds.getValue(Range.class);
                    }
                    if (!snapshot.exists()) {
                        Log.w(TAG, "????????? ????????? ?????? ??????");
                    } else {
                        counterpartyCurPoint = new LatLng(myRangeP.latitude, myRangeP.longitude);
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(counterpartyCurPoint, DEFAULT_ZOOM)); //?????? ????????? ????????? ??????
                        Log.w(TAG, "????????? ?????? " + counterpartyCurPoint);
                        if (counterpartyCurPoint != null) {
                            if (counterpartyLocationMarker == null) { //????????? ????????? ??????
                                counterpartyLocationMarker = new MarkerOptions();
                                counterpartyLocationMarker.position(counterpartyCurPoint);

                                int height = 300;
                                int width = 300;
                                BitmapDrawable bitmapdraw = (BitmapDrawable) getResources().getDrawable((R.drawable.other_gps));
                                Bitmap b = bitmapdraw.getBitmap();
                                Bitmap smallMarker = Bitmap.createScaledBitmap(b, width, height, false); //?????? ????????????

                                counterpartyLocationMarker.icon(BitmapDescriptorFactory.fromBitmap(smallMarker));
                                counterpartyMarker = map.addMarker(counterpartyLocationMarker);
                                Log.w(TAG, "??? ?????? ?????? " + counterpartyCurPoint);

                            } else if (counterpartyLocationMarker != null) { //????????? ???????????? ??????
                                counterpartyMarker.remove(); // ????????????
                                counterpartyLocationMarker.position(counterpartyCurPoint);
                                counterpartyMarker = map.addMarker(counterpartyLocationMarker);

                                //cir.radius(0);
                                if(counterpartyCir != null){ //?????? ???????????? ??????
                                    counterpartyCir.remove();
                                }
                                cir = new CircleOptions().center(counterpartyCurPoint) //??????
                                        .radius(myRangeP.getDis()) //????????? ?????? = ??????
                                        .strokeWidth(0f) //????????? 0f=?????????
                                        .fillColor(Color.parseColor("#880000ff")); //?????????
                                counterpartyCir = map.addCircle(cir);
                            }
                        }
                        return;
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {

                }
            });
        }
    }
    /////////////////////////////////////////////////////////////////////////////////////


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if(requestCode == SEARCH_ADDRESS_ACTIVITY) { //????????????
            if (resultCode == RESULT_OK) {
                String data = intent.getExtras().getString("data");
                if(data != null) {
                    rangeAddress.setText(data);
                    new Thread(() -> {
                        geoC(data.substring(7));
                        //????????? ????????????????????? ????????????
                        Range myRange = new Range(disabledAddressLat, disabledAddressLng, rad);
                        Log.w(TAG, "??????: "+ disabledAddressLat+disabledAddressLng+rad);
                        reference.child("range").child(user.getUid()).child(rName.getText().toString()).setValue(myRange);
                        // ??? ????????? ?????? ????????? ???????????????
                        mapMarker();
                    }).start();
                }
            }
        }
    }

    public void geoC(String address) { //????????? ?????? ????????? ?????????
        try {
            BufferedReader bufferedReader;
            StringBuilder stringBuilder = new StringBuilder();

            String query = "https://naveropenapi.apigw.ntruss.com/map-geocode/v2/geocode?query=" + URLEncoder.encode(address, "UTF-8");
            URL url = new URL(query);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            if (conn != null) {
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
                disabledAddressLng = Double.parseDouble(stringBuilder.substring(indexFirst + 5, indexLast));

                indexFirst = stringBuilder.indexOf("\"y\":\"");
                indexLast = stringBuilder.indexOf("\",\"distance\":");
                disabledAddressLat = Double.parseDouble(stringBuilder.substring(indexFirst + 5, indexLast));

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
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnAdd: //???????????? ??????
                Intent i = new Intent(RangeSettingActivity.this, WebViewActivity.class);
                startActivityForResult(i, SEARCH_ADDRESS_ACTIVITY);
                break;

            case R.id.btnSet:
                Range myRange = new Range(disabledAddressLat, disabledAddressLng, rad);
                reference.child("range").child(user.getUid()).child(rName.getText().toString()).setValue(myRange);

                break;
        }

    }
}
