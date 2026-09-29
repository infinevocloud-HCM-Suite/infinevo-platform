package com.infinevo.worker.notification;

/**
 * Pluggable client for sending email notifications (W-20.2, §13 decision 2).
 *
 * <p>Implemented by {@link BrevoDeliveryClient}. Sitting behind this interface ensures
 * swapping to Azure Communication Services is a drop-in change without altering consumers.
 */
public interface DeliveryClient {

    /**
     * Sends an email message.
     *
     * @param toEmail destination email address
     * @param subject email subject line
     * @param body rendered email body (HTML and plain text)
     * @throws DeliveryException if the email delivery fails (distinguishing transient vs permanent)
     */
    void sendEmail(String toEmail, String subject, String body);
}
