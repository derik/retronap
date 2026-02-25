package com.deriklima.retronap.model;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "napster_users_info")
@Getter
@Setter
@NoArgsConstructor
public class UserInfo {

  @Id
  @Column(name = "napster_user_id")
  private UUID id;

  @OneToOne
  @MapsId
  @JoinColumn(name = "napster_user_id")
  private User user;

  @Column(length = 20)
  private String zipCode;

  @Column(length = 20)
  private String country;

  @Column(length = 25)
  private String age;

  @Column(length = 10)
  private String gender;

  @Column(length = 30)
  private String income;

  @Column(length = 40)
  private String education;

  public UserInfo(
      User user,
      String zipCode,
      String country,
      String age,
      String gender,
      String income,
      String education) {
    this.user = user;
    this.zipCode = zipCode;
    this.country = country;
    this.age = age;
    this.gender = gender;
    this.income = income;
    this.education = education;
  }
}
