package com.docwork.service;

import com.docwork.dto.LoginDTO;
import com.docwork.dto.LoginVO;
import com.docwork.dto.RegisterDTO;

public interface AuthService {
    LoginVO login(LoginDTO dto);
    void register(RegisterDTO dto);
    LoginVO refreshToken(String refreshToken);
    void logout(String token);
}
