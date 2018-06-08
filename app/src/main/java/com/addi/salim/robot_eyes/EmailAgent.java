package com.addi.salim.robot_eyes;

import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListThreadsResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.Thread;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;


public class EmailAgent {

    private final File cacheDir;
    private final String appTag;
    private final Gmail emailEngine;
    private final Context appContext;

    public EmailAgent(final String appTag, GoogleAccountCredential googleAccountCredential, final File cacheDir, Context appContext) {
        this.appTag = appTag;
        this.cacheDir = cacheDir;
        this.appContext = appContext;
        final HttpTransport transport = AndroidHttp.newCompatibleTransport();
        final JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
        this.emailEngine = new com.google.api.services.gmail.Gmail.Builder(
                transport, jsonFactory, googleAccountCredential)
                .setApplicationName("Robot Eyes")
                .build();
    }

    /**
     * Send an email from the user's mailbox to its recipient.
     *
     * @param userId       User's email address. The special value "me"
     *                     can be used to indicate the authenticated user.
     * @param emailContent Email to be sent.
     * @return The sent message
     * @throws MessagingException
     * @throws IOException
     */
    public Message sendMessage(String userId, MimeMessage emailContent)
            throws MessagingException, IOException {
        Message message = createMessageWithEmail(emailContent);
        try {
            message = emailEngine.users().messages().send(userId, message).execute();
        } catch (UserRecoverableAuthIOException userRecoverableException) {
            appContext.startActivity(userRecoverableException.getIntent());
            return null;
        }

        final String threadId = message.getThreadId();
        Log.e("***", "Message ThreadId = " + threadId);
        System.out.println("Message id: " + message.getId());
        System.out.println(message.toPrettyString());
        return message;
    }

    public List<Message> readMessageReply(String threadId)
            throws IOException {
        Thread thread = emailEngine.users().threads().get("me", threadId).execute();
        System.out.println("readMessageReply Thread id: " + thread.getId());
        System.out.println("No. of messages in this thread: " + thread.getMessages().size());
        System.out.println(thread.toPrettyString());
        return thread.getMessages();
    }

    /**
     * List all Threads of the user's mailbox matching the query.
     *
     * @param userId User's email address. The special value "me"
     *               can be used to indicate the authenticated user.
     * @param query  String used to filter the Threads listed.
     * @throws IOException
     */
    public void listThreadsMatchingQuery(String userId, String query) throws IOException {
        ListThreadsResponse response = emailEngine.users().threads().list(userId).execute();
        List<Thread> threads = new ArrayList<Thread>();
        while (response.getThreads() != null) {
            threads.addAll(response.getThreads());
            if (response.getNextPageToken() != null) {
                String pageToken = response.getNextPageToken();
                response = emailEngine.users().threads().list(userId).setQ(query).setPageToken(pageToken).execute();
            } else {
                break;
            }
        }

        for (Thread thread : threads) {
            System.out.println(thread.toPrettyString());
        }
    }


