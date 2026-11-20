package com.trading.userservice.service;

import com.trading.userservice.entity.User;
import com.trading.userservice.dto.AuthResponse;
import com.trading.userservice.dto.RegisterRequest;
import com.trading.userservice.dto.UserResponse;
import com.trading.userservice.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;


import java.math.BigDecimal;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j

@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final KafkaTemplate<String,Object> kafkaTemplatea;

    public static final String USER_REGISTERED_TOPIC ="user.registered";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;


    public AuthResponse register(RegisterRequest request){
        log.info("Registering User :{}",request.getEmail());
        if(userRepository.existsByEmail(request.getEmail())){
            throw new RuntimeException("Email Already Registered");
        }
        User user=new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setWalletBalance(
                request.getInitialDeposit()==null?
                        BigDecimal.valueOf(10000):request.getInitialDeposit());
        User savedUser=userRepository.save(user);

        log.info("User Registered : {}",savedUser.getId());

        Map<String,Object> userRegisteredEvent=new HashMap<>();
        userRegisteredEvent.put("userId",savedUser.getId());
        userRegisteredEvent.put("userEmail",savedUser.getEmail());
        userRegisteredEvent.put("userFirstName",savedUser.getFirstName());
        userRegisteredEvent.put("userLastName",savedUser.getLastName());
        userRegisteredEvent.put("userWalletBalance",savedUser.getWalletBalance());

        kafkaTemplatea.send(USER_REGISTERED_TOPIC,savedUser.getId(),userRegisteredEvent);

        String token=generateToken(savedUser.getId(),savedUser.getEmail());
        String refreshToken = generateRefreshToken(savedUser.getId());
        return buildAuthResponse(savedUser,token,refreshToken);

    }

    public AuthResponse login(RegisterRequest request){
        log.info("Login Attempt : {}",request.getEmail());

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"+request.getEmail()));
        if(!passwordEncoder.matches(request.getPassword(),user.getPassword())){
            throw new RuntimeException("Invalid Creditials");
        }

        log.info("Login successful : {}",user.getId());
        String token=generateToken(user.getId(),user.getEmail());
        String refreshToken = generateRefreshToken(user.getId());
        return buildAuthResponse(user,token,refreshToken);
    }
    private String generateToken(String userId, String email){
        return Jwts.builder()
                .claim("userId", userId)
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(
                        System.currentTimeMillis() + jwtExpiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }
    private String generateRefreshToken(String userId){
        return Jwts.builder()
                .claim("userId", userId)
                .setSubject(userId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(
                        System.currentTimeMillis() + refreshExpiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }
    private Key getSigningKey(){
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }



    public UserResponse getByUserId(String userId){
       User user=userRepository.findById(userId).orElseThrow(()-> new RuntimeException("User Not Found"+userId));
       return mapToResponse(user);
    }


    public UserResponse addFunds(String userId,BigDecimal amount){
        User user=userRepository.findById(userId).orElseThrow(()->new RuntimeException("User not Found"+userId));
        user.setWalletBalance(user.getWalletBalance().add(amount));
        User savedUser=userRepository.save(user);
        log.info("Funds added {} to User {}",amount,userId);
        return mapToResponse(savedUser);
    }

    public UserResponse deductFunds(String userId,BigDecimal amount){
        User user=userRepository.findById(userId).orElseThrow(()->new RuntimeException("User not Found"+userId));
        if(user.getWalletBalance().compareTo(amount)<0){
            throw new RuntimeException("Insufficient Wallet Balance");
        }

        user.setWalletBalance(user.getWalletBalance().subtract(amount));
        User savedUser=userRepository.save(user);
        log.info("Funds deducted {} to User {}",amount,userId);
        return mapToResponse(savedUser);
    }
    public UserResponse creditFunds(String userId,BigDecimal amount){
        User user=userRepository.findById(userId).orElseThrow(()->new RuntimeException("User not Found"+userId));
        user.setWalletBalance(user.getWalletBalance().add(amount));
        User savedUser=userRepository.save(user);
        log.info("Funds Credited {} to User {}",amount,userId);
        return mapToResponse(savedUser);
    }

    private AuthResponse buildAuthResponse(User user,String token,String refreshToken){
        AuthResponse response=new AuthResponse();
        response.setId(user.getId());
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setWalletBalance(user.getWalletBalance());
        response.setAccessToken(token);
        response.setRefreshToken(refreshToken);
        return response;
    }
    private UserResponse mapToResponse(User user){
        UserResponse response=new UserResponse();
        response.setId(user.getId());
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setWalletBalance(user.getWalletBalance());
        response.setUserStatus(user.getUserStatus());
        response.setCreatedAt(user.getCreatedAt());
        return response;
    }

}
