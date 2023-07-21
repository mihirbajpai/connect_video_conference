package com.example.connect.activity;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.connect.R;
import com.example.connect.adapters.UsersAdapter;
import com.example.connect.listeners.UsersListener;
import com.example.connect.models.User;
import com.example.connect.utilities.Constants;
import com.example.connect.utilities.PreferenceManager;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity implements UsersListener {

    private PreferenceManager preferenceManager;
    private List<User> users;
    private UsersAdapter usersAdapter;
    private TextView textErrorMessage;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ImageView imageConference;
    private int REQUEST_CODE_BATTERY_OPTIMIZATIONS=1;

    @SuppressLint({"NotifyDataSetChanged", "MissingInflatedId"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferenceManager=new PreferenceManager(getApplicationContext());

        imageConference=findViewById(R.id.imageConference);

        TextView textTitle=findViewById(R.id.textTitle);

        textTitle.setText(String.format(
                "%s %s",
                preferenceManager.getString(Constants.KEY_FIRST_NAME),
                preferenceManager.getString(Constants.KEY_LAST_NAME)
        ));

        findViewById(R.id.textSignOut).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                signOut();
            }
        });

        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(new OnCompleteListener<String>() {
            @Override
            public void onComplete(@NonNull Task<String> task) {
                if (task.isComplete() && task.getResult()!=null){
                    final String[] tokenn = {""};
                    tokenn[0] = task.getResult();
                    sendFCMTokenToDatabase(tokenn[0]);
                }
            }
        });

        RecyclerView userRecyclerView = findViewById(R.id.userRecyclerView);

        textErrorMessage=findViewById(R.id.textErrorMessage);

        users =new ArrayList<>();
        usersAdapter=new UsersAdapter(users, this);
        userRecyclerView.setAdapter(usersAdapter);

        swipeRefreshLayout=findViewById(R.id.swipeRefreshLayout);

        swipeRefreshLayout.setOnRefreshListener(this::getUsers);

        getUsers();

        checkFOrBatteryOptimization();

    }

    private void getUsers(){

        swipeRefreshLayout.setRefreshing(true);

        FirebaseFirestore database=FirebaseFirestore.getInstance();
        database.collection(Constants.KEY_COLLECTION_USERS)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @SuppressLint("NotifyDataSetChanged")
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        swipeRefreshLayout.setRefreshing(false);
                        String myUserId=preferenceManager.getString(Constants.KEY_USER_ID);
                        if(task.isSuccessful() && task.getResult()!=null){
                            users.clear();
                            for(QueryDocumentSnapshot documentSnapshot: task.getResult()){
                                if(myUserId.equals(documentSnapshot.getId())){
                                    continue;
                                }
                                User user=new User();
                                user.firstName=documentSnapshot.getString(Constants.KEY_FIRST_NAME);
                                user.lastName=documentSnapshot.getString(Constants.KEY_LAST_NAME);
                                user.email=documentSnapshot.getString(Constants.KEY_EMAIL);
                                user.token=documentSnapshot.getString(Constants.KEY_FCM_TOKEN);
                                users.add(user);
                            }
                            if(users.size()>0){
                                usersAdapter.notifyDataSetChanged();
                            }else {
                                textErrorMessage.setText(String.format("s%", "No users available"));
                                textErrorMessage.setVisibility(View.VISIBLE);
                            }
                        }else {
                            textErrorMessage.setText(String.format("s%", "No users available"));
                            textErrorMessage.setVisibility(View.VISIBLE);

                        }
                    }
                });
    }

    private void sendFCMTokenToDatabase(String token){
        FirebaseFirestore database=FirebaseFirestore.getInstance();
        DocumentReference documentReference =
                database.collection(Constants.KEY_COLLECTION_USERS).document(
                        preferenceManager.getString(Constants.KEY_USER_ID)
                );

        documentReference.update(Constants.KEY_FCM_TOKEN, token)
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(MainActivity.this, "Unable to send token: "+ e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }


    private void signOut(){
        Toast.makeText(this, "Signing Out...", Toast.LENGTH_SHORT).show();
        FirebaseFirestore database= FirebaseFirestore.getInstance();
        DocumentReference documentReference=
                database.collection(Constants.KEY_COLLECTION_USERS).document(
                        preferenceManager.getString(Constants.KEY_USER_ID)
                );
        HashMap<String, Object> updates=new HashMap<>();
        updates.put(Constants.KEY_FCM_TOKEN, FieldValue.delete());
        documentReference.update(updates)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        preferenceManager.clearPreference();
                        startActivity(new Intent(getApplicationContext(), LogIn.class));
                        finish();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(MainActivity.this, "Unable to sign out", Toast.LENGTH_SHORT).show();
                    }
                });

    }

    @Override
    public void initiateVideoMeeting(User user) {
        if(user.token ==null || user.token.trim().isEmpty()){
            Toast.makeText(
                    this,
                    user.firstName+" "+user.lastName+" is not available for meeting",
                    Toast.LENGTH_SHORT
            ).show();
        }else{
            Intent intent=new Intent(getApplicationContext(), OutgoingInvitationActivity.class);
            intent.putExtra("user", user);
            intent.putExtra("type", "video");
            startActivity(intent);
        }
    }

    @Override
    public void initiateAudioMeeting(User user) {
        if(user.token ==null || user.token.trim().isEmpty()){
            Toast.makeText(
                    this,
                    user.firstName+" "+user.lastName+" is not available for meeting",
                    Toast.LENGTH_SHORT
            ).show();
        }else{
            Intent intent=new Intent(getApplicationContext(), OutgoingInvitationActivity.class);
            intent.putExtra("user", user);
            intent.putExtra("type", "audio");
            startActivity(intent);
        }
    }

    @Override
    public void onMultipleUserAction(Boolean isMultipleUserSelected) {
        if (isMultipleUserSelected){
            imageConference.setVisibility(View.VISIBLE);
            imageConference.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(getApplicationContext(), OutgoingInvitationActivity.class);
                    intent.putExtra("selectedUsers", new Gson().toJson(usersAdapter.getSelectedUsers()));
                    intent.putExtra("type", "video");
                    intent.putExtra("isMultiple", true);
                    startActivity(intent);
                }
            });
        }else {
            imageConference.setVisibility(View.GONE);
        }
    }


    private void  checkFOrBatteryOptimization(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
            PowerManager powerManager=(PowerManager) getSystemService(POWER_SERVICE);
            if(!powerManager.isIgnoringBatteryOptimizations(getPackageName())){
                AlertDialog.Builder builder=new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Warning");
                builder.setMessage("Battery optimization is enabled, it can interrupt running background service.");
                builder.setPositiveButton("Disable", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent=new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                        startActivityForResult(intent, REQUEST_CODE_BATTERY_OPTIMIZATIONS);
                    }
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                builder.create().show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==REQUEST_CODE_BATTERY_OPTIMIZATIONS){
            checkFOrBatteryOptimization();
        }
    }
}