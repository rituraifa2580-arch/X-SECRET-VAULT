package com.alamin.xvault;

import android.app.*;
import android.os.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.net.Uri;
import android.content.Intent;

public class MainActivity extends Activity {
    long holdStart = 0;
    Handler handler = new Handler();
    Runnable unlockTask;
    Handler holdHandler = new Handler(Looper.getMainLooper());
    Runnable unlockRunnable;
    boolean unlocked = false;
    boolean opened = false;
    int gold = Color.rgb(212,175,55);
    FrameLayout root;
    final int PICK_PHOTOS = 101;
    final int PICK_VIDEOS = 102;
    android.content.SharedPreferences prefs;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        prefs = getSharedPreferences("XVault", MODE_PRIVATE);
        showBlack();
    }

    void showBlack() {
        opened = false;
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        if (!opened) {
            int action = e.getActionMasked();
            int count = e.getPointerCount();

            if (action == MotionEvent.ACTION_POINTER_DOWN && count == 3) {
                holdStart = SystemClock.elapsedRealtime();
                unlockTask = () -> {
                    if (!opened && holdStart > 0) {
                        opened = true;
                        holdStart = 0;
                        showVault();
                    }
                };
                handler.postDelayed(unlockTask, 3000);
            }

            if (action == MotionEvent.ACTION_POINTER_UP ||
                action == MotionEvent.ACTION_UP ||
                action == MotionEvent.ACTION_CANCEL) {
                holdStart = 0;
                if (unlockTask != null) handler.removeCallbacks(unlockTask);
            }
            return true;
        }
        return super.dispatchTouchEvent(e);
    }

    TextView makeText(String s, int size) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(gold);
        t.setTextSize(size);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    void showVault() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(8,8,8));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(24,45,24,20);

        TextView title = makeText("X", 42);
        title.setTypeface(null, Typeface.BOLD);
        page.addView(title, new LinearLayout.LayoutParams(-1,90));

        TextView sub = makeText("SECRET GALLERY VAULT", 13);
        page.addView(sub);

        addButton(page, "▣     PRIVATE PHOTOS", () -> pickMedia(false));
        addButton(page, "▶     PRIVATE VIDEOS", () -> videoPin());
        addButton(page, "◆     SECURE FILES", () -> showMessage("Secure Files coming soon"));
        addButton(page, "⚙     SETTINGS", () -> showMessage("Settings coming soon"));

        TextView lock = makeText("LOCK VAULT", 15);
        lock.setTextColor(Color.WHITE);
        lock.setPadding(0,35,0,20);
        page.addView(lock);
        lock.setOnClickListener(v -> showBlack());

        root.addView(page);
        setContentView(root);
    }

    void addButton(LinearLayout page, String label, Runnable action) {
        TextView b = makeText(label, 16);
        b.setOnClickListener(v -> action.run());
        b.setBackgroundColor(Color.rgb(25,25,25));
        b.setPadding(12,22,12,22);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,75);
        lp.setMargins(0,14,0,0);
        page.addView(b, lp);
    }

    void pickMedia(boolean video) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType(video ? "video/*" : "image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, video ? PICK_VIDEOS : PICK_PHOTOS);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            showMessage("File selected successfully. Secure storage is not implemented yet.");
        }
    }

    void showMessage(String message) {
        new AlertDialog.Builder(this)
            .setTitle("X Vault")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show();
    }


    String hashPin(String value, byte[] salt, String algorithm) throws Exception {
        javax.crypto.spec.PBEKeySpec spec =
            new javax.crypto.spec.PBEKeySpec(value.toCharArray(), salt, 120000, 256);
        try {
            javax.crypto.SecretKeyFactory factory =
                javax.crypto.SecretKeyFactory.getInstance(algorithm);
            byte[] result = factory.generateSecret(spec).getEncoded();
            return android.util.Base64.encodeToString(result, android.util.Base64.NO_WRAP);
        } finally {
            spec.clearPassword();
        }
    }

    void saveVideoPin(String value) throws Exception {
        byte[] salt = new byte[16];
        new java.security.SecureRandom().nextBytes(salt);

        String algorithm = "PBKDF2WithHmacSHA256";
        String hash;

        try {
            hash = hashPin(value, salt, algorithm);
        } catch (Exception e) {
            algorithm = "PBKDF2WithHmacSHA1";
            hash = hashPin(value, salt, algorithm);
        }

        prefs.edit()
            .putString("video_pin_salt",
                android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP))
            .putString("video_pin_hash", hash)
            .putString("video_pin_algorithm", algorithm)
            .remove("video_pin")
            .apply();
    }

    boolean verifyVideoPin(String value) throws Exception {
        String saltText = prefs.getString("video_pin_salt", "");
        String savedHash = prefs.getString("video_pin_hash", "");
        String algorithm = prefs.getString(
            "video_pin_algorithm", "PBKDF2WithHmacSHA256");

        if (saltText.isEmpty() || savedHash.isEmpty()) return false;

        byte[] salt = android.util.Base64.decode(saltText, android.util.Base64.NO_WRAP);
        String enteredHash = hashPin(value, salt, algorithm);

        byte[] a = android.util.Base64.decode(savedHash, android.util.Base64.NO_WRAP);
        byte[] b = android.util.Base64.decode(enteredHash, android.util.Base64.NO_WRAP);

        return java.security.MessageDigest.isEqual(a, b);
    }

    void videoPin() {
        String legacy = prefs.getString("video_pin", "");
        String savedHash = prefs.getString("video_pin_hash", "");
        boolean create = legacy.isEmpty() && savedHash.isEmpty();

        EditText pin = new EditText(this);
        pin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
            android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin.setHint(create ? "Create video PIN" : "Enter video PIN");

        new AlertDialog.Builder(this)
            .setTitle(create ? "Set Video PIN" : "Video PIN")
            .setView(pin)
            .setPositiveButton("CONTINUE", (d, w) -> {
                String value = pin.getText().toString();

                if (value.length() < 4) {
                    showMessage("PIN must contain at least 4 digits.");
                    return;
                }

                try {
                    if (create) {
                        saveVideoPin(value);
                        showMessage("Video PIN created.");
                    } else if (!legacy.isEmpty()) {
                        if (legacy.equals(value)) {
                            saveVideoPin(value);
                            pickMedia(true);
                        } else {
                            showMessage("Incorrect PIN.");
                        }
                    } else if (verifyVideoPin(value)) {
                        pickMedia(true);
                    } else {
                        showMessage("Incorrect PIN.");
                    }
                } catch (Exception e) {
                    showMessage("PIN security error. Please try again.");
                }
            })
            .setNegativeButton("CANCEL", null)
            .show();
    }

}
