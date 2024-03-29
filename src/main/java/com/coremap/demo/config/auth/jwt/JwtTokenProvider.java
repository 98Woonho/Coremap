package com.coremap.demo.config.auth.jwt;


import com.coremap.demo.config.auth.PrincipalDetails;
import com.coremap.demo.domain.dto.UserDto;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;

@Slf4j
@Component
// JWT 토근 생성/관리/인증 하는 객체
public class JwtTokenProvider {

    //Key 저장
    private final Key key;

    String url = "jdbc:mysql://localhost:3306/coremap_db";
    String username = "root";
    String password = "1234";
    Connection conn;
    PreparedStatement pstmt;
    ResultSet rs;

    public JwtTokenProvider() throws Exception {

        Class.forName("com.mysql.cj.jdbc.Driver");
        conn = DriverManager.getConnection(url, username, password);
        pstmt = conn.prepareStatement("select * from signature");
        rs = pstmt.executeQuery();

        if (rs.next()) {

            byte[] keyByte = rs.getBytes("signature");                 //DB로 서명Key꺼내옴
            this.key = Keys.hmacShaKeyFor(keyByte);                                 //this.key에 저장
        } else {
            byte[] keyBytes = KeyGenerator.getKeygen();     //난수키값 가져오기
            this.key = Keys.hmacShaKeyFor(keyBytes);        // 생성된 키를 사용하여 HMAC SHA(암호화알고리즘)알고리즘에 기반한 Key 객체 생성
            pstmt = conn.prepareStatement("insert into signature values(?,now())");

            pstmt.setBytes(1, keyBytes);
            pstmt.executeUpdate();
        }

    }

    // 유저 정보를 가지고 AccessToken, RefreshToken 을 생성하는 메서드
    public TokenInfo generateToken(Authentication authentication) {
        // 권한 가져오기
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
        long now = (new Date()).getTime();

        PrincipalDetails principalDetails = (PrincipalDetails) authentication.getPrincipal();
        UserDto userDto = principalDetails.getUserDto();

        // Access Token 생성
        Date accessTokenExpiresIn = new Date(now + 3600000);
        String accessToken = Jwts.builder()
                .setSubject(authentication.getName())
                .claim("username", authentication.getName())
                .claim("password", userDto.getPassword())
                .claim("nickname", userDto.getNickname())
                .claim("name", userDto.getName())
                .claim("contactCompanyCode", userDto.getContactCompanyCode())
                .claim("contact", userDto.getContact())
                .claim("addressPostal", userDto.getAddressPostal())
                .claim("addressPrimary", userDto.getAddressPrimary())
                .claim("addressSecondary", userDto.getAddressSecondary())
                .claim("role", userDto.getRole())
                .claim("suspended", userDto.isSuspended())
                .claim("auth", authorities)
                .claim("principal", authentication.getPrincipal())
                .claim("credentials", authentication.getCredentials())
                .claim("details", authentication.getDetails())
                .claim("provider", userDto.getProvider())
                .claim("accessToken", principalDetails.getAccessToken())
                .setExpiration(accessTokenExpiresIn)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        // Refresh Token 생성
        String refreshToken = Jwts.builder()
                .setExpiration(new Date(now + 3600000)) // 1시간: 60 * 60 * 1000 = 3600000
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        return TokenInfo.builder()
                .grantType("Bearer")
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    // JWT 토큰을 복호화하여 토큰에 들어있는 정보를 꺼내는 메서드
    public Authentication getAuthentication(String accessToken) {
        // 토큰 복호화
        Claims claims = parseClaims(accessToken);

        if (claims.get("auth") == null) {
            throw new RuntimeException("권한 정보가 없는 토큰입니다.");
        }
        // 클레임에서 권한 정보 가져오기
        Collection<? extends GrantedAuthority> authorities =
                Arrays.stream(claims.get("auth").toString().split(","))
                        .map(auth -> new SimpleGrantedAuthority(auth))
                        .collect(Collectors.toList());

        String username = claims.getSubject();

        //JWT Added

        String password = (String) claims.get("password");
        String nickname = (String) claims.get("nickname");
        String name = (String) claims.get("name");
        String contactCompanyCode = (String) claims.get("contactCompanyCode");
        String contact = (String) claims.get("contact");
        String addressPostal = (String) claims.get("addressPostal");
        String addressPrimary = (String) claims.get("addressPrimary");
        String addressSecondary = (String) claims.get("addressSecondary");
        String role = (String) claims.get("role");
        boolean isSuspended = (boolean) claims.get("suspended");
        String provider = (String) claims.get("provider");
        String auth = (String) claims.get("auth");
        String oauthAccessToken = (String) claims.get("accessToken");
        UserDto userDto = new UserDto();
        userDto.setUsername(username);
        userDto.setPassword(password);
        userDto.setNickname(nickname);
        userDto.setName(name);
        userDto.setContactCompanyCode(contactCompanyCode);
        userDto.setContact(contact);
        userDto.setAddressPostal(addressPostal);
        userDto.setAddressPrimary(addressPrimary);
        userDto.setAddressSecondary(addressSecondary);
        userDto.setRole(role);
        userDto.setSuspended(isSuspended);
        userDto.setProvider(provider);
        userDto.setRole(auth);

        PrincipalDetails principalDetails = new PrincipalDetails();
        principalDetails.setUserDto(userDto);
        principalDetails.setAccessToken(oauthAccessToken);   //Oauth AccessToken


        //JWT + NO REMEMBERME
        UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken =
                new UsernamePasswordAuthenticationToken(principalDetails, claims.get("credentials"), authorities);
        return usernamePasswordAuthenticationToken;

    }

    public Claims parseClaims(String accessToken) {
        try {
            return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(accessToken).getBody();
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        }
    }

    // 토큰 정보를 검증하는 메서드
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (MalformedJwtException e) {
            log.info("Invalid JWT Token", e);
        } catch (ExpiredJwtException e) {
            log.info("Expired JWT Token", e);
            return false;
        } catch (UnsupportedJwtException e) {
            log.info("Unsupported JWT Token", e);
        } catch (IllegalArgumentException e) {
            log.info("JWT claims string is empty.", e);
        }
        return false;
    }
}