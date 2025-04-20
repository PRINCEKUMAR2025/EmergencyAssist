package com.example.emergencyassist;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;

import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

public class CustomersMapActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        com.google.android.gms.location.LocationListener {

    private GoogleMap mMap;
    private GoogleApiClient googleApiClient;
    private Location LastLocation;
    private LocationRequest locationRequest;

    private Button logoutButton, settingsButton, callCabButton;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private DatabaseReference customerDatabaseRef, driverAvailableRef, driverLocationRef;
    private LatLng customerPickUpLocation;
    private DatabaseReference driversRef;
    private int radius = 1;

    private Boolean driverFound = false, requestType = false;
    private String driverFoundID;
    private String customerID;
    private Marker driverMarker, pickUpMarker;
    private GeoQuery geoQuery;

    private ValueEventListener driverLocationRefListener;

    private TextView txtName, txtPhone, txtCarName;
    private CircleImageView profilePic;
    private RelativeLayout relativeLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customers_map);

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        customerID = FirebaseAuth.getInstance().getCurrentUser().getUid();
        customerDatabaseRef = FirebaseDatabase.getInstance().getReference().child("Customer Requests");
        driverAvailableRef = FirebaseDatabase.getInstance().getReference().child("Customers Available");
        driverLocationRef = FirebaseDatabase.getInstance().getReference().child("Drivers Working");

        logoutButton = findViewById(R.id.logout_customer_btn);
        settingsButton = findViewById(R.id.settings_customer_btn);
        callCabButton = findViewById(R.id.call_a_car_button);

        txtName = findViewById(R.id.name_driver);
        txtPhone = findViewById(R.id.phone_driver);
        txtCarName = findViewById(R.id.car_name_driver);
        profilePic = findViewById(R.id.profile_image_driver);
        relativeLayout = findViewById(R.id.rel1);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION},
                    1);
        }

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(CustomersMapActivity.this, SettingsActivity.class);
                intent.putExtra("type", "Customers");
                startActivity(intent);
            }
        });

        logoutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mAuth.signOut();
                LogOutUser();
            }
        });

        callCabButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (requestType) {
                    // End the ride request
                    requestType = false;
                    geoQuery.removeAllListeners();  // Remove the GeoQuery listener for driver search
                    driverLocationRef.removeEventListener(driverLocationRefListener);  // Stop listening for driver location updates

                    if (driverFoundID != null) {
                        // Remove the customer's ride from the driver
                        driversRef = FirebaseDatabase.getInstance().getReference()
                                .child("Users").child("Drivers").child(driverFoundID).child("CustomerRideID");
                        driversRef.removeValue();
                        driverFoundID = null;
                    }

                    driverFound = false;
                    radius = 1;  // Reset the radius for the next search

                    String customerId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                    GeoFire geoFire = new GeoFire(customerDatabaseRef);
                    geoFire.removeLocation(customerId);  // Remove the customer's location from GeoFire

                    if (pickUpMarker != null) {
                        pickUpMarker.remove();  // Remove the pickup marker from the map
                    }
                    if (driverMarker != null) {
                        driverMarker.remove();  // Remove the driver marker from the map
                    }

                    // Reset button text and hide driver details
                    callCabButton.setText("Call a Cab");
                    relativeLayout.setVisibility(View.GONE);
                } else {
                    // Requesting a cab
                    if (LastLocation == null) {
                        Toast.makeText(CustomersMapActivity.this, "Unable to get location. Please enable GPS and try again.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Set the request type to true and mark the customer's location on the map
                    requestType = true;
                    String customerId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                    GeoFire geoFire = new GeoFire(customerDatabaseRef);
                    geoFire.setLocation(customerId, new GeoLocation(LastLocation.getLatitude(), LastLocation.getLongitude()));

                    customerPickUpLocation = new LatLng(LastLocation.getLatitude(), LastLocation.getLongitude());
                    pickUpMarker = mMap.addMarker(new MarkerOptions().position(customerPickUpLocation).title("My Location").icon(BitmapDescriptorFactory.fromResource(R.drawable.user)));

                    callCabButton.setText("Getting your Driver...");
                    getClosestDriverCab();  // Start looking for drivers
                }
            }
        });

    }

    private void getClosestDriverCab() {
        DatabaseReference driverAvailableRef = FirebaseDatabase.getInstance().getReference("Drivers Working"); // Correct reference

        GeoFire geoFire = new GeoFire(driverAvailableRef);
        geoQuery = geoFire.queryAtLocation(new GeoLocation(customerPickUpLocation.latitude, customerPickUpLocation.longitude), radius);
        geoQuery.removeAllListeners();

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                if (!driverFound && requestType) {
                    driverFound = true;
                    driverFoundID = key;

                    // Assign customer to driver
                    driversRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverFoundID);
                    Map<String, Object> driversMap = new HashMap<>();
                    driversMap.put("CustomerRideID", customerID);
                    driversRef.updateChildren(driversMap);

                    GettingDriverLocation();
                    callCabButton.setText("Looking for Driver Location...");
                }
            }

            @Override
            public void onKeyExited(String key) {
            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {
            }

            @Override
            public void onGeoQueryReady() {
                if (!driverFound) {
                    if (radius < 10) { // Expand search gradually
                        radius += 1;
                        driverFound = false; // Reset search flag
                        getClosestDriverCab();
                    } else {
                        callCabButton.setText("No drivers found. Try again.");
                    }
                }
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {
                callCabButton.setText("Error finding driver");
            }
        });
    }


    private void GettingDriverLocation() {
        driverLocationRefListener = driverLocationRef.child(driverFoundID).child("l")
                .addValueEventListener(new ValueEventListener() {
                    @SuppressLint("SetTextI18n")
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists() && requestType) {
                            List<Object> driverLocationMap = (List<Object>) dataSnapshot.getValue();
                            double locationLat = 0;
                            double locationLng = 0;

                            if (driverLocationMap.get(0) != null) {
                                locationLat = Double.parseDouble(driverLocationMap.get(0).toString());
                            }
                            if (driverLocationMap.get(1) != null) {
                                locationLng = Double.parseDouble(driverLocationMap.get(1).toString());
                            }

                            LatLng driverLatLng = new LatLng(locationLat, locationLng);

                            // Move the camera smoothly to the driver's position
                            if (driverMarker != null) {
                                driverMarker.remove();  // Remove the previous marker if it exists
                            }
                            driverMarker = mMap.addMarker(new MarkerOptions().position(driverLatLng)
                                    .title("Your Driver")
                                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.car)));

                            // Animate camera to the new driver location
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(driverLatLng, 15));

                            // Calculate the distance from the driver to the customer
                            Location location1 = new Location("");
                            location1.setLatitude(customerPickUpLocation.latitude);
                            location1.setLongitude(customerPickUpLocation.longitude);

                            Location location2 = new Location("");
                            location2.setLatitude(driverLatLng.latitude);
                            location2.setLongitude(driverLatLng.longitude);

                            float distance = location1.distanceTo(location2);

                            // Update the UI with the current distance and travel status
                            if (distance < 90) {
                                callCabButton.setText("Driver's Reached");
                            } else {
                                callCabButton.setText("Driver Found: " + String.valueOf(distance/1000)+"km");
                            }

                            // Calculate estimated time based on distance (assuming average speed of 30 km/h)
                            float timeInMinutes = distance / 1000 / 30 * 60;  // time in minutes
                            String estimatedTime = String.format("Estimated Time: %.0f min", timeInMinutes);
                            txtCarName.setText(estimatedTime);  // Update the UI with time
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        // Handle cancellation of the listener
                    }
                });
    }


    private void getAssignedDriverInformation() {
        driversRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverFoundID);
        driversRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    String name = dataSnapshot.child("name").getValue().toString();
                    String phone = dataSnapshot.child("phone").getValue().toString();
                    String car = dataSnapshot.child("car").getValue().toString();
                    String profileImage = dataSnapshot.child("profileImageUrl").getValue().toString();

                    txtName.setText(name);
                    txtPhone.setText(phone);
                    txtCarName.setText(car);
                    Picasso.get().load(profileImage).into(profilePic);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }

    private void LogOutUser() {
        Intent intent = new Intent(CustomersMapActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        googleMap.setMyLocationEnabled(true);

        googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        googleApiClient.connect();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        locationRequest = new LocationRequest();
        locationRequest.setInterval(1000);
        locationRequest.setFastestInterval(1000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {}

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {}

    @Override
    public void onLocationChanged(Location location) {
        LastLocation = location;
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));
    }
}
