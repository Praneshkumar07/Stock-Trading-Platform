package com.trading.userservice.dto;

import jakarta.persistence.Column;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Email is Required")
    @Email(message = "Invalid Email Format")
    private String email;

    @NotBlank(message = "Passord is Required")
    @Size(min=6,message = "AtLeast 6 char Requird")
    private String password;

    @NotBlank(message = "firstName is Required")

    private String firstName;


    @NotBlank(message = "lastName is Required")

    private String lastName;

    private BigDecimal initialDeposit=BigDecimal.valueOf(10000);
}
