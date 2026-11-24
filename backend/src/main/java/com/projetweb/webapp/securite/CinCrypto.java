package com.projetweb.webapp.securite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Chiffrement au repos des numéros de CIN (cahier des charges §14 F3 et §18) : AES-256-GCM,
 * clé fournie par la variable d'environnement CIN_ENCRYPTION_KEY, jamais écrite dans le code.
 * Une empreinte HMAC-SHA256 (non réversible) sert à vérifier l'unicité sans déchiffrer.
 * Les mêmes primitives chiffrent la copie immuable des données (snapshot) à la clôture.
 */
@Component
public class CinCrypto {
    private static final Logger log = LoggerFactory.getLogger(CinCrypto.class);
    private static final String PREFIXE = "v1:";
    private static volatile CinCrypto instance;

    private final SecretKeySpec cle;
    private final SecretKeySpec cleHmac;
    private final SecureRandom random = new SecureRandom();

    public CinCrypto(@Value("${app.cin.key:}") String cleBase64, @Value("${app.jwt.secret}") String jwtSecret) {
        byte[] k;
        if (cleBase64 == null || cleBase64.isBlank()) {
            log.warn("CIN_ENCRYPTION_KEY non définie : clé de développement dérivée de JWT_SECRET. "
                    + "Définissez CIN_ENCRYPTION_KEY (openssl rand -base64 32) en production.");
            k = sha256(("limtic-cin|" + jwtSecret).getBytes(StandardCharsets.UTF_8));
        } else {
            k = Base64.getDecoder().decode(cleBase64.trim());
            if (k.length != 32) throw new IllegalStateException("CIN_ENCRYPTION_KEY doit faire 32 octets (Base64).");
        }
        this.cle = new SecretKeySpec(k, "AES");
        this.cleHmac = new SecretKeySpec(sha256(("hmac|" + Base64.getEncoder().encodeToString(k)).getBytes(StandardCharsets.UTF_8)), "HmacSHA256");
        instance = this;
    }

    public static CinCrypto get() {
        if (instance == null) throw new IllegalStateException("CinCrypto non initialisé");
        return instance;
    }

    public String chiffrer(String clair) {
        if (clair == null) return null;
        return PREFIXE + Base64.getEncoder().encodeToString(chiffrerOctets(clair.getBytes(StandardCharsets.UTF_8)));
    }

    public String dechiffrer(String valeur) {
        if (valeur == null) return null;
        if (!valeur.startsWith(PREFIXE)) return valeur; // valeur héritée non chiffrée
        return new String(dechiffrerOctets(Base64.getDecoder().decode(valeur.substring(PREFIXE.length()))), StandardCharsets.UTF_8);
    }

    public byte[] chiffrerOctets(byte[] clair) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, cle, new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(clair);
            return ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array();
        } catch (Exception e) {
            throw new IllegalStateException("Chiffrement impossible", e);
        }
    }

    public byte[] dechiffrerOctets(byte[] donnees) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, cle, new GCMParameterSpec(128, donnees, 0, 12));
            return c.doFinal(donnees, 12, donnees.length - 12);
        } catch (Exception e) {
            throw new IllegalStateException("Déchiffrement impossible (clé CIN_ENCRYPTION_KEY modifiée ?)", e);
        }
    }

    /** Empreinte non réversible, pour l'unicité et la détection des doublons. */
    public String empreinte(String cin) {
        if (cin == null || cin.isBlank()) return null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(cleHmac);
            return HexFormat.of().formatHex(mac.doFinal(cin.trim().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Signature HMAC générique (liens de téléchargement). */
    public byte[] signer(byte[] donnees) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(cleHmac);
            return mac.doFinal(donnees);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] sha256(byte[] b) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(b);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
