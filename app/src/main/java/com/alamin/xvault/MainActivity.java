package com.alamin.xvault;

import android.app.*;
import android.os.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.net.Uri;
import android.content.Intent;

import java.io.*;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.KeyStore;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;


import java.io.*;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.KeyStore;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;


import android.widget.MediaController;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.GCMParameterSpec;
import android.widget.VideoView;
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

        addButton(page, "▣     PRIVATE PHOTOS", () -> showMediaList(false));
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
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType(video ? "video/*" : "image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, video ? PICK_VIDEOS : PICK_PHOTOS);
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if ((requestCode == PICK_PHOTOS || requestCode == PICK_VIDEOS)
                && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try {
                boolean video = requestCode == PICK_VIDEOS;
                encryptSelectedFile(data.getData(), video);
                showMediaList(video);
                showMessage("Encrypted successfully! File saved in private vault.");
            } catch (Exception e) {
                showMessage("Encryption failed: " + e.getMessage());
            }
        }
    }

    SecretKey getVaultKey() throws Exception {
        String alias = "X_VAULT_AES_KEY";
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);

        if (ks.containsAlias(alias)) {
            return ((KeyStore.SecretKeyEntry) ks.getEntry(alias, null)).getSecretKey();
        }

        KeyGenerator generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");

        generator.init(new KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build());

        return generator.generateKey();
    }

    void encryptSelectedFile(Uri uri, boolean video) throws Exception {
        File folder = new File(
            getFilesDir(), video ? "vault/videos" : "vault/photos");

        if (!folder.exists() && !folder.mkdirs()) {
            throw new Exception("Cannot create vault folder");
        }

        File encryptedFile = new File(folder, UUID.randomUUID().toString() + ".xv");

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getVaultKey());
            byte[] iv = cipher.getIV();

            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new Exception("Cannot read selected file");

                try (FileOutputStream raw = new FileOutputStream(encryptedFile)) {
                    raw.write(iv);

                    try (CipherOutputStream output = new CipherOutputStream(raw, cipher)) {
                        byte[] buffer = new byte[8192];
                        int count;

                        while ((count = input.read(buffer)) != -1) {
                            output.write(buffer, 0, count);
                        }
                    }
                }
            }
        } catch (Exception e) {
            encryptedFile.delete();
            throw e;
        }
    }

    void showMessage(String message) {
        new AlertDialog.Builder(this)
            .setTitle("X Vault")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show();
    }



    void showMediaList(boolean video) {
        File folder = new File(getFilesDir(),
                video ? "vault/videos" : "vault/photos");
        if (!folder.exists()) folder.mkdirs();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(20, 30, 20, 20);
        page.setBackgroundColor(Color.rgb(8, 8, 8));

        TextView title = makeText(
                video ? "PRIVATE VIDEOS" : "PRIVATE PHOTOS", 22);
        page.addView(title, new LinearLayout.LayoutParams(-1, 65));

        addButton(page, video ? "+ ADD VIDEO" : "+ ADD PHOTO",
                () -> pickMedia(video));

        File[] files = folder.listFiles();
        int count = 0;

        if (files != null) {
            java.util.Arrays.sort(files,
                    (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

            for (File file : files) {
                if (!file.isFile() || !file.getName().endsWith(".xv")) continue;
                count++;

                TextView item = makeText(
                        (video ? "▶  " : "▣  ") +
                        file.getName().substring(0, 8) + "  •  ENCRYPTED", 14);
                item.setBackgroundColor(Color.rgb(25, 25, 25));

                LinearLayout.LayoutParams lp =
                        new LinearLayout.LayoutParams(-1, 65);
                lp.setMargins(0, 10, 0, 0);
                page.addView(item, lp);

                item.setOnClickListener(v -> openEncryptedMedia(file, video));
                item.setOnLongClickListener(v -> {
                    new AlertDialog.Builder(this)
                            .setTitle("Delete private media?")
                            .setMessage("This file will be permanently deleted.")
                            .setNegativeButton("CANCEL", null)
                            .setPositiveButton("DELETE", (d, w) -> {
                                if (file.delete()) showMediaList(video);
                                else showMessage("Delete failed.");
                            }).show();
                    return true;
                });
            }
        }

        if (count == 0) {
            page.addView(makeText("No private media yet", 15),
                    new LinearLayout.LayoutParams(-1, 100));
        }

        addButton(page, "← BACK TO VAULT", () -> showVault());

        ScrollView scroll = new ScrollView(this);
        scroll.addView(page);
        setContentView(scroll);
    }


    File decryptToCache(File encrypted) throws Exception {
        File preview = File.createTempFile("xvault_", ".tmp", getCacheDir());

        try (InputStream raw = new FileInputStream(encrypted)) {
            byte[] iv = new byte[12];
            int offset = 0;
            while (offset < iv.length) {
                int n = raw.read(iv, offset, iv.length - offset);
                if (n < 0) throw new Exception("Invalid encrypted file");
                offset += n;
            }

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getVaultKey(),
                    new GCMParameterSpec(128, iv));

            try (CipherInputStream input = new CipherInputStream(raw, cipher);
                 FileOutputStream output = new FileOutputStream(preview)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
            }
        } catch (Exception e) {
            preview.delete();
            throw e;
        }
        return preview;
    }


    void openEncryptedMedia(File encrypted, boolean video) {
        File preview = null;
        try {
            preview = decryptToCache(encrypted);

            final File temp = preview;
            LinearLayout page = new LinearLayout(this);
            page.setOrientation(LinearLayout.VERTICAL);
            page.setBackgroundColor(Color.BLACK);
            page.setPadding(10, 20, 10, 10);

            addButton(page, "← BACK TO GALLERY", () -> {
                temp.delete();
                showMediaList(video);
            });

            if (video) {
                VideoView player = new VideoView(this);
                page.addView(player, new LinearLayout.LayoutParams(-1, 0, 1));

                MediaController controls = new MediaController(this);
                controls.setAnchorView(player);
                player.setMediaController(controls);
                player.setVideoPath(temp.getAbsolutePath());
                player.setOnErrorListener((mp, what, extra) -> {
                    showMessage("This video format could not be played.");
                    return true;
                });

                setContentView(page);
                player.start();
            } else {
                ImageView image = new ImageView(this);
                image.setAdjustViewBounds(true);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);

                Bitmap bitmap = BitmapFactory.decodeFile(temp.getAbsolutePath());
                if (bitmap == null) {
                    temp.delete();
                    showMessage("Cannot display this image.");
                    return;
                }

                image.setImageBitmap(bitmap);
                page.addView(image, new LinearLayout.LayoutParams(-1, 0, 1));
                setContentView(page);
            }
        } catch (Exception e) {
            if (preview != null) preview.delete();
            showMessage("Could not open media. File may be damaged or key unavailable.");
        }
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
                        pickMedia(true);
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
