package com.ecommerce.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    public void send(UUID customerId, String subject, String body) {
        // PLACEHOLDER: resolve customer email from a customer service / user store,
        // then send via JavaMailSender, SES, or SendGrid. Logged for local demos.
        log.info("EMAIL -> customer={} | {} | {}", customerId, subject, body);
    }
}
