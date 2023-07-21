package com.example.connect.listeners;

import com.example.connect.models.User;

public interface UsersListener {

    void initiateVideoMeeting(User user);
    void initiateAudioMeeting(User user);
    void onMultipleUserAction(Boolean isMultipleUserSelected);
}
