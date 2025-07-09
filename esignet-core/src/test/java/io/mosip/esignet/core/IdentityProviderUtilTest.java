/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.security.auth.x500.X500Principal;

import io.mosip.esignet.core.constants.ErrorConstants;
import org.bouncycastle.x509.X509V3CertificateGenerator;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;

import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class IdentityProviderUtilTest {


    @Test
    public void validateRedirectURIPositiveTest() throws EsignetException {
        IdentityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/**"),
                "https://api.dev.mosip.net/home/test");
        IdentityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/home/test"),
                "https://api.dev.mosip.net/home/test");
        IdentityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/home/test?"),
                "https://api.dev.mosip.net/home/test1");
        IdentityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/home/*"),
                "https://api.dev.mosip.net/home/werrrwqfdsfg5fgs34sdffggdfgsdfg?state=reefdf");
        IdentityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/home/t*"),
                "https://api.dev.mosip.net/home/testament?rr=rrr");
        IdentityProviderUtil.validateRedirectURI(Arrays.asList("io.mosip.residentapp://oauth"),
                "io.mosip.residentapp://oauth");
    }

    @Test
    public void validateRedirectURINegativeTest() {
        try {
            IdentityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/home/test1"),
                    "https://api.dev.mosip.net/home/test");
            Assertions.fail();
        } catch (EsignetException e) {}

        try {
            IdentityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/home/test1"),
                    "https://api.dev.mosip.net/home/TEST1");
            Assertions.fail();
        } catch (EsignetException e) {}

        try {
            IdentityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/home**"),
                    "https://api.dev.mosip.net/home/TEST1");
            Assertions.fail();
        } catch (EsignetException e) {}

        try {
            IdentityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/*"),
                    "https://api.dev.mosip.net/home/TEST1");
            Assertions.fail();
        } catch (EsignetException e) {}

        try {
            IdentityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/home/t*"),
                    "https://api.dev.mosip.net/home/TEST1");
            Assertions.fail();
        } catch (EsignetException e) {}
        
        try {
            IdentityProviderUtil.validateRedirectURI(Arrays.asList("test-url"),
                    "https://api.dev.mosip.net/home/TEST1");
            Assertions.fail();
        } catch (EsignetException e) {}
        try {
        IdentityProviderUtil.validateRedirectURI(Arrays.asList("HTTPS://DEV.MOSIP.NET/home"),
                "https://dev.mosip.net/home");
            Assertions.fail();
        } catch (EsignetException e) {}
    }
    
    @Test
    public void test_dateTime() {
    	Assertions.assertNotNull(IdentityProviderUtil.getUTCDateTime());
    	Assertions.assertNotNull(IdentityProviderUtil.getUTCDateTimeWithNanoSeconds());
    	Assertions.assertTrue(IdentityProviderUtil.getEpochSeconds() > 0);
    }
    
    @Test
    public void test_splitAndTrimValue() {
    	Assertions.assertTrue(IdentityProviderUtil.splitAndTrimValue("test split", " ").length == 2);
    	Assertions.assertTrue(IdentityProviderUtil.splitAndTrimValue(null, " ").length == 0);
    }
    
    @Test
    public void test_generateHexEncodedHash() {
    	Assertions.assertNotNull(IdentityProviderUtil.generateHexEncodedHash("sha-256", "test-hexencoded-hash"));
    	try {
    		IdentityProviderUtil.generateHexEncodedHash("test-algorithm", "test");
            Assertions.fail();
        } catch (EsignetException e) {}
    }
    
    @Test
    public void test_generateB64EncodedHash() {
    	Assertions.assertNotNull(IdentityProviderUtil.generateB64EncodedHash("sha-256", "test-b64-hash"));
    	try {
    		IdentityProviderUtil.generateB64EncodedHash("test-algorithm", "test");
            Assertions.fail();
        } catch (EsignetException e) {}
    }
    
    @Test
    public void test_encodeDecode() {
    	Assertions.assertNotNull(IdentityProviderUtil.b64Encode("test-encode-string"));
    	Assertions.assertNotNull(IdentityProviderUtil.b64Encode("test-bytes".getBytes()));
    	Assertions.assertNotNull(IdentityProviderUtil.b64Decode("test-decode-string"));
    }
    
    @Test
    public void test_generateOIDCAtHash() {
    	Assertions.assertNotNull(IdentityProviderUtil.generateOIDCAtHash("test-access-token"));
    }
    
    @Test
    public void test_createTransactionId() {
    	Assertions.assertNotNull(IdentityProviderUtil.createTransactionId(null));
    	Assertions.assertNotNull(IdentityProviderUtil.createTransactionId(IdentityProviderUtil.getUTCDateTimeWithNanoSeconds()));
    }
    
    @Test
    public void test_generateSalt() {
    	Assertions.assertNotNull(IdentityProviderUtil.generateSalt(2048));
    }
    
    @Test
    public void test_getJWKString() {
    	Assertions.assertNotNull(IdentityProviderUtil.getJWKString((Map<String, Object>) generateJWK_RSA().getRequiredParams()));
    	try {
    		IdentityProviderUtil.getJWKString(new HashMap<String, Object>());
    		Assertions.fail();
        } catch (EsignetException e) {}
    }
    
    @Test
    public void test_getCertificateThumbprint() throws Exception {
    	Assertions.assertNotNull(IdentityProviderUtil.getCertificateThumbprint("SHA-256", getCertificate()));
    	try {
    		IdentityProviderUtil.getCertificateThumbprint("test", getCertificate());
    		Assertions.fail();
        } catch (EsignetException e) {
            Assertions.assertEquals(e.getMessage(),ErrorConstants.INVALID_ALGORITHM);
        }
    }

    @Test
    public void test_generateThumbprintByCertificate()throws EsignetException{
        String thumbprint="YfRxd-cG6urE1r_Ij7yRwMzt0JHoIadZ-lqkdlE0FYo";
        String certificateString="-----BEGIN CERTIFICATE-----\n" +
                "MIICrzCCAZegAwIBAgIGAYohPDZlMA0GCSqGSIb3DQEBCwUAMBMxETAPBgNVBAMT\n" +
                "CE1vY2stSURBMB4XDTIzMDgyMzAxNDE0OFoXDTIzMDkwMjAxNDE0OFowHjEcMBoG\n" +
                "A1UEAxMTU2lkZGhhcnRoIEsgTWFuc291cjCCASIwDQYJKoZIhvcNAQEBBQADggEP\n" +
                "ADCCAQoCggEBANcfMOxGBmCZ0sn/Fr1ZvGE1nl0zOxTdhSPkLxgHpq09minv6HsJ\n" +
                "Om9Y5FBbPQavSYdliFO/61VlOMnKYpCKXx+Rf/+QCBgx4/Wc57bu3xmNtxl76ARh\n" +
                "HnRGWEz0UH/JX2mX1XgnHSBMgS8F+ckQuvoA7vN/LTIxXl89OkUyHa7HIylvQpsS\n" +
                "8bv7qXohaHf6IjbQGbjdSpKlLhNgOtgPWHxQu6nzBqtTR/Ks1S1zutfv8p5gip4F\n" +
                "vLGQ68Il+Nco6vcvKmYIqBZQyMwMBGxYzwmDFeLMBjMi5LR3Qikj/BaH2aVPX8Zg\n" +
                "D2TqeUvYzobV8Xc+qV6XnGkQdRNKDBKYGmcCAwEAATANBgkqhkiG9w0BAQsFAAOC\n" +
                "AQEAo7Tjx59tq1hSv6XaGw2BUnBKPqyGpmHDb9y6VXQXkI2YAZghtDoebeppCnrU\n" +
                "d5219dwEgM0FoUW3pumMN/rM5NGXljktMp5xhyYU1rbBwvj8mGg9YTv7oUk1IQ0K\n" +
                "keecYS5ZFmbz0N5CgbitJginXn4HKTPd9CEXYEBtkO7C7Onl0LbnH0g2grVuNGqH\n" +
                "pD5P6TbGJzwrlnxstOCyCVMmRfVIpQFTygMpNjDQTlsXwWt4ZEf/ZiB2W4zYcDMk\n" +
                "cXGZv5rZBqX/uuptptN7HhYD45Ir4ZAyNFlZuPusQvxiSm674bCkV3lN6oH0Jw2/\n" +
                "dHnX5TRuFoits1+jx3cNSBHmjA==\n" +
                "-----END CERTIFICATE-----\n";
        Assertions.assertEquals(thumbprint,IdentityProviderUtil.generateCertificateThumbprint(certificateString));
        try {
            IdentityProviderUtil.generateCertificateThumbprint("test");
            Assertions.fail();
        } catch (EsignetException e) {
            Assertions.assertEquals(e.getMessage(),ErrorConstants.INVALID_CERTIFICATE);
        }
    }
    
    public static JWK generateJWK_RSA() {
        // Generate the RSA key pair
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair keyPair = gen.generateKeyPair();
            // Convert public key to JWK format
            return new RSAKey.Builder((RSAPublicKey)keyPair.getPublic())
                    .privateKey((RSAPrivateKey)keyPair.getPrivate())
                    .keyUse(KeyUse.SIGNATURE)
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }
    
    private X509Certificate getCertificate() throws Exception {
		X509V3CertificateGenerator generator = new X509V3CertificateGenerator();
		X500Principal dnName = new X500Principal("CN=Test");
		generator.setSubjectDN(dnName);
		generator.setIssuerDN(dnName); // use the same
		generator.setNotBefore(new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000));
		generator.setNotAfter(new Date(System.currentTimeMillis() + 24 * 365 * 24 * 60 * 60 * 1000));
		generator.setPublicKey(generateJWK_RSA().toRSAKey().toPublicKey());
		generator.setSignatureAlgorithm("SHA256WITHRSA");
		generator.setSerialNumber(new BigInteger(String.valueOf(System.currentTimeMillis())));
		return generator.generate(generateJWK_RSA().toRSAKey().toPrivateKey());
	}

    @Test
    public void testHash() {
        String hash = IdentityProviderUtil.generateB64EncodedHash("SHA-256", """
                {"authFactors":[[{"count":0,"subTypes":null,"type":"WLA"}],[{"count":0,"subTypes":null,"type":"KBI"}],[{"count":0,"subTypes":null,"type":"OTP"}],[{"count":0,"subTypes":null,"type":"PWD"}]],"authorizeScopes":[],"clientName":{"@none":"Adams, Bernhard and Abernathy"},"configs":{"auth.factor.kbi.field-details":{"errors":{"required":{"ara":"هذه الخانة مطلوبه","eng":"This field is required","hin":"यह फ़ील्ड आवश्यक है","kan":"ಈ ಕ್ಷೇತ್ರ ಕಡ್ಡಾಯವಾಗಿದೆ","khm":"ត្រូវការបំពេញវាលនេះ","tam":"இந்த புலம் தேவை"}},"language":{"langCodeMap":{"ara":"ar","eng":"en","hin":"hi","kan":"kn","khm":"km","tam":"ta"},"mandatory":["eng"],"optional":["khm"]},"schema":[{"alignmentGroup":"groupA","controlType":"textbox","id":"individualId","label":{"ara":"رقم السياسة","eng":"Policy Number","hin":"पॉलिसी नंबर","kan":"ಪಾಲಿಸಿ ಸಂಖ್ಯೆ","khm":"លេខគោលនយោបាយ","tam":"பாலிசி எண்"},"placeholder":{"ara":"أدخل رقم السياسة","eng":"Enter Policy Number","hin":"पॉलिसी नंबर डालें","kan":"ಪಾಲಿಸಿ ಸಂಖ್ಯೆಯನ್ನು ನಮೂದಿಸಿ","khm":"បញ្ចូលលេខគោលការណ៍","tam":"பாலிசி எண்ணை உள்ளிடவும்"},"required":true},{"alignmentGroup":"groupB","controlType":"textbox","id":"fullName","label":{"ara":"الاسم الكامل","eng":"Full Name","hin":"पूरा नाम","kan":"ಪೂರ್ಣ ಹೆಸರು","khm":"ឈ្មោះពេញ","tam":"முழு பெயர்"},"placeholder":{"ara":"الاسم الكامل","eng":"Enter Full Name","hin":"पूरा नाम दर्ज करें","kan":"ಪೂರ್ಣ ಹೆಸರನ್ನು ನಮೂದಿಸಿ","khm":"ឈ្មោះពេញ","tam":"முழு பெயரை உள்ளிடவும்"},"required":true,"validators":[{"error":{"ara":"مسموح فقط بالأحرف الخميرية","eng":"Allowed only Khmer characters","hin":"केवल खमेर वर्णों की अनुमति है","kan":"ಖಮೇರ್ ಅಕ್ಷರಗಳ ಮಾತ್ರ ಅನುಮತಿಸಲಾಗಿದೆ","khm":"អនុញ្ញាតតែតួអក្សរខ្មែរ​ប៉ុណ្ណោះ","tam":"கம்மர் எழுத்துகள் மட்டுமே அனுமதிக்கப்படும்"},"regex":"^[\\\\u1780-\\\\u17FF\\\\u19E0-\\\\u19FF\\\\u1A00-\\\\u1A9F\\\\u0020]{1,30}$"}]}]},"auth.factor.kbi.individual-id-field":"individualId","auth.txnid.length":"10","captcha.enable":"","captcha.sitekey":"6LfQKwYrAAAAAIGieYOcG_c4OE6efqFdWTcowi3F","clientAdditionalConfig":{"consent_expire_in_mins":20,"forgot_pwd_link_required":true,"purpose":{"subTitle":{"@none":"subtitle"},"title":{"@none":"title"},"type":"verify"},"signup_banner_required":true,"userinfo_response_type":"JWE"},"consent.screen.timeout-buffer-in-secs":5,"consent.screen.timeout-in-secs":600,"eKYC-steps.config":"https://signup.es-dev.mosip.net/identity-verification","error.banner.close-timer":10,"forgot-password.config":{"forgot-password":true,"forgot-password.url":"https://signup.es-dev.mosip.net/reset-password"},"linked-transaction-expire-in-secs":240,"login-id.options":[{"id":"mobile","maxLength":9,"postfix":"","prefixes":[{"label":"KHM","regex":"^\\\\d{8,9}$","value":"+855"},{"label":"IND","maxLength":"","regex":"","value":"+91"}],"regex":"^\\\\d*$","svg":"mobile_icon"},{"id":"nrc","maxLength":"","postfix":"","prefixes":"","regex":"","svg":"nrc_id_icon"},{"id":"vid","postfix":"","svg":"vid_icon"},{"id":"email","postfix":"@email","regex":"","svg":"email_icon"}],"otp.length":6,"password.max-length":20,"password.regex":"^.{8,20}$","preauth-screen-timeout-in-secs":600,"resend.otp.delay.secs":180,"sbi.bio.subtypes.finger":"UNKNOWN","sbi.bio.subtypes.iris":"UNKNOWN","sbi.capture.count.face":1,"sbi.capture.count.finger":1,"sbi.capture.count.iris":1,"sbi.capture.score.face":70,"sbi.capture.score.finger":70,"sbi.capture.score.iris":70,"sbi.env":"Developer","sbi.port.range":"4501-4600","sbi.timeout.CAPTURE":30,"sbi.timeout.DINFO":30,"sbi.timeout.DISC":30,"send.otp.channels":"email,phone","signup.config":{"signup.banner":true,"signup.url":"https://signup.es-dev.mosip.net/signup"},"username.input-type":"text","username.max-length":16,"username.postfix":"","username.prefix":"+855","username.regex":"^[1-9][0-9]{7,11}$","wallet.config":[{"wallet.deep-link-uri":"io.mosip.residentapp.inji://wla-auth?linkCode=LINK_CODE&linkExpireDateTime=LINK_EXPIRE_DT","wallet.download-uri":"#","wallet.footer":true,"wallet.logo-url":"/images/qr_code.png","wallet.name":"Inji"}],"wallet.qr-code-buffer-in-secs":10},"credentialScopes":[],"essentialClaims":["name","phone_number"],"logoUrl":"http://placeimg.com/640/480","redirectUri":"https://healthservices.es-dev.mosip.net/userprofile","transactionId":"RvSL_Nwi9QSpguOm9z-XOPnNgOyo-Am_KHo2a30nkvA","voluntaryClaims":["birthdate","address","gender","email","picture"]}""");
        System.out.println(hash);
    }
}
