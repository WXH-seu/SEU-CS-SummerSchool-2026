package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** Fields that a student is allowed to maintain in his or her own record. */
public final class StudentProfileUpdateRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String gender;
    private final String birthDate;
    private final String phone;
    private final String email;

    public StudentProfileUpdateRequest(String gender, String birthDate,
                                       String phone, String email) {
        this.gender = gender;
        this.birthDate = birthDate;
        this.phone = phone;
        this.email = email;
    }

    public String getGender() { return gender; }
    public String getBirthDate() { return birthDate; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
}
