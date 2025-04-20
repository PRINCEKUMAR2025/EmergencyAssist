package com.example.emergencyassist;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Camera;
import android.location.Location;
import android.location.LocationListener;
import android.os.PersistableBundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;
;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.squareup.picasso.Picasso;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

import static android.app.PendingIntent.getActivity;

public class DriverMapActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        com.google.android.gms.location.LocationListener {

    private GoogleMap mMap;
    GoogleApiClient googleApiClient;
    LocationRequest locationRequest;

    private Button LogoutDriverBtn;
    private Button SettingsDriverButton;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private Boolean currentLogOutUserStatus = false;

    //getting request customer's id
    private String customerID = "";
    private String driverID;
    private DatabaseReference AssignedCustomerRef;
    private DatabaseReference AssignedCustomerPickUpRef;
    Marker PickUpMarker;

    private ValueEventListener AssignedCustomerPickUpRefListner;

    private TextView txtName, txtPhone;
    private CircleImageView profilePic;
    private RelativeLayout relativeLayout;
    Location lastLocation;



    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        //notice
        setContentView(R.layout.activity_driver_map);


        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        driverID = mAuth.getCurrentUser().getUid();


        LogoutDriverBtn = (Button) findViewById(R.id.logout_driv_btn);
        SettingsDriverButton = (Button) findViewById(R.id.settings_driver_btn);

        txtName = findViewById(R.id.name_customer);
        txtPhone = findViewById(R.id.phone_customer);
        profilePic = findViewById(R.id.profile_image_customer);
        relativeLayout = findViewById(R.id.rel2);


        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(DriverMapActivity.this);




        SettingsDriverButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view)
            {
                Intent intent = new Intent(DriverMapActivity.this, SettingsActivity.class);
                intent.putExtra("type", "Drivers");
                startActivity(intent);
            }
        });

        LogoutDriverBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                currentLogOutUserStatus = true;
                DisconnectDriver();

                mAuth.signOut();

                LogOutUser();
            }
        });
        getAssignedCustomersRequest();
    }

    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Check and request location permissions
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        buildGoogleApiClient();
        mMap.setMyLocationEnabled(true);
    }

    protected synchronized void buildGoogleApiClient() {
        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        googleApiClient.connect();
    }




    private void getAssignedCustomersRequest() {
        AssignedCustomerRef = FirebaseDatabase.getInstance().getReference().child("Users")
                .child("Drivers").child(driverID).child("CustomerRideID");

        AssignedCustomerRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    customerID = dataSnapshot.getValue().toString();
                    Log.d("DriverAssignment", "Driver assigned to Customer ID: " + customerID);

                    if (lastLocation != null) {  // Ensure lastLocation is not null
                        DatabaseReference driversWorkingRef = FirebaseDatabase.getInstance().getReference().child("Drivers Working").child(driverID);
                        DatabaseReference driversOccupiedRef = FirebaseDatabase.getInstance().getReference().child("Drivers Occupied").child(driverID);

                        // 1. Remove driver from "Drivers Working" (GeoFire + Database)
                        GeoFire geoFireWorking = new GeoFire(FirebaseDatabase.getInstance().getReference().child("Drivers Working"));
                        geoFireWorking.removeLocation(driverID, (key, error) -> {
                            if (error == null) {
                                Log.d("GeoFire", "Successfully removed driver from Drivers Working");

                                // Now remove from Firebase Database
                                driversWorkingRef.removeValue().addOnCompleteListener(task -> {
                                    if (task.isSuccessful()) {
                                        Log.d("FirebaseUpdate", "Driver successfully removed from Drivers Working");

                                        // 2. Add driver to "Drivers Occupied"
                                        GeoFire geoFireOccupied = new GeoFire(FirebaseDatabase.getInstance().getReference().child("Drivers Occupied"));
                                        geoFireOccupied.setLocation(driverID, new GeoLocation(lastLocation.getLatitude(), lastLocation.getLongitude()));

                                        Map<String, Object> driverData = new HashMap<>();
                                        driverData.put("g", "someGeoHash"); // Replace with actual geo hash
                                        driverData.put("l", Arrays.asList(lastLocation.getLatitude(), lastLocation.getLongitude()));

                                        driversOccupiedRef.setValue(driverData).addOnCompleteListener(task1 -> {
                                            if (task1.isSuccessful()) {
                                                Log.d("FirebaseUpdate", "Driver moved to Drivers Occupied");
                                            } else {
                                                Log.e("FirebaseError", "Failed to add driver to Drivers Occupied");
                                            }
                                        });

                                    } else {
                                        Log.e("FirebaseError", "Failed to remove driver from Drivers Working");
                                    }
                                });

                            } else {
                                Log.e("GeoFireError", "Failed to remove location from GeoFire: " + error.getMessage());
                            }
                        });

                    } else {
                        Toast.makeText(DriverMapActivity.this, "Location not available!", Toast.LENGTH_SHORT).show();
                    }

                    // Show customer details
                    relativeLayout.setVisibility(View.VISIBLE);
                    getAssignedCustomerInformation();
                    GetAssignedCustomerPickupLocation();

                } else {
                    // No customer assigned - reset driver to "Drivers Working"
                    customerID = "";
                    Log.d("DriverAssignment", "No customer assigned. Resetting driver...");

                    if (PickUpMarker != null) {
                        PickUpMarker.remove();
                    }

                    if (AssignedCustomerPickUpRefListner != null) {
                        AssignedCustomerPickUpRef.removeEventListener(AssignedCustomerPickUpRefListner);
                    }

                    relativeLayout.setVisibility(View.GONE);

                    if (lastLocation != null) {
                        DatabaseReference driversAvailableRef = FirebaseDatabase.getInstance().getReference().child("Drivers Working").child(driverID);
                        DatabaseReference driversOccupiedRef = FirebaseDatabase.getInstance().getReference().child("Drivers Occupied").child(driverID);

                        // Move driver back to "Drivers Working"
                        GeoFire geoFireAvailable = new GeoFire(FirebaseDatabase.getInstance().getReference().child("Drivers Working"));
                        geoFireAvailable.setLocation(driverID, new GeoLocation(lastLocation.getLatitude(), lastLocation.getLongitude()));

                        // Remove driver from "Drivers Occupied"
                        GeoFire geoFireOccupied = new GeoFire(FirebaseDatabase.getInstance().getReference().child("Drivers Occupied"));
                        geoFireOccupied.removeLocation(driverID, (key, error) -> {
                            if (error == null) {
                                Log.d("GeoFire", "Successfully removed driver from Drivers Occupied");

                                driversOccupiedRef.removeValue().addOnCompleteListener(task -> {
                                    if (task.isSuccessful()) {
                                        Log.d("FirebaseUpdate", "Driver removed from Drivers Occupied");
                                    } else {
                                        Log.e("FirebaseError", "Failed to remove driver from Drivers Occupied");
                                    }
                                });

                            } else {
                                Log.e("GeoFireError", "Failed to remove location from GeoFire: " + error.getMessage());
                            }
                        });
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("FirebaseError", "Database operation cancelled: " + databaseError.getMessage());
            }
        });
    }



    private void GetAssignedCustomerPickupLocation()
    {
        AssignedCustomerPickUpRef = FirebaseDatabase.getInstance().getReference().child("Customer Requests")
                .child(customerID).child("l");

        AssignedCustomerPickUpRefListner = AssignedCustomerPickUpRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot)
            {
                if(dataSnapshot.exists())
                {
                    List<Object> customerLocationMap = (List<Object>) dataSnapshot.getValue();
                    double LocationLat = 0;
                    double LocationLng = 0;

                    if(customerLocationMap.get(0) != null)
                    {
                        LocationLat = Double.parseDouble(customerLocationMap.get(0).toString());
                    }
                    if(customerLocationMap.get(1) != null)
                    {
                        LocationLng = Double.parseDouble(customerLocationMap.get(1).toString());
                    }

                    LatLng DriverLatLng = new LatLng(LocationLat, LocationLng);
                    PickUpMarker = mMap.addMarker(new MarkerOptions().position(DriverLatLng).title("Customer PickUp Location").icon(BitmapDescriptorFactory.fromResource(R.drawable.user)));
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState, @Nullable PersistableBundle persistentState)
    {
        super.onCreate(savedInstanceState, persistentState);


    }

    @Override
    public void onLocationChanged(Location location) {
        lastLocation = location;

        // Move camera to the new location and set zoom
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        mMap.animateCamera(CameraUpdateFactory.zoomTo(12));

        // Update the driver's location in Firebase
        DatabaseReference driversAvailableRef = FirebaseDatabase.getInstance().getReference().child("Drivers Available");
        GeoFire geoFireAvailable = new GeoFire(driversAvailableRef);

        DatabaseReference driversWorkingRef = FirebaseDatabase.getInstance().getReference().child("Drivers Working");
        GeoFire geoFireWorking = new GeoFire(driversWorkingRef);

        if (driverID != null) {
            geoFireWorking.setLocation(driverID, new GeoLocation(location.getLatitude(), location.getLongitude()));
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        locationRequest = new LocationRequest();
        locationRequest.setInterval(1000); // 1 second
        locationRequest.setFastestInterval(1000); // 1 second
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }


    //create this method -- for useing apis


    @Override
    protected void onStop()
    {
        super.onStop();

        if(!currentLogOutUserStatus)
        {
            DisconnectDriver();
        }
    }


    private void DisconnectDriver() {
        String userID = FirebaseAuth.getInstance().getCurrentUser().getUid();

        DatabaseReference driversAvailableRef = FirebaseDatabase.getInstance().getReference().child("Drivers Working");
        GeoFire geoFireAvailable = new GeoFire(driversAvailableRef);
        geoFireAvailable.removeLocation(userID);

        DatabaseReference driversWorkingRef = FirebaseDatabase.getInstance().getReference().child("Drivers Occupied");
        GeoFire geoFireWorking = new GeoFire(driversWorkingRef);
        geoFireWorking.removeLocation(userID);
    }




    public void LogOutUser()
    {
        currentLogOutUserStatus = true;
        DisconnectDriver();

        mAuth.signOut();
        Intent startPageIntent = new Intent(DriverMapActivity.this, WelcomeActivity.class);
        startPageIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(startPageIntent);
        finish();
    }




    private void getAssignedCustomerInformation()
    {
        DatabaseReference reference = FirebaseDatabase.getInstance().getReference()
                .child("Users").child("Customers").child(customerID);

        reference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot)
            {
                if (dataSnapshot.exists()  &&  dataSnapshot.getChildrenCount() > 0)
                {
                    String name = dataSnapshot.child("name").getValue().toString();
                    String phone = dataSnapshot.child("phone").getValue().toString();

                    txtName.setText(name);
                    txtPhone.setText(phone);

                    if (dataSnapshot.hasChild("image"))
                    {
                        String image = dataSnapshot.child("image").getValue().toString();
                        Picasso.get().load(image).into(profilePic);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }
}
