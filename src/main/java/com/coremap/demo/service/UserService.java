package com.coremap.demo.service;


import com.coremap.demo.domain.dto.EmailAuthDto;
import com.coremap.demo.domain.dto.UserDto;
import com.coremap.demo.domain.entity.ContactCompany;
import com.coremap.demo.domain.entity.EmailAuth;
import com.coremap.demo.domain.entity.User;
import com.coremap.demo.domain.repository.ContactCompanyRepository;
import com.coremap.demo.domain.repository.EmailAuthRepository;
import com.coremap.demo.domain.repository.UserRepository;
import com.coremap.demo.utils.CryptoUtil;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

@Service
public class UserService {
    @Autowired
    private EmailAuthRepository emailAuthRepository;

    @Autowired
    private ContactCompanyRepository contactCompanyRepository;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    public User getUser(String username) {
        return userRepository.findById(username).get();
    }

    public List<ContactCompany> getAllContactCompanyList() {
        return contactCompanyRepository.findAll();
    }

    public List<User> getUserList(String name, String contact) {
        return userRepository.findByNameAndContact(name, contact);
    }

    @Transactional(rollbackFor = Exception.class)
    public String sendJoinEmail(EmailAuthDto emailAuthDto, boolean resetPassword) throws MessagingException {
        if (!resetPassword) {
            if (userRepository.existsById(emailAuthDto.getEmail())) {
                return "FAILURE_DUPLICATE_EMAIL";
            }
        }

        String code = RandomStringUtils.randomNumeric(6);
        emailAuthDto.setCode(code);
        emailAuthDto.setCreatedAt(LocalDateTime.now()); // 이메일이 보내졌을 때의 시간
        emailAuthDto.setExpiresAt(emailAuthDto.getCreatedAt().plus(5, ChronoUnit.MINUTES)); // 이메일을 보내고 난 뒤 5분 후의 시간
        emailAuthDto.setVerified(false);

        String salt = CryptoUtil.hashSha512(String.format("%s%s%f%f",
                emailAuthDto.getEmail(),
                code,
                Math.random(),
                Math.random()));
        emailAuthDto.setSalt(salt);

        Context context = new Context();
        context.setVariable("emailAuthDto", emailAuthDto);

        if(resetPassword) {
            String textHtml = this.templateEngine.process("user/resetPasswordEmail.html", context);

            MimeMessage message = this.mailSender.createMimeMessage();
            MimeMessageHelper mimemessageHelper = new MimeMessageHelper(message, false);
            mimemessageHelper.setTo(emailAuthDto.getEmail());
            mimemessageHelper.setSubject("[Coremap] 비밀번호 재설정 인증번호");
            mimemessageHelper.setText(textHtml, true);
            this.mailSender.send(message);

            EmailAuth emailAuth = EmailAuthDto.emailAuthDtoToEntity(emailAuthDto);

            emailAuthRepository.save(emailAuth);
        } else {
            String textHtml = this.templateEngine.process("user/joinEmail.html", context);

            MimeMessage message = this.mailSender.createMimeMessage();
            MimeMessageHelper mimemessageHelper = new MimeMessageHelper(message, false);
            mimemessageHelper.setTo(emailAuthDto.getEmail());
            mimemessageHelper.setSubject("[Coremap] 회원가입 인증번호");
            mimemessageHelper.setText(textHtml, true);
            this.mailSender.send(message);

            EmailAuth emailAuth = EmailAuthDto.emailAuthDtoToEntity(emailAuthDto);

            emailAuthRepository.save(emailAuth);
        }

        return "SUCCESS";
    }

    @Transactional(rollbackFor = Exception.class)
    public String verifyCode(EmailAuthDto emailAuthDto) {
        EmailAuth emailAuth = emailAuthRepository.findByEmailAndCodeAndSalt(emailAuthDto.getEmail(), emailAuthDto.getCode(), emailAuthDto.getSalt());

        if (emailAuth == null) {
            return "FAILURE_INVALID_CODE";
        }

        if (LocalDateTime.now().isAfter(emailAuth.getExpiresAt())) {
            return "FAILURE_EXPIRED";
        }

        emailAuth.setIsVerified(true);

        emailAuthRepository.save(emailAuth);

        return "SUCCESS";
    }

    public String confirmDuplicateNickname(String nickname) {
        List<User> userList = userRepository.findAll();

        for (User user : userList) {
            if (user.getNickname() != null) {
                if (user.getNickname().equals(nickname)) {
                    return "FAILURE_DUPLICATED_NICKNAME";
                }
            }
        }
        return "SUCCESS";
    }

    public String confirmEmptyUser(String username) {
        List<User> userList = userRepository.findAll();

        for (User user : userList) {
            if (user.getUsername().equals(username)) {
                return "SUCCESS";
            }
        }
        return "FAILURE_EMPTY_USERNAME";
    }

    @Transactional(rollbackFor = Exception.class)
    public String join(UserDto userDto) {
        userDto.setPassword(passwordEncoder.encode(userDto.getPassword()));
        ContactCompany contactCompany = contactCompanyRepository.findById(userDto.getContactCompanyCode()).get();

        User user = User.builder()
                .username(userDto.getUsername())
                .password(userDto.getPassword())
                .nickname(userDto.getNickname())
                .name(userDto.getName())
                .contactCompany(contactCompany)
                .contact(userDto.getContact())
                .addressPostal(userDto.getAddressPostal())
                .addressPrimary(userDto.getAddressPrimary())
                .addressSecondary(userDto.getAddressSecondary())
                .role("ROLE_USER")
                .isSuspended(false)
                .registeredAt(LocalDateTime.now())
                .build();

        userRepository.save(user);

        return "SUCCESS";
    }

    @Transactional(rollbackFor = Exception.class)
    public String resetPassword(UserDto userDto) {
        User user = userRepository.findById(userDto.getUsername()).get();

        if (passwordEncoder.matches(userDto.getPassword(), user.getPassword())) {
            return "FAILURE_SAME_PASSWORD";
        }

        userDto.setPassword(passwordEncoder.encode(userDto.getPassword()));
        user.setPassword(userDto.getPassword());

        userRepository.save(user);

        return "SUCCESS";
    }

    @Transactional(rollbackFor = Exception.class)
    public String modifyUser(UserDto userDto, String username) {
        User user = userRepository.findById(username).get();

        ContactCompany contactCompany = contactCompanyRepository.findById(userDto.getContactCompanyCode()).get();


        user.setNickname(userDto.getNickname());
        user.setName(userDto.getName());
        user.setContactCompany(contactCompany);
        user.setContact(userDto.getContact());
        user.setAddressPostal(userDto.getAddressPostal());
        user.setAddressPrimary(userDto.getAddressPrimary());
        user.setAddressSecondary(userDto.getAddressSecondary());

        userRepository.save(user);

        return "SUCCESS";
    }

    @Transactional(rollbackFor = Exception.class)
    public String deleteUser(String username) {
        userRepository.deleteById(username);

        return "SUCCESS";
    }
}