    /**
     * Create a message from an email.
     *
     * @param emailContent Email to be set to raw of message
     * @return a message containing a base64url encoded email
     * @throws IOException
     * @throws MessagingException
     */
    public Message createMessageWithEmail(MimeMessage emailContent)
            throws MessagingException, IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        emailContent.writeTo(buffer);
        byte[] bytes = buffer.toByteArray();
        String encodedEmail = Base64.encodeBase64URLSafeString(bytes);
        Message message = new Message();
        message.setRaw(encodedEmail);
        return message;
    }

    /**
     * Create a MimeMessage using the parameters provided.
     *
     * @param to       email address of the receiver
     * @param subject  subject of the email
     * @param bodyText body text of the email
     * @return the MimeMessage to be used to send email
     * @throws MessagingException
     */
    public MimeMessage createEmail(Set<String> to,
                                   String subject,
                                   String bodyText)
            throws MessagingException {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);

        MimeMessage email = new MimeMessage(session);
        for (String recipient : to) {
            email.addRecipient(javax.mail.Message.RecipientType.TO,
                    new InternetAddress(recipient));
        }
        email.setSubject(subject);
        email.setText(bodyText);
        return email;
    }

    /**
     * Create a MimeMessage using the parameters provided.
     *
     * @param to       Email address of the receiver.
     * @param subject  Subject of the email.
     * @param bodyText Body text of the email.
     * @param file     Path to the file to be attached.
     * @return MimeMessage to be used to send email.
     * @throws MessagingException
     */
    public MimeMessage createEmail(Set<String> to,
                                   String subject,
                                   String bodyText,
                                   File file)
            throws MessagingException, IOException {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);

        MimeMessage email = new MimeMessage(session);

        for (String recipient : to) {
            email.addRecipient(javax.mail.Message.RecipientType.TO,
                    new InternetAddress(recipient));
        }

        email.setSubject(subject);

        MimeBodyPart mimeBodyPart = new MimeBodyPart();
        mimeBodyPart.setContent(bodyText, "text/plain");

        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(mimeBodyPart);

        mimeBodyPart = new MimeBodyPart();
        DataSource source = new FileDataSource(file);

        mimeBodyPart.setDataHandler(new DataHandler(source));
        mimeBodyPart.setFileName(file.getName());

        multipart.addBodyPart(mimeBodyPart);
        email.setContent(multipart);

        return email;
    }

    public void emailPictureAlarm(Set<String> recipientEmails, String alarmTime, int distance, byte[] attachmentData, AlarmEmailThreadReader alarmEmailThreadReader) {
        final String subjectText = "Object detected within " + distance + "cm distance at " + alarmTime;
        final String bodyText = "RobotEyes has detected a moving object within " + distance + "cm distance at " + alarmTime;

        new MakeRequestTask(recipientEmails, subjectText, bodyText, attachmentData, alarmEmailThreadReader).execute();
    }

    public void emailAlarmDismissed(Set<String> recipientEmails, String time) {
        final String subjectText = "Alarm has been dismissed at " + time;
        final String bodyText = "RobotEyes has dismissed the alarm at " + time;

        new MakeRequestTask(recipientEmails, subjectText, bodyText, null, null).execute();
    }

    public static String getMessageBody(Message message) {
        String emailBody = "";
        List<MessagePart> parts = message.getPayload().getParts();

        for (MessagePart part : parts) {
            if (part.getMimeType().contains("text/plain")) {
                emailBody += new String(Base64.decodeBase64(part.getBody().getData().getBytes()));
            }
        }

        return emailBody;
    }

    /**
     * An asynchronous task that handles the Gmail API call.
     * Placing the API calls in their own task.
     */
    private class MakeRequestTask extends AsyncTask<Void, Void, Void> {
        final private Set<String> recipientEmails;
        final private byte[] attachmentData;
        final private AlarmEmailThreadReader alarmEmailThreadReader;
        final private String subject;
        final private String body;

        MakeRequestTask(Set<String> recipientEmails, String subject, String body, byte[] attachmentData, @Nullable AlarmEmailThreadReader alarmEmailThreadReader) {
            this.recipientEmails = recipientEmails;
            this.attachmentData = attachmentData;
            this.alarmEmailThreadReader = alarmEmailThreadReader;
            this.subject = subject;
            this.body = body;
        }

        /**
         * Background task to call Gmail API.
         *
         * @param params no parameters needed for this task.
         */
        @Override
        protected Void doInBackground(Void... params) {
            try {
                File pictureFile = null;
                if (attachmentData != null) {
                    pictureFile = Util.getOutputMediaFile(MEDIA_TYPE_IMAGE, appTag, cacheDir);
                    if (pictureFile != null) {
                        FileOutputStream fos = new FileOutputStream(pictureFile);
                        fos.write(attachmentData);
                        fos.close();
                    }
                }

                final MimeMessage mimeMessage;
                if (pictureFile != null) {
                    mimeMessage = createEmail(recipientEmails,
                            subject,
                            body, pictureFile);
                } else {
                    mimeMessage = createEmail(recipientEmails,
                            subject,
                            body);
                }

                final Message email = sendMessage("me", mimeMessage);
                if (alarmEmailThreadReader != null && email != null) {
                    alarmEmailThreadReader.onAlarmEmailSent(email.getThreadId());
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }
    }
}
