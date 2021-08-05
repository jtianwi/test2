/*
 * (C) Copyright 2019 Johnson Controls, Inc.
 * Use or copying of all or any part of the document, except as
 * permitted by the License Agreement is prohibited.
 */

package com.jci.xlauncher.security;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.text.TextUtils;

import com.jci.cola.utils.ColaLog;
import com.jci.cola.utils.StringUtil;
import com.jci.xlauncher.database.SecurityBean;
import com.jci.xlauncher.database.SecurityDBDao;
import com.jci.xlauncher.database.SimpleUserBean;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableEntryException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidParameterSpecException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import static android.security.keystore.KeyProperties.BLOCK_MODE_GCM;
import static android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE;
import static android.security.keystore.KeyProperties.KEY_ALGORITHM_AES;
import static android.security.keystore.KeyProperties.PURPOSE_DECRYPT;
import static android.security.keystore.KeyProperties.PURPOSE_ENCRYPT;
import static com.jci.xlauncher.security.SecurityUtils.SECURITY_LEVEL_ADMIN;
import static com.jci.xlauncher.security.SecurityUtils.SECURITY_LEVEL_NONE;

public class XmsSecurityManager {
    private static final String LOG_TAG = XmsSecurityManager.class.getSimpleName();
    private int mLoginId = NONE_LOGIN_ID;
    private static XmsSecurityManager mInstance;
    //If the devices is doomed, we need lock the device
    public static final int DOOMED_LOGIN_ID = -100;
    //If there is no user login, reset the login id to NONE_LOGIN_ID,
    // then the device will be locked, if there is already user exists
    public static final int NONE_LOGIN_ID = -1;
    /**
     * If in any case we failed to initialize keystore, keys, or we think we are being hacked,
     * this flag is set and we go into fully locked mode.
     */
    private boolean mDoomed = false;
    /**
     * The symmetric key to encrypt/decrypt data
     */
    private static final String KEY_ALIAS_PRIMARY_ENCRYPT = "rmkey_encrypt";
    /**
     * this is the bytes we used to save the IV length field in the encrypted data.
     * GCM spec. requires IV to be less than 256B, so a 4 bytes integer is large enough
     */
    private static final int GCM_IV_LENGTH_FILED_LENGTH = 4; // bytes
    /**
     * this is the bytes we used to save the TAG length field in the encrypted data.
     */
    private static final int GCM_TAG_LENGTH_FIELD_LENGTH = 4; // bytes
    /**
     * The algorithm we used to decrypt credentials encrypted at development phase
     */
    private static final String CIPHER_TRANSFORM_KEY = "AES/GCM/NoPadding";
    /**
     * The Android Keystore
     */
    private KeyStore mKeyStore;

    public static XmsSecurityManager getInstance() {
        return mInstance;
    }

    public static void setInstance(XmsSecurityManager instance) {
        mInstance = instance;
    }

