package com.example.connect.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.connect.R;
import com.example.connect.models.User;
import com.example.connect.network.ApiClient;
import com.example.connect.network.ApiService;
import com.example.connect.utilities.Constants;
import com.example.connect.utilities.PreferenceManager;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.common.reflect.TypeToken;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.gson.Gson;

import org.jitsi.meet.sdk.JitsiMeetActivity;
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions;
import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.UUID;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OutgoingInvitationActivity extends AppCompatActivity {

    private PreferenceManager preferenceManager;
    private String inviterToken = null;
    String meetingRoom=null;
    private String meetingType=null;
    private TextView textFirstChar;
    private TextView textUserName;
    private TextView textEmail;
    private int rejectionCount=0;
    private int totalReceivers=0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_outgoing_invitation);
        preferenceManager=new PreferenceManager(getApplicationContext());

        ImageView imageMeetingType=findViewById(R.id.imageMeetingType);
        meetingType=getIntent().getStringExtra("type");

        if(meetingType != null){
            if(meetingType.equals("video")){
                imageMeetingType.setImageResource(R.drawable.ic_video);
            }else {
                imageMeetingType.setImageResource(R.drawable.ic_call);
            }
        }

        textFirstChar = findViewById(R.id.textFirstChar);
        textUserName = findViewById(R.id.textUsername);
        textEmail = findViewById(R.id.textEmail);

        User user=(User) getIntent().getSerializableExtra("user");

        if(user!=null){
            textFirstChar.setText(user.firstName.substring(0, 1));
            textUserName.setText(String.format("%s %s", user.firstName, user.lastName));
            textEmail.setText(user.email);

            ImageView imageStopInvitation = findViewById(R.id.imageStopInvitation);
            imageStopInvitation.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(getIntent().getBooleanExtra("isMultiple", false)){
                        Type type=new TypeToken<ArrayList<User>>(){
                        }.getType();
                        ArrayList<User> receivers =new Gson().fromJson(getIntent().getStringExtra("selectedUswe"), type);
                        cancelledInvitation(null, receivers);
                    }else {
                        if(user!=null){
                            cancelledInvitation(user.token, null);
                        }
                    }
                }
            });

            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(new OnCompleteListener<String>() {
                @Override
                public void onComplete(@NonNull Task<String> task) {
                    if (task.isComplete() && task.getResult()!=null){
                        inviterToken = task.getResult();
                        if(meetingType!=null){
                            if(getIntent().getBooleanExtra("isMultiple", false)){
                                Type type=new TypeToken<ArrayList<User>>(){
                                }.getType();
                                ArrayList<User> receivers =new Gson().fromJson(getIntent().getStringExtra("selectedUsers"), type);
                                if(receivers!=null){
                                    totalReceivers=receivers.size();
                                }
                                initiateMeeting(meetingType, null, receivers);
                            }else {
                                if(user!=null){
                                    totalReceivers=1;
                                    initiateMeeting(meetingType, user.token, null);
                                }
                            }
                        }
                    }
                }
            });
        }
    }

    private void initiateMeeting(String meetingType, String receiverToken, ArrayList<User> receivers){
        try {
            JSONArray tokens=new JSONArray();

            if(receiverToken !=null){
                tokens.put(receiverToken);
            }
            if(receivers != null && receivers.size()>0){
                StringBuilder userNames=new StringBuilder();
                for(int i=0; i<receivers.size(); i++){
                    tokens.put(receivers.get(i).token);
                    userNames.append(receivers.get(i).firstName)
                            .append(" ")
                            .append(receivers.get(i).lastName)
                            .append("\n");
                }
                textUserName.setVisibility(View.GONE);
                textEmail.setVisibility(View.GONE);
                textUserName.setText(userNames.toString());
            }

            tokens.put(receiverToken);
            JSONObject body=new JSONObject();
            JSONObject data=new JSONObject();

            data.put(Constants.REMOTE_MSG_TYPE, Constants.REMOTE_MSG_INVITATION);
            data.put(Constants.REMOTE_MSG_MEETING_TYPE, meetingType);
            data.put(Constants.KEY_FIRST_NAME, preferenceManager.getString(Constants.KEY_FIRST_NAME));
            data.put(Constants.KEY_LAST_NAME, preferenceManager.getString(Constants.KEY_LAST_NAME));
            data.put(Constants.KEY_EMAIL, preferenceManager.getString(Constants.KEY_EMAIL));
            data.put(Constants.REMOTE_MSG_INVITER_TOKEN, inviterToken);

            meetingRoom=
                    preferenceManager.getString(Constants.KEY_USER_ID)+"_"+
                                        UUID.randomUUID().toString().substring(0, 5);

            data.put(Constants.REMOTE_MSG_MEETING_ROOM, meetingRoom);

            body.put(Constants.REMOTE_MSG_DATA, data);
            body.put(Constants.REMOTE_MSG_REGISTRATION_IDS, tokens);

            sendRemoteMessage(body.toString(), Constants.REMOTE_MSG_INVITATION);
        }catch (Exception exception){
            Toast.makeText(OutgoingInvitationActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();

        }
    }

    private void sendRemoteMessage(String remoteMessageBody, String type){
        ApiClient.getClient().create(ApiService.class).sendRemoteMessage(
                Constants.getRemoteMessageHeaders(), remoteMessageBody
        ).enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if(response.isSuccessful()){
                    if(type.equals(Constants.REMOTE_MSG_INVITATION)){

                        Toast.makeText(OutgoingInvitationActivity.this, "Invitation sent successfully", Toast.LENGTH_SHORT).show();
                    } else if (type.equals(Constants.REMOTE_MSG_INVITATION_RESPONSE)) {
                        Toast.makeText(OutgoingInvitationActivity.this, "Invitation Cancelled", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }else{
                    Toast.makeText(OutgoingInvitationActivity.this, response.message(), Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                Toast.makeText(OutgoingInvitationActivity.this, t.getMessage(), Toast.LENGTH_SHORT).show();

            }
        });
    }

    private void cancelledInvitation(String receiverToken, ArrayList<User> receivers){
        try{
            JSONArray tokens =new JSONArray();

            if(receiverToken!=null){
                tokens.put(receiverToken);
            }

            if(receivers != null && receivers.size()>0){
                for(User user: receivers){
                    tokens.put(user.token);
                }
            }

            tokens.put(receiverToken);

            JSONObject body = new JSONObject();
            JSONObject data = new JSONObject();

            data.put(Constants.REMOTE_MSG_TYPE, Constants.REMOTE_MSG_INVITATION_RESPONSE);
            data.put(Constants.REMOTE_MSG_INVITATION_RESPONSE, Constants.REMOTE_MSG_INVITATION_CANCELLED);

            body.put(Constants.REMOTE_MSG_DATA, data);
            body.put(Constants.REMOTE_MSG_REGISTRATION_IDS, tokens);

            sendRemoteMessage(body.toString(), Constants.REMOTE_MSG_INVITATION_RESPONSE);
        }catch (Exception exception){
            Toast.makeText(OutgoingInvitationActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private BroadcastReceiver invitationResponseReceiver=new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String type=intent.getStringExtra(Constants.REMOTE_MSG_INVITATION_RESPONSE);
            if(type != null){
                if(type.equals(Constants.REMOTE_MSG_INVITATION_ACCEPTED)){
                    try {
                        URL serverURL=new URL("https://meet.jit.si");

                        JitsiMeetConferenceOptions.Builder builder=new JitsiMeetConferenceOptions.Builder();
                        builder.setServerURL(serverURL);
                        builder.setFeatureFlag("call-integration.enabled", false);
                        builder.setRoom(meetingRoom);
                        if(meetingType.equals("audio")){
                            builder.setVideoMuted(true);
                        }
//                        JitsiMeetConferenceOptions conferenceOptions=
//                                new JitsiMeetConferenceOptions.Builder()
//                                        .setServerURL(serverURL)
//                                        .setFeatureFlag("call-integration.enabled", false)
//                                        .setRoom(meetingRoom)
//                                        .build();
//                        JitsiMeetActivity.launch(OutgoingInvitationActivity.this, conferenceOptions);

                        JitsiMeetActivity.launch(OutgoingInvitationActivity.this, builder.build());

                        finish();
                    }catch (Exception exception){
                        Toast.makeText(OutgoingInvitationActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                        finish();

                    }
                } else if (type.equals(Constants.REMOTE_MSG_INVITATION_REJECTED)) {
                    rejectionCount++;
                    if(rejectionCount==totalReceivers){
                        Toast.makeText(OutgoingInvitationActivity.this, "Invitation Rejected", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }
            }
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(
                invitationResponseReceiver,
                new IntentFilter(Constants.REMOTE_MSG_INVITATION_RESPONSE)
        );
    }

    @Override
    protected void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(
                invitationResponseReceiver
        );
    }
}