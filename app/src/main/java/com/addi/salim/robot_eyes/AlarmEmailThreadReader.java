package com.addi.salim.robot_eyes;

import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;

import com.google.api.services.gmail.model.Message;

import java.io.IOException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import static android.speech.tts.TextToSpeech.QUEUE_FLUSH;

public class AlarmEmailThreadReader extends Timer {
    private final long EMAIL_REPLIES_CHECK_INTERVAL = TimeUnit.SECONDS.toMillis(3);
    private final long SPEAK_LAST_MESSAGE_INTERVAL = TimeUnit.SECONDS.toMillis(10);

    private String threadId;
    private int threadEmailRepliesIndex = 1;
    private TimerTask emailRepliesReaderTask;
    private EmailAgent emailAgent;
    private final TextToSpeech textToSpeech;
    private String lastMessage = "";
    private TimerTask readLastReplyMessageTask;

    public AlarmEmailThreadReader(EmailAgent emailAgent, TextToSpeech textToSpeech) {
        super();
        this.emailAgent = emailAgent;
        this.textToSpeech = textToSpeech;
    }

    void onAlarmEmailSent(@NonNull final String emailThreadId) {
        this.threadId = emailThreadId;
        if (emailRepliesReaderTask == null) {
            emailRepliesReaderTask = new TimerTask() {

                @Override
                public void run() {
                    //check for email thread replies!
                    List<Message> messages;
                    try {
                        messages = emailAgent.readMessageReply(emailThreadId);
                        if (messages.size() > threadEmailRepliesIndex) {
                            final Message lastReply = messages.get(threadEmailRepliesIndex);
                            List<String> labels = lastReply.getLabelIds();
                            if (labels.contains("INBOX")) {
                                lastMessage = emailAgent.getMessageBody(lastReply);
                                textToSpeech.speak(lastMessage, QUEUE_FLUSH, null);
                                System.out.println("emailBody: " + lastMessage);
                                threadEmailRepliesIndex++;
                            }
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            };

            readLastReplyMessageTask = new TimerTask() {
                @Override
                public void run() {
                    textToSpeech.speak(lastMessage, QUEUE_FLUSH, null);
                }
            };

            schedule(emailRepliesReaderTask, EMAIL_REPLIES_CHECK_INTERVAL, EMAIL_REPLIES_CHECK_INTERVAL);
            schedule(readLastReplyMessageTask, SPEAK_LAST_MESSAGE_INTERVAL, SPEAK_LAST_MESSAGE_INTERVAL);
        }
    }


    public void stop() {
        cancel();
    }

}