    public XmsSecurityManager(Context ctx) {
        /* construct or load the keystore */
        try {
            mKeyStore = KeyStore.getInstance("AndroidKeyStore");
            mKeyStore.load(null);

            boolean created = initializeEncryptionKey();
            if (!created) {
                /* we have the keys but the file is missing? Attacker might have deleted
                 * the key file. We'll go into fully locked mode.
                 * TODO: Is it even possible the attacker deletes keystore file? */
                mDoomed = true;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Is the system locked, in Triatek project,
     * if there is at least one user created, the system will be locked
     *
     * @return
     */
    public boolean isLocked() {
        return SecurityDBDao.getInstance().getUserCount() > 0;
    }

    /**
     * Login with the username or password or pin
     *
     * @param userName username
     * @param password password or pin
     * @return true if login success
     */
    public boolean loginByName(String userName, String password) {
        if (TextUtils.isEmpty(userName) || TextUtils.isEmpty(password)) {
            return false;
        }
        SecurityBean bean = SecurityDBDao.getInstance().queryByName(userName);
        if (bean != null) {
            byte[] pwd = bean.getAccessLevel()
                    == SECURITY_LEVEL_ADMIN ? bean.getPassword() : bean.getPin();
            if (Arrays.equals(password.getBytes(), decrypt(pwd))) {
                setLoginId(bean.getId());
                return true;
            }
        }
        return false;
    }

    /**
     * Login with id and password or pin
     *
     * @param userId   userId
     * @param password password or pin
     * @return true if login success
     */
    public boolean loginById(int userId, String password) {
        if (verify(userId, password)) {
            setLoginId(userId);
            return true;
        }

        return false;
    }

    /**
     * Verify the userId and password
     *
     * @param userId   userId
     * @param password password or pin
     * @return true if verify success
     */
    public boolean verify(int userId, String password) {
        if (TextUtils.isEmpty(password)) {
            return false;
        }
        SecurityBean bean = SecurityDBDao.getInstance().queryBeanById(userId);
        if (bean != null) {
            byte[] pwd = bean.getAccessLevel()
                    == SECURITY_LEVEL_ADMIN ? bean.getPassword() : bean.getPin();
            if (Arrays.equals(password.getBytes(), decrypt(pwd))) {
                return true;
            }
        }
        return false;
    }

    public void logout() {
        mLoginId = NONE_LOGIN_ID;
    }

    public int getLoginId() {
        return mLoginId;
    }

    private void setLoginId(int id) {
        mLoginId = id;
    }

    /**
     * When the first admin is created, it should login as default
     */
    public void loginDefaultAdmin() {
        if (mLoginId == NONE_LOGIN_ID && SecurityDBDao.getInstance().getAdminCount() == 1) {
            List<SimpleUserBean> list = SecurityDBDao.getInstance()
                    .querySimpleUserBeanAboveLevel(SECURITY_LEVEL_ADMIN);
            if (list != null && list.size() > 0) {
                setLoginId(list.get(0).getId());
            }
        }
    }

    /**
     * Get the current login user's access level
     *
     * @return the login user's level
     */
    public int getLoginLevel() {
        if (mLoginId != NONE_LOGIN_ID) {
            SimpleUserBean bean = SecurityDBDao.getInstance().querySimpleUserBeanById(mLoginId);
            if (bean != null) {
                return bean.getAccessLevel();
            }
        }
        return SECURITY_LEVEL_NONE;
    }

    public boolean isDoomed() {
        return mDoomed;
    }

    /**
     * Helper method to encrypt passed in plain text with the provided key.
     * We are using AES with GCM mode, hence the data is both encrypted and authenticated.
     *
     * @param pt the plain text to be encrypted.
     * @return encrypted data in following format:
     * -------------------------------------------------------------------
     * | IV length | Tag length |       IV       |       Cipher Text
     * |     * -------------------------------------------------------------------
     * |    4B     |     4B     |     12 B       |      variable len
     * |
     */
    public byte[] encrypt(byte[] pt) {
        if (pt == null) {
            return null;
        }
        try {
            SecretKey key = (SecretKey) mKeyStore.getKey(KEY_ALIAS_PRIMARY_ENCRYPT, null);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM_KEY);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            GCMParameterSpec spec = cipher.getParameters().getParameterSpec(GCMParameterSpec.class);
            byte[] iv = spec.getIV();
            int tLen = spec.getTLen();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            CipherOutputStream cipherOutputStream = new CipherOutputStream(
                    outputStream, cipher);
            cipherOutputStream.write(pt);
            cipherOutputStream.close();
            /* the final returned bytes are a concatenation of IV and CT */
            byte[] ctData = outputStream.toByteArray();
            byte[] ret = new byte[GCM_IV_LENGTH_FILED_LENGTH
                    + GCM_TAG_LENGTH_FIELD_LENGTH
                    + iv.length
                    + ctData.length];
            StringUtil.int2Bytes(iv.length, ret, 0);
            StringUtil.int2Bytes(tLen, ret, GCM_IV_LENGTH_FILED_LENGTH);
            System.arraycopy(iv, 0,
                    ret, (GCM_IV_LENGTH_FILED_LENGTH + GCM_TAG_LENGTH_FIELD_LENGTH),
                    iv.length);
            System.arraycopy(ctData, 0,
                    ret, (GCM_IV_LENGTH_FILED_LENGTH + GCM_TAG_LENGTH_FIELD_LENGTH + iv.length),
                    ctData.length);
            return ret;
        } catch (NoSuchAlgorithmException
                | NoSuchPaddingException
                | InvalidKeyException
                | InvalidParameterSpecException
                | IOException e) {
            ColaLog.e(LOG_TAG, "failed to encrypt: " + e);
            return null;
        } catch (UnrecoverableKeyException e) {
            ColaLog.e(LOG_TAG, "UnrecoverableKeyException : " + e);
            return null;
        } catch (KeyStoreException e) {
            ColaLog.e(LOG_TAG, "KeyStoreException : " + e);
            return null;
        }
    }

    /**
     * Helper method to decrypt passed in cipher text with the provided key
     *
     * @param data the byte stream that was generated by encrypt.
     * @return the decrypted plain text
     */
    public byte[] decrypt(byte[] data) {
        if (data == null) {
            return null;
        }
        try {
            SecretKey key = (SecretKey) mKeyStore.getKey(KEY_ALIAS_PRIMARY_ENCRYPT, null);
            /* firstly, retrieve IV and tag len from data and re-construct GCM param spec. */
            int ivLen = StringUtil.bytes2Int(data, 0);
            int tLen = StringUtil.bytes2Int(data, GCM_IV_LENGTH_FILED_LENGTH);
            byte[] iv = new byte[ivLen];
            System.arraycopy(data,
                    (GCM_IV_LENGTH_FILED_LENGTH + GCM_TAG_LENGTH_FIELD_LENGTH),
                    iv, 0, ivLen);

            GCMParameterSpec spec = new GCMParameterSpec(tLen, iv);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM_KEY);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);
            int headerLen = GCM_IV_LENGTH_FILED_LENGTH + GCM_TAG_LENGTH_FIELD_LENGTH + ivLen;
            CipherInputStream cipherInputStream = new CipherInputStream(
                    new ByteArrayInputStream(data, headerLen, data.length - headerLen),
                    cipher);
            ArrayList<Byte> values = new ArrayList<>();
            int nextByte;
            while ((nextByte = cipherInputStream.read()) != -1) {
                values.add((byte) nextByte);
            }
            cipherInputStream.close();
            byte[] bytes = new byte[values.size()];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = values.get(i);
            }
            return bytes;
        } catch (NoSuchAlgorithmException
                | NoSuchPaddingException
                | InvalidKeyException
                | InvalidAlgorithmParameterException
                | IOException e) {
            ColaLog.e(LOG_TAG, "failed to decrypt: " + e);
            return null;
        } catch (UnrecoverableKeyException e) {
            ColaLog.e(LOG_TAG, "UnrecoverableKeyException: " + e);
            return null;
        } catch (KeyStoreException e) {
            ColaLog.e(LOG_TAG, "KeyStoreException: " + e);
            return null;
        }
    }

    /**
     * Check if we already have primary encryption key and create it if not.
     *
     * @return true if the key is created, false if the key already exists.
     * @throws KeyStoreException
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @throws CertificateException
     * @throws UnrecoverableEntryException
     * @throws NoSuchProviderException
     * @throws InvalidAlgorithmParameterException
     */
    private boolean initializeEncryptionKey() throws
            KeyStoreException,
            IOException,
            NoSuchAlgorithmException,
            CertificateException,
            UnrecoverableEntryException,
            NoSuchProviderException,
            InvalidAlgorithmParameterException {
        SecretKey key = (SecretKey) mKeyStore.getKey(KEY_ALIAS_PRIMARY_ENCRYPT, null);
        if (key == null) {
            /* we don't have the encryption key yet, generate one */
            KeyGenerator keyGenerator
                    = KeyGenerator.getInstance(KEY_ALGORITHM_AES, "AndroidKeyStore");
            keyGenerator.init(new KeyGenParameterSpec.Builder(
                    KEY_ALIAS_PRIMARY_ENCRYPT,
                    PURPOSE_ENCRYPT | PURPOSE_DECRYPT)
                    .setBlockModes(BLOCK_MODE_GCM)
                    .setEncryptionPaddings(ENCRYPTION_PADDING_NONE)
                    .build());
            /* it's generated in keystore and we can retrieve it back with the alias */
            keyGenerator.generateKey();
            return true;
        }
        return false;
    }

}
