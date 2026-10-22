package com.ysd.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Map;

public class JwtUtil {

    /**
     * 由配置中的秘钥字符串推导出 256 位的 HMAC-SHA 秘钥。
     *
     * <p>jjwt 0.12 起，{@code Keys.hmacShaKeyFor} 会对秘钥长度做强校验：HS256 要求至少 256 位（32 字节），
     * 否则直接抛 {@code WeakKeyException}。而本项目的配置秘钥（如 {@code change-me-in-env}）不足 32 字节，
     * 0.9.1 时代允许短秘钥。这里统一做一次 SHA-256 摘要，得到的 32 字节结果对任意长度的配置秘钥都成立，
     * 从而不必改动 application.yml 与调用方。
     *
     * <p>注意：签名用的实际密钥与 0.9.1 时代不同，升级前签发的 token 会失效，需重新登录。
     */
    private static SecretKey hmacKey(String secretKey) {
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                    .digest(secretKey.getBytes(StandardCharsets.UTF_8));
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持 SHA-256", e);
        }
    }

    /**
     * 生成jwt
     * 使用Hs256算法
     *
     * @param secretKey jwt秘钥
     * @param ttlMillis jwt过期时间(毫秒)
     * @param claims    设置的信息
     * @return
     */
    public static String createJWT(String secretKey, long ttlMillis, Map<String, Object> claims) {
        long expMillis = System.currentTimeMillis() + ttlMillis;
        Date exp = new Date(expMillis);

        return Jwts.builder()
                // 私有声明必须最先设置，写在标准声明赋值之后会覆盖掉标准声明
                .claims(claims)
                .expiration(exp)
                .signWith(hmacKey(secretKey), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Token解密
     *
     * @param secretKey jwt秘钥 此秘钥一定要保留好在服务端, 不能暴露出去, 否则sign就可以被伪造, 如果对接多个客户端建议改造成多个
     * @param token     加密后的token
     * @return
     */
    public static Claims parseJWT(String secretKey, String token) {
        return Jwts.parser()
                .verifyWith(hmacKey(secretKey))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

}
