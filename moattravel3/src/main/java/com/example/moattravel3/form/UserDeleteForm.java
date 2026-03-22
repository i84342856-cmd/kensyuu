package com.example.moattravel3.form;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserDeleteForm {
	private Integer id;
	private String name;
    private String furigana;
    private String postalCode;
    private String address;
    private String phoneNumber;
    private String email;
}
