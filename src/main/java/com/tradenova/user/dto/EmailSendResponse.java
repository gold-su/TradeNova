package com.tradenova.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class EmailSendResponse {
    private String message;
    private String devCode; //개발환경에서만 내려주기 용도 (나중에 제거)
}
