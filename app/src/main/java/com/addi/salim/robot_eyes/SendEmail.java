package com.addi.salim.robot_eyes;

import android.os.AsyncTask;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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


public class SendEmail {

    private final File cacheDir;
    private final String appTag;

    public SendEmail(final String appTag, final File cacheDir) {
        this.appTag = appTag;
        this.cacheDir = cacheDir;
    }

    /**
     * Send an email from the user's mailbox to its recipient.
     *
     * @param service      Authorized Gmail API instance.
     * @param userId       User's email address. The special value "me"
     *                     can be used to indicate the authenticated user.
     * @param emailContent Email to be sent.
     * @return The sent message
     * @throws MessagingException
     * @throws IOException
     */
    public Message sendMessage(Gmail service,
                               String userId,
                               MimeMessage emailContent)
            throws MessagingException, IOException {
        Message message = createMessageWithEmail(emailContent);
        message = service.users().messages().send(userId, message).execute();

        System.out.println("Message id: " + message.getId());
        System.out.println(message.toPrettyString());
        return message;
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

    public void emailPictureAlarm(GoogleAccountCredential service, Set<String> recipientEmails, byte[] attachmentData) {
        new MakeRequestTask(service, recipientEmails, attachmentData).execute();
    }

    /**
     * An asynchronous task that handles the Gmail API call.
     * Placing the API calls in their own task.
     */
    private class MakeRequestTask extends AsyncTask<Void, Void, Void> {
        private com.google.api.services.gmail.Gmail mService = null;
        final private Set<String> recipientEmails;
        final private byte[] attachmentData;

        MakeRequestTask(GoogleAccountCredential credential, Set<String> recipientEmails, byte[] attachmentData) {
            this.recipientEmails = recipientEmails;
            this.attachmentData = attachmentData;
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.gmail.Gmail.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName("Robot Eyes")
                    .build();
        }

        /**
         * Background task to call Gmail API.
         *
         * @param params no parameters needed for this task.
         */
        @Override
        protected Void doInBackground(Void... params) {
            try {
                File pictureFile = Util.getOutputMediaFile(MEDIA_TYPE_IMAGE, appTag, cacheDir);
                if (pictureFile != null) {
                    FileOutputStream fos = new FileOutputStream(pictureFile);
                    fos.write(attachmentData);
                    fos.close();
                }

                final MimeMessage mimeMessage;
                if (pictureFile != null) {
                    mimeMessage = createEmail(recipientEmails,
                            "Robot Eyes Alarm!",
                            "Robot Eyes Alarm!", pictureFile);
                } else {
                    mimeMessage = createEmail(recipientEmails,
                            "Robot Eyes Alarm!",
                            "Robot Eyes Alarm!");
                }

                sendMessage(mService,
                        "me",
                        mimeMessage);

            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }
    }
}
