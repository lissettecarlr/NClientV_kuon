package com.maxwai.nclientv3;

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK;
import static androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.maxwai.nclientv3.components.activities.AppLock;
import com.maxwai.nclientv3.components.activities.GeneralActivity;

import java.util.concurrent.Executor;

public class PINActivity extends GeneralActivity {
    private SharedPreferences preferences;
    private boolean authSuccess = false;
    private boolean leaving = false;// [FORK]

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Global.initActivity(this);
        setContentView(R.layout.activity_pin);
        preferences = getSharedPreferences("Settings", 0);
        if (!hasPin()) {
            finish();
            return;
        }
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(this,
            executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode,
                                              @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                Toast.makeText(getApplicationContext(),
                        getString(R.string.auth_error),
                        Toast.LENGTH_SHORT)
                    .show();
            }

            @Override
            public void onAuthenticationSucceeded(
                @NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                authSuccess = true;
                AppLock.unlock();// [FORK]
                finish();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(getApplicationContext(),
                        getString(R.string.auth_error),
                        Toast.LENGTH_SHORT)
                    .show();
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.auth_title))
            .setSubtitle(getString(R.string.auth_subtitle))
            .setAllowedAuthenticators(BIOMETRIC_WEAK | DEVICE_CREDENTIAL)
            .build();

        Button unlockButton = findViewById(R.id.unlockButton);
        unlockButton.setOnClickListener(view -> biometricPrompt.authenticate(promptInfo));
        unlockButton.performClick();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean hasPin() {
        return preferences.getBoolean(getString(R.string.preference_key_has_credentials), false);
    }

    @Override
    public void finish() {
        // [FORK] shown over another activity (deep link, back from background): just go back to it,
        // and leave the app when the user gives up
        if (getIntent().getBooleanExtra(AppLock.EXTRA_OVERLAY, false)) {
            if (!authSuccess && !leaving) {
                leaving = true;
                finishAffinity();
                return;
            }
            super.finish();
            return;
        }
        Intent i = new Intent(this, MainActivity.class);
        if (!hasPin() || authSuccess) startActivity(i);
        authSuccess = false;
        super.finish();
    }

    // [FORK]
    @Override
    protected void onDestroy() {
        if (getIntent().getBooleanExtra(AppLock.EXTRA_OVERLAY, false)) AppLock.promptEnded();
        super.onDestroy();
    }
}
