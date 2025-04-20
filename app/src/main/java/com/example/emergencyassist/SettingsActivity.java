package com.example.emergencyassist;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.squareup.picasso.Picasso;

import java.util.HashMap;

import de.hdodenhof.circleimageview.CircleImageView;

public class SettingsActivity extends AppCompatActivity {

    private String getType;

    private CircleImageView profileImageView;
    private EditText nameEditText, phoneEditText, driverCarName;
    private ImageView closeButton, saveButton;

    private DatabaseReference databaseReference;
    private FirebaseAuth mAuth;

    private StorageReference storageProfilePicsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        getType = getIntent().getStringExtra("type");
        Toast.makeText(this, getType, Toast.LENGTH_SHORT).show();

        mAuth = FirebaseAuth.getInstance();
        databaseReference = FirebaseDatabase.getInstance().getReference().child("Users").child(getType);
        storageProfilePicsRef = FirebaseStorage.getInstance().getReference().child("Profile Pictures");

        profileImageView = findViewById(R.id.profile_image);
        nameEditText = findViewById(R.id.name);
        phoneEditText = findViewById(R.id.phone_number);
        driverCarName = findViewById(R.id.driver_car_name);

        if (getType.equals("Drivers")) {
            driverCarName.setVisibility(View.VISIBLE);
        }

        closeButton = findViewById(R.id.close_button);
        saveButton = findViewById(R.id.save_button);

        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                navigateToMapActivity();
            }
        });

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                validateAndSaveOnlyInformation();
            }
        });

        getUserInformation();
    }

    private void validateAndSaveOnlyInformation() {
        if (TextUtils.isEmpty(nameEditText.getText().toString())) {
            Toast.makeText(this, "Please provide your name.", Toast.LENGTH_SHORT).show();
        } else if (TextUtils.isEmpty(phoneEditText.getText().toString())) {
            Toast.makeText(this, "Please provide your phone number.", Toast.LENGTH_SHORT).show();
        } else if (getType.equals("Drivers") && TextUtils.isEmpty(driverCarName.getText().toString())) {
            Toast.makeText(this, "Please provide your car Name.", Toast.LENGTH_SHORT).show();
        } else {
            HashMap<String, Object> userMap = new HashMap<>();
            userMap.put("uid", mAuth.getCurrentUser().getUid());
            userMap.put("name", nameEditText.getText().toString());
            userMap.put("phone", phoneEditText.getText().toString());

            if (getType.equals("Drivers")) {
                userMap.put("car", driverCarName.getText().toString());
            }

            databaseReference.child(mAuth.getCurrentUser().getUid()).updateChildren(userMap);
            navigateToMapActivity();
        }
    }

    private void navigateToMapActivity() {
        if (getType.equals("Drivers")) {
            startActivity(new Intent(SettingsActivity.this, DriverMapActivity.class));
        } else {
            startActivity(new Intent(SettingsActivity.this, CustomersMapActivity.class));
        }
    }

    private void getUserInformation() {
        databaseReference.child(mAuth.getCurrentUser().getUid()).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() > 0) {
                    String name = dataSnapshot.child("name").getValue().toString();
                    String phone = dataSnapshot.child("phone").getValue().toString();

                    nameEditText.setText(name);
                    phoneEditText.setText(phone);

                    if (getType.equals("Drivers")) {
                        String car = dataSnapshot.child("car").getValue().toString();
                        driverCarName.setText(car);
                    }

                    if (dataSnapshot.hasChild("image")) {
                        String image = dataSnapshot.child("image").getValue().toString();
                        Picasso.get().load(image).into(profileImageView);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle error
            }
        });
    }
}
