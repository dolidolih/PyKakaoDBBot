//SendMsg : ye-seola/go-kdb
//Kakaodecrypt : jiru/kakaodecrypt

//WIP: Decrypt all values when sendding a db record to a web server.
//WIP: Socket based remote SQL Query

import android.os.IBinder;
import android.os.ServiceManager;
import android.app.IActivityManager;
import android.content.Intent;
import android.content.ComponentName;
import android.app.RemoteInput;
import android.os.Bundle;
import org.json.JSONObject;
import org.json.JSONException;

import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLEncoder;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.IOException;
import android.util.Base64;
import java.io.File;
import java.io.FileReader;
import java.io.FileOutputStream;
import android.net.Uri;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.ArrayList;
import java.util.List;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;


class SendMsgDB {
    private static IBinder binder = ServiceManager.getService("activity");
    private static IActivityManager activityManager = IActivityManager.Stub.asInterface(binder);
    private static final int PORT = 3000;
    private static final String NOTI_REF;
    private static String DB_PATH_CONFIG;
    private static String WATCH_FILE;
    private static long lastModifiedTime = 0;
    private static final String CONFIG_FILE_PATH = "/data/local/tmp/config.json";

    static {
        String notiRefValue = null;
        File prefsFile = new File("/data/data/com.kakao.talk/shared_prefs/KakaoTalk.hw.perferences.xml");
        BufferedReader prefsReader = null;
        try {
            prefsReader = new BufferedReader(new FileReader(prefsFile));
            String line;
            while ((line = prefsReader.readLine()) != null) {
                if (line.contains("<string name=\"NotificationReferer\">")) {
                    int start = line.indexOf(">") + 1;
                    int end = line.indexOf("</string>");
                    notiRefValue = line.substring(start, end);
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading preferences file: " + e.toString());
            notiRefValue = "default_noti_ref";
        } finally {
            if (prefsReader != null) {
                try {
                    prefsReader.close();
                } catch (IOException e) {
                    System.err.println("Error closing preferences file reader: " + e.toString());
                }
            }
        }

        if (notiRefValue == null || notiRefValue.equals("default_noti_ref")) {
            System.err.println("NotificationReferer not found in preferences file or error occurred, using default or potentially failed to load.");
        } else {
            System.out.println("NotificationReferer loaded: " + notiRefValue);
        }
        NOTI_REF = (notiRefValue != null) ? notiRefValue : "default_noti_ref";

        String dbPathValue = "/data/data/com.kakao.talk/databases";

        DB_PATH_CONFIG = dbPathValue;
        WATCH_FILE = DB_PATH_CONFIG + "/KakaoTalk.db-wal";
    }


    public static void main(String[] args) {
        ServerSocket serverSocket = null;
        SendMsgDB.KakaoDB kakaoDb = new SendMsgDB.KakaoDB();
        SendMsgDB.ObserverHelper observerHelper = new SendMsgDB.ObserverHelper();

        checkDbChanges(kakaoDb, observerHelper);

        Thread dbWatcherThread = new Thread(() -> {
            while (true) {
                checkDbChanges(kakaoDb, observerHelper);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("DB Watcher thread interrupted: " + e.toString());
                    break;
                }
            }
        });
        dbWatcherThread.start();


        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Server listening on port " + PORT);

            while (true) {
                Socket clientSocket = null;
                BufferedReader in = null;
                PrintWriter out = null;
                try {
                    clientSocket = serverSocket.accept();
                    System.out.println("Client connected: " + clientSocket.getInetAddress());

                    in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    out = new PrintWriter(clientSocket.getOutputStream(), true);

                    String line;
                    while ((line = in.readLine()) != null) {
                        try {
                            long start = System.currentTimeMillis();
                            System.out.println("Message received: " + line);
                            JSONObject obj = new JSONObject(line);
                            String type = obj.optString("type");
                            String encodedRoom = obj.getString("room");
                            String decodedRoom = new String(android.util.Base64.decode(encodedRoom, android.util.Base64.DEFAULT));
                            Long chatId = Long.parseLong(decodedRoom);
                            String encodedMsg = obj.getString("data");
                            String decodedMsg = new String(android.util.Base64.decode(encodedMsg, android.util.Base64.DEFAULT));
                            if ("image".equals(type)) {
                                SendPhoto(decodedRoom, decodedMsg);
                            } else {
                                SendMessage(NOTI_REF, chatId, decodedMsg);
                            }

                            long end = System.currentTimeMillis();

                            Map mmap = new HashMap<String, Object>();
                            mmap.put("success", true);
                            mmap.put("time", end - start);
                            String successJson = new JSONObject(mmap).toString();

                            out.println(successJson);
                        } catch (Exception e) {
                            Map map = new HashMap<String, Object>();
                            map.put("success", false);
                            map.put("error", e.toString());

                            out.println(new JSONObject(map).toString());
                        }
                    }
                } catch (IOException e) {
                    System.err.println("IO Exception in client connection: " + e.toString());
                } finally {
                    try {
                        if (out != null) {
                            out.close();
                        }
                        if (in != null) {
                            in.close();
                        }
                        if (clientSocket != null) {
                            clientSocket.close();
                        }
                    } catch (IOException e) {
                        System.err.println("Error closing socket resources: " + e.toString());
                    }
                    System.out.println("Client disconnected");
                }
            }
        } catch (IOException e) {
            System.err.println("Could not listen on port " + PORT + ": " + e.toString());
            System.exit(1);
        } finally {
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    System.err.println("Error closing server socket: " + e.toString());
                }
            }
            kakaoDb.closeConnection();
            dbWatcherThread.interrupt();
            try {
                dbWatcherThread.join();
            } catch (InterruptedException e) {
                System.err.println("Error joining DB watcher thread: " + e.toString());
            }
        }
    }


    private static void checkDbChanges(SendMsgDB.KakaoDB kakaoDb, SendMsgDB.ObserverHelper observerHelper) {
        File watchFile = new File(WATCH_FILE);
        long currentModifiedTime = watchFile.lastModified();

        if (currentModifiedTime > lastModifiedTime) {
            lastModifiedTime = currentModifiedTime;
            System.out.println("Database file changed detected at: " + new java.util.Date(currentModifiedTime));
            observerHelper.checkChange(kakaoDb, WATCH_FILE);
        }
    }


    private static void SendMessage(String notiRef, Long chatId, String msg) throws Exception {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName("com.kakao.talk", "com.kakao.talk.notification.NotificationActionService"));

        intent.putExtra("noti_referer", notiRef);
        intent.putExtra("chat_id", chatId);
        intent.setAction("com.kakao.talk.notification.REPLY_MESSAGE");

        Bundle results = new Bundle();
        results.putCharSequence("reply_message", msg);

        RemoteInput remoteInput = new RemoteInput.Builder("reply_message").build();
        RemoteInput[] remoteInputs = new RemoteInput[]{remoteInput};
        RemoteInput.addResultsToIntent(remoteInputs, intent, results);
        activityManager.startService(
                null,
                intent,
                intent.getType(),
                false,
                "com.android.shell",
                null,
                -2
        );
    }

    private static void SendPhoto(String room, String base64ImageDataString) throws Exception {
        byte[] decodedImage = android.util.Base64.decode(base64ImageDataString, android.util.Base64.DEFAULT);
        String timestamp = String.valueOf(System.currentTimeMillis());
        File picDir = new File("/sdcard/Android/data/com.kakao.talk/files");
        if (!picDir.exists()) {
            picDir.mkdirs();
        }
        File imageFile = new File(picDir, timestamp + ".png");
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(imageFile);
            fos.write(decodedImage);
            fos.flush();
        } catch (IOException e) {
            System.err.println("Error saving image to file: " + e.toString());
            throw e;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    System.err.println("Error closing FileOutputStream: " + e.toString());
                }
            }
        }

        Uri imageUri = Uri.fromFile(imageFile);

        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(imageUri);
        try {
            activityManager.broadcastIntent(
                    null,
                    mediaScanIntent,
                    null,
                    null,
                    0,
                    null,
                    null,
                    null,
                    -1,
                    null,
                    false,
                    false,
                    -2
            );
            System.out.println("Media scanner broadcast intent sent for: " + imageUri.toString());
        } catch (Exception e) {
            System.err.println("Error broadcasting media scanner intent: " + e.toString());
            throw e;
        }

        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SENDTO);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_STREAM, imageUri);
        intent.putExtra("key_id", Long.parseLong(room));
        intent.putExtra("key_type", 1);
        intent.putExtra("key_from_direct_share", true);
        intent.setPackage("com.kakao.talk");

        try {
            activityManager.startActivityAsUserWithFeature(
                    null,
                    "com.android.shell",
                    null,
                    intent,
                    intent.getType(),
                    null, null, 0, 0,
                    null,
                    null,
                    -2
            );
        } catch (Exception e) {
            System.err.println("Error starting activity for sending image: " + e.toString());
            throw e;
        }
    }

    // --- Inner Class: KakaoDecrypt ---
    static class KakaoDecrypt {
        private static final java.util.Map<String, byte[]> keyCache = new java.util.HashMap<>();
        private static long BOT_USER_ID;

        static {
            try {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new FileReader(CONFIG_FILE_PATH))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                }
                JSONObject config = new JSONObject(sb.toString());
                BOT_USER_ID = config.getLong("bot_id");
            } catch (IOException | org.json.JSONException e) {
                System.err.println("Error reading config.json or parsing bot_id: " + e.toString());
                BOT_USER_ID = 0;
            }
        }


        private static String incept(int n) {
            String[] dict1 = {"adrp.ldrsh.ldnp", "ldpsw", "umax", "stnp.rsubhn", "sqdmlsl", "uqrshl.csel", "sqshlu", "umin.usubl.umlsl", "cbnz.adds", "tbnz",
                    "usubl2", "stxr", "sbfx", "strh", "stxrb.adcs", "stxrh", "ands.urhadd", "subs", "sbcs", "fnmadd.ldxrb.saddl",
                    "stur", "ldrsb", "strb", "prfm", "ubfiz", "ldrsw.madd.msub.sturb.ldursb", "ldrb", "b.eq", "ldur.sbfiz", "extr",
                    "fmadd", "uqadd", "sshr.uzp1.sttrb", "umlsl2", "rsubhn2.ldrh.uqsub", "uqshl", "uabd", "ursra", "usubw", "uaddl2",
                    "b.gt", "b.lt", "sqshl", "bics", "smin.ubfx", "smlsl2", "uabdl2", "zip2.ssubw2", "ccmp", "sqdmlal",
                    "b.al", "smax.ldurh.uhsub", "fcvtxn2", "b.pl"};
            String[] dict2 = {"saddl", "urhadd", "ubfiz.sqdmlsl.tbnz.stnp", "smin", "strh", "ccmp", "usubl", "umlsl", "uzp1", "sbfx",
                    "b.eq", "zip2.prfm.strb", "msub", "b.pl", "csel", "stxrh.ldxrb", "uqrshl.ldrh", "cbnz", "ursra", "sshr.ubfx.ldur.ldnp",
                    "fcvtxn2", "usubl2", "uaddl2", "b.al", "ssubw2", "umax", "b.lt", "adrp.sturb", "extr", "uqshl",
                    "smax", "uqsub.sqshlu", "ands", "madd", "umin", "b.gt", "uabdl2", "ldrsb.ldpsw.rsubhn", "uqadd", "sttrb",
                    "stxr", "adds", "rsubhn2.umlsl2", "sbcs.fmadd", "usubw", "sqshl", "stur.ldrsh.smlsl2", "ldrsw", "fnmadd", "stxrb.sbfiz",
                    "adcs", "bics.ldrb", "l1ursb", "subs.uhsub", "ldurh", "uabd", "sqdmlal"};
            String word1 = dict1[n % dict1.length];
            String word2 = dict2[(n + 31) % dict2.length];
            return word1 + '.' + word2;
        }

        private static byte[] genSalt(long user_id, int encType) {
            if (user_id <= 0) {
                return new byte[16];
            }

            String[] prefixes = {"", "", "12", "24", "18", "30", "36", "12", "48", "7", "35", "40", "17", "23", "29",
                    "isabel", "kale", "sulli", "van", "merry", "kyle", "james", "maddux",
                    "tony", "hayden", "paul", "elijah", "dorothy", "sally", "bran",
                    incept(830819), "veil"};
            String saltStr;
            try {
                saltStr = prefixes[encType] + user_id;
                saltStr = saltStr.substring(0, Math.min(saltStr.length(), 16));
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new IllegalArgumentException("Unsupported encoding type " + encType, e);
            }
            saltStr = saltStr + "\0".repeat(Math.max(0, 16 - saltStr.length()));
            return saltStr.getBytes(StandardCharsets.UTF_8);
        }

        private static void pkcs16adjust(byte[] a, int aOff, byte[] b) {
            int x = (b[b.length - 1] & 0xff) + (a[aOff + b.length - 1] & 0xff) + 1;
            a[aOff + b.length - 1] = (byte) (x % 256);
            x = x >> 8;
            for (int i = b.length - 2; i >= 0; i--) {
                x = x + (b[i] & 0xff) + (a[aOff + i] & 0xff);
                a[aOff + i] = (byte) (x % 256);
                x = x >> 8;
            }
        }

        private static byte[] deriveKey(byte[] passwordBytes, byte[] saltBytes, int iterations, int dkeySize) throws Exception {
            String password = new String(passwordBytes, StandardCharsets.US_ASCII) + "\0";
            byte[] passwordUTF16BE = password.getBytes(StandardCharsets.UTF_16BE);

            MessageDigest hasher = MessageDigest.getInstance("SHA-1");
            int digestSize = hasher.getDigestLength();
            int blockSize = 64;

            byte[] D = new byte[blockSize];
            for (int i = 0; i < blockSize; i++) {
                D[i] = 1;
            }
            byte[] S = new byte[blockSize * ((saltBytes.length + blockSize - 1) / blockSize)];
            for (int i = 0; i < S.length; i++) {
                S[i] = saltBytes[i % saltBytes.length];
            }
            byte[] P = new byte[blockSize * ((passwordUTF16BE.length + blockSize - 1) / blockSize)];
            for (int i = 0; i < P.length; i++) {
                P[i] = passwordUTF16BE[i % passwordUTF16BE.length];
            }

            byte[] I = new byte[S.length + P.length];
            System.arraycopy(S, 0, I, 0, S.length);
            System.arraycopy(P, 0, I, S.length, P.length);

            byte[] B = new byte[blockSize];
            int c = (dkeySize + digestSize - 1) / digestSize;

            byte[] dKey = new byte[dkeySize];
            for (int i = 1; i <= c; i++) {
                hasher = MessageDigest.getInstance("SHA-1");
                hasher.update(D);
                hasher.update(I);
                byte[] A = hasher.digest();

                for (int j = 1; j < iterations; j++) {
                    hasher = MessageDigest.getInstance("SHA-1");
                    hasher.update(A);
                    A = hasher.digest();
                }

                for (int j = 0; j < B.length; j++) {
                    B[j] = A[j % A.length];
                }

                for (int j = 0; j < I.length / blockSize; j++) {
                    pkcs16adjust(I, j * blockSize, B);
                }

                int start = (i - 1) * digestSize;
                if (i == c) {
                    System.arraycopy(A, 0, dKey, start, dkeySize - start);
                } else {
                    System.arraycopy(A, 0, dKey, start, A.length);
                }
            }

            return dKey;
        }

        public static String decrypt(int encType, String b64_ciphertext, long user_id) throws Exception {
            byte[] keyBytes = new byte[] {
                (byte)0x16, (byte)0x08, (byte)0x09, (byte)0x6f, (byte)0x02, (byte)0x17, (byte)0x2b, (byte)0x08,
                (byte)0x21, (byte)0x21, (byte)0x0a, (byte)0x10, (byte)0x03, (byte)0x03, (byte)0x07, (byte)0x06
            };
            byte[] ivBytes = new byte[] {
                (byte)0x0f, (byte)0x08, (byte)0x01, (byte)0x00, (byte)0x19, (byte)0x47, (byte)0x25, (byte)0xdc,
                (byte)0x15, (byte)0xf5, (byte)0x17, (byte)0xe0, (byte)0xe1, (byte)0x15, (byte)0x0c, (byte)0x35
            };

            byte[] salt = genSalt(user_id, encType);
            byte[] key;
            String saltStr = new String(salt, StandardCharsets.UTF_8);
            if (keyCache.containsKey(saltStr)) {
                key = keyCache.get(saltStr);
            } else {
                key = deriveKey(keyBytes, salt, 2, 32);
                keyCache.put(saltStr, key);
            }

            SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
            IvParameterSpec ivParameterSpec = new IvParameterSpec(ivBytes);
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");

            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);

            byte[] ciphertext = java.util.Base64.getDecoder().decode(b64_ciphertext);
            if (ciphertext.length == 0) {
                return b64_ciphertext;
            }
            byte[] padded;
            try {
                padded = cipher.doFinal(ciphertext);
            } catch (javax.crypto.BadPaddingException e) {
                System.err.println("BadPaddingException during decryption, possibly due to incorrect key or data. Returning original ciphertext.");
                return b64_ciphertext;
            }


            int paddingLength = padded[padded.length - 1];
            if (paddingLength <= 0 || paddingLength > cipher.getBlockSize()) {
                throw new IllegalArgumentException("Invalid padding length: " + paddingLength);
            }

            byte[] plaintextBytes = new byte[padded.length - paddingLength];
            System.arraycopy(padded, 0, plaintextBytes, 0, plaintextBytes.length);


            return new String(plaintextBytes, StandardCharsets.UTF_8);

        }

        public static String encrypt(int encType, String plaintext, long user_id) throws Exception {
            byte[] keyBytes = new byte[] {
                (byte)0x16, (byte)0x08, (byte)0x09, (byte)0x6f, (byte)0x02, (byte)0x17, (byte)0x2b, (byte)0x08,
                (byte)0x21, (byte)0x21, (byte)0x0a, (byte)0x10, (byte)0x03, (byte)0x03, (byte)0x07, (byte)0x06
            };
            byte[] ivBytes = new byte[] {
                (byte)0x0f, (byte)0x08, (byte)0x01, (byte)0x00, (byte)0x19, (byte)0x47, (byte)0x25, (byte)0xdc,
                (byte)0x15, (byte)0x5, (byte)0x17, (byte)0xe0, (byte)0xe1, (byte)0x15, (byte)0x0c, (byte)0x35
            };

            byte[] salt = genSalt(user_id, encType);
            byte[] key;
            String saltStr = new String(salt, StandardCharsets.UTF_8);
            if (keyCache.containsKey(saltStr)) {
                key = keyCache.get(saltStr);
            } else {
                key = deriveKey(keyBytes, salt, 2, 32);
                keyCache.put(saltStr, key);
            }
            SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
            IvParameterSpec ivParameterSpec = new IvParameterSpec(ivBytes);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            String b64_ciphertext = java.util.Base64.getEncoder().encodeToString(ciphertext);
            return b64_ciphertext;
        }
    }

    // --- Inner Class: KakaoDB ---
    static class KakaoDB extends SendMsgDB.KakaoDecrypt {
        private JSONObject config;
        private String DB_PATH;
        private long BOT_ID;
        private String BOT_NAME;
        private SQLiteDatabase db = null;

        public KakaoDB() {
            try {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new FileReader(CONFIG_FILE_PATH))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                }
                config = new JSONObject(sb.toString());
                DB_PATH = "/data/data/com.kakao.talk/databases";
                BOT_ID = config.getLong("bot_id");
                BOT_NAME = config.getString("bot_name");

                db = SQLiteDatabase.openDatabase(DB_PATH + "/KakaoTalk.db", null, SQLiteDatabase.OPEN_READWRITE);

                db.execSQL("ATTACH DATABASE '" + DB_PATH + "/KakaoTalk2.db' AS db2");


            } catch (SQLiteException e) {
                System.err.println("SQLiteException: " + e.getMessage());
                System.err.println("You don't have a permission to access KakaoTalk Database.");
                System.exit(1);
            }  catch (IOException e) {
                System.err.println("IO Exception reading config.json: " + e.toString());
                System.exit(1);
            } catch (JSONException e) {
                System.err.println("JSON parsing error in config.json: " + e.toString());
                System.exit(1);
            }
        }

        public List<String> getColumnInfo(String table) {
            List<String> cols = new ArrayList<>();
            Cursor cursor = null;
            try {
                cursor = db.rawQuery("SELECT * FROM " + table + " LIMIT 1", null);
                if (cursor != null && cursor.moveToFirst()) {
                    String[] columnNames = cursor.getColumnNames();
                    for (String columnName : columnNames) {
                        cols.add(columnName);
                    }
                }
            } catch (SQLiteException e) {
                System.err.println("Error in getColumnInfo for table " + table + ": " + e.getMessage());
                return new ArrayList<>();
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            return cols;
        }


        public List<String> getTableInfo() {
            List<String> tables = new ArrayList<>();
            Cursor cursor = null;
            try {
                cursor = db.rawQuery("SELECT name FROM sqlite_schema WHERE type='table'", null);
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        tables.add(cursor.getString(0));
                    }
                }
            } catch (SQLiteException e) {
                System.err.println("Error in getTableInfo: " + e.getMessage());
                return new ArrayList<>();
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            return tables;
        }

        public String getNameOfUserId(long userId) {
            String dec_row_name = null;
            Cursor cursor = null;
            try {
                String sql;
                String[] stringUserId = {Long.toString(userId)};
                if (checkNewDb()) {
                    sql = "WITH info AS (SELECT ? AS user_id) " +
                            "SELECT COALESCE(open_chat_member.nickname, friends.name) AS name, " +
                            "COALESCE(open_chat_member.enc, friends.enc) AS enc " +
                            "FROM info " +
                            "LEFT JOIN db2.open_chat_member ON open_chat_member.user_id = info.user_id " +
                            "LEFT JOIN db2.friends ON friends.id = info.user_id;";
                } else {
                    sql = "SELECT name, enc FROM db2.friends WHERE id = ?";
                }
                cursor = db.rawQuery(sql, stringUserId);

                if (cursor != null && cursor.moveToNext()) {
                    String row_name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                    String enc = cursor.getString(cursor.getColumnIndexOrThrow("enc"));
                    dec_row_name = SendMsgDB.KakaoDecrypt.decrypt(Integer.parseInt(enc), row_name, KakaoDecrypt.BOT_USER_ID);
                }

            } catch (SQLiteException e) {
                System.err.println("Error in getNameOfUserId: " + e.getMessage());
                return "";
            } catch (Exception e) {
                System.err.println("Decryption error in getNameOfUserId: " + e.getMessage());
                return "";
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            return dec_row_name;
        }


        public String[] getUserInfo(long chatId, long userId) {
            String sender;
            if (userId == BOT_ID) {
                sender = BOT_NAME;
            } else {
                sender = getNameOfUserId(userId);
            }

            String room = sender;
            Cursor cursor = null;
            try {
                String sql = "SELECT name FROM db2.open_link WHERE id = (SELECT link_id FROM chat_rooms WHERE id = ?)";
                String[] selectionArgs = {String.valueOf(chatId)};
                cursor = db.rawQuery(sql, selectionArgs);

                if (cursor != null && cursor.moveToNext()) {
                    room = cursor.getString(0);
                }
            } catch (SQLiteException e) {
                System.err.println("Error in getUserInfo: " + e.getMessage());
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            return new String[]{room, sender};
        }

        public Map<String, Object> getRowFromLogId(long logId) {
            Map<String, Object> rowMap = new HashMap<>();
            Cursor cursor = null;
            try {
                String sql = "SELECT * FROM chat_logs WHERE id = ?";
                String[] selectionArgs = {String.valueOf(logId)};
                cursor = db.rawQuery(sql, selectionArgs);
                if (cursor != null && cursor.moveToNext()) {
                    String[] columnNames = cursor.getColumnNames();
                    for (String columnName : columnNames) {
                        int columnIndex = cursor.getColumnIndexOrThrow(columnName);
                        rowMap.put(columnName, cursor.getString(columnIndex));
                    }
                }
            } catch (SQLiteException e) {
                System.err.println("Error in getRowFromLogId: " + e.getMessage());
                return null;
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            return rowMap;
        }


        public Map<String, Object> logToDict(long logId) {
            Map<String, Object> dict = new HashMap<>();
            Cursor cursor = null;
            try {
                String sql = "SELECT * FROM chat_logs ORDER BY _id DESC LIMIT 1";
                cursor = db.rawQuery(sql, null);
                if (cursor != null && cursor.moveToNext()) {
                    String[] columnNames = cursor.getColumnNames();
                    for (String columnName : columnNames) {
                        int columnIndex = cursor.getColumnIndexOrThrow(columnName);
                        dict.put(columnName, cursor.getString(columnIndex));
                    }
                }
            } catch (SQLiteException e) {
                System.err.println("Error in logToDict (getLastLog): " + e.getMessage());
                return null;
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            return dict;
        }


        public boolean checkNewDb() {
            boolean isNewDb = false;
            Cursor cursor = null;
            try {
                cursor = db.rawQuery("SELECT name FROM db2.sqlite_master WHERE type='table' AND name='open_chat_member'", null);
                isNewDb = cursor.getCount() > 0;
            } catch (SQLiteException e) {
                System.err.println("Error in checkNewDb: " + e.getMessage());
                return false;
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            return isNewDb;
        }

        public void closeConnection() {
            if (db != null && db.isOpen()) {
                db.close();
                System.out.println("Database connection closed.");
            }
        }

        public SQLiteDatabase getConnection() {
            return this.db;
        }
    }

    // --- Inner Class: ObserverHelper ---
    static class ObserverHelper {
        private long lastLogId = 0;
        private JSONObject config;
        private long BOT_ID;
        private String BOT_NAME;
        private String BOT_IP;
        private int BOT_SOCKET_PORT;
        private String WEB_SERVER_ENDPOINT; // added web server endpoint

        public ObserverHelper() {
            try {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new FileReader(CONFIG_FILE_PATH))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                }
                config = new JSONObject(sb.toString());
                BOT_ID = config.getLong("bot_id");
                BOT_NAME = config.getString("bot_name");
                BOT_IP = config.getString("bot_ip"); // keep bot_ip for possible other usages, as commented in user's config example
                BOT_SOCKET_PORT = config.getInt("bot_socket_port");
                WEB_SERVER_ENDPOINT = config.getString("web_server_endpoint"); // read web_server_endpoint

            } catch (IOException e) {
                System.err.println("IO Exception reading config.json: " + e.toString());
            } catch (JSONException e) {
                System.err.println("JSON parsing error in config.json: " + e.toString());
            }
        }

        private String makePostData(String decMsg, String room, String sender, JSONObject js) throws JSONException {
            JSONObject data = new JSONObject();
            data.put("msg", decMsg);
            data.put("room", room);
            data.put("sender", sender);
            data.put("json", js);
            return data.toString();
        }

        public void checkChange(SendMsgDB.KakaoDB db, String watchFile) {
            if (lastLogId == 0) {
                Map<String, Object> lastLog = db.logToDict(0);
                if (lastLog != null && lastLog.containsKey("_id")) {
                    lastLogId = Long.parseLong((String)lastLog.get("_id"));
                } else {
                    lastLogId = 0;
                }
                System.out.println("Initial lastLogId: " + lastLogId);
                return;
            }

            String sql = "select * from chat_logs where _id > ? order by _id asc";
            Cursor res = null;
            try {
                String[] selectionArgs = {String.valueOf(lastLogId)};
                res = db.getConnection().rawQuery(sql, selectionArgs);
                List<String> description = new ArrayList<>();
                if (res.getColumnNames() != null) {
                    for (String columnName : res.getColumnNames()) {
                        description.add(columnName);
                    }
                }


                while (res != null && res.moveToNext()) {
                    long currentLogId = res.getLong(res.getColumnIndexOrThrow("_id"));
                    if (currentLogId > lastLogId) {
                        lastLogId = currentLogId;
                        JSONObject logJson = new JSONObject();
                        for (String columnName : description) {
                            try {
                                logJson.put(columnName, res.getString(res.getColumnIndexOrThrow(columnName)));
                            } catch (JSONException e) {
                                System.err.println("JSONException while adding log data to JSON object: " + e.getMessage());
                                continue;
                            }
                        }


                        String enc_msg = res.getString(res.getColumnIndexOrThrow("message"));
                        long user_id = res.getLong(res.getColumnIndexOrThrow("user_id"));
                        int encType = 0;
                        try {
                            JSONObject v = new JSONObject(res.getString(res.getColumnIndexOrThrow("v")));
                            encType = v.getInt("enc");
                        } catch (JSONException e) {
                            System.err.println("Error parsing 'v' JSON for encType: " + e.getMessage());
                            encType = 0;
                        }


                        String dec_msg;
                        try {
                            dec_msg = SendMsgDB.KakaoDecrypt.decrypt(encType, enc_msg, user_id);
                        } catch (Exception e) {
                            System.err.println("Decryption error for logId " + currentLogId + ": " + e.toString());
                            dec_msg = "[Decryption Failed]";
                        }
                        long chat_id = res.getLong(res.getColumnIndexOrThrow("chat_id"));
                        String[] userInfo = db.getUserInfo(chat_id, user_id);
                        String room = userInfo[0];
                        String sender = userInfo[1];
                        if (room.equals(BOT_NAME)) {
                            room = sender;
                        }
                        String postData;
                        try {
                            postData = makePostData(dec_msg, room, sender, logJson);
                            // modified to use WEB_SERVER_ENDPOINT
                            sendPostRequest(WEB_SERVER_ENDPOINT, postData);
                        } catch (JSONException e) {
                            System.err.println("JSON error creating post data: " + e.getMessage());
                        }
                        System.out.println("New message from " + sender + " in " + room + ": " + dec_msg);
                    }
                }
            } catch (SQLiteException e) {
                System.err.println("SQL error in checkChange: " + e.getMessage());
            } finally {
                if (res != null) {
                    res.close();
                }
            }
        }

        private void sendPostRequest(String urlStr, String jsonData) {
            System.out.println("Sending HTTP POST request to: " + urlStr);
            System.out.println("JSON Data being sent: " + jsonData);
            try {
                URL url = new URL(urlStr);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("POST");

                con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                con.setRequestProperty("Accept", "application/json");

                con.setDoOutput(true);

                String postData = "data=" + URLEncoder.encode(jsonData, StandardCharsets.UTF_8.toString());
                try (OutputStream os = con.getOutputStream()) {
                    byte[] input = postData.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = con.getResponseCode();
                System.out.println("HTTP Response Code: " + responseCode);

                try (BufferedReader br = new BufferedReader(
                        new java.io.InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    String responseBody = response.toString();
                    System.out.println("HTTP Response Body: " + responseBody);
                } catch (IOException e) {
                    System.err.println("Error reading HTTP response body: " + e.getMessage());
                }
                con.disconnect();

            } catch (IOException e) {
                System.err.println("IO error sending POST request: " + e.getMessage());
            }
        }
    }
}
