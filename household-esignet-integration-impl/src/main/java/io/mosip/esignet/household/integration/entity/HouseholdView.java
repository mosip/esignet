package io.mosip.esignet.household.integration.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;


@Entity
@Table(name = "household")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HouseholdView {

    //    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "household_ID")
    private long householdId;

    @Column(name = "group_name")
    private String groupName;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "ID_number")
    private String idNumber;

    @Column(name = "password")
    private String password;

    @Column(name = "tamwini_consented")
    private boolean tamwiniConsented=true;
}
