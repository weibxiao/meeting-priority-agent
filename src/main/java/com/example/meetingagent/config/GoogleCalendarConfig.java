package com.example.meetingagent.config;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

/**
 * Sets up a read-only Google Calendar client using the OAuth 2.0
 * "installed application" flow. On first run this opens a browser window
 * asking you to sign in and consent; the resulting token is cached under
 * {@code tokens/} so subsequent runs are non-interactive.
 *
 * Prerequisites:
 *  1. Create a Google Cloud project, enable the "Google Calendar API".
 *  2. Create an OAuth Client ID of type "Desktop app".
 *  3. Download the JSON and save it as src/main/resources/credentials.json
 *     (see application.yml -> google.calendar.credentials-file).
 */
@Configuration
public class GoogleCalendarConfig {

    private static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR_READONLY);
    private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    @Value("${google.calendar.application-name:Meeting Priority Agent}")
    private String applicationName;

    @Value("${google.calendar.credentials-file:classpath:credentials.json}")
    private String credentialsFile;

    @Value("${google.calendar.tokens-directory:tokens}")
    private String tokensDirectory;

    @Bean
    public Calendar googleCalendarClient() throws GeneralSecurityException, IOException {
        var httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        Credential credential = authorize(httpTransport);
        return new Calendar.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(applicationName)
                .build();
    }

    private Credential authorize(com.google.api.client.http.HttpTransport httpTransport) throws IOException {
        InputStream in = resolveCredentialsStream();
        if (in == null) {
            throw new IOException("Could not find Google OAuth credentials file at: " + credentialsFile
                    + ". Download it from Google Cloud Console (OAuth Client ID, Desktop app) and place it there.");
        }

        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new File(tokensDirectory)))
                .setAccessType("offline")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8080).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    private InputStream resolveCredentialsStream() throws IOException {
        if (credentialsFile.startsWith("classpath:")) {
            String path = credentialsFile.substring("classpath:".length());
            return getClass().getClassLoader().getResourceAsStream(path);
        }
        File file = new File(credentialsFile);
        return file.exists() ? Files.newInputStream(file.toPath()) : null;
    }
}
