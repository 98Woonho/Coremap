package com.coremap.demo.controller;

import com.coremap.demo.domain.dto.EmailAuthDto;
import com.coremap.demo.domain.dto.UserDto;
import com.coremap.demo.domain.entity.ContactCompany;
import com.coremap.demo.domain.entity.User;
import com.coremap.demo.domain.service.UserService;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@Slf4j
@RequestMapping(value = "user")
public class UserController {
    @Autowired
    private UserService userService;

    @GetMapping("login")
    public void getLogin(@RequestParam(value = "error", required = false) String error,
                         @RequestParam(value = "exception", required = false)
                         String exception,
                         @RequestParam(value = "username", required = false) String username,
                         Model model) {
        model.addAttribute("error", error);
        model.addAttribute("exception", exception);
        model.addAttribute("username", username);
    }

    @GetMapping(value = "confirmDuplication")
    @ResponseBody
    public String getConfirmDuplication(@RequestParam(value = "nickname") String nickname) {
        return this.userService.confirmDuplicateNickname(nickname);
    }

    @GetMapping("join")
    public void getJoin(Model model) {
        List<ContactCompany> contactCompanyList = userService.getAllContactCompanyList();

        model.addAttribute("contactCompanyList", contactCompanyList);
    }

    @PostMapping("join")
    @ResponseBody
    public String postJoin(UserDto userDto) throws Exception {
        return this.userService.join(userDto);
    }

    @PostMapping(value = "sendMail")
    @ResponseBody
    public String postSendMail(@RequestParam(value = "resetPassword", required = false) boolean resetPassword, EmailAuthDto emailAuthDto) throws MessagingException {
        String result = this.userService.sendJoinEmail(emailAuthDto, resetPassword);
        JSONObject responseObject = new JSONObject();
        responseObject.put("result", result);
        if (result.equals("SUCCESS")) {
            responseObject.put("salt", emailAuthDto.getSalt());
        }

        return responseObject.toString();
    }

    @PatchMapping(value = "sendMail")
    @ResponseBody
    public String patchSendMail(EmailAuthDto emailAuthDto) {
        return this.userService.verifyJoinEmail(emailAuthDto);
    }

    @GetMapping("findEmail")
    public void getFindEmail() {

    }

    @GetMapping("findEmailResult")
    public void getFindEmailResult(@RequestParam(value = "name") String name,
                                   @RequestParam(value = "contact") String contact,
                                   Model model) {
        List<User> userList = userService.getUserList(name, contact);

        model.addAttribute("userList", userList);
    }

    @GetMapping("resetPasswordStep1")
    public void getResetPasswordStep1() {

    }

    @GetMapping("resetPasswordStep2")
    public void getResetPasswordStep2(@RequestParam(value = "username") String username, Model model) {
        model.addAttribute("username", username);
    }

    @PostMapping("resetPasswordStep1")
    @ResponseBody
    public String postResetPasswordStep1(String username) {
        return userService.confirmEmptyUser(username);
    }

    @PostMapping("resetPasswordStep2")
    @ResponseBody
    public String postResetPasswordStep2(UserDto userDto) {
        return userService.resetPassword(userDto);
    }
}
